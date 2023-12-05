/*
 * Copyright (c) 2020-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.dynamic;

import uk.ac.manchester.tornado.api.DRMode;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Montecarlo algorithm to approximate the PI value. This version has been adapted from Marawacc test-suite.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.dynamic.MontecarloMT
 * </code>
 */
public class MontecarloMT {

    public static void computeMontecarlo(FloatArray output) {
        for (@Parallel int j = 0; j < output.getSize(); j++) {
            long seed = j;
            // generate a pseudo random number (you do need it twice)
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);

            // this generates a number between 0 and 1 (with an awful entropy)
            float x = (seed & 0x0FFFFFFF) / 268435455f;

            // repeat for y
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            float y = (seed & 0x0FFFFFFF) / 268435455f;

            float dist = TornadoMath.sqrt(x * x + y * y);
            if (dist <= 1.0f) {
                output.set(j, 1.0f);
            } else {
                output.set(j, 0.0f);
            }
        }
    }

    public static void computeMontecarloThreads(FloatArray output, int threads, Thread[] th) throws InterruptedException {
        int balk = output.getSize() / threads;
        for (int i = 0; i < threads; i++) {
            final int current = i;
            int lowBound = current * balk;
            int upperBound = (current + 1) * balk;
            if (current == threads - 1) {
                upperBound = output.getSize();
            }
            int finalUpperBound = upperBound;
            th[i] = new Thread(() -> {
                for (int k = lowBound; k < finalUpperBound; k++) {
                    long seed = k;
                    // generate a pseudo random number (you do need it twice)
                    seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                    seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);

                    // this generates a number between 0 and 1 (with an awful entropy)
                    float x = (seed & 0x0FFFFFFF) / 268435455f;

                    // repeat for y
                    seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                    seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                    float y = (seed & 0x0FFFFFFF) / 268435455f;

                    float dist = TornadoMath.sqrt(x * x + y * y);
                    if (dist <= 1.0f) {
                        output.set(k, 1.0f);
                    } else {
                        output.set(k, 0.0f);
                    }
                }
            });
        }
        for (int i = 0; i < threads; i++) {
            th[i].start();
        }
        for (int i = 0; i < threads; i++) {
            th[i].join();
        }
    }

    public static void montecarlo(final int size, final String executionType, final int iterations) throws InterruptedException {

        FloatArray output = new FloatArray(size);
        FloatArray seq = new FloatArray(size);
        long start;
        long end;

        long startInit = System.nanoTime();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", MontecarloMT::computeMontecarlo, output)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);

        long stopInit = System.nanoTime();
        System.out.println("Initialization time:  " + (stopInit - startInit) + " ns" + "\n");

        int maxSystemThreads = Runtime.getRuntime().availableProcessors();
        Thread[] threads = new Thread[maxSystemThreads];

        for (int i = 0; i < iterations; i++) {
            switch (executionType) {
                case "performance":
                    start = System.nanoTime();
                    executor.withDynamicReconfiguration(Policy.PERFORMANCE, DRMode.SERIAL).execute();
                    end = System.nanoTime();
                    break;
                case "end":
                    start = System.nanoTime();
                    executor.withDynamicReconfiguration(Policy.END_2_END, DRMode.SERIAL).execute();
                    end = System.nanoTime();
                    break;
                case "sequential":
                    System.gc();
                    start = System.nanoTime();
                    computeMontecarlo(output);
                    end = System.nanoTime();
                    break;
                case "multi":
                    System.gc();
                    start = System.nanoTime();
                    computeMontecarloThreads(output, maxSystemThreads, threads);
                    end = System.nanoTime();
                    break;
                default:
                    start = System.nanoTime();
                    executor.execute();
                    end = System.nanoTime();
            }
            System.out.println("Total time:  " + (end - start) + " ns" + "\n");
        }

        float sum = 0;
        for (int j = 0; j < size; j++) {
            sum += output.get(j);
        }
        sum *= 4;
        System.out.println("Pi value (TornadoVM) : " + (sum / size));

        computeMontecarlo(seq);

        sum = 0;
        for (int j = 0; j < size; j++) {
            sum += seq.get(j);
        }
        sum *= 4;

        System.out.println("Pi value (Sequential) : " + (sum / size));

    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Montecarlo Computation");

        if (args.length < 3) {
            System.out.println("Usage: <elements> <mode:performance|end|sequential> <iterations>");
            System.exit(-1);
        }
        int inputSize = Integer.parseInt(args[0]);
        String executionType = args[1];
        int iterations = Integer.parseInt(args[2]);
        montecarlo(inputSize, executionType, iterations);
    }
}
