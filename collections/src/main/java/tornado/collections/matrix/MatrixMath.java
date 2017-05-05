/*
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.collections.matrix;

import tornado.api.Parallel;
import tornado.collections.types.*;

import static java.lang.Math.abs;
import static tornado.collections.types.Float6.dot;
import static tornado.collections.types.MatrixFloat.scale;
import static tornado.collections.types.MatrixFloat.transpose;
import static tornado.collections.types.VectorFloat.dot;

public final class MatrixMath {

    private MatrixMath() {
    }

    /**
     * SGEMM - performs matrix-matrix multiplication C = alpha*op(A)*op(B) +
     * beta*C
     *
     * @param transA
     * @param transB
     * @param alpha
     * @param a
     * @param b
     * @param beta
     * @param c
     */
    public static final void sgemm(boolean transA, boolean transB, float alpha,
            MatrixFloat a, MatrixFloat b, float beta, MatrixFloat c) {
        if (transA) {
            transpose(a);
        }

        if (transB) {
            transpose(b);
        }

        scale(a, alpha);

        for (int row = 0; row < c.M(); row++) {
            for (int col = 0; col < c.N(); col++) {
                float sum = c.get(row, col) * beta;
                for (int k = 0; k < b.M(); k++) {
                    sum += a.get(row, k) * b.get(k, col);
                }
                c.set(row, col, sum);
            }
        }
    }

    /**
     * SGEMM - performs matrix-matrix multiplication C = A * B
     *
     * @param a
     * @param b
     * @param c
     */
    public static final void sgemm(MatrixFloat a, MatrixFloat b, MatrixFloat c) {
        sgemm(false, false, 1f, a, b, 0f, c);
    }

    public static final void sgemm(Matrix4x4Float a, Matrix4x4Float b, Matrix4x4Float c) {
        for (@Parallel int row = 0; row < c.M(); row++) {
            for (@Parallel int col = 0; col < c.N(); col++) {
                float sum = 0f;
                for (int k = 0; k < b.M(); k++) {
                    sum += a.get(row, k) * b.get(k, col);
                }
                c.set(row, col, sum);
            }
        }
    }

    public static final void dgemm(MatrixDouble a, MatrixDouble b, MatrixDouble c) {
        for (@Parallel int row = 0; row < c.M(); row++) {
            for (@Parallel int col = 0; col < c.N(); col++) {
                double sum = 0;
                for (int k = 0; k < b.M(); k++) {
                    sum += a.get(row, k) * b.get(k, col);
                }
                c.set(row, col, sum);
            }
        }
    }

    //SSYTRD in LAPACK, tred2 in EISPACK
    public static void tred2(double[][] V, double[] d, double[] e, int n) {

        //  This is derived from the Algol procedures tred2 by
        //  Bowdler, Martin, Reinsch, and Wilkinson, Handbook for
        //  Auto. Comp., Vol.ii-Linear Algebra, and the corresponding
        //  Fortran subroutine in EISPACK.
        for (int j = 0; j < n; j++) {
            d[j] = V[n - 1][j];
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
                    d[j] = V[i - 1][j];
                    V[i][j] = 0.0;
                    V[j][i] = 0.0;
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
                    V[j][i] = f;
                    g = e[j] + V[j][j] * f;
                    for (int k = j + 1; k <= i - 1; k++) {
                        g += V[k][j] * d[k];
                        e[k] += V[k][j] * f;
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
                        V[k][j] -= (f * e[k] + g * d[k]);
                    }
                    d[j] = V[i - 1][j];
                    V[i][j] = 0.0;
                }
            }
            d[i] = h;
        }

        // Accumulate transformations.
        for (int i = 0; i < n - 1; i++) {
            V[n - 1][i] = V[i][i];
            V[i][i] = 1.0;
            double h = d[i + 1];
            if (h != 0.0) {
                for (int k = 0; k <= i; k++) {
                    d[k] = V[k][i + 1] / h;
                }
                for (int j = 0; j <= i; j++) {
                    double g = 0.0;
                    for (int k = 0; k <= i; k++) {
                        g += V[k][i + 1] * V[k][j];
                    }
                    for (int k = 0; k <= i; k++) {
                        V[k][j] -= g * d[k];
                    }
                }
            }
            for (int k = 0; k <= i; k++) {
                V[k][i + 1] = 0.0;
            }
        }
        for (int j = 0; j < n; j++) {
            d[j] = V[n - 1][j];
            V[n - 1][j] = 0.0;
        }
        V[n - 1][n - 1] = 1.0;
        e[0] = 0.0;
    }

    /**
     * Matrix-vector multiplication
     *
     * @param y result
     * @param m matrix
     * @param x vector
     */
    public static void multiply(VectorFloat y, MatrixFloat m, VectorFloat x) {
        for (int i = 0; i < m.N(); i++) {
            y.set(i, dot(m.row(i), x));
        }
    }

    public static void multiply(Float6 y, MatrixFloat m, Float6 x) {
        //System.out.printf("y: %s\n",y.toString());
        //System.out.printf("M:\n %s\n",m.toString());
        //System.out.printf("x: %s\n",x.toString());
        final Float6 row = new Float6();
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                //System.out.printf("m[%d,%d]: %f\n",i,j,m.get(i,j));
                row.set(j, m.get(i, j));
            }
            //System.out.printf("y[%d] = dot (%s,  %s)\n",i,row.toString(),x.toString());
            y.set(i, dot(row, x));
        }
    }

//	/**
//     * SGEMV - perform one of the matrix-vector operations
//     * y = alpha*A*x + beta*y or y = alpha*A'*x + beta*y
//     * @param transpose
//     * @param alpha
//     * @param a
//     * @param x
//     * @param beta
//     * @param y
//     */
//    public final static void sgemv(
//            boolean transpose, float alpha,
//            MatrixFloat a, VectorFloat x,
//            float beta, VectorFloat y ){
//
//        if(transpose)
//           a.transpose();
//
//        if(alpha!=1f)
//        	a.scale(alpha);
//
//
//        for(int r = 0; r < a.M(); r++ ){
//            float sum = 0.0f;
//            for(int c = 0; c < a.N(); c++){
//                sum +=a.get(r, c) * x.get(c);
//            }
//            y.set(r, sum + (beta*y.get(r)));
//        }
//
//    }
//    public final static void sgemv(MatrixFloat a,VectorFloat x, VectorFloat y){
//       sgemv(false,1f,a,x,0f,y);
//    }
}
