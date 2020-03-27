/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.examples.fpga;

import java.util.Random;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class SGEMMFPGA {

    public static void sgemm(final int A[], final int B[], final int C[], final int[] dims) {

        for (@Parallel int i = 0; i < dims[0]; i++) {
            for (@Parallel int j = 0; j < dims[0]; j++) {
                int sum = 0;
                for (int k = 0; k < dims[0]; k++) {
                    sum += A[(i * dims[0]) + k] * B[(k * dims[0]) + j];
                }
                C[(i * dims[0]) + j] = sum;
            }
        }

    }

    public static void main(String[] args) {
        int size = Integer.parseInt(args[0]);

        int m = size;
        int n = size;

        int[] a,b,c,result,dims;

        a = new int[m * n];
        b = new int[m * n];
        c = new int[m * n];
        result = new int[m * n];
        dims = new int[1];

        dims[0] = size;

        final Random random = new Random();

        for (int i = 0; i < m; i++) {
            a[i * (m + 1)] = 45;
        }

        for (int i = 0; i < m * n; i++) {
            b[i] = 1;
        }

        TaskSchedule t0 = new TaskSchedule("s0").task("t0", SGEMMFPGA::sgemm, a, b, c, dims).streamOut(c);

        t0.warmup();

        for (int y = 0; y < 10; y++) {

            t0.execute();

            t0.syncObject(c);

            System.out.println("Checking result");
            boolean wrongResult = false;
            long start = System.nanoTime();

            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    int sum = 0;
                    for (int k = 0; k < size; k++) {
                        sum += a[(i * size) + k] * b[(k * size) + j];
                    }
                    result[(i * size) + j] = sum;

                    if (result[(i * size) + j] != c[(i * size) + j]) {
                        wrongResult = true;
                        break;
                    }
                }
            }
            long end = System.nanoTime();
            System.out.println("Sequential execution time of iteration  is: " + (end - start) + " ns");

            if (!wrongResult) {
                System.out.println("Test success");
            } else {
                System.out.println("Result is wrong");
            }
        }
    }
}
