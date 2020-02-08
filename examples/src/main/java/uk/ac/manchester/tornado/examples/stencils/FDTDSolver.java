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

import uk.ac.manchester.tornado.api.annotations.Parallel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class FDTDSolver {
    final static int PB_STEPS = 20;
    final static int PB_N = 1024;
    final static int ITERATIONS = 31;

    // private static void kernelOne() {
    // for (@Parallel int i = 0; i < sizeX; i++) {
    // for (@Parallel int j = 0; j < sizeY; j++) {
    // if (i == 0) {
    //
    // } else {
    //
    // }
    // }
    // }
    // }
    //
    // private static void kernelTwo() {
    // for (@Parallel int i = 0; i < sizeX; i++) {
    // for (@Parallel int j = 0; j < sizeY; j++) {
    //
    // }
    // }
    // }
    //
    // private static void kernelThree() {
    // for (@Parallel int i = 0; i < sizeX; i++) {
    // for (@Parallel int j = 0; j < sizeY; j++) {
    //
    // }
    // }
    // }

    private static float[]  fdtd(int tmax, int nx, int ny, float[] fict, float[] ex, float[] ey, float[] hz) {
        for (int t = 0; t < tmax; t++) {
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

    private static LinkedList<float[]> initArrays(int tmax, int nx, int ny) {
        LinkedList<float[]> initList = new LinkedList<>();

        float[] fict = new float[tmax];
        float[] ex = new float[nx * ny];
        float[] ey = new float[nx * ny];
        float[] hz = new float[nx * ny];

        for (int i = 0; i < tmax; i++) {
            fict[i] = (float) i;
        }
        initList.add(fict);

        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                ex[i * nx + j] = ((float) i * (j + 1) + 1 / nx);
                ey[i * nx + j] = ((float) (i - 1) * (j + 2) + 2 / nx);
                hz[i * nx + j] = ((float) (i - 9) * (j + 4) + 3 / nx);
            }
        }
        initList.add(ex);
        initList.add(ey);
        initList.add(hz);
        return initList;
    }

    public static void main(String[] args) {
        int size,steps,iterations,input;

        size = (args.length == 1) ? Integer.parseInt(args[0]) : PB_N;
        steps = (args.length == 2) ? Integer.parseInt(args[1]) : PB_STEPS;
        iterations = (args.length == 3) ? Integer.parseInt(args[2]) : ITERATIONS;

        LinkedList<float[]> initializedArrays = initArrays(steps, size, size);

        float[] fict = initializedArrays.get(0);
        float[] ex = initializedArrays.get(1);
        float[] ey = initializedArrays.get(2);
        float[] hz = initializedArrays.get(3);

        long start = 0L;
        long end = 0L;

        start = System.nanoTime();
        hz = fdtd(steps,size,size,fict, ex,ey,hz);
        end = System.nanoTime();

        System.out.println(Arrays.toString(hz));
    }

}
