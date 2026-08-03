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

import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * 3D finite-difference wave stencil (FDTD-style), naive vs XY shared-memory tiled.
 *
 * <p>
 * A symmetric star stencil of radius {@value #RADIUS}: each interior point reads
 * {@code 6 * RADIUS + 1} neighbours -- itself plus {@code RADIUS} points in each of the six axial
 * directions -- weighted by a coefficient that depends only on distance. Unlike the other examples
 * in this directory, this one is genuinely <b>memory-bound</b>: 25 loads feed about 25 FLOPs, so
 * the arithmetic cannot hide the traffic. That makes it the case where shared-memory tiling is
 * actually expected to pay, which is why it is worth having alongside the tiled N-body (where it
 * demonstrably does not).
 *
 * <p>
 * The naive kernel reads every neighbour straight from global memory. Adjacent threads re-read the
 * same points -- with radius 4 in X and Y, each value is loaded up to 9 times across the block.
 * The tiled kernel loads one XY slab (plus a {@value #RADIUS}-wide halo on each side) into shared
 * memory per Z step, so the X and Y neighbours are read once per block instead of once per thread:
 *
 * <pre>
 * for each z in the interior:
 *     tile[ty+R][tx+R] = in[x, y, z]        // interior point
 *     threads with tx &lt; R also fetch the left/right halo columns
 *     threads with ty &lt; R also fetch the top/bottom halo rows
 *     localBarrier()
 *     out[x,y,z] = c0*tile[centre]
 *                + sum over r of c[r] * (X and Y neighbours from the tile
 *                                        + Z neighbours from global memory)
 *     localBarrier()
 * </pre>
 *
 * <p>
 * Z neighbours still come from global memory here. The full CUDA sample additionally keeps a
 * rolling window of {@code 2*RADIUS+1} Z values in registers as it sweeps, so the Z direction is
 * reused too; that refinement is deliberately not implemented, to keep the shared-memory effect
 * isolated and measurable on its own.
 *
 * <p>
 * The second {@code localBarrier} matters: without it a thread can start overwriting the tile for
 * the next Z step while its neighbours are still reading the current one.
 *
 * <h2>Measured result: the tiling makes it slower, and the bandwidth figure explains why</h2>
 *
 * <p>
 * RTX 3070 (sm_86), CUDA 13.0, 256x256x64, radius 4, mean of 10 steady-state iterations,
 * kernel-only via {@code --enableProfiler console}:
 *
 * <pre>
 *   fdtdNaive     155.3 us    2148 GiB/s effective
 *   fdtdTiled     183.2 us    1821 GiB/s effective     0.85x
 * </pre>
 *
 * <p>
 * The decisive number is the effective bandwidth. Counting {@code 6*RADIUS+2} accesses per
 * interior point, the naive kernel is moving the equivalent of <b>2148 GiB/s</b> -- roughly five
 * times an RTX 3070's ~448 GB/s of DRAM bandwidth. A kernel cannot exceed DRAM bandwidth, so most
 * of those accesses are never reaching DRAM: the L1/L2 hierarchy is already absorbing the
 * stencil's reuse. Staging the same values manually in shared memory therefore buys no traffic
 * reduction and adds real cost -- two barriers per Z step, halo-index arithmetic, and a
 * conditional halo fetch that makes the first {@value #RADIUS} threads of each row and column do
 * extra work.
 *
 * <p>
 * This matches what the tiled N-body example in this directory found, and the two together make
 * the general point: on Ampere-class hardware the cache hierarchy has largely absorbed the reuse
 * that manual shared-memory tiling was invented to provide. The optimisation was formulated for
 * GPUs whose caches were far weaker relative to their arithmetic. Tiling still matters when a
 * working set genuinely does not fit, or when the access pattern defeats the cache -- but it is
 * no longer a reflexive win, and it needs measuring rather than assuming.
 *
 * <h2>Beware the end-to-end number here</h2>
 *
 * <p>
 * Wall-clock per execution is ~11 ms, but the kernel is only ~0.16 ms of that: the rest is copying
 * the 16 MB result volume back to the host. This example is an extreme case of something worth
 * internalising -- for a short kernel over a large array, end-to-end timing measures PCIe, not the
 * GPU. Against the sequential Java reference the end-to-end figure suggests ~7x, while the kernels
 * themselves are closer to 500x.
 *
 * <p>
 * How to run:
 * </p>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.compute.Fdtd3dStencil
 * </pre>
 *
 * <p>
 * Kernel-only timing:
 * </p>
 *
 * <pre>
 * tornado --enableProfiler console -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.compute.Fdtd3dStencil
 * </pre>
 */
public class Fdtd3dStencil {

    private static final int RADIUS = 4;

    private static final int TILE_X = 32;
    private static final int TILE_Y = 8;

    private static final int DIM_X = 256;
    private static final int DIM_Y = 256;
    private static final int DIM_Z = 64;

    private static final int SHARED_X = TILE_X + 2 * RADIUS;
    private static final int SHARED_Y = TILE_Y + 2 * RADIUS;

    private static final int ITERATIONS = 10;

    /** Naive: every neighbour comes straight from global memory. */
    public static void fdtdNaive(KernelContext ctx, FloatArray in, FloatArray out, FloatArray coeff) {
        int x = ctx.globalIdx;
        int y = ctx.globalIdy;

        if (x < RADIUS || x >= DIM_X - RADIUS || y < RADIUS || y >= DIM_Y - RADIUS) {
            return;
        }

        for (int z = RADIUS; z < DIM_Z - RADIUS; z++) {
            int centre = (z * DIM_Y + y) * DIM_X + x;
            float value = coeff.get(0) * in.get(centre);
            for (int r = 1; r <= RADIUS; r++) {
                float c = coeff.get(r);
                value += c * (in.get(centre + r) + in.get(centre - r));
                value += c * (in.get(centre + r * DIM_X) + in.get(centre - r * DIM_X));
                value += c * (in.get(centre + r * DIM_X * DIM_Y) + in.get(centre - r * DIM_X * DIM_Y));
            }
            out.set(centre, value);
        }
    }

    /** Tiled: X and Y neighbours are staged in shared memory once per block per Z step. */
    public static void fdtdTiled(KernelContext ctx, FloatArray in, FloatArray out, FloatArray coeff) {
        int x = ctx.globalIdx;
        int y = ctx.globalIdy;
        int tx = ctx.localIdx;
        int ty = ctx.localIdy;

        float[] tile = ctx.allocateFloatLocalArray(SHARED_X * SHARED_Y);

        boolean interior = x >= RADIUS && x < DIM_X - RADIUS && y >= RADIUS && y < DIM_Y - RADIUS;

        for (int z = RADIUS; z < DIM_Z - RADIUS; z++) {
            int slice = z * DIM_Y * DIM_X;

            // Interior point for this thread.
            tile[(ty + RADIUS) * SHARED_X + tx + RADIUS] = in.get(slice + y * DIM_X + x);

            // Halo columns: the first RADIUS threads of each row fetch both sides.
            if (tx < RADIUS) {
                int leftX = x - RADIUS;
                int rightX = x + TILE_X;
                if (leftX >= 0) {
                    tile[(ty + RADIUS) * SHARED_X + tx] = in.get(slice + y * DIM_X + leftX);
                }
                if (rightX < DIM_X) {
                    tile[(ty + RADIUS) * SHARED_X + tx + RADIUS + TILE_X] = in.get(slice + y * DIM_X + rightX);
                }
            }
            // Halo rows: the first RADIUS threads of each column fetch top and bottom.
            if (ty < RADIUS) {
                int topY = y - RADIUS;
                int bottomY = y + TILE_Y;
                if (topY >= 0) {
                    tile[ty * SHARED_X + tx + RADIUS] = in.get(slice + topY * DIM_X + x);
                }
                if (bottomY < DIM_Y) {
                    tile[(ty + RADIUS + TILE_Y) * SHARED_X + tx + RADIUS] = in.get(slice + bottomY * DIM_X + x);
                }
            }
            ctx.localBarrier();

            if (interior) {
                int centre = slice + y * DIM_X + x;
                int localCentre = (ty + RADIUS) * SHARED_X + tx + RADIUS;
                float value = coeff.get(0) * tile[localCentre];
                for (int r = 1; r <= RADIUS; r++) {
                    float c = coeff.get(r);
                    value += c * (tile[localCentre + r] + tile[localCentre - r]);
                    value += c * (tile[localCentre + r * SHARED_X] + tile[localCentre - r * SHARED_X]);
                    // Z stays in global memory -- see the class javadoc.
                    value += c * (in.get(centre + r * DIM_X * DIM_Y) + in.get(centre - r * DIM_X * DIM_Y));
                }
                out.set(centre, value);
            }
            // Nobody may overwrite the tile until every thread has finished reading it.
            ctx.localBarrier();
        }
    }

    /** Sequential Java reference. */
    private static void fdtdSequential(FloatArray in, FloatArray out, FloatArray coeff) {
        for (int z = RADIUS; z < DIM_Z - RADIUS; z++) {
            for (int y = RADIUS; y < DIM_Y - RADIUS; y++) {
                for (int x = RADIUS; x < DIM_X - RADIUS; x++) {
                    int centre = (z * DIM_Y + y) * DIM_X + x;
                    float value = coeff.get(0) * in.get(centre);
                    for (int r = 1; r <= RADIUS; r++) {
                        float c = coeff.get(r);
                        value += c * (in.get(centre + r) + in.get(centre - r));
                        value += c * (in.get(centre + r * DIM_X) + in.get(centre - r * DIM_X));
                        value += c * (in.get(centre + r * DIM_X * DIM_Y) + in.get(centre - r * DIM_X * DIM_Y));
                    }
                    out.set(centre, value);
                }
            }
        }
    }

    private static float maxDeviation(FloatArray got, FloatArray expected) {
        float worst = 0.0f;
        for (int z = RADIUS; z < DIM_Z - RADIUS; z++) {
            for (int y = RADIUS; y < DIM_Y - RADIUS; y++) {
                for (int x = RADIUS; x < DIM_X - RADIUS; x++) {
                    int i = (z * DIM_Y + y) * DIM_X + x;
                    worst = Math.max(worst, Math.abs(got.get(i) - expected.get(i)));
                }
            }
        }
        return worst;
    }

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

    /** Effective bandwidth: each interior point does 6*RADIUS+1 reads and one write. */
    private static double effectiveGiBs(long nanos) {
        long interior = (long) (DIM_X - 2 * RADIUS) * (DIM_Y - 2 * RADIUS) * (DIM_Z - 2 * RADIUS);
        double bytes = (double) interior * (6 * RADIUS + 2) * 4.0;
        return (bytes / (nanos / 1e9)) / (1024.0 * 1024.0 * 1024.0);
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        System.out.printf("FDTD 3D stencil -- %dx%dx%d, radius %d (%d-point star), tile %dx%d%n", DIM_X, DIM_Y, DIM_Z, RADIUS, 6 * RADIUS + 1, TILE_X, TILE_Y);
        System.out.println("Memory-bound: ~25 loads per ~25 FLOPs, so tiling is expected to matter here.");
        System.out.println();

        int volume = DIM_X * DIM_Y * DIM_Z;
        Random rng = new Random(19);
        FloatArray in = new FloatArray(volume);
        for (int i = 0; i < volume; i++) {
            in.set(i, rng.nextFloat());
        }
        FloatArray coeff = new FloatArray(RADIUS + 1);
        coeff.set(0, -6.0f);
        for (int r = 1; r <= RADIUS; r++) {
            coeff.set(r, 1.0f / (r * r));
        }

        FloatArray refOut = new FloatArray(volume);
        long seqStart = System.nanoTime();
        fdtdSequential(in, refOut, coeff);
        long seqNs = System.nanoTime() - seqStart;

        KernelContext context = new KernelContext();

        // ---- naive ----
        FloatArray naiveOut = new FloatArray(volume);
        WorkerGrid2D naiveWorker = new WorkerGrid2D(DIM_X, DIM_Y);
        naiveWorker.setLocalWork(TILE_X, TILE_Y, 1);
        GridScheduler naiveGrid = new GridScheduler("naive.stencil", naiveWorker);
        TaskGraph naiveGraph = new TaskGraph("naive") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in, coeff) //
                .task("stencil", Fdtd3dStencil::fdtdNaive, context, in, naiveOut, coeff) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, naiveOut);

        long[] naiveTimes;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(naiveGraph.snapshot())) {
            naiveTimes = timePlan(plan, naiveGrid);
        }

        // ---- tiled ----
        FloatArray tiledOut = new FloatArray(volume);
        WorkerGrid2D tiledWorker = new WorkerGrid2D(DIM_X, DIM_Y);
        tiledWorker.setLocalWork(TILE_X, TILE_Y, 1);
        GridScheduler tiledGrid = new GridScheduler("tiled.stencil", tiledWorker);
        TaskGraph tiledGraph = new TaskGraph("tiled") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in, coeff) //
                .task("stencil", Fdtd3dStencil::fdtdTiled, context, in, tiledOut, coeff) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tiledOut);

        long[] tiledTimes;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tiledGraph.snapshot())) {
            tiledTimes = timePlan(plan, tiledGrid);
        }

        System.out.println("Results:");
        System.out.printf("  %-24s steady=%8.3f ms  %7.1f GiB/s  first-run=%8.1f ms  maxDev=%.6f%n", "naive (all global)", naiveTimes[1] / 1e6, effectiveGiBs(naiveTimes[1]), naiveTimes[0] / 1e6, maxDeviation(naiveOut, refOut));
        System.out.printf("  %-24s steady=%8.3f ms  %7.1f GiB/s  first-run=%8.1f ms  maxDev=%.6f%n", "tiled (XY in shared)", tiledTimes[1] / 1e6, effectiveGiBs(tiledTimes[1]), tiledTimes[0] / 1e6, maxDeviation(tiledOut, refOut));
        System.out.printf("  %-24s       %8.3f ms%n", "sequential Java (1 core)", seqNs / 1e6);
        System.out.println();
        System.out.printf("tiled vs naive:              %.2fx%n", (double) naiveTimes[1] / tiledTimes[1]);
        System.out.printf("tiled vs sequential Java:    %.2fx%n", (double) seqNs / tiledTimes[1]);
        System.out.println();
        System.out.println("End-to-end includes the volume copy-back; for kernel-only timing use:");
        System.out.println("  tornado --enableProfiler console -m ...Fdtd3dStencil");
    }

}
