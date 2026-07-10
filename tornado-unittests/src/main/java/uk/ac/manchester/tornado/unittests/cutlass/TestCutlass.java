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
package uk.ac.manchester.tornado.unittests.cutlass;

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
import uk.ac.manchester.tornado.cutlass.Cutlass;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Unit tests for the NVIDIA CUTLASS library-task provider ({@code nvidia/cutlass}).
 * All GEMMs are row-major ({@code C = alpha*A*B + beta*C}); tests validate the
 * kernel in isolation, interleaved with JIT-compiled tasks, and under CUDA-graph
 * capture. Skipped unless the default device is the CUDA backend and
 * libtornado-cutlass is present.
 */
public class TestCutlass extends TornadoTestBase {

    private static final Random random = new Random(42);

    @Before
    public void cutlassMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "CUTLASS library tasks require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, PTX, SPIRV, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        try {
            System.loadLibrary("tornado-cutlass");
        } catch (UnsatisfiedLinkError e) {
            throw new TornadoVMCUDANotSupported("libtornado-cutlass is not available: " + e.getMessage());
        }
    }

    private static FloatArray randomArray(int size) {
        FloatArray array = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            array.set(i, random.nextFloat() - 0.5f);
        }
        return array;
    }

    /** Row-major reference GEMM: C = alpha*A*B + beta*C. */
    private static void gemmJava(int m, int n, int k, float alpha, FloatArray a, FloatArray b, float beta, FloatArray c) {
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float acc = 0.0f;
                for (int p = 0; p < k; p++) {
                    acc += a.get(i * k + p) * b.get(p * n + j);
                }
                c.set(i * n + j, alpha * acc + beta * c.get(i * n + j));
            }
        }
    }

    private static void assertClose(int size, FloatArray expected, FloatArray actual, float relTol) {
        for (int i = 0; i < size; i++) {
            float e = expected.get(i);
            assertEquals(e, actual.get(i), relTol * Math.max(1.0f, Math.abs(e)));
        }
    }

    public static void addOne(FloatArray array) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, array.get(i) + 1.0f);
        }
    }

    @Test
    public void testSgemm() throws TornadoExecutionPlanException {
        final int m = 128;
        final int n = 96;
        final int k = 64;
        FloatArray a = randomArray(m * k);
        FloatArray b = randomArray(k * n);
        FloatArray c = new FloatArray(m * n);

        FloatArray expected = new FloatArray(m * n);
        gemmJava(m, n, k, 1.0f, a, b, 0.0f, expected);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("gemm", Cutlass::cutlassSgemm, m, n, k, 1.0f, a, b, 0.0f, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }
        assertClose(m * n, expected, c, 1e-4f);
    }

    @Test
    public void testSgemmBeta() throws TornadoExecutionPlanException {
        final int m = 64;
        final int n = 64;
        final int k = 64;
        final float alpha = 2.0f;
        final float beta = 3.0f;
        FloatArray a = randomArray(m * k);
        FloatArray b = randomArray(k * n);
        FloatArray c = randomArray(m * n);

        FloatArray expected = new FloatArray(m * n);
        for (int i = 0; i < m * n; i++) {
            expected.set(i, c.get(i));
        }
        gemmJava(m, n, k, alpha, a, b, beta, expected);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c) //
                .libraryTask("gemm", Cutlass::cutlassSgemm, m, n, k, alpha, a, b, beta, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }
        assertClose(m * n, expected, c, 1e-4f);
    }

    /**
     * Mixed graph: JIT pre-task mutates A, CUTLASS GEMM, JIT post-task mutates
     * the result - the whole pipeline sharing TornadoVM-managed device buffers,
     * looped to exercise buffer reuse.
     */
    @Test
    public void testSgemmWithJitPreAndPost() throws TornadoExecutionPlanException {
        final int m = 96;
        final int n = 80;
        final int k = 48;
        FloatArray a = randomArray(m * k);
        FloatArray b = randomArray(k * n);
        FloatArray c = new FloatArray(m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("pre", TestCutlass::addOne, a) //
                .libraryTask("gemm", Cutlass::cutlassSgemm, m, n, k, 1.0f, a, b, 0.0f, c) //
                .task("post", TestCutlass::addOne, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int it = 0; it < 10; it++) {
                // Reset inputs each iteration: EVERY_EXECUTION re-copies a and b, but
                // the JIT pre-task mutates the host copy of a, so restore it here.
                for (int i = 0; i < m * k; i++) {
                    a.set(i, ((i * 31 + 7) % 97) / 97.0f - 0.5f);
                }
                FloatArray aPlusOne = new FloatArray(m * k);
                for (int i = 0; i < m * k; i++) {
                    aPlusOne.set(i, a.get(i) + 1.0f);
                }
                FloatArray expected = new FloatArray(m * n);
                gemmJava(m, n, k, 1.0f, aPlusOne, b, 0.0f, expected);
                for (int i = 0; i < m * n; i++) {
                    expected.set(i, expected.get(i) + 1.0f);
                }

                plan.execute();
                assertClose(m * n, expected, c, 1e-4f);
            }
        }
    }

    /** Same GEMM under CUDA-graph capture, exercising the prepare() workspace hook. */
    @Test
    public void testGemmWithCudaGraph() throws TornadoExecutionPlanException {
        final int m = 128;
        final int n = 128;
        final int k = 128;
        FloatArray a = randomArray(m * k);
        FloatArray b = randomArray(k * n);
        FloatArray c = new FloatArray(m * n);

        FloatArray expected = new FloatArray(m * n);
        gemmJava(m, n, k, 1.0f, a, b, 0.0f, expected);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("gemm", Cutlass::cutlassSgemm, m, n, k, 1.0f, a, b, 0.0f, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withCUDAGraph();
            for (int it = 0; it < 5; it++) {
                plan.execute();
                assertClose(m * n, expected, c, 1e-4f);
            }
        }
    }
}
