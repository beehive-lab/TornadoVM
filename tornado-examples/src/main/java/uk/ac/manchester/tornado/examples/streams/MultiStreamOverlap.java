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
 * Transfer/compute overlap with {@link TornadoExecutionPlan#withIntraPlanConcurrency()}. The task
 * graph holds {@code UNITS} independent host-to-device -> compute -> device-to-host pipelines. On the
 * single-stream path every operation is issued to one CUDA stream and therefore runs in program
 * order; with intra-plan concurrency the copies go to the H2D/D2H role streams and the kernels to the
 * COMPUTE stream pool, so unit {@code i+1}'s upload overlaps unit {@code i}'s kernel.
 *
 * <p>Expected GPU timeline (Nsight Systems, stream rows are NVTX-named):
 *
 * <pre>
 *   DATA_TRANSFER_H2D: [H2D u0][H2D u1][H2D u2][H2D u3]
 *   COMPUTE:                   [--k u0--]
 *   COMPUTE_1:                          [--k u1--]
 *   COMPUTE_2:                                   [--k u2--]
 *   DATA_TRANSFER_D2H:                      [D2H u0][D2H u1]
 *                                       ^^^^^^^^^^^^^^^^^^^
 *                              uploads, kernels and downloads in flight together
 * </pre>
 *
 * <p>How to run:
 *
 * <pre>
 *   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.streams.MultiStreamOverlap
 *
 *   # which stream each operation was issued to:
 *   tornado --printBytecodes -m tornado.examples/...MultiStreamOverlap
 *
 *   # timeline:
 *   nsys profile --trace=cuda,nvtx -o overlap tornado -m tornado.examples/...MultiStreamOverlap
 *   nsys stats --report cuda_gpu_trace --report cuda_api_sum overlap.nsys-rep
 * </pre>
 *
 * <p>Only the PTX and CUDA backends implement intra-plan concurrency; elsewhere the call is ignored
 * and both measurements report the single-stream path.
 */
public class MultiStreamOverlap {

    /** Independent pipelines in the graph; more than the default COMPUTE pool size (4). */
    private static final int UNITS = 8;
    /** 6M floats = 24 MB per array, so each copy is a visible bar on the timeline. */
    private static final int SIZE = 6 * 1024 * 1024;
    /** Per-element loop count: raises kernel time to the same order as the 24 MB copy. */
    private static final int COMPUTE_ITERATIONS = 256;
    private static final int WARMUP = 3;
    private static final int ITERATIONS = 10;
    private static final float ALPHA = 0.5f;
    private static final float DELTA = 1e-2f;

    public static void compute(FloatArray x, FloatArray y, FloatArray result, float alpha) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {
            float value = alpha * x.get(i) + y.get(i);
            for (int j = 0; j < COMPUTE_ITERATIONS; j++) {
                value = value * alpha + y.get(i);
            }
            result.set(i, value);
        }
    }

    private static float expected(float x, float y, float alpha) {
        float value = alpha * x + y;
        for (int j = 0; j < COMPUTE_ITERATIONS; j++) {
            value = value * alpha + y;
        }
        return value;
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        FloatArray[] x = new FloatArray[UNITS];
        FloatArray[] y = new FloatArray[UNITS];
        FloatArray[] r = new FloatArray[UNITS];

        TaskGraph graph = new TaskGraph("overlap");
        for (int u = 0; u < UNITS; u++) {
            x[u] = new FloatArray(SIZE);
            y[u] = new FloatArray(SIZE);
            r[u] = new FloatArray(SIZE);
            x[u].init(u + 1.0f);
            y[u].init(u + 2.0f);
            graph.transferToDevice(DataTransferMode.EVERY_EXECUTION, x[u], y[u]) //
                    .task("u" + u, MultiStreamOverlap::compute, x[u], y[u], r[u], ALPHA) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, r[u]);
        }

        ImmutableTaskGraph immutableGraph = graph.snapshot();
        double single = median(immutableGraph, false);
        double concurrent = median(immutableGraph, true);
        verify(r);

        System.out.printf("%n%-22s %10s %10s %9s%n", "MultiStreamOverlap", "median ms", "GB/s", "speedup");
        double bytes = (double) UNITS * SIZE * Float.BYTES * 3.0; // 2 arrays in, 1 out, per unit
        System.out.printf("%-22s %10.2f %10.2f %9s%n", "single stream", single, bytes / (single * 1e6), "-");
        System.out.printf("%-22s %10.2f %10.2f %8.2fx%n", "intra-plan concurrency", concurrent, bytes / (concurrent * 1e6), single / concurrent);
        System.out.printf("%nUnits: %d, %d MB per array, %d timed iterations (median).%n", UNITS, SIZE * Float.BYTES / (1024 * 1024), ITERATIONS);
    }

    /** Median wall-clock time of {@code ITERATIONS} executions, after {@code WARMUP} untimed ones. */
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

    private static void verify(FloatArray[] r) {
        for (int u = 0; u < UNITS; u++) {
            float want = expected(u + 1.0f, u + 2.0f, ALPHA);
            for (int i = 0; i < r[u].getSize(); i++) {
                if (Math.abs(want - r[u].get(i)) > DELTA * Math.max(1.0f, Math.abs(want))) {
                    throw new AssertionError("unit " + u + " element " + i + ": expected " + want + " but was " + r[u].get(i));
                }
            }
        }
        System.out.println("Result verified against the sequential reference.");
    }
}
