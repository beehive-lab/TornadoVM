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
package uk.ac.manchester.tornado.cufft.tests;

import java.util.Random;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cufft.CuFft;

/**
 * Benchmark: 1D complex FP32 Fourier transform three ways - sequential Java
 * DFT (O(n^2)), TornadoVM JIT-compiled parallel DFT kernel (O(n^2), the
 * pattern of the {@code DFTVector} example), and a cuFFT library task
 * (O(n log n)). Results are cross-validated.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cufft/uk.ac.manchester.tornado.cufft.tests.BenchmarkFft [n] [iterations]
 * </code>
 */
public class BenchmarkFft {

    private static final int WARMUP_ITERATIONS = 10;

    /** Parallel DFT on split real/imag arrays (same as the DFTVector example). */
    public static void computeDFT(FloatArray inReal, FloatArray inImag, FloatArray outReal, FloatArray outImag) {
        int n = inReal.getSize();
        for (@Parallel int k = 0; k < n; k++) {
            float sumReal = 0;
            float sumImag = 0;
            for (int t = 0; t < n; t++) {
                float angle = ((2 * TornadoMath.floatPI() * t * k) / n);
                sumReal += inReal.get(t) * TornadoMath.cos(angle) + inImag.get(t) * TornadoMath.sin(angle);
                sumImag += -inReal.get(t) * TornadoMath.sin(angle) + inImag.get(t) * TornadoMath.cos(angle);
            }
            outReal.set(k, sumReal);
            outImag.set(k, sumImag);
        }
    }

    private static void computeDFTJava(FloatArray inReal, FloatArray inImag, FloatArray outReal, FloatArray outImag) {
        int n = inReal.getSize();
        for (int k = 0; k < n; k++) {
            float sumReal = 0;
            float sumImag = 0;
            for (int t = 0; t < n; t++) {
                double angle = (2.0 * Math.PI * t * k) / n;
                sumReal += (float) (inReal.get(t) * Math.cos(angle) + inImag.get(t) * Math.sin(angle));
                sumImag += (float) (-inReal.get(t) * Math.sin(angle) + inImag.get(t) * Math.cos(angle));
            }
            outReal.set(k, sumReal);
            outImag.set(k, sumImag);
        }
    }

    private static double benchmark(TornadoExecutionPlan executionPlan, int iterations) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executionPlan.execute();
        }
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            executionPlan.execute();
        }
        return (System.nanoTime() - start) / (double) iterations;
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {

        final int n = (args.length > 0) ? Integer.parseInt(args[0]) : 65536;
        final int iterations = (args.length > 1) ? Integer.parseInt(args[1]) : 20;

        System.out.println("FFT benchmark: 1D complex FP32, n=" + n + ", " + iterations + " iterations (+" + WARMUP_ITERATIONS + " warm-up)");

        // Split representation for the DFT kernels
        FloatArray inReal = new FloatArray(n);
        FloatArray inImag = new FloatArray(n);
        FloatArray outRealJit = new FloatArray(n);
        FloatArray outImagJit = new FloatArray(n);
        FloatArray outRealSeq = new FloatArray(n);
        FloatArray outImagSeq = new FloatArray(n);

        // Interleaved representation for cuFFT
        FloatArray inputInterleaved = new FloatArray(2 * n);
        FloatArray outputCuFft = new FloatArray(2 * n);

        Random random = new Random(42);
        for (int i = 0; i < n; i++) {
            float re = random.nextFloat() - 0.5f;
            float im = random.nextFloat() - 0.5f;
            inReal.set(i, re);
            inImag.set(i, im);
            inputInterleaved.set(2 * i, re);
            inputInterleaved.set(2 * i + 1, im);
        }

        // Sequential Java DFT (once: O(n^2) on the host is slow)
        System.out.println("Running sequential Java DFT (single run)...");
        long seqStart = System.nanoTime();
        computeDFTJava(inReal, inImag, outRealSeq, outImagSeq);
        double seqTime = System.nanoTime() - seqStart;

        TaskGraph jitGraph = new TaskGraph("jitDft") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inReal, inImag) //
                .task("dft", BenchmarkFft::computeDFT, inReal, inImag, outRealJit, outImagJit) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outRealJit, outImagJit);

        TaskGraph cufftGraph = new TaskGraph("cufft") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inputInterleaved) //
                .libraryTask("fft", CuFft::cufftForwardC2C, inputInterleaved, outputCuFft, n, 1) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputCuFft);

        double jitTime;
        double cufftTime;

        try (TornadoExecutionPlan jitPlan = new TornadoExecutionPlan(jitGraph.snapshot())) {
            jitTime = benchmark(jitPlan, iterations);
            jitPlan.execute().transferToHost(outRealJit, outImagJit);
        }

        try (TornadoExecutionPlan cufftPlan = new TornadoExecutionPlan(cufftGraph.snapshot())) {
            cufftTime = benchmark(cufftPlan, iterations);
            cufftPlan.execute().transferToHost(outputCuFft);
        }

        // Cross-validate cuFFT vs the JIT DFT kernel (float trig, relaxed tolerance)
        boolean isResultCorrect = true;
        float scale = (float) Math.sqrt(n);
        for (int i = 0; i < n; i++) {
            float expectedRe = outRealJit.get(i);
            float expectedIm = outImagJit.get(i);
            if (Math.abs(expectedRe - outputCuFft.get(2 * i)) > 0.05f * Math.max(scale, Math.abs(expectedRe)) //
                    || Math.abs(expectedIm - outputCuFft.get(2 * i + 1)) > 0.05f * Math.max(scale, Math.abs(expectedIm))) {
                System.out.println("Mismatch at " + i + ": jit=(" + expectedRe + "," + expectedIm + "), cufft=(" + outputCuFft.get(2 * i) + "," + outputCuFft.get(2 * i + 1) + ")");
                isResultCorrect = false;
                break;
            }
        }

        System.out.printf("Sequential Java DFT (O(n^2))        : %12.3f ms%n", seqTime * 1e-6);
        System.out.printf("TornadoVM JIT DFT kernel (O(n^2))   : %12.3f ms%n", jitTime * 1e-6);
        System.out.printf("cuFFT library task (O(n log n))     : %12.3f ms%n", cufftTime * 1e-6);
        System.out.printf("cuFFT speedup vs Java DFT           : %12.1fx%n", seqTime / cufftTime);
        System.out.printf("cuFFT speedup vs JIT DFT kernel     : %12.1fx%n", jitTime / cufftTime);
        System.out.println(isResultCorrect ? "Results match" : "Results DO NOT match");
    }
}
