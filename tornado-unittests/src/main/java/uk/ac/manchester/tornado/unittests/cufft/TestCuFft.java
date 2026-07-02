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
}
