/*
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
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

import static org.junit.Assert.assertEquals;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.collections.types.Byte3;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.ImageByte3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.api.collections.types.Matrix2DFloat;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Test to check functionality of benchmarks available in the compute-benchmark
 * package.
 *
 * <p>
 * How to run?
 * </p>
 *
 * <code>
 *     tornado-test -V --fast uk.ac.manchester.tornado.unittests.compute.ComputeTests
 * </code>
 *
 */
public class ComputeTests extends TornadoTestBase {

    private static final float DELTA = 0.005f;
    private static final float ESP_SQR = 500.0f;
    // Parameters for the algorithm used
    private static final int MAX_ITERATIONS = 1000;
    private static final float ZOOM = 1;
    private static final float CX = -0.7f;
    private static final float CY = 0.27015f;
    private static final float MOVE_X = 0;
    private static final float MOVE_Y = 0;
    private static int NROWS = 1024;
    private static int NCOLS = 1024;

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

    public static void computeDFTFloat(float[] inreal, float[] inimag, float[] outreal, float[] outimag) {
        int n = inreal.length;
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            float sumReal = 0;
            float simImag = 0;
            for (int t = 0; t < n; t++) { // For each input element
                float angle = (2 * TornadoMath.floatPI() * t * k) / n;
                sumReal += inreal[t] * TornadoMath.cos(angle) + inimag[t] * TornadoMath.sin(angle);
                simImag += -inreal[t] * TornadoMath.sin(angle) + inimag[t] * TornadoMath.cos(angle);
            }
            outreal[k] = sumReal;
            outimag[k] = simImag;
        }
    }

    public static void hilbertComputation(float[] output, int rows, int cols) {
        for (@Parallel int i = 0; i < rows; i++) {
            for (@Parallel int j = 0; j < cols; j++) {
                output[i * rows + j] = (float) 1 / ((i + 1) + (j + 1) - 1);
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

    public static void mandelbrotFractal(int size, short[] output) {
        final int iterations = 10000;
        float space = 2.0f / size;

        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float Zr = 0.0f;
                float Zi = 0.0f;
                float Cr = (1 * j * space - 1.5f);
                float Ci = (1 * i * space - 1.0f);
                float ZrN = 0;
                float ZiN = 0;
                int y = 0;
                for (int ii = 0; ii < iterations; ii++) {
                    if (ZiN + ZrN <= 4.0f) {
                        Zi = 2.0f * Zr * Zi + Ci;
                        Zr = 1 * ZrN - ZiN + Cr;
                        ZiN = Zi * Zi;
                        ZrN = Zr * Zr;
                        y++;
                    } else {
                        ii = iterations;
                    }
                }
                float temp = (y * 255) / (float) iterations;
                short r = (short) temp;
                output[i * size + j] = r;
            }
        }
    }

    private static void euler(int size, long[] five, long[] outputA, long[] outputB, long[] outputC, long[] outputD, long[] outputE) {
        for (@Parallel int e = 1; e < five.length; e++) {
            long e5 = five[e];
            for (@Parallel int a = 1; a < five.length; a++) {
                long a5 = five[a];
                for (int b = a; b < size; b++) {
                    long b5 = five[b];
                    for (int c = b; c < size; c++) {
                        long c5 = five[c];
                        for (int d = c; d < size; d++) {
                            long d5 = five[d];
                            if (a5 + b5 + c5 + d5 == e5) {
                                outputA[e] = a;
                                outputB[e] = b;
                                outputC[e] = c;
                                outputD[e] = d;
                                outputE[e] = e;
                            }
                        }
                    }
                }
            }
        }
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
                Byte3 pixel;
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

    public static void juliaSetTornado(int size, float[] hue, float[] brightness) {
        for (@Parallel int ix = 0; ix < size; ix++) {
            for (@Parallel int jx = 0; jx < size; jx++) {
                float zx = 1.5f * (ix - size / 2) / (0.5f * ZOOM * size) + MOVE_X;
                float zy = (jx - size / 2) / (0.5f * ZOOM * size) + MOVE_Y;
                float k = MAX_ITERATIONS;
                while ((zx * zx + zy * zy < 4)) {
                    if (k < 0) {
                        break;
                    }
                    float tmp = zx * zx - zy * zy + CX;
                    zy = 2.0f * zx * zy + CY;
                    zx = tmp;
                    k--;
                }
                hue[ix * size + jx] = (MAX_ITERATIONS / k);
                brightness[ix * size + jx] = k > 0 ? 1 : 0;
            }
        }
    }

    private static BufferedImage writeFile(int[] output, int size) {
        BufferedImage img = null;
        try {
            img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            WritableRaster write = img.getRaster();

            String tmpDirsLocation = System.getProperty("java.io.tmpdir");
            File outputFile = new File(tmpDirsLocation + "/juliaSets.png");

            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    int colour = output[(i * size + j)];
                    write.setSample(i, j, 1, colour);
                }
            }
            ImageIO.write(img, "PNG", outputFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return img;
    }

    @Test
    public void testNBody() {

        final int numBodies = 16384;
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
        workerGrid.setLocalWork(32, 1, 1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, posTornadoVM, velTornadoVM) //
                .task("t0", ComputeTests::nBody, numBodies, posTornadoVM, velTornadoVM) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, posTornadoVM, velTornadoVM);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        validate(numBodies, posTornadoVM, velTornadoVM, posSeq, velSeq);
    }

    @Test
    public void testNBodySmall() {

        final int numBodies = 2048;
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
        workerGrid.setLocalWork(32, 1, 1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, posTornadoVM, velTornadoVM) //
                .task("t0", ComputeTests::nBody, numBodies, posTornadoVM, velTornadoVM) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, posTornadoVM, velTornadoVM);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        validate(numBodies, posTornadoVM, velTornadoVM, posSeq, velSeq);
    }

    @Test
    public void testNBodyBigNoWorker() {

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

        TaskGraph taskGraph = new TaskGraph("compute") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, posTornadoVM, velTornadoVM) //
                .task("nbody", ComputeTests::nBody, numBodies, posTornadoVM, velTornadoVM) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, posTornadoVM, velTornadoVM);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        validate(numBodies, posTornadoVM, velTornadoVM, posSeq, velSeq);
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
    public void testDFTDouble() {
        final int size = 4096;
        TaskGraph taskGraph;
        float[] inReal = new float[size];
        float[] inImag = new float[size];
        float[] outReal = new float[size];
        float[] outImag = new float[size];

        for (int i = 0; i < size; i++) {
            inReal[i] = 1 / (float) (i + 2);
            inImag[i] = 1 / (float) (i + 2);
        }

        taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inReal, inImag) //
                .task("t0", ComputeTests::computeDFT, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        validateDFT(size, inReal, inImag, outReal, outImag);
    }

    @Test
    public void testDFTFloat() {
        final int size = 4096;
        TaskGraph taskGraph;
        float[] inReal = new float[size];
        float[] inImag = new float[size];
        float[] outReal = new float[size];
        float[] outImag = new float[size];

        for (int i = 0; i < size; i++) {
            inReal[i] = 1 / (float) (i + 2);
            inImag[i] = 1 / (float) (i + 2);
        }

        taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inReal, inImag) //
                .task("t0", ComputeTests::computeDFTFloat, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        validateDFT(size, inReal, inImag, outReal, outImag);
    }

    @Test
    public void testHilbert() {
        float[] output = new float[NROWS * NCOLS];
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", ComputeTests::hilbertComputation, output, NROWS, NCOLS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        float[] seq = new float[NROWS * NCOLS];
        hilbertComputation(seq, NROWS, NCOLS);
        for (int i = 0; i < NROWS; i++) {
            for (int j = 0; j < NCOLS; j++) {
                assertEquals(seq[i * NROWS + j], output[i * NROWS + j], 0.1f);
            }
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

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", ComputeTests::blackScholesKernel, input, callPrice, putPrice) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, callPrice, putPrice);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        blackScholesKernel(input, seqCall, seqPut);

        blackScholesKernel(input, seqCall, seqPut);

        checkBlackScholes(seqCall, seqPut, callPrice, putPrice);
    }

    @Test
    public void testMontecarlo() {
        final int size = 8192;
        float[] output = new float[size];
        float[] seq = new float[size];

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", ComputeTests::computeMontecarlo, output, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

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

    private void validateMandelbrot(int size, short[] output) {
        short[] result = new short[size * size];

        // Run sequential
        mandelbrotFractal(size, result);

        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
                assertEquals(result[i * size + j], output[i * size + j]);
    }

    @Test
    public void testMandelbrot() {
        final int size = 512;
        short[] output = new short[size * size];

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", ComputeTests::mandelbrotFractal, size, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        validateMandelbrot(size, output);
    }

    private long[] init(int size) {
        long[] input = new long[size];
        for (int i = 0; i < size; i++) {
            input[i] = (long) i * i * i * i * i;
        }
        return input;
    }

    @Test
    public void testEuler() {
        final int size = 128;
        long[] input = init(128);
        long[] outputA = new long[size];
        long[] outputB = new long[size];
        long[] outputC = new long[size];
        long[] outputD = new long[size];
        long[] outputE = new long[size];

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("s0", ComputeTests::euler, size, input, outputA, outputB, outputC, outputD, outputE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputA, outputB, outputC, outputD, outputE);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        long[] outputAT = new long[size];
        long[] outputBT = new long[size];
        long[] outputCT = new long[size];
        long[] outputDT = new long[size];
        long[] outputET = new long[size];
        euler(size, input, outputAT, outputBT, outputCT, outputDT, outputET);

        for (int i = 0; i < size; i++) {
            assertEquals(outputAT[i], outputA[i]);
            assertEquals(outputBT[i], outputB[i]);
            assertEquals(outputCT[i], outputC[i]);
            assertEquals(outputDT[i], outputD[i]);
            assertEquals(outputET[i], outputE[i]);
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

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", ComputeTests::renderTrack, outputTornadoVM, input) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputTornadoVM);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        renderTrack(outputJava, input);
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < m; y++) {
                assertEquals(outputJava.get(x, y).getX(), outputTornadoVM.get(x, y).getX(), 0.1);
                assertEquals(outputJava.get(x, y).getY(), outputTornadoVM.get(x, y).getY(), 0.1);
                assertEquals(outputJava.get(x, y).getZ(), outputTornadoVM.get(x, y).getZ(), 0.1);
            }
        }
    }

    @Test
    public void testJuliaSets() {
        final int size = 1024;
        float[] hue = new float[size * size];
        float[] brightness = new float[size * size];
        int[] result = new int[size * size];

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", ComputeTests::juliaSetTornado, size, hue, brightness) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, hue, brightness);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                result[i * size + j] = Color.HSBtoRGB(hue[i * size + j] % 1, 1, brightness[i * size + j]);
            }
        }

        writeFile(result, size);

        // Run Sequential Code
        float[] hueSeq = new float[size * size];
        float[] brightnessSeq = new float[size * size];
        juliaSetTornado(size, hueSeq, brightnessSeq);

        float delta = 0.01f;
        for (int i = 0; i < hueSeq.length; i++) {
            assertEquals(hueSeq[i], hue[i], delta);
            assertEquals(brightnessSeq[i], brightness[i], delta);
        }
    }

    private static void computeMatrixVector(Matrix2DFloat matrix, VectorFloat vector, VectorFloat output) {
        for (@Parallel int i = 0; i < vector.size(); i++) {
            float sum = 0.0f;
            for (int j = 0; j < matrix.getNumColumns(); j++) {
                sum += vector.get(i) * matrix.get(i, i);
            }
            output.set(i, sum);
        }
    }

    @Test
    public void matrixVector() {
        int size = 4096;

        // Create a matrix of M rows and N columns (MxN)
        Matrix2DFloat matrix2DFloat = new Matrix2DFloat(size, size);

        // Vector must be of size N
        VectorFloat vectorFloat = new VectorFloat(size);

        // Output
        VectorFloat result = new VectorFloat(size);

        VectorFloat resultSeq = new VectorFloat(size);

        Random r = new Random();

        final int s = size;

        // Init Data
        IntStream.range(0, size).forEach(idx -> vectorFloat.set(idx, r.nextFloat()));
        IntStream.range(0, size).forEach(idx -> IntStream.range(0, s).forEach(jdx -> {
            matrix2DFloat.set(idx, jdx, r.nextFloat());
        }));

        TaskGraph taskGraph = new TaskGraph("la") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix2DFloat, vectorFloat) //
                .task("mv", ComputeTests::computeMatrixVector, matrix2DFloat, vectorFloat, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        computeMatrixVector(matrix2DFloat, vectorFloat, resultSeq);
        for (int i = 0; i < vectorFloat.size(); i++) {
            assertEquals(resultSeq.get(i), resultSeq.get(i), 0.1);
        }
    }
}
