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
 * How many COMPUTE streams a plan should use, measured rather than guessed. The graph holds
 * {@code UNITS} independent kernels that are deliberately too small to fill the device, so several of
 * them can be resident at once; {@link TornadoExecutionPlan#withIntraPlanConcurrency(int)} sizes the
 * pool those kernels are spread over.
 *
 * <p>Reading the numbers: the time should fall as the pool grows and then flatten once the device is
 * saturated (or the pool exceeds the number of independent kernels). A workload whose single kernel
 * already fills the device shows no benefit at any pool size - that is the expected result, not a
 * missing optimization.
 *
 * <p>How to run:
 *
 * <pre>
 *   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.streams.ConcurrentKernelsPoolSweep
 *
 *   # confirm the pool size in effect (distinct COMPUTE stream labels per launch):
 *   tornado --printBytecodes -m tornado.examples/...ConcurrentKernelsPoolSweep
 * </pre>
 */
public class ConcurrentKernelsPoolSweep {

    private static final int UNITS = 8;
    /** Small grid: a single kernel leaves most of the device idle. */
    private static final int SIZE = 32 * 1024;
    private static final int ITERATIONS_PER_ELEMENT = 1 << 16;
    private static final int WARMUP = 3;
    private static final int ITERATIONS = 10;
    private static final int[] POOL_SIZES = { 1, 2, 4, 8 };
    private static final float ALPHA = 0.5f;
    private static final float DELTA = 1e-2f;

    public static void spin(FloatArray x, FloatArray y, FloatArray result, float alpha) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {
            float value = alpha * x.get(i) + y.get(i);
            for (int j = 0; j < ITERATIONS_PER_ELEMENT; j++) {
                value = value * alpha + y.get(i);
            }
            result.set(i, value);
        }
    }

    private static float expected(float x, float y, float alpha) {
        float value = alpha * x + y;
        for (int j = 0; j < ITERATIONS_PER_ELEMENT; j++) {
            value = value * alpha + y;
        }
        return value;
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        FloatArray[] x = new FloatArray[UNITS];
        FloatArray[] y = new FloatArray[UNITS];
        FloatArray[] r = new FloatArray[UNITS];

        TaskGraph graph = new TaskGraph("poolSweep");
        for (int u = 0; u < UNITS; u++) {
            x[u] = new FloatArray(SIZE);
            y[u] = new FloatArray(SIZE);
            r[u] = new FloatArray(SIZE);
            x[u].init(u + 1.0f);
            y[u].init(u + 2.0f);
            graph.transferToDevice(DataTransferMode.EVERY_EXECUTION, x[u], y[u]) //
                    .task("u" + u, ConcurrentKernelsPoolSweep::spin, x[u], y[u], r[u], ALPHA) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, r[u]);
        }
        ImmutableTaskGraph immutableGraph = graph.snapshot();

        double single = median(immutableGraph, 0);
        System.out.printf("%n%-24s %10s %9s%n", "ConcurrentKernels", "median ms", "speedup");
        System.out.printf("%-24s %10.2f %9s%n", "single stream", single, "-");
        for (int pool : POOL_SIZES) {
            double time = median(immutableGraph, pool);
            verify(r);
            System.out.printf("%-24s %10.2f %8.2fx%n", "concurrency, pool=" + pool, time, single / time);
        }
        System.out.printf("%n%d independent kernels of %d elements, %d timed iterations (median).%n", UNITS, SIZE, ITERATIONS);
    }

    /** {@code poolSize} of 0 measures the single-stream path. */
    private static double median(ImmutableTaskGraph immutableGraph, int poolSize) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(immutableGraph)) {
            if (poolSize > 0) {
                plan.withIntraPlanConcurrency(poolSize);
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
    }
}
