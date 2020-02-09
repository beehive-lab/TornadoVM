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

import java.util.LinkedList;

public class ABISolver {

    final static int PB_STEPS = 20;
    final static int PB_N = 1024;
    final static int ITERATIONS = 31;

    private static void runABISequential(float[] x, float[] a, float[] b, int steps, int size) {
        for (int t = 0; t < steps; t++) {
            for (int i1 = 0; i1 < size; i1++) {
                for (int i2 = 1; i2 < size; i2++) {
                    x[i1 * size + i2] = x[i1 * size + i2] - x[i1 * size + (i2 - 1)] * b[i1 * size + i2] / b[i1 * (i2 - 1)];
                    b[i1 * size + i2] = b[i1 * size + i2] - a[i1 * size + i2] * a[i1 * size + i2] / b[i1 * (i2 - 1)];
                }
            }

            for (int i1 = 0; i1 < size; i1++) {
                x[i1 * size + (size - 1)] = x[i1 * size + (size - 1)] / b[i1 * size + (size - 1)];
            }

            for (int i1 = 0; i1 < size; i1++) {
                for (int i2 = 0; i2 < size - 2; i2++) {
                    x[i1 * size + (size - i2 - 2)] = (x[i1 * size + (size - 2 - i2)] - x[i1 * size + (size - 2 - i2 - 1)] * a[i1 * size + (size - i2 - 3)]) / b[i1 * size + (size - 3 - i2)];
                }
            }

            for (int i1 = 1; i1 < size; i1++) {
                for (int i2 = 0; i2 < size; i2++) {
                    x[i1 * size + i2] = x[i1 * size + i2] - x[(i1 - 1) * size + i2] * a[i1 * size + i2] / b[(i1 - 1) * size + i2];
                    b[i1 * size + i2] = b[i1 * size + i2] - a[i1 * size + i2] * a[i1 * size + i2] / b[(i1 - 1) * size + i2];
                }
            }

            for (int i2 = 0; i2 < size; i2++) {
                x[(size - 1) * size + i2] = x[(size - 1) * size + i2] / b[(size - 1) * size + i2];
            }

            for (int i1 = 0; i1 < size - 2; i1++) {
                for (int i2 = 0; i2 < size; i2++) {
                    x[(size - 2 - i1) * size + i2] = (x[(size - 2 - i1) * size + i2] - x[(size - i1 - 3) * size + i2] * a[(size - 3 - i1) * size + i2]) / b[(size - 2 - i1) * size + i2];
                }
            }
        }

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

    private static void kernelThree(float[] a, float[] b, int size) {
        for (@Parallel int i = 1; i < size - 1; i++) {
            for (@Parallel int j = 1; j < size - 1; j++) {
                b[i * size + j] = 0.2f * (a[i * size + j] + a[i * size + (j + 1)] + a[(1 + i) * size + j] + a[(i - 1) * size + j]);
            }
        }
    }

    private static void kernelFour(float[] a, float[] b, int size) {
        for (@Parallel int i = 1; i < size - 1; i++) {
            for (@Parallel int j = 1; j < size - 1; j++) {
                a[i * size + j] = b[i * size + j];
            }
        }
    }

    private static void kernelFive(float[] a, float[] b, int size) {
        for (@Parallel int i = 1; i < size - 1; i++) {
            for (@Parallel int j = 1; j < size - 1; j++) {
                b[i * size + j] = 0.2f * (a[i * size + j] + a[i * size + (j + 1)] + a[(1 + i) * size + j] + a[(i - 1) * size + j]);
            }
        }
    }

    private static void kernelSix(float[] a, float[] b, int size) {
        for (@Parallel int i = 1; i < size - 1; i++) {
            for (@Parallel int j = 1; j < size - 1; j++) {
                a[i * size + j] = b[i * size + j];
            }
        }
    }

    private static LinkedList<float[]> initArrays(int nx, int ny) {
        LinkedList<float[]> initList = new LinkedList<>();

        float[] x = new float[nx * ny];
        float[] a = new float[nx * ny];
        float[] b = new float[nx * ny];

        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                x[i * nx + j] = (((float) i * (j + 1) + 1) / nx);
                a[i * nx + j] = (((float) (i - 1) * (j + 4) + 2) / nx);
                b[i * nx + j] = ((float) ((i + 3) * (j + 7) + 3) / nx);
            }
        }
        initList.add(x);
        initList.add(a);
        initList.add(b);
        return initList;
    }

    public static void main(String[] args) {
        int size,iterations,steps;

        size = PB_N;
        steps = PB_STEPS;
        iterations = ITERATIONS;

        if (args.length > 1) {
            size = Integer.parseInt(args[0]);
            steps = Integer.parseInt(args[1]);
            iterations = Integer.parseInt(args[2]);
        }

        LinkedList<float[]> initializedArrays = initArrays(size, size);

        float[] x = initializedArrays.get(0);
        float[] a = initializedArrays.get(1);
        float[] b = initializedArrays.get(2);

        long start = 0;
        long end = 0;

        StringBuilder se = new StringBuilder();
        StringBuilder par = new StringBuilder();
        for (int i = 0; i < iterations; i++) {
            System.gc();
            start = System.nanoTime();
            runABISequential(x, a, b, steps, size);
            end = System.nanoTime();
            se.append("\tSequential execution time of iteration is: " + (end - start) + " ns \n");
        }

        // @formatter:off
//        final TaskSchedule graph = new TaskSchedule("s0")
//                .task("t0", ADISolver::kernelOne, a,b,size)
//                .task("t1", ADISolver::kernelTwo, a, b, size);
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
        // System.out.println(par);
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
