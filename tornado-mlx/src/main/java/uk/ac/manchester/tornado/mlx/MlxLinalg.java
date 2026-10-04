/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.mlx;

import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

/**
 * MLX linear algebra (Tier 2) as TornadoVM library tasks: cross products and vector and matrix
 * norms.
 */
public final class MlxLinalg {

    private MlxLinalg() {
    }

    /** {@code out[i] = a[i] x b[i]} for {@code count} 3-vectors stored as {@code [count, 3]}. */
    @MlxOp("mlx_linalg_cross")
    public static LibraryTaskDescriptor cross(FloatArray a, FloatArray b, FloatArray out, int count) {
        return Mlx.task("linalg_cross", 2, a, b, out, count);
    }

    /**
     * {@code out[r]} = the {@code ord}-norm of row {@code r} of {@code x[rows, cols]}:
     * {@code (sum |x|^ord)^(1/ord)}; infinity and -infinity give the largest and smallest
     * magnitude, 0 the number of non-zeros.
     */
    @MlxOp("mlx_linalg_norm")
    public static LibraryTaskDescriptor norm(FloatArray x, FloatArray out, int rows, int cols, float ord) {
        return Mlx.task("linalg_norm", 1, x, out, rows, cols, ord);
    }

    /** {@code out[r]} = the Euclidean norm of row {@code r} of {@code x[rows, cols]}. */
    @MlxOp("mlx_linalg_norm_l2")
    public static LibraryTaskDescriptor l2Norm(FloatArray x, FloatArray out, int rows, int cols) {
        return Mlx.task("linalg_norm_l2", 1, x, out, rows, cols);
    }

    /** {@code out[b]} = the Frobenius norm of matrix {@code b} of {@code x[batch, rows, cols]}. */
    @MlxOp("mlx_linalg_norm_matrix")
    public static LibraryTaskDescriptor frobeniusNorm(FloatArray x, FloatArray out, int batch, int rows, int cols) {
        return Mlx.task("linalg_norm_matrix", 1, x, out, batch, rows, cols);
    }

    /** {@code out[i] = a[i] x b[i]} for {@code count} 3-vectors stored as {@code [count, 3]}. */
    @MlxOp("mlx_linalg_cross")
    public static LibraryTaskDescriptor cross(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int count) {
        return Mlx.task("linalg_cross", 2, a, b, out, count);
    }

    /**
     * {@code out[r]} = the {@code ord}-norm of row {@code r} of {@code x[rows, cols]}:
     * {@code (sum |x|^ord)^(1/ord)}; infinity and -infinity give the largest and smallest
     * magnitude, 0 the number of non-zeros.
     */
    @MlxOp("mlx_linalg_norm")
    public static LibraryTaskDescriptor norm(HalfFloatArray x, HalfFloatArray out, int rows, int cols, float ord) {
        return Mlx.task("linalg_norm", 1, x, out, rows, cols, ord);
    }

    /** {@code out[r]} = the Euclidean norm of row {@code r} of {@code x[rows, cols]}. */
    @MlxOp("mlx_linalg_norm_l2")
    public static LibraryTaskDescriptor l2Norm(HalfFloatArray x, HalfFloatArray out, int rows, int cols) {
        return Mlx.task("linalg_norm_l2", 1, x, out, rows, cols);
    }

    /** {@code out[b]} = the Frobenius norm of matrix {@code b} of {@code x[batch, rows, cols]}. */
    @MlxOp("mlx_linalg_norm_matrix")
    public static LibraryTaskDescriptor frobeniusNorm(HalfFloatArray x, HalfFloatArray out, int batch, int rows, int cols) {
        return Mlx.task("linalg_norm_matrix", 1, x, out, batch, rows, cols);
    }

    /** {@code out[i] = a[i] x b[i]} for {@code count} 3-vectors stored as {@code [count, 3]}. */
    @MlxOp("mlx_linalg_cross")
    public static LibraryTaskDescriptor cross(BFloat16Array a, BFloat16Array b, BFloat16Array out, int count) {
        return Mlx.task("linalg_cross", 2, a, b, out, count);
    }

    /**
     * {@code out[r]} = the {@code ord}-norm of row {@code r} of {@code x[rows, cols]}:
     * {@code (sum |x|^ord)^(1/ord)}; infinity and -infinity give the largest and smallest
     * magnitude, 0 the number of non-zeros.
     */
    @MlxOp("mlx_linalg_norm")
    public static LibraryTaskDescriptor norm(BFloat16Array x, BFloat16Array out, int rows, int cols, float ord) {
        return Mlx.task("linalg_norm", 1, x, out, rows, cols, ord);
    }

    /** {@code out[r]} = the Euclidean norm of row {@code r} of {@code x[rows, cols]}. */
    @MlxOp("mlx_linalg_norm_l2")
    public static LibraryTaskDescriptor l2Norm(BFloat16Array x, BFloat16Array out, int rows, int cols) {
        return Mlx.task("linalg_norm_l2", 1, x, out, rows, cols);
    }

    /** {@code out[b]} = the Frobenius norm of matrix {@code b} of {@code x[batch, rows, cols]}. */
    @MlxOp("mlx_linalg_norm_matrix")
    public static LibraryTaskDescriptor frobeniusNorm(BFloat16Array x, BFloat16Array out, int batch, int rows, int cols) {
        return Mlx.task("linalg_norm_matrix", 1, x, out, batch, rows, cols);
    }

}
