/*
 * Copyright (c) 2021-2022, 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.compute;

import static org.junit.Assert.assertEquals;

import java.awt.Color;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4;
import uk.ac.manchester.tornado.api.types.images.ImageByte3;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat4;
import uk.ac.manchester.tornado.api.types.vectors.Byte3;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
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
 * tornado-test -V uk.ac.manchester.tornado.unittests.compute.ComputeTests
 * </code>
 *
 */
public class ComputeTests extends TornadoTestBase {
    // CHECKSTYLE:OFF

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

    private static void nBody(int numBodies, FloatArray refPos, FloatArray refVel) {
        for (@Parallel int i = 0; i < numBodies; i++) {
            int body = 4 * i;

            float[] acc = new float[] { 0.0f, 0.0f, 0.0f };
            for (int j = 0; j < numBodies; j++) {
                float[] r = new float[3];
                int index = 4 * j;

                float distSqr = 0.0f;
                for (int k = 0; k < 3; k++) {
                    r[k] = refPos.get(index + k) - refPos.get(body + k);
                    distSqr += r[k] * r[k];
                }

                float invDist = (float) (1.0f / Math.sqrt(distSqr + ESP_SQR));

                float invDistCube = invDist * invDist * invDist;
                float s = refPos.get(index + 3) * invDistCube;

                for (int k = 0; k < 3; k++) {
                    acc[k] += s * r[k];
                }
            }
            for (int k = 0; k < 3; k++) {
                refPos.set(body + k, refPos.get(body + k) + refPos.get(body + k) * DELTA + 0.5f * acc[k] * DELTA * DELTA);
                refVel.set(body + k, refPos.get(body + k) + acc[k] * DELTA);
            }
        }
    }

    public static void validate(int numBodies, FloatArray posTornadoVM, FloatArray velTornadoVM, FloatArray posSequential, FloatArray velSequential) {
        for (int i = 0; i < numBodies * 4; i++) {
            assertEquals(posSequential.get(i), posTornadoVM.get(i), 0.1f);
            assertEquals(velSequential.get(i), velTornadoVM.get(i), 0.1f);
        }
    }

    public static void computeDFT(FloatArray inreal, FloatArray inimag, FloatArray outreal, FloatArray outimag) {
        int n = inreal.getSize();
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            float sumReal = 0;
            float simImag = 0;
            for (int t = 0; t < n; t++) { // For each input element
                float angle = (float) ((2 * Math.PI * t * k) / n);
                sumReal += inreal.get(t) * Math.cos(angle) + inimag.get(t) * Math.sin(angle);
                simImag += -inreal.get(t) * Math.sin(angle) + inimag.get(t) * Math.cos(angle);
            }
            outreal.set(k, sumReal);
            outimag.set(k, simImag);
        }
    }

    public static void computeDFTFloat(FloatArray inreal, FloatArray inimag, FloatArray outreal, FloatArray outimag) {
        int n = inreal.getSize();
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            float sumReal = 0;
            float simImag = 0;
            for (int t = 0; t < n; t++) { // For each input element
                float angle = (2 * TornadoMath.floatPI() * t * k) / n;
                sumReal += inreal.get(t) * TornadoMath.cos(angle) + inimag.get(t) * TornadoMath.sin(angle);
                simImag += -inreal.get(t) * TornadoMath.sin(angle) + inimag.get(t) * TornadoMath.cos(angle);
            }
            outreal.set(k, sumReal);
            outimag.set(k, simImag);
        }
    }

    public static void computeDFTVector(VectorFloat4 inreal, VectorFloat4 inimag, VectorFloat4 outreal, VectorFloat4 outimag) {
        int n = inreal.getLength();
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            Float4 sumReal = new Float4();
            Float4 simImag = new Float4();
            for (int t = 0; t < n; t++) { // For each input element
                float angle = ((2 * TornadoMath.floatPI() * t * k) / n);

                Float4 partA = Float4.mult(inreal.get(t), TornadoMath.cos(angle));
                Float4 partB = Float4.mult(inimag.get(t), TornadoMath.sin(angle));
                Float4 partC = Float4.add(partA, partB);
                sumReal = Float4.add(sumReal, partC);

                Float4 neg = Float4.mult(inreal.get(t), new Float4(-1, -1, -1, -1));
                Float4 partAImag = Float4.mult(neg, TornadoMath.sin(angle));
                Float4 partBImag = Float4.mult(inimag.get(t), TornadoMath.cos(angle));
                Float4 partCImag = Float4.add(partAImag, partBImag);
                simImag = Float4.add(simImag, partCImag);

            }
            outreal.set(k, sumReal);
            outimag.set(k, simImag);
        }
    }

    public static void hilbertComputation(FloatArray output, int rows, int cols) {
        for (@Parallel int i = 0; i < rows; i++) {
            for (@Parallel int j = 0; j < cols; j++) {
                output.set(i * rows + j, (float) 1 / ((i + 1) + (j + 1) - 1));
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

    private static void blackScholesKernel(FloatArray input, FloatArray callResult, FloatArray putResult) {
        for (@Parallel int idx = 0; idx < callResult.getSize(); idx++) {
            float rand = input.get(idx);
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
            callResult.set(idx, S * cnd(d1) - K * TornadoMath.exp(T * (-1) * r) * cnd(d2));
            putResult.set(idx, K * TornadoMath.exp(T * -r) * cnd(-d2) - S * cnd(-d1));
        }
    }

    private static void checkBlackScholes(FloatArray call, FloatArray put, FloatArray callPrice, FloatArray putPrice) {
        double delta = 1.8;
        for (int i = 0; i < call.getSize(); i++) {
            assertEquals(call.get(i), callPrice.get(i), delta);
            assertEquals(put.get(i), putPrice.get(i), delta);
        }
    }

    private static void computeMontecarlo(FloatArray output, final int iterations) {
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
                output.set(j, 1.0f);
            } else {
                output.set(j, 0.0f);
            }
        }
    }

    public static void mandelbrotFractal(int size, ShortArray output) {
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
                output.set(i * size + j, r);
            }
        }
    }

    private static void euler(int size, LongArray five, LongArray outputA, LongArray outputB, LongArray outputC, LongArray outputD, LongArray outputE) {
        for (@Parallel int e = 1; e < five.getSize(); e++) {
            long e5 = five.get(e);
            for (@Parallel int a = 1; a < five.getSize(); a++) {
                long a5 = five.get(a);
                for (int b = a; b < size; b++) {
                    long b5 = five.get(b);
                    for (int c = b; c < size; c++) {
                        long c5 = five.get(c);
                        for (int d = c; d < size; d++) {
                            long d5 = five.get(d);
                            if (a5 + b5 + c5 + d5 == e5) {
                                outputA.set(e, a);
                                outputB.set(e, b);
                                outputC.set(e, c);
                                outputD.set(e, d);
                                outputE.set(e, e);
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

    public static void juliaSetTornado(int size, FloatArray hue, FloatArray brightness) {
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
                hue.set(ix * size + jx, (MAX_ITERATIONS / k));
                brightness.set(ix * size + jx, k > 0 ? 1 : 0);
            }
        }
    }

    private static void computeMatrixVector(Matrix2DFloat matrix, VectorFloat vector, VectorFloat output) {
        for (@Parallel int i = 0; i < matrix.getNumRows(); i++) {
            float sum = 0.0f;
            for (int j = 0; j < matrix.getNumColumns(); j++) {
                sum += matrix.get(i, j) * vector.get(j);
            }
            output.set(i, sum);
        }
    }

    private static void computeMatrixVectorFloat4(Matrix2DFloat4 matrix, VectorFloat4 vector, VectorFloat output) {
        for (@Parallel int i = 0; i < matrix.getNumRows(); i++) {
            float sum = 0;
            for (int j = 0; j < matrix.getNumColumns(); j++) {
                sum += Float4.sum(Float4.mult(matrix.get(i, j), vector.get(j)));
            }
            output.set(i, sum);
        }
    }

    private static void matrixMultiplicationHalfFloats(final HalfFloatArray A, final HalfFloatArray B, final HalfFloatArray C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                HalfFloat sum = new HalfFloat(0.0f);
                for (int k = 0; k < size; k++) {
                    HalfFloat mult = HalfFloat.mult(A.get((i * size) + k), B.get((k * size) + j));
                    sum = HalfFloat.add(sum, mult);
                }
                C.set((i * size) + j, sum);
            }
        }
    }

    private static void matrixMultiplicationHalfFloatToFloat(final HalfFloatArray A, final HalfFloatArray B, final FloatArray C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                HalfFloat sum = new HalfFloat(0.0f);
                for (int k = 0; k < size; k++) {
                    HalfFloat mult = HalfFloat.mult(A.get((i * size) + k), B.get((k * size) + j));
                    sum = HalfFloat.add(sum, mult);
                }
                C.set((i * size) + j, sum.getFloat32());
            }
        }
    }

    private static void matrixMultiplicationHalfFloatToFloat2(final HalfFloatArray A, final HalfFloatArray B, final FloatArray C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    float mult = A.get((i * size) + k).getFloat32() * B.get((k * size) + j).getFloat32();
                    sum += mult;
                }
                C.set((i * size) + j, sum);
            }
        }
    }

    @Test
    public void testNBody() throws TornadoExecutionPlanException {

        final int numBodies = 16384;
        FloatArray posSeq = new FloatArray(numBodies * 4);
        FloatArray velSeq = new FloatArray(numBodies * 4);

        for (int i = 0; i < posSeq.getSize(); i++) {
            posSeq.set(i, (float) Math.random());
        }

        velSeq.init(0.0f);

        FloatArray posTornadoVM = new FloatArray(numBodies * 4);
        FloatArray velTornadoVM = new FloatArray(numBodies * 4);

        for (int i = 0; i < numBodies * 4; i++) {
            posTornadoVM.set(i, posSeq.get(i));
            velTornadoVM.set(i, velSeq.get(i));
        }

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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        validate(numBodies, posTornadoVM, velTornadoVM, posSeq, velSeq);
    }

    @Test
    public void testNBodySmall() throws TornadoExecutionPlanException {

        final int numBodies = 2048;
        FloatArray posSeq = new FloatArray(numBodies * 4);
        FloatArray velSeq = new FloatArray(numBodies * 4);

        for (int i = 0; i < posSeq.getSize(); i++) {
            posSeq.set(i, (float) Math.random());
        }

        velSeq.init(0.0f);

        FloatArray posTornadoVM = new FloatArray(numBodies * 4);
        FloatArray velTornadoVM = new FloatArray(numBodies * 4);

        for (int i = 0; i < numBodies * 4; i++) {
            posTornadoVM.set(i, posSeq.get(i));
            velTornadoVM.set(i, velSeq.get(i));
        }

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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        validate(numBodies, posTornadoVM, velTornadoVM, posSeq, velSeq);
    }

    @Test
    public void testNBodyBigNoWorker() throws TornadoExecutionPlanException {

        final int numBodies = 8192;
        FloatArray posSeq = new FloatArray(numBodies * 4);
        FloatArray velSeq = new FloatArray(numBodies * 4);

        for (int i = 0; i < posSeq.getSize(); i++) {
            posSeq.set(i, (float) Math.random());
        }

        velSeq.init(0.0f);

        FloatArray posTornadoVM = new FloatArray(numBodies * 4);
        FloatArray velTornadoVM = new FloatArray(numBodies * 4);

        for (int i = 0; i < numBodies * 4; i++) {
            posTornadoVM.set(i, posSeq.get(i));
            velTornadoVM.set(i, velSeq.get(i));
        }

        // Run Sequential
        nBody(numBodies, posSeq, velSeq);

        TaskGraph taskGraph = new TaskGraph("compute") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, posTornadoVM, velTornadoVM) //
                .task("nbody", ComputeTests::nBody, numBodies, posTornadoVM, velTornadoVM) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, posTornadoVM, velTornadoVM);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        validate(numBodies, posTornadoVM, velTornadoVM, posSeq, velSeq);
    }

    private void validateDFT(int size, FloatArray inReal, FloatArray inImag, FloatArray outReal, FloatArray outImag) {
        FloatArray outRealSeq = new FloatArray(size);
        FloatArray outImagSeq = new FloatArray(size);
        computeDFT(inReal, inImag, outRealSeq, outImagSeq);
        for (int i = 0; i < size; i++) {
            assertEquals(outImagSeq.get(i), outImag.get(i), 0.1f);
            assertEquals(outRealSeq.get(i), outReal.get(i), 0.1f);
        }
    }

    private void validateDFTVector(int size, VectorFloat4 inReal, VectorFloat4 inImag, VectorFloat4 outReal, VectorFloat4 outImag) {
        VectorFloat4 outRealSeq = new VectorFloat4(size);
        VectorFloat4 outImagSeq = new VectorFloat4(size);
        computeDFTVector(inReal, inImag, outRealSeq, outImagSeq);
        for (int i = 0; i < size; i++) {
            Float4.isEqual(outImagSeq.get(i), outImag.get(i));
            Float4.isEqual(outRealSeq.get(i), outReal.get(i));
        }
    }

    @Test
    public void testDFTDouble() throws TornadoExecutionPlanException {
        final int size = 4096;
        TaskGraph taskGraph;
        FloatArray inReal = new FloatArray(size);
        FloatArray inImag = new FloatArray(size);
        FloatArray outReal = new FloatArray(size);
        FloatArray outImag = new FloatArray(size);

        for (int i = 0; i < size; i++) {
            inReal.set(i, 1 / (float) (i + 2));
            inImag.set(i, 1 / (float) (i + 2));
        }

        taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inReal, inImag) //
                .task("t0", ComputeTests::computeDFT, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        validateDFT(size, inReal, inImag, outReal, outImag);
    }

    @Test
    public void testDFTVectorTypes() throws TornadoExecutionPlanException {
        final int size = 4096;
        VectorFloat4 inReal = new VectorFloat4(size);
        VectorFloat4 inImag = new VectorFloat4(size);
        VectorFloat4 outReal = new VectorFloat4(size);
        VectorFloat4 outImag = new VectorFloat4(size);

        for (int i = 0; i < size; i++) {
            float valA = 1 / (float) (i + 2);
            float valB = 1 / (float) (i + 2);
            inReal.set(i, new Float4(valA, valA, valA, valA));
            inImag.set(i, new Float4(valB, valB, valB, valB));
        }

        TaskGraph taskGraph = new TaskGraph("dft") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inReal, inImag) //
                .task("withVectors", ComputeTests::computeDFTVector, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        validateDFTVector(size, inReal, inImag, outReal, outImag);
    }

    @Test
    public void testDFTFloat() throws TornadoExecutionPlanException {
        final int size = 4096;
        TaskGraph taskGraph;
        FloatArray inReal = new FloatArray(size);
        FloatArray inImag = new FloatArray(size);
        FloatArray outReal = new FloatArray(size);
        FloatArray outImag = new FloatArray(size);

        for (int i = 0; i < size; i++) {
            inReal.set(i, 1 / (float) (i + 2));
            inImag.set(i, 1 / (float) (i + 2));
        }

        taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inReal, inImag) //
                .task("t0", ComputeTests::computeDFTFloat, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        validateDFT(size, inReal, inImag, outReal, outImag);
    }

    @Test
    public void testHilbert() throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(NROWS * NCOLS);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", ComputeTests::hilbertComputation, output, NROWS, NCOLS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray seq = new FloatArray(NROWS * NCOLS);
        hilbertComputation(seq, NROWS, NCOLS);
        for (int i = 0; i < NROWS; i++) {
            for (int j = 0; j < NCOLS; j++) {
                assertEquals(seq.get(i * NROWS + j), output.get(i * NROWS + j), 0.1f);
            }
        }
    }

    @Test
    public void testBlackScholes() throws TornadoExecutionPlanException {
        Random random = new Random();
        final int size = 8192;
        FloatArray input = new FloatArray(size);
        FloatArray callPrice = new FloatArray(size);
        FloatArray putPrice = new FloatArray(size);
        FloatArray seqCall = new FloatArray(size);
        FloatArray seqPut = new FloatArray(size);

        IntStream.range(0, size).forEach(i -> input.set(i, random.nextFloat()));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", ComputeTests::blackScholesKernel, input, callPrice, putPrice) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, callPrice, putPrice);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        blackScholesKernel(input, seqCall, seqPut);

        blackScholesKernel(input, seqCall, seqPut);

        checkBlackScholes(seqCall, seqPut, callPrice, putPrice);
    }

    @Test
    public void testMontecarlo() throws TornadoExecutionPlanException {
        final int size = 8192;
        FloatArray output = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", ComputeTests::computeMontecarlo, output, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        float sumTornado = 0;
        for (int j = 0; j < size; j++) {
            sumTornado += output.get(j);
        }
        sumTornado *= 4;

        computeMontecarlo(seq, size);

        float sumSeq = 0;
        for (int j = 0; j < size; j++) {
            sumSeq += seq.get(j);
        }
        sumSeq *= 4;

        assertEquals(sumSeq, sumTornado, 0.1);
    }

    private void validateMandelbrot(int size, ShortArray output) {
        ShortArray result = new ShortArray(size * size);

        // Run sequential
        mandelbrotFractal(size, result);

        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
                assertEquals(result.get(i * size + j), output.get(i * size + j));
    }

    @Test
    public void testMandelbrot() throws TornadoExecutionPlanException {
        final int size = 512;
        ShortArray output = new ShortArray(size * size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", ComputeTests::mandelbrotFractal, size, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        validateMandelbrot(size, output);
    }

    private LongArray init(int size) {
        LongArray input = new LongArray(size);
        for (int i = 0; i < size; i++) {
            input.set(i, (long) i * i * i * i * i);
        }
        return input;
    }

    @Test
    public void testEuler() throws TornadoExecutionPlanException {
        final int size = 128;
        LongArray input = init(128);
        LongArray outputA = new LongArray(size);
        LongArray outputB = new LongArray(size);
        LongArray outputC = new LongArray(size);
        LongArray outputD = new LongArray(size);
        LongArray outputE = new LongArray(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("s0", ComputeTests::euler, size, input, outputA, outputB, outputC, outputD, outputE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputA, outputB, outputC, outputD, outputE);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        LongArray outputAT = new LongArray(size);
        LongArray outputBT = new LongArray(size);
        LongArray outputCT = new LongArray(size);
        LongArray outputDT = new LongArray(size);
        LongArray outputET = new LongArray(size);
        euler(size, input, outputAT, outputBT, outputCT, outputDT, outputET);

        for (int i = 0; i < size; i++) {
            assertEquals(outputAT.get(i), outputA.get(i));
            assertEquals(outputBT.get(i), outputB.get(i));
            assertEquals(outputCT.get(i), outputC.get(i));
            assertEquals(outputDT.get(i), outputD.get(i));
            assertEquals(outputET.get(i), outputE.get(i));
        }
    }

    @Test
    public void testRenderTrack() throws TornadoExecutionPlanException {
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

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
    public void testJuliaSets() throws TornadoExecutionPlanException {
        final int size = 1024;
        FloatArray hue = new FloatArray(size * size);
        FloatArray brightness = new FloatArray(size * size);
        IntArray result = new IntArray(size * size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", ComputeTests::juliaSetTornado, size, hue, brightness) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, hue, brightness);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                result.set(i * size + j, Color.HSBtoRGB(hue.get(i * size + j) % 1, 1, brightness.get(i * size + j)));
            }
        }

        // Run Sequential Code
        FloatArray hueSeq = new FloatArray(size * size);
        FloatArray brightnessSeq = new FloatArray(size * size);
        juliaSetTornado(size, hueSeq, brightnessSeq);

        float delta = 0.01f;
        for (int i = 0; i < hueSeq.getSize(); i++) {
            assertEquals(hueSeq.get(i), hue.get(i), delta);
            assertEquals(brightnessSeq.get(i), brightness.get(i), delta);
        }
    }

    @Test
    public void matrixVector() throws TornadoExecutionPlanException {
        int size = 4096;

        Matrix2DFloat matrix2DFloat = new Matrix2DFloat(size, size);
        VectorFloat vectorFloat = new VectorFloat(size);
        VectorFloat result = new VectorFloat(size);
        VectorFloat resultSeq = new VectorFloat(size);

        Random r = new Random();
        final int s = size;

        IntStream.range(0, size).forEach(idx -> vectorFloat.set(idx, r.nextFloat()));
        IntStream.range(0, size).forEach(idx -> IntStream.range(0, s).forEach(jdx -> {
            matrix2DFloat.set(idx, jdx, r.nextFloat());
        }));

        TaskGraph taskGraph = new TaskGraph("graph") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix2DFloat, vectorFloat) //
                .task("mv", ComputeTests::computeMatrixVector, matrix2DFloat, vectorFloat, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        computeMatrixVector(matrix2DFloat, vectorFloat, resultSeq);
        for (int i = 0; i < vectorFloat.size(); i++) {
            assertEquals(resultSeq.get(i), result.get(i), 0.01f);
        }
    }

    @Test
    public void matrixVectorFloat4() throws TornadoExecutionPlanException {
        int M = 2048;
        int N = 4096;

        Matrix2DFloat4 matrix2DFloat = new Matrix2DFloat4(M, N);
        VectorFloat4 vectorFloat = new VectorFloat4(N);
        VectorFloat result = new VectorFloat(M);

        Matrix2DFloat inputA = new Matrix2DFloat(M, N * 4);
        VectorFloat inputB = new VectorFloat(N * 4);
        VectorFloat resultSeq = new VectorFloat(M);

        Random r = new Random(11);
        // Init Data
        for (int i = 0; i < vectorFloat.getLength(); i++) {
            Float4 f = new Float4(0, 1, 2, 3);
            int indexI = i * 4;
            inputB.set(indexI, f.getX());
            inputB.set(indexI + 1, f.getY());
            inputB.set(indexI + 2, f.getZ());
            inputB.set(indexI + 3, f.getW());
            vectorFloat.set(i, f);
        }
        for (int i = 0; i < matrix2DFloat.getNumRows(); i++) {
            for (int j = 0; j < matrix2DFloat.getNumColumns(); j++) {
                Float4 f = new Float4(0, 1, 2, 3);
                matrix2DFloat.set(i, j, f);
                int indexJ = j * 4;
                inputA.set(i, indexJ, f.getX());
                inputA.set(i, indexJ + 1, f.getY());
                inputA.set(i, indexJ + 2, f.getZ());
                inputA.set(i, indexJ + 3, f.getW());
            }
        }

        TaskGraph taskGraph = new TaskGraph("graph") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix2DFloat, vectorFloat) //
                .task("mv", ComputeTests::computeMatrixVectorFloat4, matrix2DFloat, vectorFloat, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        computeMatrixVector(inputA, inputB, resultSeq);
        for (int i = 0; i < result.getLength(); i++) {
            assertEquals(resultSeq.get(i), result.get(i), 0.01f);
        }
    }

    @Test
    public void testHalfFloatMatrixMultiplication() throws TornadoExecutionPlanException {
        int N = 256;
        HalfFloatArray matrixA = new HalfFloatArray(N * N);
        HalfFloatArray matrixB = new HalfFloatArray(N * N);
        HalfFloatArray matrixCSeq = new HalfFloatArray(N * N);
        HalfFloatArray matrixC = new HalfFloatArray(N * N);

        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA.set(idx, new HalfFloat(2.5f));
            matrixB.set(idx, new HalfFloat(3.5f));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", ComputeTests::matrixMultiplicationHalfFloats, matrixA, matrixB, matrixC, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph)) {
            executor.execute();
        }

        matrixMultiplicationHalfFloats(matrixA, matrixB, matrixCSeq, N);

        for (int i = 0; i < N * N; i++) {
            assertEquals(matrixCSeq.get(i).getFloat32(), matrixC.get(i).getFloat32(), DELTA);
        }
    }

    @Test
    public void testHalfFloatToFloatMatrixMultiplication() throws TornadoExecutionPlanException {
        int N = 256;
        HalfFloatArray matrixA = new HalfFloatArray(N * N);
        HalfFloatArray matrixB = new HalfFloatArray(N * N);
        FloatArray matrixCSeq = new FloatArray(N * N);
        FloatArray matrixC = new FloatArray(N * N);

        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA.set(idx, new HalfFloat(2.5f));
            matrixB.set(idx, new HalfFloat(3.5f));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", ComputeTests::matrixMultiplicationHalfFloatToFloat, matrixA, matrixB, matrixC, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph)) {
            executor.execute();
        }

        matrixMultiplicationHalfFloatToFloat(matrixA, matrixB, matrixCSeq, N);

        for (int i = 0; i < N * N; i++) {
            assertEquals(matrixCSeq.get(i), matrixC.get(i), DELTA);
        }
    }

    @Test
    public void testHalfFloatToFloatMatrixMultiplication2() throws TornadoExecutionPlanException {
        int N = 256;
        HalfFloatArray matrixA = new HalfFloatArray(N * N);
        HalfFloatArray matrixB = new HalfFloatArray(N * N);
        FloatArray matrixCSeq = new FloatArray(N * N);
        FloatArray matrixC = new FloatArray(N * N);

        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA.set(idx, new HalfFloat(2.5f));
            matrixB.set(idx, new HalfFloat(3.5f));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", ComputeTests::matrixMultiplicationHalfFloatToFloat2, matrixA, matrixB, matrixC, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph)) {
            executor.execute();
        }

        matrixMultiplicationHalfFloatToFloat2(matrixA, matrixB, matrixCSeq, N);

        for (int i = 0; i < N * N; i++) {
            assertEquals(matrixCSeq.get(i), matrixC.get(i), DELTA);
        }
    }

    // CHECKSTYLE:ON
}
