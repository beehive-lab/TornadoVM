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

import java.util.Random;

public class Convolution2D {

    final static int PB_STEPS = 20;
    final static int PB_N = 1024;
    final static int ITERATIONS = 31;

    private static void run2DJacobiTornado() {

    }

    private static void run2DJacobi(float[] a, float[] b, int steps, int size) {
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
    }

    private static void run2DconvolutionSequential(int nx, int ny, float[] a, float[] b) {
        float c11,c12,c13,c21,c22,c23,c31,c32,c33;
        c11 = +0.2f;
        c21 = +0.5f;
        c31 = -0.8f;
        c12 = -0.3f;
        c22 = +0.6f;
        c32 = -0.9f;
        c13 = +0.4f;
        c23 = +0.7f;
        c33 = +0.10f;

        for (int i = 1; i < nx - 1; i++) // 0
        {
            for (int j = 1; j < ny - 1; j++) // 1
            {
                b[i * nx + j] = c11 * a[(i - 1) * nx + (j - 1)] + c21 * a[(i - 1) * nx + (j + 0)] + c31 * a[(i - 1) * nx + (j + 1)] + c12 * a[(i + 0) * nx + (j - 1)] + c22 * a[(i + 0) * nx + (j + 0)]
                        + c32 * a[(i + 0) * nx + (j + 1)] + c13 * a[(i + 1) * nx + (j - 1)] + c23 * a[(i + 1) * nx + (j + 0)] + c33 * a[(i + 1) * nx + (j + 1)];
            }
        }

    }

    private static void convolution2Dcompute(float[] a, float[] b, int size) {
        for (@Parallel int i = 1; i < size - 1; i++) {
            for (@Parallel int j = 1; j < size - 1; j++) {
                b[i * size + j] = 0.2f * (a[i * size + j] + a[i * size + (j + 1)] + a[(1 + i) * size + j] + a[(i - 1) * size + j]);
            }
        }
    }

    private static float[] initArrayA(int size) {
        float[] a = new float[size * size];
        Random random = new Random();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                a[i * size + j] = (random.nextFloat()) / Float.MAX_VALUE;
            }
        }
        return a;
    }

    // private static float[] initArrayB(int size) {
    // float[] b = new float[size * size];
    // for (int i = 0; i < size; i++) {
    // for (int j = 0; j < size; j++) {
    // b[i * size + j] = ((float) (i - 4) * (j - 1) + 11) / size;
    // }
    // }
    // return b;
    // }

    public static void main(String[] args) {
        int size,steps,iterations;

        size = (args.length == 1) ? Integer.parseInt(args[0]) : PB_N;
        steps = (args.length == 2) ? Integer.parseInt(args[1]) : PB_STEPS;
        iterations = (args.length == 3) ? Integer.parseInt(args[2]) : ITERATIONS;

        float[] a = initArrayA(size);
        float[] aSeq = initArrayA(size);

        float[] b = new float[size * size];
        float[] bSeq = new float[size * size];

        long start = 0;
        long end = 0;

        StringBuilder se = new StringBuilder();
        StringBuilder par = new StringBuilder();
        for (int i = 0; i < iterations; i++) {
            System.gc();
            start = System.nanoTime();
            run2DconvolutionSequential(size, size, aSeq, bSeq);
            end = System.nanoTime();
            se.append("\tSequential execution time of iteration is: " + (end - start) + " ns \n");
        }

//        // @formatter:off
//        final TaskSchedule graph = new TaskSchedule("s0")
//                .task("t0", Convolution2D::kernelOne, a,b,size)
//                .task("t1", Convolution2D::kernelTwo, a, b, size);
//        // @formatter:on

        start = 0;
        end = 0;

        // for (int i = 0; i < iterations; i++) {
        // start = System.nanoTime();
        // for (int t = 0; t < steps; t++) {
        // graph.execute();
        // }
        // end = System.nanoTime();
        // par.append("\tTornado execution time of iteration is: " + (end - start) + "
        // ns \n");
        // }
        //
        // graph.syncObject(a);

        System.out.println(se);
        System.out.println(par);
        // System.out.println("\tVerify : " + verify(a, aSeq, size));
        // System.out.println("---" + Arrays.toString(aSeq));

        // System.out.println("***" + Arrays.toString(a));
    }

    private static boolean verify(float[] tornado, float[] serial, int size) {
        boolean verified = true;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (Math.abs(tornado[i]) - Math.abs(serial[i]) > 0.5f) {
                    System.out.println(tornado[i * size + j] + " : " + serial[i * size + j]);
                    verified = false;
                    break;
                }
            }
        }
        return verified;
    }
}
