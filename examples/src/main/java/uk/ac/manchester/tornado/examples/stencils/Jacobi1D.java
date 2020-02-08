/*
 * Copyright (c) 2013-2020, APT Group, School of Computer Science,
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

    private static void run2DJacobi(float[] a, float[] b, int steps) {

        for (int t = 0; t < steps; t++) {
            for (int i = 1; i < a.length - 1; i++) {
                b[i] = (float) (0.3333 * (a[i - 1] + a[i] + a[i + 1]));
            }
            for (int j = 1; j < a.length - 1; j++) {
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
        int size,steps,iterations,input;

        size = (args.length == 1) ? Integer.parseInt(args[0]) : PB_N;
        steps = (args.length == 2) ? Integer.parseInt(args[1]) : PB_STEPS;
        iterations = (args.length == 3) ? Integer.parseInt(args[2]) : ITERATIONS;

        float[] a = initArrayA(size);
        float[] b = initArrayB(size);
        float[] aSeq = initArrayA(size);
        float[] bSeq = initArrayB(size);

        long start = 0;
        long end = 0;

        StringBuilder se = new StringBuilder();
        StringBuilder par = new StringBuilder();
        for (int i = 0; i < iterations; i++) {
            System.gc();
            start = System.nanoTime();
            run2DJacobi(aSeq, bSeq, steps);
            end = System.nanoTime();
            se.append("\tSequential execution time of iteration is: " + (end - start) + " ns \n");
        }

        // @formatter:off
        final TaskSchedule graph = new TaskSchedule("s0")
                .task("t0", Jacobi1D::kernelOne, a,b,size)
                .task("t1", Jacobi1D::kernelTwo, a, b, size);
        // @formatter:on

        start = 0;
        end = 0;

        for (int i = 0; i < iterations; i++) {
            start = System.nanoTime();
            for (int t = 0; t < steps; t++) {
                graph.execute();
            }
            end = System.nanoTime();
            par.append("\tTornado execution time of iteration is: " + (end - start) + " ns \n");
        }

        graph.syncObject(a);

        System.out.println(se);
        System.out.println(par);
        System.out.println("\tVerify : " + verify(a, aSeq));
        // System.out.println("---" + Arrays.toString(aSeq));

        // System.out.println("***" + Arrays.toString(a));
    }

    private static boolean verify(float[] tornado, float[] serial) {
        boolean verified = true;

        for (int i = 0; i < tornado.length; i++) {
            if (Math.abs(tornado[i]) - Math.abs(serial[i]) > 0.9f) {
                System.out.println(tornado[i] + " : " + serial[i]);
                verified = false;
                break;
            }
        }
        return verified;
    }
}
