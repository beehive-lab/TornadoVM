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
package uk.ac.manchester.tornado.benchmarks.hostmemory;

import java.util.Arrays;
import java.util.Locale;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.memory.HostMemoryType;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Host-memory benchmark: what pinned (page-locked) and mapped (zero-copy) host memory buy over pageable
 * memory. The same cases are measured by the native CUDA reference in
 * {@code tornado-benchmarks/src/main/cuda/host_memory_bandwidth.cu}, with the same CSV output, so the two
 * can be compared line by line ({@code tornado-hostmem-compare}).
 *
 * <p>
 * Arguments: {@code <mode> [label]}, where mode is {@code pageable}, {@code pinned}, {@code mapped} or
 * {@code staged} (pageable arrays and {@code withStagedTransfers()}), and label names the rows (defaults
 * to the mode). Pageable memory without the CUDA backend's automatic registration is measured by running
 * the {@code pageable} mode with {@code -Dtornado.cuda.host.pinning=false} and label {@code nopin}.
 * </p>
 *
 * <p>
 * Cases, one CSV row each ({@code impl,label,case,bytes,median_ms,GBps}):
 * </p>
 * <ul>
 * <li>{@code h2d}, {@code d2h}: copy bandwidth, from the profiler's copy timers (not for mapped arrays,
 * which are never copied).</li>
 * <li>{@code zc_read}: a kernel streaming the array once: over the bus for mapped arrays, from device
 * memory otherwise (the array is uploaded once).</li>
 * <li>{@code saxpy}, {@code reduce_once}, {@code reread16}: end to end, per {@code execute()}, with the
 * inputs uploaded and the output read back every time.</li>
 * <li>{@code sparse_read}: a kernel reading one float per 4 KB of a 1 GB array, end to end: copies
 * move the whole array, mapped memory only what is read.</li>
 * <li>{@code alloc}: allocating and first touching an array.</li>
 * </ul>
 */
public final class HostMemoryBenchmark {

    private static final int WARMUP = Integer.getInteger("hostmem.warmup", 5);
    private static final int ITERATIONS = Integer.getInteger("hostmem.iterations", 20);
    private static final long[] SIZES_MB = { 1, 4, 16, 64, 256, 1024 };
    private static final int WORKLOAD_MB = Integer.getInteger("hostmem.workload.mb", 256);
    private static final int REREADS = 16;
    private static final int REDUCE_CHUNK = 1024;
    private static final int SPARSE_STRIDE = 1024;
    private static final int SPARSE_MB = 1024;

    private HostMemoryBenchmark() {
    }

    // ------------------------------------------------------------------ kernels

    public static void touch(FloatArray x, FloatArray out) {
        for (@Parallel int i = 0; i < 1; i++) {
            out.set(i, x.get(i) + x.get(x.getSize() - 1));
        }
    }

    public static void fill(FloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, i);
        }
    }

    public static void stream(FloatArray x, FloatArray y) {
        for (@Parallel int i = 0; i < x.getSize(); i++) {
            y.set(i, x.get(i) * 2.0f);
        }
    }

    public static void saxpy(FloatArray x, FloatArray y, FloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, 2.0f * x.get(i) + y.get(i));
        }
    }

    /** Every element read once, coalesced across threads: thread c sums x[c], x[c + chunks], ... */
    public static void reduceOnce(FloatArray x, FloatArray partial) {
        int chunks = partial.getSize();
        for (@Parallel int c = 0; c < chunks; c++) {
            float sum = 0.0f;
            for (int k = 0; k < REDUCE_CHUNK; k++) {
                sum += x.get(k * chunks + c);
            }
            partial.set(c, sum);
        }
    }

    /** Sparse access: one element per SPARSE_STRIDE (4 KB), so 1/1024 of the array is read. */
    public static void sparseRead(FloatArray x, FloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, x.get(i * SPARSE_STRIDE) * 2.0f);
        }
    }

    /** Every element read REREADS times, by different threads. */
    public static void reread(FloatArray x, FloatArray out) {
        int n = x.getSize();
        int stride = n / REREADS;
        for (@Parallel int i = 0; i < n; i++) {
            float acc = 0.0f;
            for (int k = 0; k < REREADS; k++) {
                acc += x.get((i + k * stride) % n);
            }
            out.set(i, acc);
        }
    }

    // ------------------------------------------------------------------ harness

    private static String label;
    private static HostMemoryType type;
    private static boolean staged;

    private static FloatArray array(long bytes) {
        FloatArray a = FloatArray.allocate((int) (bytes / Float.BYTES), type);
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, i % 97);
        }
        return a;
    }

    private static TornadoExecutionPlan plan(TaskGraph graph) {
        TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
        plan.withProfiler(ProfilerMode.SILENT);
        if (staged) {
            plan.withStagedTransfers();
        }
        return plan;
    }

    private interface Metric {
        long nanos(TornadoExecutionResult result, long wallNanos);
    }

    private static double medianMillis(TornadoExecutionPlan plan, Metric metric) throws TornadoExecutionPlanException {
        for (int i = 0; i < WARMUP; i++) {
            plan.execute();
        }
        double[] samples = new double[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            TornadoExecutionResult result = plan.execute();
            long wall = System.nanoTime() - start;
            samples[i] = metric.nanos(result, wall) / 1e6;
        }
        Arrays.sort(samples);
        return samples[ITERATIONS / 2];
    }

    private static double medianWallMillis(Runnable body) {
        for (int i = 0; i < WARMUP; i++) {
            body.run();
        }
        double[] samples = new double[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            body.run();
            samples[i] = (System.nanoTime() - start) / 1e6;
        }
        Arrays.sort(samples);
        return samples[ITERATIONS / 2];
    }

    private static void row(String name, long bytes, double millis) {
        double gbps = millis > 0 ? bytes / (millis * 1e6) : 0;
        System.out.printf(Locale.ROOT, "CSV,tornadovm,%s,%s,%d,%.4f,%.2f%n", label, name, bytes, millis, gbps);
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        String mode = args.length > 0 ? args[0] : "pageable";
        label = args.length > 1 ? args[1] : mode;
        staged = mode.equals("staged");
        type = switch (mode) {
            case "pinned" -> HostMemoryType.PINNED;
            case "mapped" -> HostMemoryType.MAPPED;
            default -> HostMemoryType.PAGEABLE;
        };
        System.out.println("CSV,impl,label,case,bytes,median_ms,GBps");

        for (long mb : SIZES_MB) {
            long bytes = mb << 20;
            FloatArray x = array(bytes);
            FloatArray small = FloatArray.allocate(1, HostMemoryType.PAGEABLE);
            FloatArray y = FloatArray.allocate(x.getSize(), HostMemoryType.PAGEABLE);

            if (type != HostMemoryType.MAPPED) {
                TaskGraph h2d = new TaskGraph("h2d" + mb) //
                        .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                        .task("t", HostMemoryBenchmark::touch, x, small) //
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, small);
                try (TornadoExecutionPlan plan = plan(h2d)) {
                    // Staged uploads are not timed by the profiler: time the whole execution instead
                    // (the kernel and the 4-byte read-back are negligible next to the upload).
                    row("h2d", bytes, medianMillis(plan, (r, wall) -> staged ? wall : r.getProfilerResult().getDeviceWriteTime()));
                }
                // Copy-out on demand, timed around the (blocking) copy itself: the profiler does not
                // time a blocking copy-out that is issued without dependency tracking.
                TaskGraph d2h = new TaskGraph("d2h" + mb) //
                        .task("t", HostMemoryBenchmark::fill, x) //
                        .transferToHost(DataTransferMode.UNDER_DEMAND, x);
                try (TornadoExecutionPlan plan = plan(d2h)) {
                    TornadoExecutionResult result = plan.execute();
                    row("d2h", bytes, medianWallMillis(() -> result.transferToHost(x)));
                }
            }

            TaskGraph zc = new TaskGraph("zc" + mb) //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                    .task("t", HostMemoryBenchmark::stream, x, y);
            try (TornadoExecutionPlan plan = plan(zc)) {
                row("zc_read", bytes, medianMillis(plan, (r, wall) -> r.getProfilerResult().getDeviceKernelTime()));
            }
        }

        long bytes = (long) WORKLOAD_MB << 20;
        FloatArray x = array(bytes);
        FloatArray y = array(bytes);
        FloatArray out = array(bytes);
        FloatArray partial = FloatArray.allocate(x.getSize() / REDUCE_CHUNK, type);

        TaskGraph saxpy = new TaskGraph("saxpy") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y) //
                .task("t", HostMemoryBenchmark::saxpy, x, y, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = plan(saxpy)) {
            row("saxpy", 3 * bytes, medianMillis(plan, (r, wall) -> wall));
        }
        TaskGraph reduce = new TaskGraph("reduce") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .task("t", HostMemoryBenchmark::reduceOnce, x, partial) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, partial);
        try (TornadoExecutionPlan plan = plan(reduce)) {
            row("reduce_once", bytes, medianMillis(plan, (r, wall) -> wall));
        }
        TaskGraph reread = new TaskGraph("reread") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .task("t", HostMemoryBenchmark::reread, x, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = plan(reread)) {
            row("reread16", 2 * bytes, medianMillis(plan, (r, wall) -> wall));
        }

        // Sparse access to a large array: copies move all of it, zero-copy only what is read.
        long sparseBytes = (long) SPARSE_MB << 20;
        FloatArray big = array(sparseBytes);
        FloatArray picked = FloatArray.allocate(big.getSize() / SPARSE_STRIDE, HostMemoryType.PAGEABLE);
        TaskGraph sparse = new TaskGraph("sparse") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, big) //
                .task("t", HostMemoryBenchmark::sparseRead, big, picked) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, picked);
        try (TornadoExecutionPlan plan = plan(sparse)) {
            row("sparse_read", sparseBytes, medianMillis(plan, (r, wall) -> wall));
        }

        // Allocation and first touch.
        row("alloc", bytes, medianWallMillis(() -> FloatArray.allocate((int) (bytes / Float.BYTES), type).getSegment().fill((byte) 1)));
    }
}
