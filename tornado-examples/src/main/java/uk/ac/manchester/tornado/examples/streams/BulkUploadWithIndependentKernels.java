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
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Kernels that do not depend on a bulk upload should run <em>during</em> it. The task graph mixes one
 * large host-to-device copy (feeding a single consumer kernel) with several kernels whose inputs are
 * already resident on the device, so nothing in them waits for the copy.
 *
 * <p>This is the shape that shows what a plan's stream assignment is worth. Every kernel launch first
 * writes its argument block to the device; while those writes shared the bulk H2D stream, an
 * independent kernel could not be launched until the multi-megabyte copy ahead of it had drained, so
 * the independent work serialised behind a transfer it had no dependency on. Kernel-argument writes
 * therefore go to their own stream ({@code KERNEL_ARGS}), and each launch carries its real dependency
 * list instead of inheriting one through the argument write.
 *
 * <p>How to run:
 *
 * <pre>
 *   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.streams.BulkUploadWithIndependentKernels
 *
 *   # per-operation stream routing (look for KERNEL_ARGS vs DATA_TRANSFER_H2D):
 *   tornado --printBytecodes -m tornado.examples/...BulkUploadWithIndependentKernels
 *
 *   # timeline: the independent kernels should start while the 200 MB copy is still running
 *   nsys profile --trace=cuda,nvtx -o bulk tornado -m tornado.examples/...BulkUploadWithIndependentKernels
 *   nsys stats --report cuda_gpu_trace bulk.nsys-rep
 * </pre>
 */
public class BulkUploadWithIndependentKernels {

    /** 50M floats = 200 MB, re-uploaded on every execution: a long-running H2D copy. */
    private static final int BULK_SIZE = 50 * 1024 * 1024;
    /** Kernels that are independent of the bulk copy. */
    private static final int INDEPENDENT_UNITS = 4;
    private static final int UNIT_SIZE = 64 * 1024;
    /**
     * Per-element loop count. Sized so the independent kernels together take about as long as the bulk
     * copy: if they were much cheaper, hiding them would not move the wall clock at all.
     */
    private static final int UNIT_ITERATIONS = 1 << 17;
    private static final int WARMUP = 3;
    private static final int ITERATIONS = 10;
    private static final float DELTA = 1e-2f;

    public static void scale(FloatArray in, FloatArray out, float alpha) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, alpha * in.get(i));
        }
    }

    /** Small grid, long per-thread loop: does not saturate the device, so these kernels can co-reside. */
    public static void spin(FloatArray in, FloatArray out, float alpha) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            float value = in.get(i);
            for (int j = 0; j < UNIT_ITERATIONS; j++) {
                value = value * alpha + 1.0f;
            }
            out.set(i, value);
        }
    }

    private static float expectedSpin(float in, float alpha) {
        float value = in;
        for (int j = 0; j < UNIT_ITERATIONS; j++) {
            value = value * alpha + 1.0f;
        }
        return value;
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        FloatArray bulk = new FloatArray(BULK_SIZE);
        FloatArray bulkOut = new FloatArray(BULK_SIZE);
        bulk.init(2.0f);

        FloatArray[] in = new FloatArray[INDEPENDENT_UNITS];
        FloatArray[] out = new FloatArray[INDEPENDENT_UNITS];

        // Declaration order matters: the bulk copy and its consumer come FIRST, so the independent
        // kernels are issued after them. Their argument writes are what used to queue behind the bulk
        // copy on the H2D stream, delaying work that has no dependency on it.
        TaskGraph graph = new TaskGraph("bulkPlusIndependent") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, bulk) //
                .task("consumer", BulkUploadWithIndependentKernels::scale, bulk, bulkOut, 2.0f);
        for (int u = 0; u < INDEPENDENT_UNITS; u++) {
            in[u] = new FloatArray(UNIT_SIZE);
            out[u] = new FloatArray(UNIT_SIZE);
            in[u].init(u + 1.0f);
            // Uploaded once: after the first execution these kernels have no transfer dependency at all.
            graph.transferToDevice(DataTransferMode.FIRST_EXECUTION, in[u]) //
                    .task("independent" + u, BulkUploadWithIndependentKernels::spin, in[u], out[u], 0.5f);
        }
        graph.transferToHost(DataTransferMode.EVERY_EXECUTION, bulkOut);
        for (int u = 0; u < INDEPENDENT_UNITS; u++) {
            graph.transferToHost(DataTransferMode.EVERY_EXECUTION, out[u]);
        }

        ImmutableTaskGraph immutableGraph = graph.snapshot();
        double single = median(immutableGraph, false);
        double concurrent = median(immutableGraph, true);
        verify(bulkOut, out);

        System.out.printf("%n%-24s %10s %9s%n", "BulkUpload+Independent", "median ms", "speedup");
        System.out.printf("%-24s %10.2f %9s%n", "single stream", single, "-");
        System.out.printf("%-24s %10.2f %8.2fx%n", "intra-plan concurrency", concurrent, single / concurrent);
        System.out.printf("%n%d MB bulk copy + %d independent kernels, %d timed iterations (median).%n", //
                BULK_SIZE * Float.BYTES / (1024 * 1024), INDEPENDENT_UNITS, ITERATIONS);
    }

    private static double median(ImmutableTaskGraph immutableGraph, boolean concurrent) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(immutableGraph)) {
            if (concurrent) {
                plan.withIntraPlanConcurrency();
            } else {
                plan.withoutIntraPlanConcurrency();
            }
            for (int i = 0; i < WARMUP; i++) {
                plan.execute();
            }
            long[] samples = new long[ITERATIONS];
            for (int i = 0; i < ITERATIONS; i++) {
                long start = System.nanoTime();
                plan.execute();
                samples[i] = System.nanoTime() - start;
            }
            java.util.Arrays.sort(samples);
            return samples[samples.length / 2] / 1e6;
        }
    }

    private static void verify(FloatArray bulkOut, FloatArray[] out) {
        for (int i = 0; i < bulkOut.getSize(); i += 4096) {
            if (Math.abs(4.0f - bulkOut.get(i)) > DELTA) {
                throw new AssertionError("bulk element " + i + ": expected 4.0 but was " + bulkOut.get(i));
            }
        }
        for (int u = 0; u < out.length; u++) {
            float want = expectedSpin(u + 1.0f, 0.5f);
            for (int i = 0; i < out[u].getSize(); i++) {
                if (Math.abs(want - out[u].get(i)) > DELTA * Math.max(1.0f, Math.abs(want))) {
                    throw new AssertionError("unit " + u + " element " + i + ": expected " + want + " but was " + out[u].get(i));
                }
            }
        }
        System.out.println("Result verified against the sequential reference.");
    }
}
