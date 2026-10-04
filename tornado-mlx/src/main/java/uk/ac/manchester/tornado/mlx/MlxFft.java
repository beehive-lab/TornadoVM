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
 * MLX FFTs as TornadoVM library tasks. Complex arrays are float arrays of interleaved
 * (real, imaginary) pairs, so a complex {@code [rows, len]} array holds {@code 2 * rows * len}
 * floats. 2D and 3D transforms run over the last two or three axes; {@code norm} is
 * {@link #BACKWARD}, {@link #ORTHO} or {@link #FORWARD}.
 */
public final class MlxFft {

    /** Normalisation: the inverse transform is scaled by 1/n (the default). */
    public static final int BACKWARD = 0;
    /** Normalisation: both directions are scaled by 1/sqrt(n). */
    public static final int ORTHO = 1;
    /** Normalisation: the forward transform is scaled by 1/n. */
    public static final int FORWARD = 2;

    private MlxFft() {
    }

    /** FFT of each row of complex {@code x[rows, len]}, zero-padded or cropped to {@code n}: complex {@code out[rows, n]}. */
    @MlxOp("mlx_fft_fft")
    public static LibraryTaskDescriptor fft(FloatArray x, FloatArray out, int rows, int len, int n, int norm) {
        return Mlx.task("fft_fft", 1, x, out, rows, len, n, norm);
    }

    /** Inverse FFT of each row of complex {@code x[rows, len]}: complex {@code out[rows, n]}. */
    @MlxOp("mlx_fft_ifft")
    public static LibraryTaskDescriptor ifft(FloatArray x, FloatArray out, int rows, int len, int n, int norm) {
        return Mlx.task("fft_ifft", 1, x, out, rows, len, n, norm);
    }

    /** FFT of each row of real {@code x[rows, len]} (as length {@code n}): the non-negative frequencies, complex {@code out[rows, n / 2 + 1]}. */
    @MlxOp("mlx_fft_rfft")
    public static LibraryTaskDescriptor rfft(FloatArray x, FloatArray out, int rows, int len, int n, int norm) {
        return Mlx.task("fft_rfft", 1, x, out, rows, len, n, norm);
    }

    /** Inverse of {@link #rfft}: complex {@code x[rows, len]} (non-negative frequencies) to real {@code out[rows, n]}. */
    @MlxOp("mlx_fft_irfft")
    public static LibraryTaskDescriptor irfft(FloatArray x, FloatArray out, int rows, int len, int n, int norm) {
        return Mlx.task("fft_irfft", 1, x, out, rows, len, n, norm);
    }

    /** 2D FFT of each complex {@code x[b]} of {@code [batch, h, w]}. */
    @MlxOp("mlx_fft_fft2")
    public static LibraryTaskDescriptor fft2(FloatArray x, FloatArray out, int batch, int h, int w, int norm) {
        return Mlx.task("fft_fft2", 1, x, out, batch, h, w, norm);
    }

    /** 2D inverse FFT of each complex {@code x[b]} of {@code [batch, h, w]}. */
    @MlxOp("mlx_fft_ifft2")
    public static LibraryTaskDescriptor ifft2(FloatArray x, FloatArray out, int batch, int h, int w, int norm) {
        return Mlx.task("fft_ifft2", 1, x, out, batch, h, w, norm);
    }

    /** 2D FFT of each real {@code x[b]} of {@code [batch, h, w]}: complex {@code out[batch, h, w / 2 + 1]}. */
    @MlxOp("mlx_fft_rfft2")
    public static LibraryTaskDescriptor rfft2(FloatArray x, FloatArray out, int batch, int h, int w, int norm) {
        return Mlx.task("fft_rfft2", 1, x, out, batch, h, w, norm);
    }

    /** Inverse of {@link #rfft2}: complex {@code x[batch, h, w / 2 + 1]} to real {@code out[batch, h, w]}. */
    @MlxOp("mlx_fft_irfft2")
    public static LibraryTaskDescriptor irfft2(FloatArray x, FloatArray out, int batch, int h, int w, int norm) {
        return Mlx.task("fft_irfft2", 1, x, out, batch, h, w, norm);
    }

    /** 3D FFT of each complex {@code x[b]} of {@code [batch, d, h, w]}. */
    @MlxOp("mlx_fft_fftn")
    public static LibraryTaskDescriptor fftn(FloatArray x, FloatArray out, int batch, int d, int h, int w, int norm) {
        return Mlx.task("fft_fftn", 1, x, out, batch, d, h, w, norm);
    }

    /** 3D inverse FFT of each complex {@code x[b]} of {@code [batch, d, h, w]}. */
    @MlxOp("mlx_fft_ifftn")
    public static LibraryTaskDescriptor ifftn(FloatArray x, FloatArray out, int batch, int d, int h, int w, int norm) {
        return Mlx.task("fft_ifftn", 1, x, out, batch, d, h, w, norm);
    }

    /** 3D FFT of each real {@code x[b]} of {@code [batch, d, h, w]}: complex {@code out[batch, d, h, w / 2 + 1]}. */
    @MlxOp("mlx_fft_rfftn")
    public static LibraryTaskDescriptor rfftn(FloatArray x, FloatArray out, int batch, int d, int h, int w, int norm) {
        return Mlx.task("fft_rfftn", 1, x, out, batch, d, h, w, norm);
    }

    /** Inverse of {@link #rfftn}: complex {@code x[batch, d, h, w / 2 + 1]} to real {@code out[batch, d, h, w]}. */
    @MlxOp("mlx_fft_irfftn")
    public static LibraryTaskDescriptor irfftn(FloatArray x, FloatArray out, int batch, int d, int h, int w, int norm) {
        return Mlx.task("fft_irfftn", 1, x, out, batch, d, h, w, norm);
    }

    /** {@code out[i] = } the i-th sample frequency of an n-point FFT with sample spacing {@code d} ({@code out} has n elements). */
    @MlxOp("mlx_fft_fftfreq")
    public static LibraryTaskDescriptor fftfreq(FloatArray out, int n, float d) {
        return Mlx.task("fft_fftfreq", 0, out, n, d);
    }

    /** The non-negative sample frequencies of an n-point real FFT ({@code out} has n / 2 + 1 elements). */
    @MlxOp("mlx_fft_rfftfreq")
    public static LibraryTaskDescriptor rfftfreq(FloatArray out, int n, float d) {
        return Mlx.task("fft_rfftfreq", 0, out, n, d);
    }

    /** Moves the zero-frequency term of each row of {@code x[rows, len]} to the centre (a roll by {@code len / 2}). */
    @MlxOp("mlx_fft_fftshift")
    public static LibraryTaskDescriptor fftshift(FloatArray x, FloatArray out, int rows, int len) {
        return Mlx.task("fft_fftshift", 1, x, out, rows, len);
    }

    /** Inverse of {@link #fftshift} (a roll by {@code -(len / 2)}). */
    @MlxOp("mlx_fft_ifftshift")
    public static LibraryTaskDescriptor ifftshift(FloatArray x, FloatArray out, int rows, int len) {
        return Mlx.task("fft_ifftshift", 1, x, out, rows, len);
    }

    /** Moves the zero-frequency term of each row of {@code x[rows, len]} to the centre (a roll by {@code len / 2}). */
    @MlxOp("mlx_fft_fftshift")
    public static LibraryTaskDescriptor fftshift(HalfFloatArray x, HalfFloatArray out, int rows, int len) {
        return Mlx.task("fft_fftshift", 1, x, out, rows, len);
    }

    /** Inverse of {@link #fftshift} (a roll by {@code -(len / 2)}). */
    @MlxOp("mlx_fft_ifftshift")
    public static LibraryTaskDescriptor ifftshift(HalfFloatArray x, HalfFloatArray out, int rows, int len) {
        return Mlx.task("fft_ifftshift", 1, x, out, rows, len);
    }

    /** Moves the zero-frequency term of each row of {@code x[rows, len]} to the centre (a roll by {@code len / 2}). */
    @MlxOp("mlx_fft_fftshift")
    public static LibraryTaskDescriptor fftshift(BFloat16Array x, BFloat16Array out, int rows, int len) {
        return Mlx.task("fft_fftshift", 1, x, out, rows, len);
    }

    /** Inverse of {@link #fftshift} (a roll by {@code -(len / 2)}). */
    @MlxOp("mlx_fft_ifftshift")
    public static LibraryTaskDescriptor ifftshift(BFloat16Array x, BFloat16Array out, int rows, int len) {
        return Mlx.task("fft_ifftshift", 1, x, out, rows, len);
    }
}
