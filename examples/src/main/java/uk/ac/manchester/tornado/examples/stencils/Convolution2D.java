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

import java.util.Random;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

/**
 * Convolution2D stencil computation. This version has been adapted from
 * the PolyBench-ACC benchmark-suite available in:
 * https://github.com/cavazos-lab/PolyBench-ACC.
 */
public class Convolution2D {
    final static int PB_N = 128;
    final static int ITERATIONS = 1;

    private static void run2DConvolutionTornado(int nx, int ny, float[] a, float[] b) {
        float c11,c12,c13,c21,c22,c23,c31,c32,c33;

        // @formatter:off
        c11 = +0.2f;  c21 = +0.5f;  c31 = -0.8f;
        c12 = -0.3f;  c22 = +0.6f;  c32 = -0.9f;
        c13 = +0.4f;  c23 = +0.7f;  c33 = +0.10f;
        // @formatter:off

        for (@Parallel int i = 1; i < nx - 1; i++) {
            for (@Parallel int j = 1; j < ny - 1; j++) {
                b[i * nx + j] = c11 * a[(i - 1) * nx + (j - 1)] + c21 * a[(i - 1) * nx + (j + 0)] + c31 * a[(i - 1) * nx + (j + 1)] + c12 * a[(i + 0) * nx + (j - 1)] + c22 * a[(i + 0) * nx + (j + 0)]
                        + c32 * a[(i + 0) * nx + (j + 1)] + c13 * a[(i + 1) * nx + (j - 1)] + c23 * a[(i + 1) * nx + (j + 0)] + c33 * a[(i + 1) * nx + (j + 1)];
            }
        }
    }

    private static float[] run2DConvolutionSequential(int nx, int ny, float[] a, float[] b) {
        float c11,c12,c13,c21,c22,c23,c31,c32,c33;

        // @formatter:off
        c11 = +0.2f;  c21 = +0.5f;  c31 = -0.8f;
        c12 = -0.3f;  c22 = +0.6f;  c32 = -0.9f;
        c13 = +0.4f;  c23 = +0.7f;  c33 = +0.10f;
        // @formatter:off
        
        for (int i = 1; i < nx - 1; i++) {
            for (int j = 1; j < ny - 1; j++) {
                b[i * nx + j] = c11 * a[(i - 1) * nx + (j - 1)] + c21 * a[(i - 1) * nx + (j + 0)] + c31 * a[(i - 1) * nx + (j + 1)] + c12 * a[(i + 0) * nx + (j - 1)] + c22 * a[(i + 0) * nx + (j + 0)]
                        + c32 * a[(i + 0) * nx + (j + 1)] + c13 * a[(i + 1) * nx + (j - 1)] + c23 * a[(i + 1) * nx + (j + 0)] + c33 * a[(i + 1) * nx + (j + 1)];
            }
        }
        return b;
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

    public static void main(String[] args) {
        int size,iterations;

        size = PB_N;
        iterations = ITERATIONS;

        if (args.length > 1) {
            size = Integer.parseInt(args[0]);
            iterations = Integer.parseInt(args[1]);
        }

        float[] a = initArrayA(size);
        float[] aSeq = initArrayA(size);

        float[] b = new float[size * size];
        float[] bSeq = new float[size * size];

        long start;
        long end;

        StringBuilder se = new StringBuilder();
        StringBuilder par = new StringBuilder();

        for (int i = 0; i < iterations; i++) {
            System.gc();
            start = System.nanoTime();
            bSeq  =run2DConvolutionSequential(size, size, aSeq, bSeq);
            end = System.nanoTime();
            se.append("Sequential execution time of iteration is: " + (end - start) + " ns \n");
        }

        // @formatter:off
        final TaskSchedule graph = new TaskSchedule("s0")
                .task("t0", Convolution2D::run2DConvolutionTornado, size, size,a,b)
                .streamOut(b);
        // @formatter:on

        for (int i = 0; i < iterations; i++) {
            start = System.nanoTime();
            graph.execute();
            end = System.nanoTime();
            par.append("Tornado execution time of iteration is: " + (end - start) + " ns \n");
        }

        System.out.println(se);
        System.out.println(par);
        System.out.println("Verify : " + verify(b, bSeq, size));
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
