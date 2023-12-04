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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.dynamic.SaxpyMT
 * </code>
 */
public class SaxpyMT {

    public static void saxpy(float alpha, FloatArray x, FloatArray y, FloatArray b) {
        for (@Parallel int i = 0; i < y.getSize(); i++) {
            y.set(i, alpha * x.get(i) + b.get(i));
        }
    }

    public static void saxpyThreads(float alpha, FloatArray x, FloatArray y, FloatArray b, int threads, Thread[] th) throws InterruptedException {
        int balk = y.getSize() / threads;
        for (int i = 0; i < threads; i++) {
            final int current = i;
            int lowBound = current * balk;
            int upperBound = (current + 1) * balk;
            if (current == threads - 1) {
                upperBound = y.getSize();
            }
            int finalUpperBound = upperBound;
            th[i] = new Thread(() -> {
                for (int k = lowBound; k < finalUpperBound; k++) {
                    y.set(k, alpha * x.get(k) + b.get(k));
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
        long start;
        long end;
        float alpha = 2f;

        FloatArray x = new FloatArray(numElements);
        FloatArray y = new FloatArray(numElements);
        FloatArray b = new FloatArray(numElements);

        for (int i = 0; i < numElements; i++) {
            x.set(i, 450);
            y.set(i, 0);
            b.set(i, 20);
        }

        graph = new TaskGraph("s0");
        TornadoExecutionPlan executor = null;
        if (!executionType.equals("multi") && !executionType.equals("sequential")) {
            long startInit = System.nanoTime();

            graph.transferToDevice(DataTransferMode.FIRST_EXECUTION, x, b) //
                    .task("t0", SaxpyMT::saxpy, alpha, x, y, b) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

            ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
            executor = new TornadoExecutionPlan(immutableTaskGraph);

            long stopInit = System.nanoTime();
            System.out.println("Initialization time:  " + (stopInit - startInit) + " ns" + "\n");
        }

        int maxSystemThreads = Runtime.getRuntime().availableProcessors();
        Thread[] threads = new Thread[maxSystemThreads];
        for (int idx = 0; idx < iterations; idx++) {
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
                    executor.execute();
                    end = System.nanoTime();
            }
            System.out.println("Total Time:" + (end - start) + " ns");
        }
        boolean wrongResult = false;
        for (int i = 0; i < y.getSize(); i++) {
            if (Math.abs(y.get(i) - (alpha * x.get(i) + b.get(i))) > 0.01) {
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
