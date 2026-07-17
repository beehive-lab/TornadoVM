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
package uk.ac.manchester.tornado.unittests.cublas;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.FP8;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.cublas.CuBlasLt;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Unit tests for cuBLASLt library tasks (matmul with fused epilogues).
 * Skipped ([UNSUPPORTED]) unless the default device is on the CUDA backend and
 * libtornado-cublas is loadable.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.cublas.TestCuBlasLt
 * </code>
 */
public class TestCuBlasLt extends TornadoTestBase {

    private static final int SIZE = 128;
    private static final Random random = new Random(42);

    @Before
    public void cuBlasLtMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "cuBLASLt library tasks require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, PTX, SPIRV, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        try {
            System.loadLibrary("tornado-cublas");
        } catch (UnsatisfiedLinkError e) {
            throw new TornadoVMCUDANotSupported("libtornado-cublas is not available: " + e.getMessage());
        }
    }

    /** GELU with the tanh approximation, as used by the cuBLASLt epilogue. */
    private static float geluTanh(float x) {
        return 0.5f * x * (1.0f + (float) Math.tanh(0.7978845608f * (x + 0.044715f * x * x * x)));
    }

    /**
     * Row-major reference: C = A * B (+ bias per column) (+ GELU). The GPU call
     * uses the column-major trick (C_cm = B_cm * A_cm), where the cuBLAS bias
     * (per row of C_cm) is per column of the row-major C.
     */
    private static void reference(FloatArray a, FloatArray b, FloatArray c, float[] bias, boolean gelu, int size) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int p = 0; p < size; p++) {
                    sum += a.get(i * size + p) * b.get(p * size + j);
                }
                if (bias != null) {
                    sum += bias[j];
                }
                c.set(i * size + j, gelu ? geluTanh(sum) : sum);
            }
        }
    }

    @Test
    public void testLtMatmulFP32() throws TornadoExecutionPlanException {
        FloatArray matrixA = new FloatArray(SIZE * SIZE);
        FloatArray matrixB = new FloatArray(SIZE * SIZE);
        FloatArray matrixC = new FloatArray(SIZE * SIZE);
        FloatArray expected = new FloatArray(SIZE * SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrixA.set(i, random.nextFloat() - 0.5f);
            matrixB.set(i, random.nextFloat() - 0.5f);
        }

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB) //
                .libraryTask("lt", CuBlasLt::ltMatmulFP32, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        SIZE, SIZE, SIZE, 1.0f, matrixB, SIZE, matrixA, SIZE, 0.0f, matrixC, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        reference(matrixA, matrixB, expected, null, false, SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            assertEquals(expected.get(i), matrixC.get(i), 0.01f * Math.max(1.0f, Math.abs(expected.get(i))));
        }
    }

    /**
     * FP8 (E4M3) matmul on the GPU's FP8 tensor cores via cuBLASLt: A, B as one-byte E4M3 in a
     * {@link ByteArray}, C in FP16. cuBLASLt FP8 requires the TN form (op(A) = A^T, op(B) = B), so
     * for square, column-major {@code lda=ldb=ldc=FP8}: {@code C[i+j*N] = sum_l A[l+i*N]*B[l+j*N]}.
     * Inputs are exactly-FP8-representable values, so the only error is the FP16 store of C.
     */
    @Test
    public void testLtMatmulFP8() throws TornadoExecutionPlanException {
        final int fp8 = 64; // multiple of 16 (FP8 leading-dim alignment)
        // Exact E4M3 values -> decoded inputs carry no quantization error.
        float[] palette = { -2.0f, -1.0f, -0.5f, 0.5f, 1.0f, 2.0f };
        ByteArray a = new ByteArray(fp8 * fp8);
        ByteArray b = new ByteArray(fp8 * fp8);
        float[] aF = new float[fp8 * fp8];
        float[] bF = new float[fp8 * fp8];
        for (int i = 0; i < fp8 * fp8; i++) {
            float va = palette[random.nextInt(palette.length)];
            float vb = palette[random.nextInt(palette.length)];
            a.set(i, FP8.e4m3FromFloat(va));
            b.set(i, FP8.e4m3FromFloat(vb));
            aF[i] = FP8.e4m3ToFloat(a.get(i));
            bF[i] = FP8.e4m3ToFloat(b.get(i));
        }
        HalfFloatArray c = new HalfFloatArray(fp8 * fp8);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("ltFP8", CuBlasLt::ltMatmulFP8, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        fp8, fp8, fp8, 1.0f, a, fp8, b, fp8, 0.0f, c, fp8) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < fp8; i++) {
            for (int j = 0; j < fp8; j++) {
                float sum = 0.0f;
                for (int l = 0; l < fp8; l++) {
                    sum += aF[l + i * fp8] * bF[l + j * fp8];
                }
                assertEquals(sum, c.get(i + j * fp8).getFloat32(), 1e-2f * Math.max(1.0f, Math.abs(sum)));
            }
        }
    }

    @Test
    public void testLtMatmulFP16() throws TornadoExecutionPlanException {
        HalfFloatArray matrixA = new HalfFloatArray(SIZE * SIZE);
        HalfFloatArray matrixB = new HalfFloatArray(SIZE * SIZE);
        HalfFloatArray matrixC = new HalfFloatArray(SIZE * SIZE);
        FloatArray aF = new FloatArray(SIZE * SIZE);
        FloatArray bF = new FloatArray(SIZE * SIZE);
        FloatArray expected = new FloatArray(SIZE * SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrixA.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            matrixB.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            aF.set(i, matrixA.get(i).getFloat32());
            bF.set(i, matrixB.get(i).getFloat32());
        }

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB) //
                .libraryTask("lt", CuBlasLt::ltMatmulFP16, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        SIZE, SIZE, SIZE, 1.0f, matrixB, SIZE, matrixA, SIZE, 0.0f, matrixC, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        reference(aF, bF, expected, null, false, SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            assertEquals(expected.get(i), matrixC.get(i).getFloat32(), 2e-2f * Math.max(1.0f, Math.abs(expected.get(i))));
        }
    }

    @Test
    public void testLtMatmulBiasFP16() throws TornadoExecutionPlanException {
        HalfFloatArray matrixA = new HalfFloatArray(SIZE * SIZE);
        HalfFloatArray matrixB = new HalfFloatArray(SIZE * SIZE);
        HalfFloatArray matrixC = new HalfFloatArray(SIZE * SIZE);
        HalfFloatArray bias = new HalfFloatArray(SIZE);
        FloatArray aF = new FloatArray(SIZE * SIZE);
        FloatArray bF = new FloatArray(SIZE * SIZE);
        float[] biasF = new float[SIZE];
        FloatArray expected = new FloatArray(SIZE * SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrixA.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            matrixB.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            aF.set(i, matrixA.get(i).getFloat32());
            bF.set(i, matrixB.get(i).getFloat32());
        }
        for (int i = 0; i < SIZE; i++) {
            bias.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            biasF[i] = bias.get(i).getFloat32();
        }

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB, bias) //
                .libraryTask("lt", CuBlasLt::ltMatmulBiasFP16, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        SIZE, SIZE, SIZE, 1.0f, matrixB, SIZE, matrixA, SIZE, 0.0f, matrixC, SIZE, bias) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        reference(aF, bF, expected, biasF, false, SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            assertEquals(expected.get(i), matrixC.get(i).getFloat32(), 2e-2f * Math.max(1.0f, Math.abs(expected.get(i))));
        }
    }

    @Test
    public void testLtMatmulGeluBiasFP16() throws TornadoExecutionPlanException {
        HalfFloatArray matrixA = new HalfFloatArray(SIZE * SIZE);
        HalfFloatArray matrixB = new HalfFloatArray(SIZE * SIZE);
        HalfFloatArray matrixC = new HalfFloatArray(SIZE * SIZE);
        HalfFloatArray bias = new HalfFloatArray(SIZE);
        FloatArray aF = new FloatArray(SIZE * SIZE);
        FloatArray bF = new FloatArray(SIZE * SIZE);
        float[] biasF = new float[SIZE];
        FloatArray expected = new FloatArray(SIZE * SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrixA.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            matrixB.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            aF.set(i, matrixA.get(i).getFloat32());
            bF.set(i, matrixB.get(i).getFloat32());
        }
        for (int i = 0; i < SIZE; i++) {
            bias.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            biasF[i] = bias.get(i).getFloat32();
        }

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB, bias) //
                .libraryTask("lt", CuBlasLt::ltMatmulGeluBiasFP16, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        SIZE, SIZE, SIZE, 1.0f, matrixB, SIZE, matrixA, SIZE, 0.0f, matrixC, SIZE, bias) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        reference(aF, bF, expected, biasF, true, SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            assertEquals(expected.get(i), matrixC.get(i).getFloat32(), 2e-2f * Math.max(0.1f, Math.abs(expected.get(i))));
        }
    }

    @Test
    public void testLtPlanCacheReuse() throws TornadoExecutionPlanException {
        // Repeated executions of the same shape must reuse the cached plan and
        // keep producing correct results.
        FloatArray matrixA = new FloatArray(SIZE * SIZE);
        FloatArray matrixB = new FloatArray(SIZE * SIZE);
        FloatArray matrixC = new FloatArray(SIZE * SIZE);
        FloatArray expected = new FloatArray(SIZE * SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrixA.set(i, random.nextFloat() - 0.5f);
            matrixB.set(i, random.nextFloat() - 0.5f);
        }

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB) //
                .libraryTask("lt", CuBlasLt::ltMatmulFP32, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        SIZE, SIZE, SIZE, 1.0f, matrixB, SIZE, matrixA, SIZE, 0.0f, matrixC, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int it = 0; it < 5; it++) {
                plan.execute();
            }
        }

        reference(matrixA, matrixB, expected, null, false, SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            assertEquals(expected.get(i), matrixC.get(i), 0.01f * Math.max(1.0f, Math.abs(expected.get(i))));
        }
    }
}
