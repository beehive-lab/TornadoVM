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
package uk.ac.manchester.tornado.examples.compute;

import java.util.ArrayList;
import java.util.LongSummaryStatistics;
import java.util.Random;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Row-major matrix-vector multiplication benchmark comparing the default device
 * allocation path against CUDA Unified Memory selected per-plan via
 * {@code withCudaUM()}.
 *
 * <p>
 * What this measures: end-to-end execution time of {@code out = W * x} for a
 * {@code d x n} weight matrix, run with explicit device allocation
 * ({@code cuMemAlloc}) versus managed allocation ({@code cuMemAllocManaged}).
 *
 * <p>
 * What to expect: for matrices that fit in VRAM the two paths are close, since
 * this phase still performs host&lt;-&gt;device copies; managed memory mainly
 * removes the explicit zeroing of write-only outputs. The headline benefit of
 * Unified Memory is <b>over-subscription</b>: a matrix larger than physical VRAM
 * can still be allocated and computed because the CUDA runtime pages it on
 * demand. To exercise that, pass dimensions whose weight matrix exceeds VRAM and
 * raise the TornadoVM device-memory accounting cap, e.g.
 * {@code -Dtornado.device.memory=32GB}.
 *
 * <p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixVectorUnifiedMemory [n] [d] [iterations]
 * </code>
 */
public class MatrixVectorUnifiedMemory {

    private static final float DELTA = 1e-3f;
    private static final Random RANDOM = new Random(42);

    public static void matrixVector(FloatArray x, FloatArray out, FloatArray w, int n, int d) {
        for (@Parallel int i = 0; i < d; i++) {
            float sum = 0.0f;
            int rowOffset = i * n;
            for (int j = 0; j < n; j++) {
                sum += w.get(rowOffset + j) * x.get(j);
            }
            out.set(i, sum);
        }
    }

    private static void fill(FloatArray array, float min, float max) {
        float range = max - min;
        for (int i = 0; i < array.getSize(); i++) {
            array.set(i, min + RANDOM.nextFloat() * range);
        }
    }

    /**
     * Runs {@code iterations} executions of the plan and returns the per-iteration
     * timings in nanoseconds. When {@code unifiedMemory} is true the plan opts into
     * CUDA Unified Memory via {@link TornadoExecutionPlan#withCudaUM()}.
     */
    private static ArrayList<Long> run(ImmutableTaskGraph graph, boolean unifiedMemory, int iterations, int warmup) throws TornadoExecutionPlanException {
        ArrayList<Long> timings = new ArrayList<>();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph)) {
            if (unifiedMemory) {
                plan.withCudaUM();
            }
            for (int i = 0; i < warmup; i++) {
                plan.execute();
            }
            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                plan.execute();
                timings.add(System.nanoTime() - start);
            }
        }
        return timings;
    }

    private static void report(String label, ArrayList<Long> timings) {
        LongSummaryStatistics stats = timings.stream().mapToLong(Long::longValue).summaryStatistics();
        System.out.printf("%-28s avg=%8.3f ms   min=%8.3f ms   max=%8.3f ms%n", label, stats.getAverage() / 1e6, stats.getMin() / 1e6, stats.getMax() / 1e6);
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        int n = 8192;  // input dimension (columns)
        int d = 4096;  // output dimension (rows)
        int iterations = 100;
        if (args.length >= 2) {
            n = Integer.parseInt(args[0]);
            d = Integer.parseInt(args[1]);
        }
        if (args.length >= 3) {
            iterations = Integer.parseInt(args[2]);
        }
        int warmup = Math.max(10, iterations / 10);

        long weightBytes = (long) n * d * Float.BYTES;
        System.out.println("Matrix-Vector Unified Memory Benchmark");
        System.out.println("======================================");
        System.out.println("- Backend            : " + TornadoRuntimeProvider.getTornadoRuntime().getBackendType(TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().getBackendIndex()));
        System.out.println("- Device             : " + TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().getPhysicalDevice().getDeviceName());
        System.out.printf("- Dimensions         : n=%d, d=%d%n", n, d);
        System.out.printf("- Weight matrix size : %.1f MB%n", weightBytes / (1024.0 * 1024.0));
        System.out.printf("- Device max alloc   : %.1f MB%n", TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().getMaxAllocMemory() / (1024.0 * 1024.0));
        System.out.printf("- Iterations         : %d (warmup %d)%n", iterations, warmup);
        System.out.println();

        FloatArray x = new FloatArray(n);
        FloatArray w = new FloatArray(n * d);
        FloatArray outDefault = new FloatArray(d);
        FloatArray outUM = new FloatArray(d);
        fill(x, -1.0f, 1.0f);
        fill(w, -0.1f, 0.1f);

        TaskGraph defaultGraph = new TaskGraph("default") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x, w) //
                .task("t0", MatrixVectorUnifiedMemory::matrixVector, x, outDefault, w, n, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outDefault);

        TaskGraph umGraph = new TaskGraph("um") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x, w) //
                .task("t0", MatrixVectorUnifiedMemory::matrixVector, x, outUM, w, n, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outUM);

        ArrayList<Long> defaultTimings = run(defaultGraph.snapshot(), false, iterations, warmup);
        ArrayList<Long> umTimings = run(umGraph.snapshot(), true, iterations, warmup);

        report("Default (cuMemAlloc)", defaultTimings);
        report("Unified Memory (.withCudaUM)", umTimings);

        // Correctness: both paths must agree.
        int mismatches = 0;
        for (int i = 0; i < d; i++) {
            if (Math.abs(outDefault.get(i) - outUM.get(i)) > DELTA) {
                mismatches++;
            }
        }
        System.out.println();
        System.out.println(mismatches == 0 ? "Result check: PASS (default == unified memory)" : "Result check: FAIL (" + mismatches + " mismatches)");
    }
}
