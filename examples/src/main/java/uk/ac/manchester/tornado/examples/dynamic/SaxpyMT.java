/*
 * Copyright (c) 2019-2020, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package uk.ac.manchester.tornado.examples.dynamic;

import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado -m tornado.examples/uk.ac.manchester.tornado.examples.dynamic.SaxpyMT
 * </code>
 */
public class SaxpyMT {

    public static void saxpy(float alpha, float[] x, float[] y, float[] b) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = alpha * x[i] + b[i];
        }
    }

    public static void saxpyThreads(float alpha, float[] x, float[] y, float[] b, int threads, Thread[] th) throws InterruptedException {
        int balk = y.length / threads;
        for (int i = 0; i < threads; i++) {
            final int current = i;
            int lowBound = current * balk;
            int upperBound = (current + 1) * balk;
            if (current == threads - 1) {
                upperBound = y.length;
            }
            int finalUpperBound = upperBound;
            th[i] = new Thread(() -> {
                for (int k = lowBound; k < finalUpperBound; k++) {
                    y[k] = alpha * x[k] + b[k];
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

    public static void main(String[] args) {

        if (args.length < 3) {
            System.out.println("Usage: <elements> <mode:performance|end|sequential> <iterations>");
            System.exit(-1);
        }

        int numElements = Integer.parseInt(args[0]);
        String executionType = args[1];
        int iterations = Integer.parseInt(args[2]);
        TaskGraph graph;
        long start,end;
        float alpha = 2f;

        float[] x = new float[numElements];
        float[] y = new float[numElements];
        float[] b = new float[numElements];

        for (int i = 0; i < numElements; i++) {
            x[i] = 450;
            y[i] = 0;
            b[i] = 20;
        }

        graph = new TaskGraph("s0");
        if (!executionType.equals("multi") && !executionType.equals("sequential")) {
            long startInit = System.nanoTime();

            graph.transferToDevice(DataTransferMode.FIRST_EXECUTION, x, b) //
                    .task("t0", SaxpyMT::saxpy, alpha, x, y, b) //
                    .transferToHost(y);

            long stopInit = System.nanoTime();
            System.out.println("Initialization time:  " + (stopInit - startInit) + " ns" + "\n");
        }

        int maxSystemThreads = Runtime.getRuntime().availableProcessors();
        Thread[] threads = new Thread[maxSystemThreads];
        for (int idx = 0; idx < iterations; idx++) {
            switch (executionType) {
                case "performance":
                    start = System.nanoTime();
                    graph.executeWithProfilerSequential(Policy.PERFORMANCE);
                    end = System.nanoTime();
                    break;
                case "end":
                    start = System.nanoTime();
                    graph.executeWithProfilerSequential(Policy.END_2_END);
                    end = System.nanoTime();
                    break;
                case "sequential":
                    System.gc();
                    start = System.nanoTime();
                    saxpy(alpha, x, y, b);
                    end = System.nanoTime();
                    break;
                case "multi":
                    System.gc();
                    start = System.nanoTime();
                    try {
                        saxpyThreads(alpha, x, y, b, maxSystemThreads, threads);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    end = System.nanoTime();
                    break;
                default:
                    start = System.nanoTime();
                    graph.execute();
                    end = System.nanoTime();
            }
            System.out.println("Total Time:" + (end - start) + " ns");
        }
        boolean wrongResult = false;
        for (int i = 0; i < y.length; i++) {
            if (Math.abs(y[i] - (alpha * x[i] + b[i])) > 0.01) {
                wrongResult = true;
                break;
            }
        }
        if (!wrongResult) {
            System.out.println("Test success");
        } else {
            System.out.println("Result is wrong");
        }
    }
}
