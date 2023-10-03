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
package uk.ac.manchester.tornado.benchmarks;

import uk.ac.manchester.tornado.api.annotations.Parallel;

public class LinearAlgebraArrays {
    // CHECKSTYLE:OFF

    public static void saxpy(float alpha, float[] x, float[] y) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] += alpha * x[i];
        }
    }

    public static void sgemv(int M, int N, float[] A, float[] X, float[] Y) {
        for (@Parallel int i = 0; i < M; i++) {
            float y0 = 0f;
            for (int j = 0; j < N; j++) {
                y0 += A[j + (i * N)] * X[j];
            }
            Y[i] = y0;
        }
    }

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

    public static void dgemm(final int M, final int N, final int K, final double A[], final double B[], final double C[]) {
        for (@Parallel int i = 0; i < N; i++) {
            for (@Parallel int j = 0; j < N; j++) {
                double sum = 0.0;
                for (int k = 0; k < K; k++) {
                    sum += A[(i * N) + k] * B[(k * N) + j];
                }
                C[(i * N) + j] = sum;
            }
        }

    }

    public static void spmv(final float[] val, final int[] cols, final int[] rowDelimiters, final float[] vec, final int dim, final float[] out) {
        for (@Parallel int i = 0; i < dim; i++) {
            float t = 0.0f;
            for (int j = rowDelimiters[i]; j < rowDelimiters[i + 1]; j++) {
                final int col = cols[j];
                t += val[j] * vec[col];
            }
            out[i] = t;
        }
    }
    // CHECKSTYLE:ON
}
