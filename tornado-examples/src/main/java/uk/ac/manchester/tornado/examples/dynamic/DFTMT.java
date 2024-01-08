/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.dynamic.DFTMT
 * </code>
 */
public class DFTMT {

    public static boolean CHECK_RESULT = true;

    public static void computeDFT(FloatArray inreal, FloatArray inimag, FloatArray outreal, FloatArray outimag, IntArray inputSize) {
        int n = inreal.getSize();
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            float sumreal = 0;
            float sumimag = 0;
            for (int t = 0; t < n; t++) { // For each input element
                float angle = ((2 * TornadoMath.floatPI() * t * k) / n);
                sumreal += (inreal.get(t) * (TornadoMath.cos(angle)) + inimag.get(t) * (TornadoMath.sin(angle)));
                sumimag += -(inreal.get(t) * (TornadoMath.sin(angle)) + inimag.get(t) * (TornadoMath.cos(angle)));
            }
            outreal.set(k, sumreal);
            outimag.set(k, sumimag);
        }
    }

    public static void computeDFTThreads(FloatArray inreal, FloatArray inimag, FloatArray outreal, FloatArray outimag, int threads, Thread[] th) throws InterruptedException {
        int n = inreal.getSize();
        int balk = inreal.getSize() / threads;
        for (int i = 0; i < threads; i++) {
            final int current = i;
            int lowBound = current * balk;
            int upperBound = (current + 1) * balk;
            if (current == threads - 1) {
                upperBound = inreal.getSize();
            }
            int finalUpperBound = upperBound;
            th[i] = new Thread(() -> {
                for (int k = lowBound; k < finalUpperBound; k++) {
                    float sumreal = 0;
                    float sumimag = 0;
                    for (int t = 0; t < inreal.getSize(); t++) { // For each input element
                        float angle = (float) ((2 * Math.PI * t * k) / (float) n);
                        sumreal += (float) (inreal.get(t) * (Math.cos(angle)) + inimag.get(t) * (Math.sin(angle)));
                        sumimag += -(float) (inreal.get(t) * (Math.sin(angle)) + inimag.get(t) * (Math.cos(angle)));
                    }
                    outreal.set(k, sumreal);
                    outimag.set(k, sumimag);
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

    public static boolean validate(int size, FloatArray inReal, FloatArray inImag, FloatArray outReal, FloatArray outImag, IntArray inputSize) {
        boolean val = true;
        FloatArray outRealTor = new FloatArray(size);
        FloatArray outImagTor = new FloatArray(size);

        computeDFT(inReal, inImag, outRealTor, outImagTor, inputSize);

        for (int i = 0; i < size; i++) {
            if (Math.abs(outImagTor.get(i) - outImag.get(i)) > 0.1) {
                System.out.println(outImagTor.get(i) + " vs " + outImag.get(i) + "\n");
                val = false;
                break;
            }
            if (Math.abs(outReal.get(i) - outRealTor.get(i)) > 0.1) {
                System.out.println(outReal.get(i) + " vs " + outRealTor.get(i) + "\n");
                val = false;
                break;
            }
        }
        return val;
    }

    public static void main(String[] args) throws InterruptedException {

        if (args.length < 3) {
            System.out.println("Usage: <size> <mode:performance|end|sequential|multi> <iterations>");
            System.exit(-1);
        }

        final int size = Integer.parseInt(args[0]);
        String executionType = args[1];
        int iterations = Integer.parseInt(args[2]);

        long end;
        long start;

        TaskGraph graph;
        FloatArray inReal;
        FloatArray inImag;
        FloatArray outReal;
        FloatArray outImag;
        IntArray inputSize;

        inReal = new FloatArray(size);
        inImag = new FloatArray(size);
        outReal = new FloatArray(size);
        outImag = new FloatArray(size);
        inputSize = new IntArray(1);

        inputSize.set(0, size);

        for (int i = 0; i < size; i++) {
            inReal.set(i, 1 / (float) (i + 2));
            inImag.set(i, 1 / (float) (i + 2));
        }

        graph = new TaskGraph("s0");
        TornadoExecutionPlan executor = null;
        if (!executionType.equals("multi") && !executionType.equals("sequential")) {
            long startInit = System.nanoTime();
            graph.transferToDevice(DataTransferMode.FIRST_EXECUTION, inReal, inImag) //
                    .task("t0", DFTMT::computeDFT, inReal, inImag, outReal, outImag, inputSize) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag);

            ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
            executor = new TornadoExecutionPlan(immutableTaskGraph);
            long stopInit = System.nanoTime();
            System.out.println("Initialization time:  " + (stopInit - startInit) + " ns" + "\n");
        }

        int maxSystemThreads = Runtime.getRuntime().availableProcessors();
        Thread[] threads = new Thread[maxSystemThreads];

        System.out.println("Version running: " + executionType + " ! ");
        for (int i = 0; i < iterations; i++) {
            System.gc();
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
                    start = System.nanoTime();
                    computeDFT(inReal, inImag, outReal, outImag, inputSize);
                    end = System.nanoTime();
                    break;
                case "multi":
                    start = System.nanoTime();
                    computeDFTThreads(inReal, inImag, outReal, outImag, maxSystemThreads, threads);
                    end = System.nanoTime();
                    break;
                default:
                    start = System.nanoTime();
                    executor.execute();
                    end = System.nanoTime();
            }
            System.out.println("Total time:  " + (end - start) + " ns" + " \n");
        }

        if (CHECK_RESULT) {
            if (validate(size, inReal, inImag, outReal, outImag, inputSize)) {
                System.out.println("Validation: " + "SUCCESS " + "\n");
            } else {
                System.out.println("Validation: " + " FAIL " + "\n");
            }
        }
    }
}
