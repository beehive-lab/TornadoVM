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

package uk.ac.manchester.tornado.examples.ooo;

import java.util.Random;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class OOOTest {

    public static void sgemm(final int M, final int N, final int K, final float A[], final float B[], final float C[]) {

        for (@Parallel int i = 0; i < N; i++) {
            for (@Parallel int j = 0; j < N; j++) {
                float sum = 0.0f;
                for (int k = 0; k < K; k++) {
                    sum += A[(i * N) + k] * B[(k * N) + j];
                }
                C[(i * N) + j] = sum;
            }
        }

    }

    public static final void main(String[] args) {

        int[] sizes = new int[] { 4096, 8, 512, 64, 2048, 128, 8, 1024 };
        int numArrays = Integer.parseInt(args[0]);
        float[][] As = new float[numArrays][];
        float[][] Bs = new float[numArrays][];
        float[][] Cs = new float[numArrays][];

        System.out.printf("using %d maxtricies\n", numArrays);

        final Random random = new Random();
        TaskSchedule graph = new TaskSchedule("example");
        for (int ii = 0; ii < numArrays; ii++) {
            int n = sizes[ii % sizes.length];
            float[] a = new float[n * n];
            float[] b = new float[n * n];
            float[] c = new float[n * n];

            for (int i = 0; i < n; i++) {
                a[i * (n + 1)] = 1;
            }

            for (int i = 0; i < n * n; i++) {
                b[i] = random.nextFloat();
            }

            graph.task("t" + ii, OOOTest::sgemm, n, n, n, a, b, c);

            As[ii] = a;
            Bs[ii] = b;
            Cs[ii] = c;

        }

        graph.warmup();
        graph.execute();
        graph.clearProfiles();

        final long t0 = System.nanoTime();
        graph.execute();
        final long t1 = System.nanoTime();

        graph.dumpEvents();

        System.out.printf("time=%.9f s\n", (t1 - t0) * 1e-9);
    }

}
