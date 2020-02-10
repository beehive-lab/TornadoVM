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
package uk.ac.manchester.tornado.examples.stencils;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

/**
 * Jacobi1D stencil computation. This version has been adapted from
 * the PolyBench-ACC benchmark-suite available in:
 * https://github.com/cavazos-lab/PolyBench-ACC.
 */
public class Jacobi1D {

    final static int PB_STEPS = 20;
    final static int PB_N = 1024;
    final static int ITERATIONS = 31;

    private static float[] run2DJacobi(float[] a, float[] b, int steps) {
        for (int t = 0; t < steps; t++) {
            for (int i = 1; i < a.length - 1; i++) {
                b[i] = (float) (0.3333 * (a[i - 1] + a[i] + a[i + 1]));
            }
            for (int j = 1; j < a.length - 1; j++) {
                a[j] = b[j];
            }
        }
        return a;
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
        int size,steps,iterations;

        size = PB_N;
        steps = PB_STEPS;
        iterations = ITERATIONS;

        if (args.length > 1) {
            size = Integer.parseInt(args[0]);
            steps = Integer.parseInt(args[1]);
            iterations = Integer.parseInt(args[2]);
        }

        float[] a = initArrayA(size);
        float[] b = initArrayB(size);
        float[] aSeq = initArrayA(size);
        float[] bSeq = initArrayB(size);

        long start;
        long end;

        StringBuilder se = new StringBuilder();
        StringBuilder par = new StringBuilder();

        for (int i = 0; i < iterations; i++) {
            System.gc();
            start = System.nanoTime();
            aSeq = run2DJacobi(aSeq, bSeq, steps);
            end = System.nanoTime();
            se.append("Sequential execution time of iteration is: " + (end - start) + " ns \n");
        }

        // @formatter:off
        final TaskSchedule graph = new TaskSchedule("s0")
                .task("t0", Jacobi1D::kernelOne, a, b, size)
                .task("t1", Jacobi1D::kernelTwo, a, b, size)
                .streamOut(a);
        // @formatter:on

        for (int i = 0; i < iterations; i++) {
            start = System.nanoTime();
            for (int t = 0; t < steps; t++) {
                graph.execute();
            }
            end = System.nanoTime();
            par.append("Tornado execution time of iteration is: " + (end - start) + " ns \n");
        }

        System.out.println(se);
        System.out.println(par);
        System.out.println("Verify : " + verify(a, aSeq));
    }

    private static boolean verify(float[] tornado, float[] serial) {
        boolean verified = true;

        for (int i = 0; i < tornado.length; i++) {
            if (Math.abs(tornado[i]) - Math.abs(serial[i]) > 0.5f) {
                System.out.println(tornado[i] + " : " + serial[i]);
                verified = false;
                break;
            }
        }
        return verified;
    }
}
