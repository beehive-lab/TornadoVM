/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.unittests.compute;

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.collections.types.Byte3;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.ImageByte3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * Test to check functionality of benchmarks available in the compute-benchmark
 * package.
 * 
 * How to run?
 * 
 * <code>
 *     tornado-test.py -V --fast uk.ac.manchester.tornado.unittests.compute.ComputeTests
 * </code>
 * 
 */
public class ComputeTests extends TornadoTestBase {

    private static float DELTA = 0.005f;
    private static float ESP_SQR = 500.0f;

    private static void nBody(int numBodies, float[] refPos, float[] refVel) {
        for (@Parallel int i = 0; i < numBodies; i++) {
            int body = 4 * i;

            float[] acc = new float[] { 0.0f, 0.0f, 0.0f };
            for (int j = 0; j < numBodies; j++) {
                float[] r = new float[3];
                int index = 4 * j;

                float distSqr = 0.0f;
                for (int k = 0; k < 3; k++) {
                    r[k] = refPos[index + k] - refPos[body + k];
                    distSqr += r[k] * r[k];
                }

                float invDist = (float) (1.0f / Math.sqrt(distSqr + ESP_SQR));

                float invDistCube = invDist * invDist * invDist;
                float s = refPos[index + 3] * invDistCube;

                for (int k = 0; k < 3; k++) {
                    acc[k] += s * r[k];
                }
            }
            for (int k = 0; k < 3; k++) {
                refPos[body + k] += refVel[body + k] * DELTA + 0.5f * acc[k] * DELTA * DELTA;
                refVel[body + k] += acc[k] * DELTA;
            }
        }
    }

    public static void validate(int numBodies, float[] posTornadoVM, float[] velTornadoVM, float[] posSequential, float[] velSequential) {
        for (int i = 0; i < numBodies * 4; i++) {
            assertEquals(posSequential[i], posTornadoVM[i], 0.1f);
            assertEquals(velSequential[i], velTornadoVM[i], 0.1f);
        }
    }

    @Test
    public void testNBody() {

        final int numBodies = 8192;
        float[] posSeq = new float[numBodies * 4];
        float[] velSeq = new float[numBodies * 4];

        for (int i = 0; i < posSeq.length; i++) {
            posSeq[i] = (float) Math.random();
        }

        Arrays.fill(velSeq, 0.0f);

        float[] posTornadoVM = new float[numBodies * 4];
        float[] velTornadoVM = new float[numBodies * 4];

        System.arraycopy(posSeq, 0, posTornadoVM, 0, posSeq.length);
        System.arraycopy(velSeq, 0, velTornadoVM, 0, velSeq.length);

        // Run Sequential
        nBody(numBodies, posSeq, velSeq);

        WorkerGrid workerGrid = new WorkerGrid1D(numBodies);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);
        workerGrid.setGlobalWork(numBodies, 1, 1);
        workerGrid.setLocalWork(1024, 1, 1);

        new TaskSchedule("s0") //
                .task("t0", ComputeTests::nBody, numBodies, posTornadoVM, velTornadoVM) //
                .streamOut(posTornadoVM, velTornadoVM) //
                .execute(gridScheduler);

        validate(numBodies, posTornadoVM, velTornadoVM, posSeq, velSeq);
    }

    public static void computeDFT(float[] inreal, float[] inimag, float[] outreal, float[] outimag) {
        int n = inreal.length;
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            float sumReal = 0;
            float simImag = 0;
            for (int t = 0; t < n; t++) { // For each input element
                float angle = (float) ((2 * Math.PI * t * k) / n);
                sumReal += inreal[t] * Math.cos(angle) + inimag[t] * Math.sin(angle);
                simImag += -inreal[t] * Math.sin(angle) + inimag[t] * Math.cos(angle);
            }
            outreal[k] = sumReal;
            outimag[k] = simImag;
        }
    }

    private void validateDFT(int size, float[] inReal, float[] inImag, float[] outReal, float[] outImag) {
        float[] outRealSeq = new float[size];
        float[] outImagSeq = new float[size];
        computeDFT(inReal, inImag, outRealSeq, outImagSeq);
        for (int i = 0; i < size; i++) {
            assertEquals(outImagSeq[i], outImag[i], 0.1f);
            assertEquals(outRealSeq[i], outReal[i], 0.1f);
        }
    }

    @Test
    public void testDFT() {
        final int size = 1024;
        TaskSchedule graph;
        float[] inReal = new float[size];
        float[] inImag = new float[size];
        float[] outReal = new float[size];
        float[] outImag = new float[size];

        for (int i = 0; i < size; i++) {
            inReal[i] = 1 / (float) (i + 2);
            inImag[i] = 1 / (float) (i + 2);
        }

        graph = new TaskSchedule("s0") //
                .task("t0", ComputeTests::computeDFT, inReal, inImag, outReal, outImag) //
                .streamOut(outReal, outImag);
        graph.execute();

        validateDFT(size, inReal, inImag, outReal, outImag);
    }

    private static int NROWS = 1024;
    private static int NCOLS = 1024;

    public static void hilbertComputation(float[] output, int rows, int cols) {
        for (@Parallel int i = 0; i < rows; i++) {
            for (@Parallel int j = 0; j < cols; j++) {
                output[i * rows + j] = (float) 1 / ((i + 1) + (j + 1) - 1);
            }
        }
    }

    @Test
    public void testHilbert() {
        float[] output = new float[NROWS * NCOLS];
        TaskSchedule s0 = new TaskSchedule("s0") //
                .task("t0", ComputeTests::hilbertComputation, output, NROWS, NCOLS) //
                .streamOut(output);
        s0.execute();
        float[] seq = new float[NROWS * NCOLS];
        hilbertComputation(seq, NROWS, NCOLS);
        for (int i = 0; i < NROWS; i++) {
            for (int j = 0; j < NCOLS; j++) {
                assertEquals(seq[i * NROWS + j], output[i * NROWS + j], 0.1f);
            }
        }
    }

    private static float cnd(float X) {
        final float c1 = 0.319381530f;
        final float c2 = -0.356563782f;
        final float c3 = 1.781477937f;
        final float c4 = -1.821255978f;
        final float c5 = 1.330274429f;
        final float zero = 0.0f;
        final float one = 1.0f;
        final float two = 2.0f;
        final float temp4 = 0.2316419f;
        final float oneBySqrt2pi = 0.398942280f;
        float absX = TornadoMath.abs(X);
        float t = one / (one + temp4 * absX);
        float y = one - oneBySqrt2pi * TornadoMath.exp(-X * X / two) * t * (c1 + t * (c2 + t * (c3 + t * (c4 + t * c5))));
        return (X < zero) ? (one - y) : y;
    }

    private static void blackScholesKernel(float[] input, float[] callResult, float[] putResult) {
        for (@Parallel int idx = 0; idx < callResult.length; idx++) {
            float rand = input[idx];
            final float S_LOWER_LIMIT = 10.0f;
            final float S_UPPER_LIMIT = 100.0f;
            final float K_LOWER_LIMIT = 10.0f;
            final float K_UPPER_LIMIT = 100.0f;
            final float T_LOWER_LIMIT = 1.0f;
            final float T_UPPER_LIMIT = 10.0f;
            final float R_LOWER_LIMIT = 0.01f;
            final float R_UPPER_LIMIT = 0.05f;
            final float SIGMA_LOWER_LIMIT = 0.01f;
            final float SIGMA_UPPER_LIMIT = 0.10f;
            final float S = S_LOWER_LIMIT * rand + S_UPPER_LIMIT * (1.0f - rand);
            final float K = K_LOWER_LIMIT * rand + K_UPPER_LIMIT * (1.0f - rand);
            final float T = T_LOWER_LIMIT * rand + T_UPPER_LIMIT * (1.0f - rand);
            final float r = R_LOWER_LIMIT * rand + R_UPPER_LIMIT * (1.0f - rand);
            final float v = SIGMA_LOWER_LIMIT * rand + SIGMA_UPPER_LIMIT * (1.0f - rand);

            float d1 = (TornadoMath.log(S / K) + ((r + (v * v / 2)) * T)) / v * TornadoMath.sqrt(T);
            float d2 = d1 - (v * TornadoMath.sqrt(T));
            callResult[idx] = S * cnd(d1) - K * TornadoMath.exp(T * (-1) * r) * cnd(d2);
            putResult[idx] = K * TornadoMath.exp(T * -r) * cnd(-d2) - S * cnd(-d1);
        }
    }

    private static void checkBlackScholes(float[] call, float[] put, float[] callPrice, float[] putPrice) {
        double delta = 1.8;
        for (int i = 0; i < call.length; i++) {
            assertEquals(call[i], callPrice[i], delta);
            assertEquals(put[i], putPrice[i], delta);
        }
    }

    @Test
    public void testBlackScholes() {
        Random random = new Random();
        final int size = 8192;
        float[] input = new float[size];
        float[] callPrice = new float[size];
        float[] putPrice = new float[size];
        float[] seqCall = new float[size];
        float[] seqPut = new float[size];

        IntStream.range(0, size).forEach(i -> input[i] = random.nextFloat());

        TaskSchedule graph = new TaskSchedule("s0") //
                .streamIn(input) //
                .task("t0", ComputeTests::blackScholesKernel, input, callPrice, putPrice) //
                .streamOut(callPrice, putPrice);

        graph.execute();

        blackScholesKernel(input, seqCall, seqPut);

        blackScholesKernel(input, seqCall, seqPut);

        checkBlackScholes(seqCall, seqPut, callPrice, putPrice);
    }

    private static void computeMontecarlo(float[] output, final int iterations) {
        for (@Parallel int j = 0; j < iterations; j++) {
            long seed = j;
            // generate a pseudo random number (you do need it twice)
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);

            // this generates a number between 0 and 1 (with an awful entropy)
            float x = (seed & 0x0FFFFFFF) / 268435455f;

            // repeat for y
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            float y = (seed & 0x0FFFFFFF) / 268435455f;

            float dist = (float) Math.sqrt(x * x + y * y);
            if (dist <= 1.0f) {
                output[j] = 1.0f;
            } else {
                output[j] = 0.0f;
            }
        }
    }

    @Test
    public void testMontecarlo() {
        final int size = 8192;
        float[] output = new float[size];
        float[] seq = new float[size];

        TaskSchedule t0 = new TaskSchedule("s0") //
                .task("t0", ComputeTests::computeMontecarlo, output, size) //
                .streamOut(output);

        t0.execute();

        float sumTornado = 0;
        for (int j = 0; j < size; j++) {
            sumTornado += output[j];
        }
        sumTornado *= 4;

        computeMontecarlo(seq, size);

        float sumSeq = 0;
        for (int j = 0; j < size; j++) {
            sumSeq += seq[j];
        }
        sumSeq *= 4;

        assertEquals(sumSeq, sumTornado, 0.1);
    }

    /**
     * Render track version found in KFusion SLAMBENCH
     * 
     * @param output
     * @param input
     */
    public static void renderTrack(ImageByte3 output, ImageFloat3 input) {
        for (@Parallel int y = 0; y < input.Y(); y++) {
            for (@Parallel int x = 0; x < input.X(); x++) {
                Byte3 pixel = null;
                final int result = (int) input.get(x, y).getS2();
                switch (result) {
                    case 1: // ok GREY
                        pixel = new Byte3((byte) 128, (byte) 128, (byte) 128);
                        break;
                    case -1: // no input BLACK
                        pixel = new Byte3((byte) 0, (byte) 0, (byte) 0);
                        break;
                    case -2: // not in image RED
                        pixel = new Byte3((byte) 255, (byte) 0, (byte) 0);
                        break;
                    case -3: // no correspondence GREEN
                        pixel = new Byte3((byte) 0, (byte) 255, (byte) 0);
                        break;
                    case -4: // too far away BLUE
                        pixel = new Byte3((byte) 0, (byte) 0, (byte) 255);
                        break;
                    case -5: // wrong normal YELLOW
                        pixel = new Byte3((byte) 255, (byte) 255, (byte) 0);
                        break;
                    default:
                        pixel = new Byte3((byte) 255, (byte) 128, (byte) 128);
                        break;
                }
                output.set(x, y, pixel);
            }
        }
    }

    @Test
    public void testRenderTrack() {
        int n = 2048;
        int m = 2048;

        ImageByte3 outputTornadoVM = new ImageByte3(n, m);
        ImageByte3 outputJava = new ImageByte3(n, m);
        ImageFloat3 input = new ImageFloat3(n, m);

        Random r = new Random();
        for (int i = 0; i < input.X(); i++) {
            for (int j = 0; j < input.Y(); j++) {
                float value = (float) r.nextInt(10) * -1;
                input.set(i, j, new Float3(i, j, value));
            }
        }

        TaskSchedule task = new TaskSchedule("s0") //
                .task("t0", ComputeTests::renderTrack, outputTornadoVM, input) //
                .streamOut(outputTornadoVM);

        task.execute();

        renderTrack(outputJava, input);
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < m; y++) {
                assertEquals(outputJava.get(x, y).getX(), outputTornadoVM.get(x, y).getX(), 0.1);
                assertEquals(outputJava.get(x, y).getY(), outputTornadoVM.get(x, y).getY(), 0.1);
                assertEquals(outputJava.get(x, y).getZ(), outputTornadoVM.get(x, y).getZ(), 0.1);
            }
        }
    }
}
