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

import java.util.LinkedList;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

/**
 * FDTD solver stencil computation. This version has been adapted from the
 * PolyBench-ACC benchmark-suite available in:
 * https://github.com/cavazos-lab/PolyBench-ACC.
 */
public class FDTDSolver {
    final static int PB_STEPS = 5;
    final static int PB_N = 256;
    final static int ITERATIONS = 1;

    public static void kernelOne(int nx, int ny, float[] fict, float[] ey, float[] hz, int step) {
        for (@Parallel int i = 0; i < nx; i++) {
            for (@Parallel int j = 0; j < ny; j++) {
                if (i == 0) {
                    ey[0 * nx + j] = fict[step];
                } else {
                    ey[i * nx + j] = (float) (ey[i * nx + j] - 0.5 * (hz[i * nx + j] - hz[(i - 1) * nx + j]));
                }
            }
        }
    }

    public static void kernelTwo(int nx, int ny, float[] ex, float[] hz) {
        for (@Parallel int i = 0; i < nx; i++) {
            for (@Parallel int j = 1; j < ny; j++) {
                ex[i * nx + j] = (float) (ex[i * nx + j] - 0.5 * (hz[i * nx + j] - hz[i * nx + (j - 1)]));
            }
        }
    }

    public static void kernelThree(int nx, int ny, float[] ex, float[] hz, float[] ey) {
        for (@Parallel int i = 0; i < nx - 1; i++) {
            for (@Parallel int j = 0; j < ny - 1; j++) {
                hz[i * nx + j] = (float) (hz[i * nx + j] - 0.7 * (ex[i * nx + (j + 1)] - ex[i * nx + j] + ey[(i + 1) * nx + j] - ey[i * nx + j]));
            }
        }
    }

    private static float[] fdtd(int maxSteps, int nx, int ny, float[] fict, float[] ex, float[] ey, float[] hz) {
        for (int t = 0; t < maxSteps; t++) {
            for (int j = 0; j < ny; j++) {
                ey[0 * nx + j] = fict[t];
            }

            for (int i = 1; i < nx; i++) {
                for (int j = 0; j < ny; j++) {
                    ey[i * nx + j] = (float) (ey[i * nx + j] - 0.5 * (hz[i * nx + j] - hz[(i - 1) * nx + j]));
                }
            }

            for (int i = 0; i < nx; i++) {
                for (int j = 1; j < ny; j++) {
                    ex[i * nx + j] = (float) (ex[i * nx + j] - 0.5 * (hz[i * nx + j] - hz[i * nx + (j - 1)]));
                }
            }

            for (int i = 0; i < nx - 1; i++) {
                for (int j = 0; j < ny - 1; j++) {
                    hz[i * nx + j] = (float) (hz[i * nx + j] - 0.7 * (ex[i * nx + (j + 1)] - ex[i * nx + j] + ey[(i + 1) * nx + j] - ey[i * nx + j]));
                }
            }
        }
        return hz;
    }

    private static LinkedList<float[]> initArrays(int maxSteps, int nx, int ny) {
        LinkedList<float[]> initList = new LinkedList<>();

        float[] fict = new float[maxSteps];
        float[] ex = new float[nx * ny];
        float[] ey = new float[nx * ny];
        float[] hz = new float[nx * ny];

        for (int i = 0; i < maxSteps; i++) {
            fict[i] = (float) i;
        }

        initList.add(fict);

        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                ex[i * nx + j] = ((float) (i * (j + 1) + 1) / nx);
                ey[i * nx + j] = ((float) ((i - 1) * (j + 2) + 2) / nx);
                hz[i * nx + j] = ((float) ((i - 9) * (j + 4) + 3) / nx);
            }
        }

        initList.add(ex);
        initList.add(ey);
        initList.add(hz);
        return initList;
    }

    public static void main(String[] args) {
        int size,steps,iterations;
        long start,end;

        size = PB_N;
        steps = PB_STEPS;
        iterations = ITERATIONS;

        if (args.length > 1) {
            size = Integer.parseInt(args[0]);
            steps = Integer.parseInt(args[1]);
            iterations = Integer.parseInt(args[2]);
        }

        LinkedList<float[]> initializedArrays = initArrays(steps, size, size);

        float[] fict = initializedArrays.get(0);
        float[] ex = initializedArrays.get(1);
        float[] ey = initializedArrays.get(2);
        float[] hz = initializedArrays.get(3);

        float[] fictSeq = fict;
        float[] exSeq = ex;
        float[] eySeq = ey;
        float[] hzSeq = hz;

        StringBuilder se = new StringBuilder();
        StringBuilder par = new StringBuilder();

        for (int i = 0; i < iterations; i++) {
            System.gc();
            start = System.nanoTime();
            hzSeq = fdtd(steps, size, size, fictSeq, exSeq, eySeq, hzSeq);
            end = System.nanoTime();
            se.append("Sequential execution time of iteration is: " + (end - start) + " ns \n");
        }

        for (int i = 0; i < iterations; i++) {
            System.gc();
            start = System.nanoTime();
            for (int step = 0; step < steps; step++) {
                //@formatter:off
                TaskSchedule graph = new TaskSchedule("s0")
                        .task("t0", FDTDSolver::kernelOne, size, size, fict, ey, hz, step)
                        .task("t1", FDTDSolver::kernelTwo, size, size, ex, hz)
                        .task("t2", FDTDSolver::kernelThree, size, size, ex, hz, ey);
                //@formatter:on
                graph.streamOut(hz);
                graph.execute();
                graph.clearProfiles();
            }
            end = System.nanoTime();
            par.append("Tornado execution time of iteration is: " + (end - start) + " ns \n");
        }

        System.out.println(se);
        System.out.println(par);
        System.out.println("Verify : " + verify(hz, hzSeq, size));
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
