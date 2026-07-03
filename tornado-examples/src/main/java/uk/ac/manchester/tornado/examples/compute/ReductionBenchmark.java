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

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Benchmark for {@code @Reduce} sum codegen. On the CUDA-C backend, run twice
 * to compare the generated local-memory reduction tree against the
 * CUB-delegated path:
 *
 * <p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.ReductionBenchmark 67108864 50
 * tornado --jvm="-Dtornado.cuda.reduce.cub=True" -m tornado.examples/uk.ac.manchester.tornado.examples.compute.ReductionBenchmark 67108864 50
 * </code>
 * </p>
 */
public class ReductionBenchmark {

    private static final int WARMUP_ITERATIONS = 20;

    public static void reduceSum(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {

        final int size = (args.length > 0) ? Integer.parseInt(args[0]) : 64 * 1024 * 1024;
        final int iterations = (args.length > 1) ? Integer.parseInt(args[1]) : 50;
        final boolean cub = Boolean.parseBoolean(System.getProperty("tornado.cuda.reduce.cub", "False"));

        System.out.println("@Reduce sum benchmark: " + size + " floats, " + iterations + " iterations (+" + WARMUP_ITERATIONS + " warm-up), cub=" + cub);

        FloatArray input = new FloatArray(size);
        FloatArray result = new FloatArray(1);
        double expected = 0.0;
        java.util.Random random = new java.util.Random(42);
        for (int i = 0; i < size; i++) {
            input.set(i, random.nextFloat() - 0.5f);
            expected += input.get(i);
        }

        TaskGraph taskGraph = new TaskGraph("reduce") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("sum", ReductionBenchmark::reduceSum, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        double avgNanos;
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                executionPlan.execute();
            }
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                executionPlan.execute();
            }
            avgNanos = (System.nanoTime() - start) / (double) iterations;
        }

        boolean isResultCorrect = Math.abs(result.get(0) - expected) / Math.max(1.0, Math.abs(expected)) < 1e-2;
        System.out.printf("Reduce %d floats: %.3f ms/iter (%.1f GB/s)%n", size, avgNanos * 1e-6, (size * 4.0 / 1e9) / (avgNanos * 1e-9));
        System.out.println("gpu=" + result.get(0) + " cpu=" + expected);
        System.out.println(isResultCorrect ? "Result is correct" : "Result is wrong");
    }
}
