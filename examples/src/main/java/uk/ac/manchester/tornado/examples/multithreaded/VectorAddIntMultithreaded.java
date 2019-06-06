/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.examples.multithreaded;

import java.util.Arrays;

public class VectorAddIntMultithreaded {

    private static void vectorAdd(int[] a, int[] b, int[] c) {
        for (int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    private static void vectorAddThreads(int[] array1, int[] array2, int[] result, int threads, Thread[] th) throws InterruptedException {
        int balk = array1.length / threads;
        for (int i = 0; i < threads; i++) {
            final int current = i;
            th[i] = new Thread(() -> {
                for (int k = current * balk; k < (current + 1) * balk; k++) {
                    result[k] = array1[k] + array2[k];
                }
            });
            th[i].start();
        }
        for (int i = 0; i < threads; i++) {
            th[i].join();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int size = Integer.parseInt(args[0]);
        boolean version = Boolean.parseBoolean(args[1]);

        int maxThreadCount = Runtime.getRuntime().availableProcessors();

        Thread[] th = new Thread[maxThreadCount];

        int[] a = new int[size];
        int[] b = new int[size];
        int[] c = new int[size];
        int[] result = new int[size];
        long startTime = 0;
        long endTime = 0;

        Arrays.fill(a, 10);
        Arrays.fill(b, 20);

        vectorAdd(a, b, c);

        for (int idx = 0; idx < 10; idx++) {
            // vectorAdd(a, b, result);

            startTime = System.nanoTime();
            vectorAddThreads(a, b, c, maxThreadCount, th);
            endTime = System.nanoTime();

            System.out.println("[Multi] Run " + idx + " --- Time in (ns) " + (endTime - startTime));

            startTime = System.nanoTime();
            vectorAdd(a, b, result);
            endTime = System.nanoTime();

            System.out.println("[Serial] Run " + idx + " --- Time in (ns) " + (endTime - startTime));
            // Check Result
            boolean wrongResult = false;
            for (int i = 0; i < c.length; i++) {
                if (c[i] != 30) {
                    wrongResult = true;
                    break;
                }
            }
            if (wrongResult) {
                System.out.println("Result is wrong");
            } else {
                System.out.println("Result is correct");
            }
        }
    }
}
