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
package uk.ac.manchester.tornado.unittests.cufft;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cufft.CuFft;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Unit tests for cuFFT library tasks. Skipped ([UNSUPPORTED]) unless the
 * default device is on the CUDA backend and libtornado-cufft is loadable.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.cufft.TestCuFft
 * </code>
 */
public class TestCuFft extends TornadoTestBase {

    private static final int N = 256;
    private static final Random random = new Random(42);

    @Before
    public void cuFftMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "cuFFT library tasks require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, PTX, SPIRV, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        try {
            System.loadLibrary("tornado-cufft");
        } catch (UnsatisfiedLinkError e) {
            throw new TornadoVMCUDANotSupported("libtornado-cufft is not available: " + e.getMessage());
        }
    }

    /** Naive DFT reference on interleaved complex data: X[k] = sum x[t] e^(-2*pi*i*t*k/n). */
    private static void dftJava(FloatArray input, FloatArray output, int n, int batchIndex) {
        int base = 2 * n * batchIndex;
        for (int k = 0; k < n; k++) {
            float sumRe = 0.0f;
            float sumIm = 0.0f;
            for (int t = 0; t < n; t++) {
                double angle = (2.0 * Math.PI * t * k) / n;
                float re = input.get(base + 2 * t);
                float im = input.get(base + 2 * t + 1);
                sumRe += (float) (re * Math.cos(angle) + im * Math.sin(angle));
                sumIm += (float) (-re * Math.sin(angle) + im * Math.cos(angle));
            }
            output.set(base + 2 * k, sumRe);
            output.set(base + 2 * k + 1, sumIm);
        }
    }

    private static FloatArray randomComplex(int n, int batch) {
        FloatArray array = new FloatArray(2 * n * batch);
        for (int i = 0; i < array.getSize(); i++) {
            array.set(i, random.nextFloat() - 0.5f);
        }
        return array;
    }

    public static void scaleBy(FloatArray array, float factor) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, array.get(i) * factor);
        }
    }

    @Test
    public void testForwardC2C() throws TornadoExecutionPlanException {
        FloatArray input = randomComplex(N, 1);
        FloatArray output = new FloatArray(2 * N);
        FloatArray expected = new FloatArray(2 * N);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .libraryTask("fft", CuFft::cufftForwardC2C, input, output, N, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        dftJava(input, expected, N, 0);
        for (int i = 0; i < 2 * N; i++) {
            assertEquals(expected.get(i), output.get(i), 1e-2f * Math.max(1.0f, Math.abs(expected.get(i))));
        }
    }

    @Test
    public void testForwardC2CBatched() throws TornadoExecutionPlanException {
        final int batch = 4;
        FloatArray input = randomComplex(N, batch);
        FloatArray output = new FloatArray(2 * N * batch);
        FloatArray expected = new FloatArray(2 * N * batch);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .libraryTask("fft", CuFft::cufftForwardC2C, input, output, N, batch) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int b = 0; b < batch; b++) {
            dftJava(input, expected, N, b);
        }
        for (int i = 0; i < 2 * N * batch; i++) {
            assertEquals(expected.get(i), output.get(i), 1e-2f * Math.max(1.0f, Math.abs(expected.get(i))));
        }
    }

    /**
     * Round trip inside one mixed task graph: forward FFT -> inverse FFT -> a
     * JIT task normalizing by 1/n (cuFFT is unnormalized). The result must be
     * the original signal, and the normalization kernel demonstrates a JIT
     * task consuming a cuFFT output on the device.
     */
    @Test
    public void testRoundTripWithJitNormalization() throws TornadoExecutionPlanException {
        FloatArray input = randomComplex(N, 1);
        FloatArray spectrum = new FloatArray(2 * N);
        FloatArray restored = new FloatArray(2 * N);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .libraryTask("forward", CuFft::cufftForwardC2C, input, spectrum, N, 1) //
                .libraryTask("inverse", CuFft::cufftInverseC2C, spectrum, restored, N, 1) //
                .task("normalize", TestCuFft::scaleBy, restored, 1.0f / N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, restored);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < 2 * N; i++) {
            assertEquals(input.get(i), restored.get(i), 1e-3f);
        }
    }

    @Test
    public void testForwardR2C() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            input.set(i, random.nextFloat() - 0.5f);
        }
        // R2C output: the non-redundant half of the Hermitian spectrum
        FloatArray output = new FloatArray(2 * (N / 2 + 1));

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .libraryTask("fft", CuFft::cufftForwardR2C, input, output, N, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        // Reference: complex DFT of the real signal, first N/2+1 bins
        for (int k = 0; k <= N / 2; k++) {
            float sumRe = 0.0f;
            float sumIm = 0.0f;
            for (int t = 0; t < N; t++) {
                double angle = (2.0 * Math.PI * t * k) / N;
                sumRe += (float) (input.get(t) * Math.cos(angle));
                sumIm += (float) (-input.get(t) * Math.sin(angle));
            }
            assertEquals(sumRe, output.get(2 * k), 1e-2f * Math.max(1.0f, Math.abs(sumRe)));
            assertEquals(sumIm, output.get(2 * k + 1), 1e-2f * Math.max(1.0f, Math.abs(sumIm)));
        }
    }

    @Test
    public void testR2CC2RRoundTrip() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            input.set(i, random.nextFloat() - 0.5f);
        }
        FloatArray spectrum = new FloatArray(2 * (N / 2 + 1));
        FloatArray restored = new FloatArray(N);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .libraryTask("forward", CuFft::cufftForwardR2C, input, spectrum, N, 1) //
                .libraryTask("inverse", CuFft::cufftInverseC2R, spectrum, restored, N, 1) //
                .task("normalize", TestCuFft::scaleBy, restored, 1.0f / N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, restored);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals(input.get(i), restored.get(i), 1e-3f);
        }
    }

    @Test
    public void testForwardZ2ZDoublePrecision() throws TornadoExecutionPlanException {
        DoubleArray input = new DoubleArray(2 * N);
        for (int i = 0; i < 2 * N; i++) {
            input.set(i, random.nextDouble() - 0.5);
        }
        DoubleArray output = new DoubleArray(2 * N);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .libraryTask("fft", CuFft::cufftForwardZ2Z, input, output, N, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int k = 0; k < N; k++) {
            double sumRe = 0.0;
            double sumIm = 0.0;
            for (int t = 0; t < N; t++) {
                double angle = (2.0 * Math.PI * t * k) / N;
                double re = input.get(2 * t);
                double im = input.get(2 * t + 1);
                sumRe += re * Math.cos(angle) + im * Math.sin(angle);
                sumIm += -re * Math.sin(angle) + im * Math.cos(angle);
            }
            assertEquals(sumRe, output.get(2 * k), 1e-9 * Math.max(1.0, Math.abs(sumRe)));
            assertEquals(sumIm, output.get(2 * k + 1), 1e-9 * Math.max(1.0, Math.abs(sumIm)));
        }
    }

    @Test
    public void testForward2dC2C() throws TornadoExecutionPlanException {
        final int nx = 16;
        final int ny = 16;
        FloatArray input = randomComplex(nx * ny, 1);
        FloatArray output = new FloatArray(2 * nx * ny);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .libraryTask("fft2d", CuFft::cufftForward2dC2C, input, output, nx, ny) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        // Reference: 2D DFT, X[kx][ky] = sum x[tx][ty] e^(-2 pi i (tx kx / nx + ty ky / ny))
        for (int kx = 0; kx < nx; kx++) {
            for (int ky = 0; ky < ny; ky++) {
                double sumRe = 0.0;
                double sumIm = 0.0;
                for (int tx = 0; tx < nx; tx++) {
                    for (int ty = 0; ty < ny; ty++) {
                        double angle = 2.0 * Math.PI * ((double) tx * kx / nx + (double) ty * ky / ny);
                        double re = input.get(2 * (tx * ny + ty));
                        double im = input.get(2 * (tx * ny + ty) + 1);
                        sumRe += re * Math.cos(angle) + im * Math.sin(angle);
                        sumIm += -re * Math.sin(angle) + im * Math.cos(angle);
                    }
                }
                int idx = 2 * (kx * ny + ky);
                assertEquals((float) sumRe, output.get(idx), 1e-2f * Math.max(1.0f, (float) Math.abs(sumRe)));
                assertEquals((float) sumIm, output.get(idx + 1), 1e-2f * Math.max(1.0f, (float) Math.abs(sumIm)));
            }
        }
    }

    /**
     * FFT pipeline under CUDA Graph capture/replay: plans are created in the
     * provider's prepare() hook (pre-capture, since cufftPlan1d allocates a
     * device work area), so the whole forward -> inverse -> JIT-normalize
     * region is captured on iteration 0 and replayed afterwards.
     */
    @Test
    public void testRoundTripWithCudaGraph() throws TornadoExecutionPlanException {
        FloatArray input = randomComplex(N, 1);
        FloatArray original = new FloatArray(2 * N);
        for (int i = 0; i < 2 * N; i++) {
            original.set(i, input.get(i));
        }
        FloatArray spectrum = new FloatArray(2 * N);
        FloatArray restored = new FloatArray(2 * N);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .libraryTask("forward", CuFft::cufftForwardC2C, input, spectrum, N, 1) //
                .libraryTask("inverse", CuFft::cufftInverseC2C, spectrum, restored, N, 1) //
                .task("normalize", TestCuFft::scaleBy, restored, 1.0f / N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, restored);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withCUDAGraph();
            for (int it = 0; it < 5; it++) {
                for (int i = 0; i < 2 * N; i++) {
                    input.set(i, original.get(i));
                }
                plan.execute();
                for (int i = 0; i < 2 * N; i++) {
                    assertEquals("iteration " + it, original.get(i), restored.get(i), 1e-3f);
                }
            }
        }
    }
}
