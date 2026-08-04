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
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * European call pricing with a Cox-Ross-Rubinstein binomial tree -- a
 * <b>block-per-work-item wavefront</b>, which is a parallel decomposition nothing else in the
 * TornadoVM examples uses.
 *
 * <p>
 * Every other {@code KernelContext} example here maps one thread to one output element and uses
 * the block purely as a staging area (tiled GEMM, tiled N-body, reductions). This one inverts
 * that: <b>one block prices one option</b>, and the threads inside the block cooperate on that
 * single option's tree. Each thread owns one tree node; the whole block then walks the tree
 * backwards from expiry to the present, collapsing one level per iteration:
 *
 * <pre>
 * leaf:  every thread computes its own terminal payoff
 * for level = STEPS down to 1:
 *     tree[tid] = myValue          // publish
 *     localBarrier()               // everyone's value is visible
 *     if (tid &lt; level)
 *         myValue = pu*tree[tid+1] + pd*tree[tid]   // collapse one level
 *     localBarrier()               // nobody overwrites before all reads land
 * thread 0 holds the option price
 * </pre>
 *
 * <p>
 * Note the loop runs {@code STEPS} iterations with <b>two barriers each</b> -- 510 barriers per
 * block, versus the single barrier per K-tile in the GEMM kernels. The active thread count shrinks
 * by one every level, so the block finishes almost entirely idle. That is inherent to the
 * algorithm, and it is exactly why this shape is interesting to have as an example: it stresses
 * barrier throughput and divergence rather than memory bandwidth or FLOPs.
 *
 * <p>
 * <b>Why both barriers are load-bearing.</b> The update reads {@code tree[tid]} and
 * {@code tree[tid+1]} and writes {@code tree[tid]}. Drop the second barrier and thread
 * {@code tid} can overwrite {@code tree[tid]} while thread {@code tid-1} is still reading it as
 * its {@code tid+1} neighbour -- a classic in-place-sweep race. Keeping the new value in a
 * register ({@code myValue}) and only publishing it at the top of the next iteration is what makes
 * the two-barrier form correct.
 *
 * <p>
 * A {@code @Parallel} one-option-per-thread version is included as a baseline. It needs no shared
 * memory and no barriers at all, but each thread must hold the whole tree -- and on a GPU that is
 * fatal: see its javadoc, the launch is rejected outright.
 *
 * <h2>Measured result</h2>
 *
 * <p>
 * RTX 3070 (sm_86), CUDA 13.0, 2048 options, 255 tree steps, mean of 10 steady-state iterations:
 *
 * <pre>
 *   block-per-option (GPU, kernel-only)   35265 us
 *   sequential Java (1 CPU core)          44561 us
 *   -> 1.25x
 * </pre>
 *
 * <p>
 * 1.25x over a single CPU core is a poor return for 524288 GPU threads, and the reason is
 * structural rather than fixable by tuning. The kernel is <b>barrier- and divergence-bound</b>,
 * not compute- or bandwidth-bound:
 *
 * <ul>
 * <li>2048 blocks x 510 barriers is over a million block-wide synchronisations, and there is no
 * arithmetic to hide them behind -- each level does two multiplies and an add per node.</li>
 * <li>The active thread count falls by one per level, from 255 down to 1. Averaged over the
 * sweep only half the block is ever doing useful work, and the final levels run a 256-thread
 * block to compute a handful of values.</li>
 * <li>Kernel-only time (35.3 ms) is essentially the whole end-to-end time (35.6 ms), so data
 * movement is not the issue either.</li>
 * </ul>
 *
 * <p>
 * The honest summary: a binomial tree of this depth is a bad fit for one-block-per-option on this
 * hardware. The real CUDA sample amortises the cost differently -- far more options in flight and
 * a cache-blocked sweep so the tree does not have to be walked level-by-level in lockstep. This
 * example is kept for the pattern, not the throughput: it is the only place in the TornadoVM
 * examples where a block cooperates on a single work item, and it shows what that costs.
 *
 * <p>
 * How to run:
 * </p>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.compute.BinomialOptions
 * </pre>
 *
 * <p>
 * Kernel-only timing:
 * </p>
 *
 * <pre>
 * tornado --enableProfiler console -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.compute.BinomialOptions
 * </pre>
 */
public class BinomialOptions {

    /** Threads per block == tree leaves. STEPS is one less, so leaf index tid maps 1:1. */
    private static final int BLOCK = 256;
    private static final int STEPS = BLOCK - 1;

    private static final int NUM_OPTIONS = 2048;
    private static final int ITERATIONS = 10;

    private static final float RISK_FREE = 0.02f;
    private static final float VOLATILITY = 0.30f;

    /**
     * One block per option, one thread per tree node.
     */
    public static void binomialKernelContext(KernelContext ctx, FloatArray spot, FloatArray strike, FloatArray years, FloatArray callResult) {
        int option = ctx.groupIdx;
        int tid = ctx.localIdx;

        float s = spot.get(option);
        float k = strike.get(option);
        float t = years.get(option);

        float dt = t / STEPS;
        float vDt = VOLATILITY * (float) Math.sqrt(dt);
        float rDt = RISK_FREE * dt;
        float ifX = (float) Math.exp(rDt);
        float discount = (float) Math.exp(-rDt);
        float u = (float) Math.exp(vDt);
        float d = (float) Math.exp(-vDt);
        float pu = (ifX - d) / (u - d);
        float pd = 1.0f - pu;
        float puByDf = pu * discount;
        float pdByDf = pd * discount;

        // Terminal payoff for this thread's leaf: tid up-moves, (STEPS - tid) down-moves.
        float terminalPrice = s * (float) Math.exp(vDt * (2.0f * tid - STEPS));
        float payoff = terminalPrice - k;
        float myValue = payoff > 0.0f ? payoff : 0.0f;

        float[] tree = ctx.allocateFloatLocalArray(BLOCK);

        for (int level = STEPS; level > 0; level--) {
            tree[tid] = myValue;
            ctx.localBarrier();
            if (tid < level) {
                myValue = puByDf * tree[tid + 1] + pdByDf * tree[tid];
            }
            ctx.localBarrier();
        }

        if (tid == 0) {
            callResult.set(option, myValue);
        }
    }

    /**
     * Baseline: one thread prices a whole option, walking the tree in a private array. No shared
     * memory and no barriers -- but every thread must carry the entire tree.
     *
     * <p>
     * <b>This does not run on the GPU, and that is the point.</b> The private {@code float[256]}
     * is 1 KB per thread; multiplied by a full block's worth of resident threads it blows past the
     * per-thread local-memory budget and the launch is rejected with
     * {@code CUDA_ERROR_LAUNCH_OUT_OF_RESOURCES}. The kernel then produces no output at all, so
     * {@link #main} checks the result before reporting any timing -- a failed launch otherwise
     * looks like a spectacularly fast kernel.
     *
     * <p>
     * This is precisely the constraint that makes the block-cooperative version worth writing:
     * the tree is too big to be private per thread, but it fits comfortably in shared memory when
     * a whole block shares one copy.
     */
    public static void binomialParallel(FloatArray spot, FloatArray strike, FloatArray years, FloatArray callResult) {
        for (@Parallel int option = 0; option < NUM_OPTIONS; option++) {
            float s = spot.get(option);
            float k = strike.get(option);
            float t = years.get(option);

            float dt = t / STEPS;
            float vDt = VOLATILITY * (float) Math.sqrt(dt);
            float rDt = RISK_FREE * dt;
            float ifX = (float) Math.exp(rDt);
            float discount = (float) Math.exp(-rDt);
            float u = (float) Math.exp(vDt);
            float d = (float) Math.exp(-vDt);
            float pu = (ifX - d) / (u - d);
            float pd = 1.0f - pu;
            float puByDf = pu * discount;
            float pdByDf = pd * discount;

            float[] tree = new float[BLOCK];
            for (int i = 0; i <= STEPS; i++) {
                float terminalPrice = s * (float) Math.exp(vDt * (2.0f * i - STEPS));
                float payoff = terminalPrice - k;
                tree[i] = payoff > 0.0f ? payoff : 0.0f;
            }

            for (int level = STEPS; level > 0; level--) {
                for (int i = 0; i < level; i++) {
                    tree[i] = puByDf * tree[i + 1] + pdByDf * tree[i];
                }
            }

            callResult.set(option, tree[0]);
        }
    }

    /** Sequential Java reference. */
    private static void binomialSequential(FloatArray spot, FloatArray strike, FloatArray years, FloatArray callResult) {
        for (int option = 0; option < NUM_OPTIONS; option++) {
            float s = spot.get(option);
            float k = strike.get(option);
            float t = years.get(option);

            float dt = t / STEPS;
            float vDt = VOLATILITY * (float) Math.sqrt(dt);
            float rDt = RISK_FREE * dt;
            float ifX = (float) Math.exp(rDt);
            float discount = (float) Math.exp(-rDt);
            float u = (float) Math.exp(vDt);
            float d = (float) Math.exp(-vDt);
            float pu = (ifX - d) / (u - d);
            float pd = 1.0f - pu;
            float puByDf = pu * discount;
            float pdByDf = pd * discount;

            float[] tree = new float[BLOCK];
            for (int i = 0; i <= STEPS; i++) {
                float terminalPrice = s * (float) Math.exp(vDt * (2.0f * i - STEPS));
                float payoff = terminalPrice - k;
                tree[i] = payoff > 0.0f ? payoff : 0.0f;
            }

            for (int level = STEPS; level > 0; level--) {
                for (int i = 0; i < level; i++) {
                    tree[i] = puByDf * tree[i + 1] + pdByDf * tree[i];
                }
            }

            callResult.set(option, tree[0]);
        }
    }

    private static float maxRelativeError(FloatArray got, FloatArray expected) {
        float worst = 0.0f;
        for (int i = 0; i < got.getSize(); i++) {
            float e = expected.get(i);
            float denom = Math.abs(e) > 1e-3f ? Math.abs(e) : 1.0f;
            worst = Math.max(worst, Math.abs(got.get(i) - e) / denom);
        }
        return worst;
    }

    private static long[] timePlan(TornadoExecutionPlan plan, GridScheduler grid) throws TornadoExecutionPlanException {
        long start = System.nanoTime();
        if (grid != null) {
            plan.withGridScheduler(grid).execute();
        } else {
            plan.execute();
        }
        long firstRunNs = System.nanoTime() - start;

        long steadyStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            if (grid != null) {
                plan.withGridScheduler(grid).execute();
            } else {
                plan.execute();
            }
        }
        return new long[] { firstRunNs, (System.nanoTime() - steadyStart) / ITERATIONS };
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        System.out.printf("Binomial option pricing -- %d options, %d tree steps, block-per-option%n", NUM_OPTIONS, STEPS);
        System.out.printf("(%d barriers per block: two per tree level)%n", 2 * STEPS);
        System.out.println();

        Random rng = new Random(5);
        FloatArray spot = new FloatArray(NUM_OPTIONS);
        FloatArray strike = new FloatArray(NUM_OPTIONS);
        FloatArray years = new FloatArray(NUM_OPTIONS);
        for (int i = 0; i < NUM_OPTIONS; i++) {
            spot.set(i, 5.0f + rng.nextFloat() * 25.0f);
            strike.set(i, 5.0f + rng.nextFloat() * 25.0f);
            years.set(i, 0.25f + rng.nextFloat() * 9.75f);
        }

        FloatArray refResult = new FloatArray(NUM_OPTIONS);
        long seqStart = System.nanoTime();
        binomialSequential(spot, strike, years, refResult);
        long seqNs = System.nanoTime() - seqStart;

        // ---- block-per-option, KernelContext ----
        FloatArray ctxResult = new FloatArray(NUM_OPTIONS);
        KernelContext context = new KernelContext();
        WorkerGrid1D worker = new WorkerGrid1D(NUM_OPTIONS * BLOCK);
        worker.setLocalWork(BLOCK, 1, 1);
        GridScheduler grid = new GridScheduler("blockPerOption.price", worker);
        TaskGraph ctxGraph = new TaskGraph("blockPerOption") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, spot, strike, years) //
                .task("price", BinomialOptions::binomialKernelContext, context, spot, strike, years, ctxResult) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ctxResult);

        long[] ctxTimes;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(ctxGraph.snapshot())) {
            ctxTimes = timePlan(plan, grid);
        }

        // ---- one option per thread, @Parallel ----
        FloatArray parResult = new FloatArray(NUM_OPTIONS);
        TaskGraph parGraph = new TaskGraph("threadPerOption") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, spot, strike, years) //
                .task("price", BinomialOptions::binomialParallel, spot, strike, years, parResult) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, parResult);

        long[] parTimes;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(parGraph.snapshot())) {
            parTimes = timePlan(plan, null);
        }

        float ctxErr = maxRelativeError(ctxResult, refResult);
        float parErr = maxRelativeError(parResult, refResult);
        // A rejected launch leaves the output untouched, which reads as ~100% relative error.
        // Reporting its "time" would advertise a kernel that never ran as the fastest one.
        boolean parRan = parErr < 0.01f;

        System.out.println("Results:");
        System.out.printf("  %-30s steady=%8.3f ms  first-run=%8.1f ms  maxRelErr=%.6f%n", "block-per-option (barriers)", ctxTimes[1] / 1e6, ctxTimes[0] / 1e6, ctxErr);
        if (parRan) {
            System.out.printf("  %-30s steady=%8.3f ms  first-run=%8.1f ms  maxRelErr=%.6f%n", "thread-per-option (@Parallel)", parTimes[1] / 1e6, parTimes[0] / 1e6, parErr);
        } else {
            System.out.printf("  %-30s LAUNCH FAILED -- a %d-float private tree per thread (%d KB) exceeds%n", "thread-per-option (@Parallel)", BLOCK, BLOCK * 4 / 1024);
            System.out.printf("  %-30s the per-thread local-memory budget (CUDA_ERROR_LAUNCH_OUT_OF_RESOURCES).%n", "");
            System.out.printf("  %-30s This is why the tree has to live in shared memory, shared by a block.%n", "");
        }
        System.out.printf("  %-30s        %8.3f ms%n", "sequential Java (1 core)", seqNs / 1e6);
        System.out.println();
        if (parRan) {
            System.out.printf("block-per-option vs thread-per-option: %.2fx%n", (double) parTimes[1] / ctxTimes[1]);
        }
        System.out.printf("block-per-option vs sequential Java:   %.2fx%n", (double) seqNs / ctxTimes[1]);
        System.out.println();
        System.out.println("End-to-end times include the result copy-back; for kernel-only timing use:");
        System.out.println("  tornado --enableProfiler console -m ...BinomialOptions");
    }

}
