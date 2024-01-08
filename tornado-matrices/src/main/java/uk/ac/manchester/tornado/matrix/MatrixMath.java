/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.matrix;

import static java.lang.Math.abs;
import static uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat.scale;
import static uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat.transpose;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DDouble;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;

public final class MatrixMath {

    private MatrixMath() {
    }

    /**
     * SGEMM - performs matrix-matrix multiplication C = alpha*op(A)*op(B) + beta*C.
     *
     * @param transA
     * @param transB
     * @param alpha
     * @param a
     * @param b
     * @param beta
     * @param c
     */
    public static void sgemm(boolean transA, boolean transB, float alpha, Matrix2DFloat a, Matrix2DFloat b, float beta, Matrix2DFloat c) {
        if (transA) {
            transpose(a);
        }

        if (transB) {
            transpose(b);
        }

        scale(a, alpha);

        for (int row = 0; row < c.getNumRows(); row++) {
            for (int col = 0; col < c.getNumColumns(); col++) {
                float sum = c.get(row, col) * beta;
                for (int k = 0; k < b.getNumRows(); k++) {
                    sum += a.get(row, k) * b.get(k, col);
                }
                c.set(row, col, sum);
            }
        }
    }

    /**
     * SGEMM - performs matrix-matrix multiplication C = A * B.
     *
     * @param a
     * @param b
     * @param c
     */
    public static void sgemm(Matrix2DFloat a, Matrix2DFloat b, Matrix2DFloat c) {
        sgemm(false, false, 1f, a, b, 0f, c);
    }

    public static void sgemm(Matrix4x4Float a, Matrix4x4Float b, Matrix4x4Float c) {
        for (@Parallel int row = 0; row < c.getNumRows(); row++) {
            for (@Parallel int col = 0; col < c.getNumColumns(); col++) {
                float sum = 0f;
                for (int k = 0; k < b.getNumRows(); k++) {
                    sum += a.get(row, k) * b.get(k, col);
                }
                c.set(row, col, sum);
            }
        }
    }

    public static void dgemm(Matrix2DDouble a, Matrix2DDouble b, Matrix2DDouble c) {
        for (@Parallel int row = 0; row < c.getNumRows(); row++) {
            for (@Parallel int col = 0; col < c.getNumColumns(); col++) {
                double sum = 0;
                for (int k = 0; k < b.getNumRows(); k++) {
                    sum += a.get(row, k) * b.get(k, col);
                }
                c.set(row, col, sum);
            }
        }
    }

    // SSYTRD in LAPACK, tred2 in EISPACK
    public static void tred2(double[][] v, double[] d, double[] e, int n) {

        // This is derived from the Algol procedures tred2 by
        // Bowdler, Martin, Reinsch, and Wilkinson, Handbook for
        // Auto. Comp., Vol.ii-Linear Algebra, and the corresponding
        // Fortran subroutine in EISPACK.
        for (int j = 0; j < n; j++) {
            d[j] = v[n - 1][j];
        }

        // Householder reduction to tridiagonal form.
        for (int i = n - 1; i > 0; i--) {

            // Scale to avoid under/overflow.
            double scale = 0.0;
            double h = 0.0;
            for (int k = 0; k < i; k++) {
                scale = scale + abs(d[k]);
            }
            if (scale == 0.0) {
                e[i] = d[i - 1];
                for (int j = 0; j < i; j++) {
                    d[j] = v[i - 1][j];
                    v[i][j] = 0.0;
                    v[j][i] = 0.0;
                }
            } else {

                // Generate Householder vector.
                for (int k = 0; k < i; k++) {
                    d[k] /= scale;
                    h += d[k] * d[k];
                }
                double f = d[i - 1];
                double g = Math.sqrt(h);
                if (f > 0) {
                    g = -g;
                }
                e[i] = scale * g;
                h = h - f * g;
                d[i - 1] = f - g;
                for (int j = 0; j < i; j++) {
                    e[j] = 0.0;
                }

                // Apply similarity transformation to remaining columns.
                for (int j = 0; j < i; j++) {
                    f = d[j];
                    v[j][i] = f;
                    g = e[j] + v[j][j] * f;
                    for (int k = j + 1; k <= i - 1; k++) {
                        g += v[k][j] * d[k];
                        e[k] += v[k][j] * f;
                    }
                    e[j] = g;
                }
                f = 0.0;
                for (int j = 0; j < i; j++) {
                    e[j] /= h;
                    f += e[j] * d[j];
                }
                double hh = f / (h + h);
                for (int j = 0; j < i; j++) {
                    e[j] -= hh * d[j];
                }
                for (int j = 0; j < i; j++) {
                    f = d[j];
                    g = e[j];
                    for (int k = j; k <= i - 1; k++) {
                        v[k][j] -= (f * e[k] + g * d[k]);
                    }
                    d[j] = v[i - 1][j];
                    v[i][j] = 0.0;
                }
            }
            d[i] = h;
        }

        // Accumulate transformations.
        for (int i = 0; i < n - 1; i++) {
            v[n - 1][i] = v[i][i];
            v[i][i] = 1.0;
            double h = d[i + 1];
            if (h != 0.0) {
                for (int k = 0; k <= i; k++) {
                    d[k] = v[k][i + 1] / h;
                }
                for (int j = 0; j <= i; j++) {
                    double g = 0.0;
                    for (int k = 0; k <= i; k++) {
                        g += v[k][i + 1] * v[k][j];
                    }
                    for (int k = 0; k <= i; k++) {
                        v[k][j] -= g * d[k];
                    }
                }
            }
            for (int k = 0; k <= i; k++) {
                v[k][i + 1] = 0.0;
            }
        }
        for (int j = 0; j < n; j++) {
            d[j] = v[n - 1][j];
            v[n - 1][j] = 0.0;
        }
        v[n - 1][n - 1] = 1.0;
        e[0] = 0.0;
    }

    /**
     * Matrix-vector multiplication.
     *
     * @param y
     *     result
     * @param m
     *     matrix
     * @param x
     *     vector
     */
    public static void multiply(VectorFloat y, Matrix2DFloat m, VectorFloat x) {
        for (int i = 0; i < m.getNumColumns(); i++) {
            y.set(i, VectorFloat.dot(m.row(i), x));
        }
    }

    public static FloatArray mult(FloatArray a, FloatArray b) {
        final FloatArray result = new FloatArray(6);
        for (int i = 0; i < a.getSize(); i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static float dot(FloatArray a, FloatArray b) {
        float result = 0f;
        final FloatArray m = mult(a, b);
        for (int i = 0; i < a.getSize(); i++) {
            result += m.get(i);
        }
        return result;
    }

    public static void multiply(FloatArray y, Matrix2DFloat m, FloatArray x) {
        final FloatArray row = new FloatArray(6);
        for (int i = 0; i < row.getSize(); i++) {
            for (int j = 0; j < row.getSize(); j++) {
                row.set(j, m.get(i, j));
            }
            y.set(i, dot(row, x));
        }
    }
}
