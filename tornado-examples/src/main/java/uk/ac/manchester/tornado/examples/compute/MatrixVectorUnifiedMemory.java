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

import java.util.Random;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.TornadoProfilerResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
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
     * Aggregated metrics averaged over the measured iterations. Times are taken from
     * the TornadoVM profiler ({@link ProfilerMode#SILENT}) plus a wall-clock measure.
     */
    private static final class Metrics {
        double wallMs;          // host-measured end-to-end
        double taskGraphMs;     // profiler TOTAL_TASK_GRAPH_TIME
        long bytesCopyIn;       // profiler TOTAL_COPY_IN_SIZE_BYTES (per iteration)
        long bytesCopyOut;      // profiler TOTAL_COPY_OUT_SIZE_BYTES (per iteration)
        long deviceMemory;      // profiler total device memory usage
    }

    /**
     * Runs {@code iterations} profiled executions of the plan and returns the averaged
     * metrics. When {@code unifiedMemory} is true the plan opts into CUDA Unified
     * Memory via {@link TornadoExecutionPlan#withCudaUM()}.
     */
    private static Metrics run(ImmutableTaskGraph graph, boolean unifiedMemory, int iterations, int warmup) throws TornadoExecutionPlanException {
        Metrics m = new Metrics();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph)) {
            plan.withProfiler(ProfilerMode.SILENT);
            if (unifiedMemory) {
                plan.withCudaUM();
            }
            for (int i = 0; i < warmup; i++) {
                plan.execute();
            }
            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                TornadoExecutionResult result = plan.execute();
                m.wallMs += (System.nanoTime() - start) / 1e6;

                TornadoProfilerResult p = result.getProfilerResult();
                m.taskGraphMs += p.getTotalTime() / 1e6;
                m.bytesCopyIn = p.getTotalBytesCopyIn();
                m.bytesCopyOut = p.getTotalBytesCopyOut();
                m.deviceMemory = p.getTotalDeviceMemoryUsage();
            }
        }
        m.wallMs /= iterations;
        m.taskGraphMs /= iterations;
        return m;
    }

    private static void report(Metrics def, Metrics um) {
        System.out.printf("%-26s %14s %14s %12s%n", "Metric", "Default", "UnifiedMem", "Delta");
        System.out.println("-----------------------------------------------------------------------");
        line("Wall time (ms)", def.wallMs, um.wallMs);
        line("Task-graph time (ms)", def.taskGraphMs, um.taskGraphMs);
        System.out.println("-----------------------------------------------------------------------");
        System.out.printf("%-26s %14d %14d%n", "Bytes copy-in / iter", def.bytesCopyIn, um.bytesCopyIn);
        System.out.printf("%-26s %14d %14d%n", "Bytes copy-out / iter", def.bytesCopyOut, um.bytesCopyOut);
        System.out.printf("%-26s %11.1f MB %11.1f MB%n", "Device memory", def.deviceMemory / (1024.0 * 1024.0), um.deviceMemory / (1024.0 * 1024.0));
        System.out.println();
        System.out.println("Note: the CUDA backend currently reports device-event timers");
        System.out.println("(TOTAL_KERNEL_TIME / COPY_IN_TIME / COPY_OUT_TIME) as 0, so only");
        System.out.println("end-to-end task-graph time and byte/memory counters are compared here.");
    }

    private static void line(String label, double def, double um) {
        double delta = def == 0.0 ? 0.0 : (um - def) / def * 100.0;
        System.out.printf("%-26s %14.3f %14.3f %+11.1f%%%n", label, def, um, delta);
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

        // EVERY_EXECUTION on the weights so the full matrix is transferred to the device
        // every iteration: this is the copy-bound regime where the host<->device traffic
        // (and, in a future phase, its elimination under Unified Memory) is visible.
        TaskGraph defaultGraph = new TaskGraph("default") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, w) //
                .task("t0", MatrixVectorUnifiedMemory::matrixVector, x, outDefault, w, n, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outDefault);

        TaskGraph umGraph = new TaskGraph("um") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, w) //
                .task("t0", MatrixVectorUnifiedMemory::matrixVector, x, outUM, w, n, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outUM);

        Metrics defaultMetrics = run(defaultGraph.snapshot(), false, iterations, warmup);
        Metrics umMetrics = run(umGraph.snapshot(), true, iterations, warmup);

        report(defaultMetrics, umMetrics);

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
