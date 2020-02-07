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
package uk.ac.manchester.tornado.examples.stencils;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class Jacobi1D {

    final static int PB_STEPS = 20;
    final static int PB_N = 1024;
    final static int ITERATIONS = 31;

    private static void run2DJacobiTornado() {

    }

    private static void run2DJacobi(float[] a, float[] b) {

        for (int t = 0; t < PB_STEPS; t++) {
            for (int i = 1; i < PB_N - 1; i++) {
                b[i] = (float) (0.3333 * (a[i - 1] + a[i] + a[i + 1]));
            }
            for (int j = 1; j < PB_N - 1; j++) {
                a[j] = b[j];
            }
        }
    }

    private static void kernelOne(float[] a, float[] b, int size) {
        for (@Parallel int i = 1; i < size - 1; i++) {
            b[i] = (float) (0.3333 * (a[i - 1] + a[i] + a[i + 1]));
        }
    }

    private static void kernelTwo(float[] a, float[] b, int size) {
        for (@Parallel int j = 1; j < size - 1; j++) {
            a[j] = b[j];
        }
    }

    private static float[] initArrayA(int size) {
        float[] a = new float[size];
        for (int i = 0; i < size; i++) {
            a[i] = (float) (4 * i + 10) / size;
        }
        return a;
    }

    private static float[] initArrayB(int size) {
        float[] b = new float[size];
        for (int i = 0; i < size; i++) {
            b[i] = (float) (7 * i + 11) / size;
        }
        return b;
    }

    public static void main(String[] args) {
        int size = PB_N;

        float[] a = initArrayA(size);
        float[] b = initArrayB(size);
        float[] aSeq = initArrayA(size);
        float[] bSeq = initArrayB(size);

        long start = 0;
        long end = 0;

        for (int i = 0; i < ITERATIONS; i++) {
            System.gc();
            start = System.nanoTime();
            run2DJacobi(aSeq, bSeq);
            end = System.nanoTime();
            System.out.println("\tSequential execution time of iteration is: " + (end - start) + " ns");
        }

        // @formatter:off
        final TaskSchedule graph = new TaskSchedule("s0")
                .task("t0", Jacobi1D::kernelOne, a,b,size)
                .task("t1", Jacobi1D::kernelTwo, a, b, size);
        // @formatter:on

        start = 0;
        end = 0;

        start = System.nanoTime();
        for (int t = 0; t < PB_STEPS; t++) {
            graph.execute();
        }
        end = System.nanoTime();
        System.out.println("\tTornado execution time of iteration is: " + (end - start) + " ns");

        graph.syncObject(a);
        // System.out.println("***" + Arrays.toString(a));
        // System.out.println("---" + Arrays.toString(aSeq));

    }

}
