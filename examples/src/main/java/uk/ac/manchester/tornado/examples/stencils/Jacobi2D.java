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

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;

/**
 * Jacobi2D stencil computation. This version has been adapted from the
 * PolyBench-ACC benchmark-suite available in:
 * https://github.com/cavazos-lab/PolyBench-ACC.
 */
public class Jacobi2D {

    final static int PB_STEPS = 20;
    final static int PB_N = 1024;
    final static int ITERATIONS = 31;

    private static float[] run2DJacobi(float[] a, float[] b, int steps, int size) {
        for (int t = 0; t < steps; t++) {
            for (int i = 1; i < size - 1; i++) {
                for (int j = 1; j < size - 1; j++) {
                    b[i * size + j] = 0.2f * (a[i * size + j] + a[i * size + (j + 1)] + a[(1 + i) * size + j] + a[(i - 1) * size + j]);
                }
            }
            for (int i = 1; i < size - 1; i++) {
                for (int j = 1; j < size - 1; j++) {
                    a[i * size + j] = b[i * size + j];
                }
            }
        }
        return a;
    }

    private static void kernelOne(float[] a, float[] b, int size) {
        for (@Parallel int i = 1; i < size - 1; i++) {
            for (@Parallel int j = 1; j < size - 1; j++) {
                b[i * size + j] = 0.2f * (a[i * size + j] + a[i * size + (j + 1)] + a[(1 + i) * size + j] + a[(i - 1) * size + j]);
            }
        }
    }

    private static void kernelTwo(float[] a, float[] b, int size) {
        for (@Parallel int i = 1; i < size - 1; i++) {
            for (@Parallel int j = 1; j < size - 1; j++) {
                a[i * size + j] = b[i * size + j];
            }
        }
    }

    private static float[] initArrayA(int size) {
        float[] a = new float[size * size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                a[i * size + j] = ((float) i * (j + 2) + 10) / size;
            }
        }
        return a;
    }

    private static float[] initArrayB(int size) {
        float[] b = new float[size * size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                b[i * size + j] = ((float) (i - 4) * (j - 1) + 11) / size;
            }
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
            aSeq = run2DJacobi(aSeq, bSeq, steps, size);
            end = System.nanoTime();
            se.append("Sequential execution time of iteration is: " + (end - start) + " ns \n");
        }

        // @formatter:off
        final TaskGraph graph = new TaskGraph("s0")
                .task("t0", Jacobi2D::kernelOne, a, b, size)
                .task("t1", Jacobi2D::kernelTwo, a, b, size)
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
        System.out.println("Verify : " + verify(a, aSeq, size));

    }

    private static boolean verify(float[] tornado, float[] serial, int size) {
        boolean verified = true;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (Math.abs(tornado[i * size + j]) - Math.abs(serial[i * size + j]) > 0.1f) {
                    System.out.println(tornado[i * size + j] + " : " + serial[i * size + j]);
                    verified = false;
                    break;
                }
            }
        }
        return verified;
    }
}
