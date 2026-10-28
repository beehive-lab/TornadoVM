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
package uk.ac.manchester.tornado.cufft;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Factory methods for NVIDIA cuFFT library tasks. Complex data is interleaved
 * (re0, im0, re1, im1, ...) in a {@link FloatArray} of {@code 2 * n * batch}
 * elements. FFT plans are created once per (n, batch) shape and cached in the
 * per-(device, execution plan) context.
 *
 * <pre>
 * taskGraph.libraryTask("fft", CuFft::cufftForwardC2C, input, output, n, 1);
 * </pre>
 */
public final class CuFft {

    public static final String LIBRARY_NAME = "nvidia/cufft";

    private CuFft() {
    }

    private static Access[] inOut() {
        return new Access[] { Access.READ_ONLY, Access.WRITE_ONLY, Access.READ_ONLY, Access.READ_ONLY };
    }

    /**
     * 1D single-precision complex-to-complex forward FFT
     * (X[k] = sum x[t] * e^(-2*pi*i*t*k/n)) of {@code batch} transforms of
     * length {@code n}, stored contiguously.
     *
     * @param input
     *     Interleaved complex input, {@code 2 * n * batch} floats.
     * @param output
     *     Interleaved complex output, {@code 2 * n * batch} floats.
     */
    public static LibraryTaskDescriptor cufftForwardC2C(FloatArray input, FloatArray output, int n, int batch) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cufftForwardC2C") //
                .withParameters(new Object[] { input, output, n, batch }) //
                .withAccess(inOut());
    }

    /**
     * 1D single-precision complex-to-complex inverse FFT. Following cuFFT
     * semantics, the result is unnormalized: inverse(forward(x)) = n * x.
     */
    public static LibraryTaskDescriptor cufftInverseC2C(FloatArray input, FloatArray output, int n, int batch) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cufftInverseC2C") //
                .withParameters(new Object[] { input, output, n, batch }) //
                .withAccess(inOut());
    }

    /**
     * 1D single-precision real-to-complex forward FFT. The input holds
     * {@code n * batch} reals; the output holds {@code (n/2 + 1) * batch}
     * interleaved complex values (the non-redundant half of the Hermitian
     * spectrum), i.e. {@code 2 * (n/2 + 1) * batch} floats.
     */
    public static LibraryTaskDescriptor cufftForwardR2C(FloatArray input, FloatArray output, int n, int batch) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cufftForwardR2C") //
                .withParameters(new Object[] { input, output, n, batch }) //
                .withAccess(inOut());
    }

    /**
     * 1D single-precision complex-to-real inverse FFT (always inverse; input
     * must be Hermitian, e.g. produced by {@link #cufftForwardR2C}). The input
     * holds {@code 2 * (n/2 + 1) * batch} floats; the output {@code n * batch}
     * reals. Unnormalized: c2r(r2c(x)) = n * x.
     */
    public static LibraryTaskDescriptor cufftInverseC2R(FloatArray input, FloatArray output, int n, int batch) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cufftInverseC2R") //
                .withParameters(new Object[] { input, output, n, batch }) //
                .withAccess(inOut());
    }

    /**
     * 1D double-precision complex-to-complex forward FFT (interleaved complex
     * in a {@link DoubleArray} of {@code 2 * n * batch} elements).
     */
    public static LibraryTaskDescriptor cufftForwardZ2Z(DoubleArray input, DoubleArray output, int n, int batch) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cufftForwardZ2Z") //
                .withParameters(new Object[] { input, output, n, batch }) //
                .withAccess(inOut());
    }

    /**
     * 1D double-precision complex-to-complex inverse FFT (unnormalized).
     */
    public static LibraryTaskDescriptor cufftInverseZ2Z(DoubleArray input, DoubleArray output, int n, int batch) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cufftInverseZ2Z") //
                .withParameters(new Object[] { input, output, n, batch }) //
                .withAccess(inOut());
    }

    /**
     * 2D single-precision complex-to-complex forward FFT of an {@code nx x ny}
     * grid (row-major, interleaved complex, {@code 2 * nx * ny} floats).
     */
    public static LibraryTaskDescriptor cufftForward2dC2C(FloatArray input, FloatArray output, int nx, int ny) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cufftForward2dC2C") //
                .withParameters(new Object[] { input, output, nx, ny }) //
                .withAccess(inOut());
    }

    /**
     * 2D single-precision complex-to-complex inverse FFT (unnormalized:
     * inverse(forward(x)) = nx * ny * x).
     */
    public static LibraryTaskDescriptor cufftInverse2dC2C(FloatArray input, FloatArray output, int nx, int ny) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cufftInverse2dC2C") //
                .withParameters(new Object[] { input, output, nx, ny }) //
                .withAccess(inOut());
    }
}
