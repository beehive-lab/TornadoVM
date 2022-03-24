/*
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;

public class DFTMT {

    public static boolean CHECK_RESULT = true;

    public static void computeDft(float[] inreal, float[] inimag, float[] outreal, float[] outimag, int[] inputSize) {
        int n = inreal.length;
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            float sumreal = 0;
            float sumimag = 0;
            for (int t = 0; t < n; t++) { // For each input element
                float angle = ((2 * TornadoMath.floatPI() * t * k) / n);
                sumreal += (inreal[t] * (TornadoMath.cos(angle)) + inimag[t] * (TornadoMath.sin(angle)));
                sumimag += -(inreal[t] * (TornadoMath.sin(angle)) + inimag[t] * (TornadoMath.cos(angle)));
            }
            outreal[k] = sumreal;
            outimag[k] = sumimag;
        }
    }

    public static void computeDftThreads(float[] inreal, float[] inimag, float[] outreal, float[] outimag, int threads, Thread[] th) throws InterruptedException {
        int n = inreal.length;
        int balk = inreal.length / threads;
        for (int i = 0; i < threads; i++) {
            final int current = i;
            int lowBound = current * balk;
            int upperBound = (current + 1) * balk;
            if (current == threads - 1) {
                upperBound = inreal.length;
            }
            int finalUpperBound = upperBound;
            th[i] = new Thread(() -> {
                for (int k = lowBound; k < finalUpperBound; k++) {
                    float sumreal = 0;
                    float sumimag = 0;
                    for (int t = 0; t < inreal.length; t++) { // For each input element
                        float angle = (float) ((2 * Math.PI * t * k) / (float) n);
                        sumreal += (float) (inreal[t] * (Math.cos(angle)) + inimag[t] * (Math.sin(angle)));
                        sumimag += -(float) (inreal[t] * (Math.sin(angle)) + inimag[t] * (Math.cos(angle)));
                    }
                    outreal[k] = sumreal;
                    outimag[k] = sumimag;
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

    public static boolean validate(int size, float[] inReal, float[] inImag, float[] outReal, float[] outImag, int[] inputSize) {
        boolean val = true;
        float[] outRealTor = new float[size];
        float[] outImagTor = new float[size];

        computeDft(inReal, inImag, outRealTor, outImagTor, inputSize);

        for (int i = 0; i < size; i++) {
            if (Math.abs(outImagTor[i] - outImag[i]) > 0.1) {
                System.out.println(outImagTor[i] + " vs " + outImag[i] + "\n");
                val = false;
                break;
            }
            if (Math.abs(outReal[i] - outRealTor[i]) > 0.1) {
                System.out.println(outReal[i] + " vs " + outRealTor[i] + "\n");
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

        long end,start;

        TaskSchedule graph;
        float[] inReal;
        float[] inImag;
        float[] outReal;
        float[] outImag;
        int[] inputSize;

        inReal = new float[size];
        inImag = new float[size];
        outReal = new float[size];
        outImag = new float[size];
        inputSize = new int[1];

        inputSize[0] = size;

        for (int i = 0; i < size; i++) {
            inReal[i] = 1 / (float) (i + 2);
            inImag[i] = 1 / (float) (i + 2);
        }

        graph = new TaskSchedule("s0");
        if (executionType.equals("multi") || executionType.equals("sequential")) {
            ;
        } else {
            long startInit = System.nanoTime();
            graph.task("t0", DFTMT::computeDft, inReal, inImag, outReal, outImag, inputSize).streamOut(outReal, outImag);
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
                    graph.executeWithProfilerSequential(Policy.PERFORMANCE);
                    end = System.nanoTime();
                    break;
                case "end":
                    start = System.nanoTime();
                    graph.executeWithProfilerSequential(Policy.END_2_END);
                    end = System.nanoTime();
                    break;
                case "sequential":
                    start = System.nanoTime();
                    computeDft(inReal, inImag, outReal, outImag, inputSize);
                    end = System.nanoTime();
                    break;
                case "multi":
                    start = System.nanoTime();
                    computeDftThreads(inReal, inImag, outReal, outImag, maxSystemThreads, threads);
                    end = System.nanoTime();
                    break;
                default:
                    start = System.nanoTime();
                    graph.execute();
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
