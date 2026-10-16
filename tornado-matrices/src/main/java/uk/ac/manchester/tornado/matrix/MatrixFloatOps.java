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

import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;

public class MatrixFloatOps {

    /**
     * Inverts {@code m} in place: Gauss-Jordan elimination in double precision with partial
     * pivoting. A singular matrix is left unchanged.
     */
    public static void inverse(Matrix4x4Float m) {
        final int n = 4;
        double[][] a = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                a[i][j] = m.get(i, j);
            }
            a[i][n + i] = 1.0;
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) {
                    pivot = row;
                }
            }
            if (a[pivot][col] == 0.0 || Double.isNaN(a[pivot][col])) {
                return; // singular: m unchanged
            }
            double[] swap = a[col];
            a[col] = a[pivot];
            a[pivot] = swap;
            double scale = 1.0 / a[col][col];
            for (int j = 0; j < 2 * n; j++) {
                a[col][j] *= scale;
            }
            for (int row = 0; row < n; row++) {
                if (row != col && a[row][col] != 0.0) {
                    double factor = a[row][col];
                    for (int j = 0; j < 2 * n; j++) {
                        a[row][j] -= factor * a[col][j];
                    }
                }
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                m.set(i, j, (float) a[i][n + j]);
            }
        }
    }
}
