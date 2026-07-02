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

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cufft.CuFft;

/**
 * Hybrid FFT pipeline: a noisy real signal is low-pass filtered entirely on
 * the GPU inside one task graph — cuFFT R2C, a JIT-compiled kernel zeroing the
 * high-frequency bins, cuFFT C2R, and a JIT normalization kernel. Two library
 * tasks and two JIT tasks sharing device buffers with no host round trips.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cufft/uk.ac.manchester.tornado.cufft.tests.FrequencyFilterExample [n] [cutoff]
 * </code>
 */
public class FrequencyFilterExample {

    /** Zeroes all frequency bins at or above the cutoff (interleaved complex). */
    public static void lowPass(FloatArray spectrum, int cutoff, int bins) {
        for (@Parallel int k = 0; k < bins; k++) {
            if (k >= cutoff) {
                spectrum.set(2 * k, 0.0f);
                spectrum.set(2 * k + 1, 0.0f);
            }
        }
    }

    public static void scaleBy(FloatArray array, float factor) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, array.get(i) * factor);
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {

        final int n = (args.length > 0) ? Integer.parseInt(args[0]) : 4096;
        final int cutoff = (args.length > 1) ? Integer.parseInt(args[1]) : 16;
        final int bins = n / 2 + 1;

        System.out.println("Low-pass filter via hybrid FFT pipeline: n=" + n + ", cutoff bin=" + cutoff);

        // Signal: two low-frequency tones (kept) + one high-frequency tone (removed)
        FloatArray signal = new FloatArray(n);
        for (int t = 0; t < n; t++) {
            float lowTones = TornadoMath.sin((2 * TornadoMath.floatPI() * 3 * t) / n) + 0.5f * TornadoMath.cos((2 * TornadoMath.floatPI() * 7 * t) / n);
            float highTone = 0.8f * TornadoMath.sin((2 * TornadoMath.floatPI() * 200 * t) / n);
            signal.set(t, lowTones + highTone);
        }

        FloatArray spectrum = new FloatArray(2 * bins);
        FloatArray filtered = new FloatArray(n);

        TaskGraph taskGraph = new TaskGraph("filter") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, signal) //
                .libraryTask("forward", CuFft::cufftForwardR2C, signal, spectrum, n, 1) //
                .task("lowPass", FrequencyFilterExample::lowPass, spectrum, cutoff, bins) //
                .libraryTask("inverse", CuFft::cufftInverseC2R, spectrum, filtered, n, 1) //
                .task("normalize", FrequencyFilterExample::scaleBy, filtered, 1.0f / n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, filtered);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        // The filtered signal must match the low-frequency tones alone
        boolean isResultCorrect = true;
        float maxError = 0.0f;
        for (int t = 0; t < n; t++) {
            float expected = (float) (Math.sin((2 * Math.PI * 3 * t) / n) + 0.5f * Math.cos((2 * Math.PI * 7 * t) / n));
            maxError = Math.max(maxError, Math.abs(expected - filtered.get(t)));
            if (Math.abs(expected - filtered.get(t)) > 1e-2f) {
                if (isResultCorrect) {
                    System.out.println("Mismatch at " + t + ": expected " + expected + ", got " + filtered.get(t));
                }
                isResultCorrect = false;
            }
        }
        System.out.println("Max error vs analytic low-frequency signal: " + maxError);
        System.out.println(isResultCorrect ? "Result is correct" : "Result is wrong");
    }
}
