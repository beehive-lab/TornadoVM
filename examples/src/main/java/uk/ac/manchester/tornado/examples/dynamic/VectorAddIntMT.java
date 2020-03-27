/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

import java.util.Arrays;

import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class VectorAddIntMT {

    private static void vectorAdd(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    private static void vectorAddThreads(int[] array1, int[] array2, int[] result, int threads, Thread[] th) throws InterruptedException {
        int balk = array1.length / threads;
        for (int i = 0; i < threads; i++) {
            final int current = i;
            int lowBound = current * balk;
            int upperBound = (current + 1) * balk;
            if(current==threads-1) {
                upperBound = array1.length;
            }
            int finalUpperBound = upperBound;
            th[i] = new Thread(() -> {
                for (int k = lowBound; k < finalUpperBound; k++) {
                    result[k] = array1[k] + array2[k];
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

    public static boolean validate(int size, int[] seq, int[] par) {
        boolean validation = true;
        for (int i = 0; i < size; i++) {
            if (seq[i] != 30 || par[i] != 30) {
                validation = false;
                break;
            }
        }
        return validation;
    }

    public static void main(String[] args) throws InterruptedException {

        if (args.length < 3) {
            System.out.println("Usage: <size> <mode:performance|end|sequential|multi> <iterations>");
            System.exit(-1);
        }

        int size = Integer.parseInt(args[0]);
        String executionType = args[1];
        int iterations = Integer.parseInt(args[2]);

        int maxThreadCount = Runtime.getRuntime().availableProcessors();
        Thread[] th = new Thread[maxThreadCount];

        int[] a = new int[size];
        int[] b = new int[size];
        int[] c = new int[size];
        int[] result = new int[size];
        long start,end;

        Arrays.fill(a, 10);
        Arrays.fill(b, 20);

        TaskSchedule graph = new TaskSchedule("s0");
        if (executionType.equals("multi") || executionType.equals("sequential")) {
            ;
        } else {
            long startInit = System.nanoTime();
            graph.task("t0", VectorAddIntMT::vectorAdd, a, b, result).streamOut(result);
            long stopInit = System.nanoTime();
            System.out.println("Initialization time:  " + (stopInit - startInit) + " ns" + "\n");
        }

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
                    end = System.nanoTime();
                    break;
                case "multi":
                    start = System.nanoTime();
                    vectorAddThreads(a, b, result, maxThreadCount, th);
                    end = System.nanoTime();
                    break;
                default:
                    start = System.nanoTime();
                    graph.execute();
                    end = System.nanoTime();
            }
            System.out.println("Total time:  " + (end - start) + " ns" + " \n");
        }

        vectorAdd(a, b, c);
        if (validate(c.length, c, result)) {
            System.out.println("Result is correct");
        } else {
            System.out.println("Result is false");
        }
    }
}
