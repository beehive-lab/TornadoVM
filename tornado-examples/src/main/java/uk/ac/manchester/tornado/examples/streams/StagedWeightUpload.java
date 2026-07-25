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
package uk.ac.manchester.tornado.examples.streams;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * One-shot weight upload: direct transfers versus the pinned staging ring
 * ({@link TornadoExecutionPlan#withStagedTransfers()}), reported as effective GB/s and cross-checked
 * against the profiler's own copy-in timer.
 *
 * <p>The two paths make opposite trades, and which one wins depends on the source memory:
 *
 * <ul>
 *   <li><b>Direct</b> page-locks the whole source segment once and lets the DMA engine read it. For
 *       warm, already-faulted host memory - such as the {@link FloatArray} below - this is the faster
 *       path: the DMA reads the source in place and no byte is copied twice.</li>
 *   <li><b>Staged</b> chunks the transfer through a small ring of pinned slots and skips the
 *       whole-segment page-lock. It wins when that page-lock is expensive, which is the case for a
 *       large, cold, mmap'd source (a GGUF model file being uploaded once at start-up), because
 *       registering it has to fault in and pin every page before the first byte moves.</li>
 * </ul>
 *
 * So on this example - a heap-allocated, fully warm array - expect direct to be at or ahead of staged.
 * That is the honest result, and it is why the ring is opt-in and gated on a minimum transfer size.
 *
 * <p>How to run:
 *
 * <pre>
 *   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.streams.StagedWeightUpload
 *
 *   # bandwidth of each staged chunk on the timeline:
 *   nsys profile --trace=cuda,nvtx -o staged tornado -m tornado.examples/...StagedWeightUpload
 *   nsys stats --report cuda_gpu_mem_size_sum --report cuda_gpu_mem_time_sum staged.nsys-rep
 * </pre>
 */
public class StagedWeightUpload {

    /** 8 tensors of 32M floats = 128 MB each, 1 GB total, uploaded once. */
    private static final int TENSORS = 8;
    private static final int TENSOR_SIZE = 32 * 1024 * 1024;
    private static final int STEADY_ITERATIONS = 5;
    private static final float DELTA = 1e-3f;

    public static void scale(FloatArray in, FloatArray out, float alpha) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, alpha * in.get(i));
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        System.out.printf("%n%-34s %12s %10s %14s%n", "StagedWeightUpload", "upload ms", "GB/s", "profiler ms");
        measure("direct (whole-segment pin)", false, 0, 0, 0);
        measure("staged, 16 MB x 4 slots", true, 16L << 20, 16L << 20, 4);
        measure("staged, 4 MB x 8 slots", true, 4L << 20, 4L << 20, 8);
        System.out.printf("%n%d tensors of %d MB (%d MB total), warm host memory.%n", //
                TENSORS, TENSOR_SIZE * Float.BYTES / (1024 * 1024), (long) TENSORS * TENSOR_SIZE * Float.BYTES / (1024 * 1024));
    }

    /**
     * Times the first execution of a fresh plan (allocation + the one-shot upload + kernels) and
     * subtracts the steady-state execution time, which no longer uploads the weights. What is left is
     * attributable to the upload.
     */
    private static void measure(String label, boolean staged, long minSize, long chunkSize, int ringDepth) throws TornadoExecutionPlanException {
        FloatArray[] weights = new FloatArray[TENSORS];
        FloatArray[] out = new FloatArray[TENSORS];
        TaskGraph graph = new TaskGraph("weights");
        for (int t = 0; t < TENSORS; t++) {
            weights[t] = new FloatArray(TENSOR_SIZE);
            out[t] = new FloatArray(TENSOR_SIZE);
            weights[t].init(t + 1.0f);
            // FIRST_EXECUTION: the weights are uploaded once and then stay resident, which is the
            // access pattern the staging ring targets.
            graph.transferToDevice(DataTransferMode.FIRST_EXECUTION, weights[t]) //
                    .task("t" + t, StagedWeightUpload::scale, weights[t], out[t], 2.0f) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, out[t]);
        }
        ImmutableTaskGraph immutableGraph = graph.snapshot();

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(immutableGraph)) {
            plan.withProfiler(ProfilerMode.SILENT);
            if (staged) {
                plan.withStagedTransfers(minSize, chunkSize, ringDepth);
            } else {
                plan.withoutStagedTransfers();
            }
            // Compile ahead of the timed execution, so the measurement is transfer-bound.
            plan.withPreCompilation();

            long start = System.nanoTime();
            long copyIn = plan.execute().getProfilerResult().getDeviceWriteTime();
            double first = (System.nanoTime() - start) / 1e6;

            long[] steady = new long[STEADY_ITERATIONS];
            for (int i = 0; i < STEADY_ITERATIONS; i++) {
                long iterationStart = System.nanoTime();
                plan.execute();
                steady[i] = System.nanoTime() - iterationStart;
            }
            java.util.Arrays.sort(steady);
            double steadyMs = steady[steady.length / 2] / 1e6;

            double uploadMs = first - steadyMs;
            double bytes = (double) TENSORS * TENSOR_SIZE * Float.BYTES;
            System.out.printf("%-34s %12.2f %10.2f %14.2f%n", label, uploadMs, bytes / (uploadMs * 1e6), copyIn / 1e6);

            for (int t = 0; t < TENSORS; t++) {
                for (int i = 0; i < TENSOR_SIZE; i += 4096) {
                    if (Math.abs(2.0f * (t + 1.0f) - out[t].get(i)) > DELTA) {
                        throw new AssertionError(label + ": tensor " + t + " element " + i + " is " + out[t].get(i));
                    }
                }
            }
        }
    }
}
