/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.DeviceKernel;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Mixture-of-experts style routing with kernels launched from the device (CUDA Dynamic
 * Parallelism, {@link KernelContext#launch}).
 *
 * <p>
 * Every token is routed to one expert, and how many tokens an expert receives is only known on the
 * GPU. Launching every expert kernel from the host for the worst case wastes most of its threads,
 * and reading the counts back costs a device-to-host round trip per step. Here, one parent thread per
 * expert gathers that expert's tokens and launches a child grid of exactly that many threads.
 * </p>
 *
 * <p>
 * How to run (CUDA backend only):
 * </p>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.compute.DynamicParallelismRouting
 * tornado --threadInfo --printKernel -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.compute.DynamicParallelismRouting
 * </pre>
 *
 * <p>
 * With {@code --threadInfo}, every device launch reports its grid, so the per-expert grid sizes can be
 * read off directly.
 * </p>
 */
public class DynamicParallelismRouting {

    private static final int TOKENS = 4096;
    private static final int EXPERTS = 8;
    private static final int CHILD_BLOCK = 128;

    private static final DeviceKernel EXPERT = DeviceKernel.of(DynamicParallelismRouting::expert);

    /**
     * One thread per expert: pack the indices of the tokens routed to this expert and launch a child
     * with one thread per token. The launching thread's own writes are visible to the child it
     * launches.
     */
    public static void route(KernelContext context, IntArray routing, IntArray tokenLists, FloatArray x, FloatArray weights, FloatArray out) {
        int e = context.globalIdx;
        if (e < EXPERTS) {
            int count = 0;
            for (int t = 0; t < TOKENS; t++) {
                if (routing.get(t) == e) {
                    tokenLists.set(e * TOKENS + count, t);
                    count++;
                }
            }
            if (count > 0) {
                context.launch(EXPERT, count, CHILD_BLOCK, tokenLists, x, weights, out, e, count);
            }
        }
    }

    /** The expert: one thread per routed token. */
    public static void expert(KernelContext context, IntArray tokenLists, FloatArray x, FloatArray weights, FloatArray out, int e, int count) {
        int k = context.globalIdx;
        if (k < count) {
            int token = tokenLists.get(e * TOKENS + k);
            out.set(token, x.get(token) * weights.get(e) + e);
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        Random random = new Random(7);
        IntArray routing = new IntArray(TOKENS);
        IntArray tokenLists = new IntArray(EXPERTS * TOKENS);
        FloatArray x = new FloatArray(TOKENS);
        FloatArray weights = new FloatArray(EXPERTS);
        FloatArray out = new FloatArray(TOKENS);
        int[] counts = new int[EXPERTS];
        for (int t = 0; t < TOKENS; t++) {
            // Skewed routing: low-numbered experts receive far more tokens.
            int e = Math.min(EXPERTS - 1, (int) (Math.abs(random.nextGaussian()) * 2.5));
            routing.set(t, e);
            counts[e]++;
            x.set(t, random.nextFloat());
        }
        for (int e = 0; e < EXPERTS; e++) {
            weights.set(e, 0.5f + e);
        }

        WorkerGrid worker = new WorkerGrid1D(32);
        worker.setLocalWork(32, 1, 1);
        GridScheduler grid = new GridScheduler("moe.route", worker);

        TaskGraph taskGraph = new TaskGraph("moe") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, routing, x, weights) //
                .task("route", DynamicParallelismRouting::route, new KernelContext(), routing, tokenLists, x, weights, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        int errors = 0;
        for (int t = 0; t < TOKENS; t++) {
            int e = routing.get(t);
            float expected = x.get(t) * weights.get(e) + e;
            if (Math.abs(expected - out.get(t)) > 1e-4f) {
                errors++;
            }
        }
        StringBuilder sizes = new StringBuilder();
        for (int e = 0; e < EXPERTS; e++) {
            sizes.append(e == 0 ? "" : ", ").append(counts[e]);
        }
        System.out.println("Tokens per expert (child grid sizes): [" + sizes + "]");
        System.out.println(errors == 0 ? "Result is correct" : "Result is wrong: " + errors + " mismatches");
    }
}
