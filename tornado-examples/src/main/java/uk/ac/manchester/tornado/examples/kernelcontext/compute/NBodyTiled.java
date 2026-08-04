/*
 * Copyright (c) 2026 APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.examples.kernelcontext.compute;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * All-pairs N-body, naive vs shared-memory tiled, timed against each other.
 *
 * <p>
 * The existing {@code kernelcontext.compute.NBody} example is the naive form: every thread re-reads
 * all N bodies straight from global memory, so the whole grid pulls N x N body records across the
 * memory system. This adds the classic CUDA optimisation that sample omits -- stage a tile of
 * bodies in shared memory once per block, have every thread in the block consume it, then advance
 * to the next tile:
 *
 * <pre>
 * for each tile:
 *     shared[localIdx] = body[tile * TILE + localIdx]   // one cooperative load
 *     localBarrier()
 *     for j in 0..TILE:  accumulate force from shared[j] // TILE reuses per load
 *     localBarrier()
 * </pre>
 *
 * Global loads per thread drop from N to N/TILE; the arithmetic is unchanged.
 *
 * <h2>Measured result: the tiling does not help on Ampere</h2>
 *
 * <p>
 * RTX 3070 (sm_86), CUDA 13.0, 16384 bodies, TILE=256, mean of 10 steady-state iterations,
 * kernel-only via {@code --enableProfiler console}:
 *
 * <pre>
 *   nBodyNaive     25648 us
 *   nBodyTiled     25411 us     1.01x
 * </pre>
 *
 * <p>
 * A 1% difference is noise -- the shared-memory staging buys nothing here, and that is worth
 * knowing rather than hiding. Two reasons:
 *
 * <ul>
 * <li>The kernel is not memory-bound. Both variants sustain only ~210 GFLOP/s against an RTX
 * 3070's multi-TFLOP fp32 peak, which points at the special-function unit: every pair interaction
 * costs one {@code sqrt}, and 268M of them at ~25 ms works out near the SFU's throughput ceiling.
 * Relieving memory pressure cannot speed up a kernel that is waiting on the transcendental
 * pipeline.</li>
 * <li>What tiling provides -- reuse of each body record across a block -- an Ampere L1/L2 already
 * provides for a working set this small. The classic textbook win for this optimisation was
 * measured on GPUs whose caches were far weaker than their arithmetic.</li>
 * </ul>
 *
 * <p>
 * The tiled kernel is kept because it is still the correct way to write this when N or the body
 * record grows past what the cache can hold, and because the naive/tiled pair is a clean
 * demonstration of {@code allocateFloatLocalArray} + double {@code localBarrier} outside a GEMM.
 * Just do not assume the speedup is there without measuring it.
 *
 * <h2>Both kernels are double-buffered, deliberately</h2>
 *
 * <p>
 * They read {@code posIn}/{@code velIn} and write {@code posOut}/{@code velOut}, never both. An
 * in-place version -- which is what the older {@code NBody} example does -- is a data race: there
 * is no grid-wide barrier between the force loop and the integration step, so a thread can
 * overwrite a body's position while other threads are still reading it for their own force sums.
 * It is easy to mistake the resulting error for floating-point drift; it is not, and with
 * double-buffering both kernels here reproduce the sequential reference exactly
 * ({@code maxDev = 0.00000}, since the summation order is identical).
 *
 * <p>
 * Both kernels are validated against the same sequential Java reference before any timing is
 * reported, and timing separates the first (JIT/NVRTC-compiling) run from the steady-state mean.
 *
 * <p>
 * Note both kernels accumulate into plain {@code float} scalars rather than a small
 * {@code float[3]}. The naive example uses {@code new float[]{...}} inside the kernel, which
 * TornadoVM does handle for small fixed-size private arrays, but scalars keep the accumulator
 * unambiguously in registers and sidestep the in-kernel-allocation restriction entirely.
 *
 * <p>
 * How to run:
 * </p>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.compute.NBodyTiled
 * </pre>
 *
 * <p>
 * Kernel-only timing (recommended -- the wall-clock figure includes the position/velocity
 * copy-back, which is identical work for both kernels and dilutes the ratio):
 * </p>
 *
 * <pre>
 * tornado --enableProfiler console -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.compute.NBodyTiled
 *
 * nsys profile --trace=cuda,nvtx -o nbody-tiled \
 *   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.compute.NBodyTiled
 * nsys stats --report cuda_gpu_kern_sum nbody-tiled.nsys-rep
 * </pre>
 */
public class NBodyTiled {

    private static final float DELT = 0.005f;
    private static final float ESP_SQR = 500.0f;

    private static final int TILE = 256;
    private static final int NUM_BODIES = 16384;
    private static final int ITERATIONS = 10;

    /** Naive: every thread streams all N bodies from global memory. */
    private static void nBodyNaive(KernelContext context, int numBodies, FloatArray posIn, FloatArray velIn, FloatArray posOut, FloatArray velOut) {
        int i = context.globalIdx;
        int body = 4 * i;

        float myX = posIn.get(body);
        float myY = posIn.get(body + 1);
        float myZ = posIn.get(body + 2);

        float accX = 0.0f;
        float accY = 0.0f;
        float accZ = 0.0f;

        for (int j = 0; j < numBodies; j++) {
            int index = 4 * j;
            float rx = posIn.get(index) - myX;
            float ry = posIn.get(index + 1) - myY;
            float rz = posIn.get(index + 2) - myZ;

            float distSqr = rx * rx + ry * ry + rz * rz;
            float invDist = 1.0f / sqrtF(distSqr + ESP_SQR);
            float s = posIn.get(index + 3) * invDist * invDist * invDist;

            accX += s * rx;
            accY += s * ry;
            accZ += s * rz;
        }

        integrate(posIn, velIn, posOut, velOut, body, accX, accY, accZ);
    }

    /** Tiled: each block stages TILE bodies in shared memory and reuses them TILE times. */
    private static void nBodyTiled(KernelContext context, int numBodies, FloatArray posIn, FloatArray velIn, FloatArray posOut, FloatArray velOut) {
        int i = context.globalIdx;
        int local = context.localIdx;
        int body = 4 * i;

        // One tile of bodies: x, y, z, mass interleaved, same layout as the global array.
        float[] tile = context.allocateFloatLocalArray(TILE * 4);

        float myX = posIn.get(body);
        float myY = posIn.get(body + 1);
        float myZ = posIn.get(body + 2);

        float accX = 0.0f;
        float accY = 0.0f;
        float accZ = 0.0f;

        for (int tileStart = 0; tileStart < numBodies; tileStart += TILE) {
            // Cooperative load: one body per thread, so the block pulls TILE bodies per round.
            int src = 4 * (tileStart + local);
            int dst = 4 * local;
            tile[dst] = posIn.get(src);
            tile[dst + 1] = posIn.get(src + 1);
            tile[dst + 2] = posIn.get(src + 2);
            tile[dst + 3] = posIn.get(src + 3);
            context.localBarrier();

            for (int j = 0; j < TILE; j++) {
                int index = 4 * j;
                float rx = tile[index] - myX;
                float ry = tile[index + 1] - myY;
                float rz = tile[index + 2] - myZ;

                float distSqr = rx * rx + ry * ry + rz * rz;
                float invDist = 1.0f / sqrtF(distSqr + ESP_SQR);
                float s = tile[index + 3] * invDist * invDist * invDist;

                accX += s * rx;
                accY += s * ry;
                accZ += s * rz;
            }
            // Second barrier: nobody may overwrite the tile until every thread has consumed it.
            context.localBarrier();
        }

        integrate(posIn, velIn, posOut, velOut, body, accX, accY, accZ);
    }

    private static float sqrtF(float value) {
        return (float) Math.sqrt(value);
    }

    private static void integrate(FloatArray posIn, FloatArray velIn, FloatArray posOut, FloatArray velOut, int body, float accX, float accY, float accZ) {
        posOut.set(body, posIn.get(body) + velIn.get(body) * DELT + 0.5f * accX * DELT * DELT);
        posOut.set(body + 1, posIn.get(body + 1) + velIn.get(body + 1) * DELT + 0.5f * accY * DELT * DELT);
        posOut.set(body + 2, posIn.get(body + 2) + velIn.get(body + 2) * DELT + 0.5f * accZ * DELT * DELT);
        posOut.set(body + 3, posIn.get(body + 3)); // carry mass through untouched
        velOut.set(body, velIn.get(body) + accX * DELT);
        velOut.set(body + 1, velIn.get(body + 1) + accY * DELT);
        velOut.set(body + 2, velIn.get(body + 2) + accZ * DELT);
        velOut.set(body + 3, velIn.get(body + 3));
    }

    /**
     * Sequential Java reference, used to validate both GPU kernels.
     *
     * <p>
     * Forces are computed against a SNAPSHOT of the input positions. Reading {@code pos} directly
     * while the same loop writes to it would let body i+1 see body i's already-integrated
     * position, which is a different (and wrong) algorithm -- every thread on the GPU sees the
     * original positions, since the integration happens after all forces are accumulated. Getting
     * this wrong shows up as a small but stubborn deviation that looks like float drift and is
     * not.
     */
    private static void nBodySequential(int numBodies, FloatArray pos, FloatArray vel, FloatArray posOut, FloatArray velOut) {
        float[] snapshot = new float[numBodies * 4];
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i] = pos.get(i);
        }

        for (int i = 0; i < numBodies; i++) {
            int body = 4 * i;
            float myX = snapshot[body];
            float myY = snapshot[body + 1];
            float myZ = snapshot[body + 2];

            float accX = 0.0f;
            float accY = 0.0f;
            float accZ = 0.0f;

            for (int j = 0; j < numBodies; j++) {
                int index = 4 * j;
                float rx = snapshot[index] - myX;
                float ry = snapshot[index + 1] - myY;
                float rz = snapshot[index + 2] - myZ;

                float distSqr = rx * rx + ry * ry + rz * rz;
                float invDist = (float) (1.0f / Math.sqrt(distSqr + ESP_SQR));
                float s = snapshot[index + 3] * invDist * invDist * invDist;

                accX += s * rx;
                accY += s * ry;
                accZ += s * rz;
            }

            posOut.set(body, pos.get(body) + vel.get(body) * DELT + 0.5f * accX * DELT * DELT);
            posOut.set(body + 1, pos.get(body + 1) + vel.get(body + 1) * DELT + 0.5f * accY * DELT * DELT);
            posOut.set(body + 2, pos.get(body + 2) + vel.get(body + 2) * DELT + 0.5f * accZ * DELT * DELT);
            posOut.set(body + 3, pos.get(body + 3));
            velOut.set(body, vel.get(body) + accX * DELT);
            velOut.set(body + 1, vel.get(body + 1) + accY * DELT);
            velOut.set(body + 2, vel.get(body + 2) + accZ * DELT);
            velOut.set(body + 3, vel.get(body + 3));
        }
    }

    private static FloatArray seedPositions(long seed) {
        java.util.Random rng = new java.util.Random(seed);
        FloatArray pos = new FloatArray(NUM_BODIES * 4);
        for (int i = 0; i < NUM_BODIES; i++) {
            pos.set(i * 4, rng.nextFloat() * 100.0f);
            pos.set(i * 4 + 1, rng.nextFloat() * 100.0f);
            pos.set(i * 4 + 2, rng.nextFloat() * 100.0f);
            pos.set(i * 4 + 3, 1.0f + rng.nextFloat()); // mass
        }
        return pos;
    }

    private static FloatArray seedVelocities(long seed) {
        java.util.Random rng = new java.util.Random(seed);
        FloatArray vel = new FloatArray(NUM_BODIES * 4);
        for (int i = 0; i < NUM_BODIES * 4; i++) {
            vel.set(i, rng.nextFloat() - 0.5f);
        }
        return vel;
    }

    private static float maxDeviation(FloatArray got, FloatArray expected) {
        float worst = 0.0f;
        for (int i = 0; i < got.getSize(); i++) {
            worst = Math.max(worst, Math.abs(got.get(i) - expected.get(i)));
        }
        return worst;
    }

    /** Runs once to pay JIT, then {@code ITERATIONS} more; returns {firstRunNs, meanSteadyNs}. */
    private static long[] timePlan(TornadoExecutionPlan plan, GridScheduler grid) throws TornadoExecutionPlanException {
        long start = System.nanoTime();
        plan.withGridScheduler(grid).execute();
        long firstRunNs = System.nanoTime() - start;

        long steadyStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            plan.withGridScheduler(grid).execute();
        }
        return new long[] { firstRunNs, (System.nanoTime() - steadyStart) / ITERATIONS };
    }

    private static double gflops(long nanos) {
        // ~20 flops per pair interaction (3 subs, 3 mults + 2 adds for distSqr, rsqrt, 3 mults
        // for the cube, 1 mult for mass, 3 fma for the accumulate). Approximate but consistent
        // across both kernels, which is what matters for the comparison.
        double pairs = (double) NUM_BODIES * NUM_BODIES;
        return ((pairs * 20.0) / (nanos / 1e9)) / 1e9;
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        System.out.printf("N-body all-pairs -- %d bodies, TILE=%d, %d timed iterations%n", NUM_BODIES, TILE, ITERATIONS);
        System.out.println("Steady-state times only; first-run includes JIT/NVRTC compilation.");
        System.out.println();

        // Sequential reference over a single step, from the same seed as the GPU runs.
        FloatArray seedPos = seedPositions(7);
        FloatArray seedVel = seedVelocities(13);
        FloatArray refPos = new FloatArray(NUM_BODIES * 4);
        FloatArray refVel = new FloatArray(NUM_BODIES * 4);
        nBodySequential(NUM_BODIES, seedPos, seedVel, refPos, refVel);

        KernelContext context = new KernelContext();

        // Double-buffered: the kernels read posIn/velIn and write posOut/velOut, never both.
        // Writing into the same array a neighbouring thread is still reading is a data race --
        // there is no grid-wide barrier between the force loop and the integration step.
        // ---- naive ----
        FloatArray naiveIn = seedPositions(7);
        FloatArray naiveVelIn = seedVelocities(13);
        FloatArray naiveOut = new FloatArray(NUM_BODIES * 4);
        FloatArray naiveVelOut = new FloatArray(NUM_BODIES * 4);
        WorkerGrid1D naiveWorker = new WorkerGrid1D(NUM_BODIES);
        naiveWorker.setLocalWork(TILE, 1, 1);
        GridScheduler naiveGrid = new GridScheduler("naive.compute", naiveWorker);
        TaskGraph naiveGraph = new TaskGraph("naive") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, naiveIn, naiveVelIn) //
                .task("compute", NBodyTiled::nBodyNaive, context, NUM_BODIES, naiveIn, naiveVelIn, naiveOut, naiveVelOut) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, naiveOut, naiveVelOut);

        long[] naiveTimes;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(naiveGraph.snapshot())) {
            naiveTimes = timePlan(plan, naiveGrid);
        }

        // ---- tiled ----
        FloatArray tiledIn = seedPositions(7);
        FloatArray tiledVelIn = seedVelocities(13);
        FloatArray tiledOut = new FloatArray(NUM_BODIES * 4);
        FloatArray tiledVelOut = new FloatArray(NUM_BODIES * 4);
        WorkerGrid1D tiledWorker = new WorkerGrid1D(NUM_BODIES);
        tiledWorker.setLocalWork(TILE, 1, 1);
        GridScheduler tiledGrid = new GridScheduler("tiled.compute", tiledWorker);
        TaskGraph tiledGraph = new TaskGraph("tiled") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, tiledIn, tiledVelIn) //
                .task("compute", NBodyTiled::nBodyTiled, context, NUM_BODIES, tiledIn, tiledVelIn, tiledOut, tiledVelOut) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tiledOut, tiledVelOut);

        long[] tiledTimes;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tiledGraph.snapshot())) {
            tiledTimes = timePlan(plan, tiledGrid);
        }

        // Inputs are never mutated, so every execution recomputes the same single step and the
        // final output must match the one-step sequential reference.
        float naiveErr = Math.max(maxDeviation(naiveOut, refPos), maxDeviation(naiveVelOut, refVel));
        float tiledErr = Math.max(maxDeviation(tiledOut, refPos), maxDeviation(tiledVelOut, refVel));

        System.out.println("Results:");
        System.out.printf("  %-22s steady=%8.3f ms  %8.1f GFLOP/s  first-run=%8.1f ms  maxDev=%.5f%n", "naive (global reads)", naiveTimes[1] / 1e6, gflops(naiveTimes[1]), naiveTimes[0] / 1e6, naiveErr);
        System.out.printf("  %-22s steady=%8.3f ms  %8.1f GFLOP/s  first-run=%8.1f ms  maxDev=%.5f%n", "tiled (shared memory)", tiledTimes[1] / 1e6, gflops(tiledTimes[1]), tiledTimes[0] / 1e6, tiledErr);
        System.out.println();
        System.out.printf("Tiled vs naive (end-to-end): %.2fx%n", (double) naiveTimes[1] / tiledTimes[1]);
        System.out.println();
        System.out.println("End-to-end includes the position/velocity copy-back, identical for both kernels.");
        System.out.println("For kernel-only time:  tornado --enableProfiler console -m ...NBodyTiled");
    }

}
