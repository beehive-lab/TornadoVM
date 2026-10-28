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
package uk.ac.manchester.tornado.clblast;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Factory methods for CLBlast library tasks on the OpenCL backend.
 * Matrix layout and transpose values are defined in
 * {@link uk.ac.manchester.tornado.clblast.enums.CLBlastLayout} and
 * {@link uk.ac.manchester.tornado.clblast.enums.CLBlastTranspose}.
 * Leading dimensions and vector increments are measured in elements; increments
 * must be positive. Native array headers are skipped automatically.
 *
 * <p>Pass the returned descriptor to {@code TaskGraph.libraryTask}, or use a
 * method reference such as {@code CLBlast::sgemm}. The native CLBlast shared
 * library is required only when executing a CLBlast task.</p>
 */
public final class CLBlast {

    public static final String LIBRARY_NAME = "opencl/clblast";

    private CLBlast() {
    }

    private static LibraryTaskDescriptor descriptor(String function, Object[] args, int output, boolean readsOutput) {
        Access[] access = new Access[args.length];
        Arrays.fill(access, Access.READ_ONLY);
        access[output] = readsOutput ? Access.READ_WRITE : Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction(function) //
                .withParameters(args) //
                .withAccess(access);
    }

    /** Computes {@code C = alpha * op(A) * op(B) + beta * C} in single precision. */
    public static LibraryTaskDescriptor sgemm(int layout, int transA, int transB, int m, int n, int k,
            float alpha, FloatArray a, int lda, FloatArray b, int ldb, float beta, FloatArray c, int ldc) {
        return descriptor("CLBlastSgemm",
                new Object[] { layout, transA, transB, m, n, k, alpha, a, lda, b, ldb, beta, c, ldc }, 12, beta != 0);
    }

    /** Computes {@code y = alpha * op(A) * x + beta * y} in single precision. */
    public static LibraryTaskDescriptor sgemv(int layout, int transpose, int m, int n, float alpha,
            FloatArray a, int lda, FloatArray x, int incx, float beta, FloatArray y, int incy) {
        return descriptor("CLBlastSgemv",
                new Object[] { layout, transpose, m, n, alpha, a, lda, x, incx, beta, y, incy }, 10, beta != 0);
    }

    /** Computes {@code y = alpha * x + y}; y is read and written. */
    public static LibraryTaskDescriptor saxpy(int n, float alpha, FloatArray x, int incx, FloatArray y, int incy) {
        return descriptor("CLBlastSaxpy", new Object[] { n, alpha, x, incx, y, incy }, 4, true);
    }

    /** Writes {@code x . y} into {@code result[0]}, which stays on the device. */
    public static LibraryTaskDescriptor sdot(int n, FloatArray x, int incx, FloatArray y, int incy, FloatArray result) {
        return descriptor("CLBlastSdot", new Object[] { n, x, incx, y, incy, result }, 5, false);
    }

    /** Computes {@code C = alpha * op(A) * op(B) + beta * C} in double precision. */
    public static LibraryTaskDescriptor dgemm(int layout, int transA, int transB, int m, int n, int k,
            double alpha, DoubleArray a, int lda, DoubleArray b, int ldb, double beta, DoubleArray c, int ldc) {
        return descriptor("CLBlastDgemm",
                new Object[] { layout, transA, transB, m, n, k, alpha, a, lda, b, ldb, beta, c, ldc }, 12, beta != 0);
    }

    /** Computes {@code y = alpha * op(A) * x + beta * y} in double precision. */
    public static LibraryTaskDescriptor dgemv(int layout, int transpose, int m, int n, double alpha,
            DoubleArray a, int lda, DoubleArray x, int incx, double beta, DoubleArray y, int incy) {
        return descriptor("CLBlastDgemv",
                new Object[] { layout, transpose, m, n, alpha, a, lda, x, incx, beta, y, incy }, 10, beta != 0);
    }

    /** Computes {@code y = alpha * x + y}; y is read and written. */
    public static LibraryTaskDescriptor daxpy(int n, double alpha, DoubleArray x, int incx, DoubleArray y, int incy) {
        return descriptor("CLBlastDaxpy", new Object[] { n, alpha, x, incx, y, incy }, 4, true);
    }

    /** Writes {@code x . y} into {@code result[0]}, which stays on the device. */
    public static LibraryTaskDescriptor ddot(int n, DoubleArray x, int incx, DoubleArray y, int incy, DoubleArray result) {
        return descriptor("CLBlastDdot", new Object[] { n, x, incx, y, incy, result }, 5, false);
    }
}
