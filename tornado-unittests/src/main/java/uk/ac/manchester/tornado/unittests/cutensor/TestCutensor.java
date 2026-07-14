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
package uk.ac.manchester.tornado.unittests.cutensor;

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
import uk.ac.manchester.tornado.cutensor.Cutensor;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Unit tests for the NVIDIA cuTENSOR library-task provider ({@code nvidia/cutensor}).
 * All contractions are FP32 and row-major. Tests cover matmul-as-contraction, a
 * genuine two-mode contraction, interleaving with JIT tasks, and CUDA-graph
 * capture. Skipped unless the default device is the CUDA backend and
 * libtornado-cutensor (plus libcutensor) is present.
 */
public class TestCutensor extends TornadoTestBase {

    private static final Random random = new Random(42);

    @Before
    public void cutensorMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "cuTENSOR library tasks require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, PTX, SPIRV, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        try {
            System.loadLibrary("tornado-cutensor");
        } catch (UnsatisfiedLinkError e) {
            throw new TornadoVMCUDANotSupported("libtornado-cutensor is not available: " + e.getMessage());
        }
    }

    private static FloatArray randomArray(int size) {
        FloatArray array = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            array.set(i, random.nextFloat() - 0.5f);
        }
        return array;
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

    /** C[m,n] = sum_k A[m,k] * B[k,n]. */
    @Test
    public void testContraction() throws TornadoExecutionPlanException {
        final int m = 128;
        final int n = 96;
        final int k = 64;
        FloatArray a = randomArray(m * k);
        FloatArray b = randomArray(k * n);
        FloatArray c = new FloatArray(m * n);

        FloatArray expected = new FloatArray(m * n);
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float acc = 0.0f;
                for (int p = 0; p < k; p++) {
                    acc += a.get(i * k + p) * b.get(p * n + j);
                }
                expected.set(i * n + j, acc);
            }
        }

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("contract", Cutensor::cutensorContraction, m, n, k, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.execute();
        }
        assertClose(m * n, expected, c, 1e-4f);
    }

    /** Two-mode contraction C[i,j] = sum_{k,l} A[i,k,l] * B[k,l,j]. */
    @Test
    public void testContraction2() throws TornadoExecutionPlanException {
        final int di = 24;
        final int dj = 20;
        final int dk = 8;
        final int dl = 6;
        FloatArray a = randomArray(di * dk * dl);
        FloatArray b = randomArray(dk * dl * dj);
        FloatArray c = new FloatArray(di * dj);

        FloatArray expected = new FloatArray(di * dj);
        for (int i = 0; i < di; i++) {
            for (int j = 0; j < dj; j++) {
                float acc = 0.0f;
                for (int k = 0; k < dk; k++) {
                    for (int l = 0; l < dl; l++) {
                        acc += a.get((i * dk + k) * dl + l) * b.get((k * dl + l) * dj + j);
                    }
                }
                expected.set(i * dj + j, acc);
            }
        }

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("contract2", Cutensor::cutensorContraction2, di, dj, dk, dl, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.execute();
        }
        assertClose(di * dj, expected, c, 1e-4f);
    }

    /** JIT pre-task -> cuTENSOR contraction -> JIT post-task, looped. */
    @Test
    public void testContractionWithJitPreAndPost() throws TornadoExecutionPlanException {
        final int m = 96;
        final int n = 80;
        final int k = 48;
        FloatArray a = randomArray(m * k);
        FloatArray b = randomArray(k * n);
        FloatArray c = new FloatArray(m * n);

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("pre", TestCutensor::addOne, a) //
                .libraryTask("contract", Cutensor::cutensorContraction, m, n, k, a, b, c) //
                .task("post", TestCutensor::addOne, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            for (int it = 0; it < 10; it++) {
                FloatArray aPlusOne = new FloatArray(m * k);
                for (int i = 0; i < m * k; i++) {
                    aPlusOne.set(i, a.get(i) + 1.0f);
                }
                FloatArray expected = new FloatArray(m * n);
                for (int i = 0; i < m; i++) {
                    for (int j = 0; j < n; j++) {
                        float acc = 0.0f;
                        for (int p = 0; p < k; p++) {
                            acc += aPlusOne.get(i * k + p) * b.get(p * n + j);
                        }
                        expected.set(i * n + j, acc + 1.0f);
                    }
                }
                plan.execute();
                assertClose(m * n, expected, c, 1e-4f);
            }
        }
    }

    /** Contraction under CUDA-graph capture, exercising the prepare() workspace hook. */
    @Test
    public void testContractionWithCudaGraph() throws TornadoExecutionPlanException {
        final int m = 128;
        final int n = 128;
        final int k = 128;
        FloatArray a = randomArray(m * k);
        FloatArray b = randomArray(k * n);
        FloatArray c = new FloatArray(m * n);

        FloatArray expected = new FloatArray(m * n);
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float acc = 0.0f;
                for (int p = 0; p < k; p++) {
                    acc += a.get(i * k + p) * b.get(p * n + j);
                }
                expected.set(i * n + j, acc);
            }
        }

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("contract", Cutensor::cutensorContraction, m, n, k, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.withCUDAGraph();
            for (int it = 0; it < 5; it++) {
                plan.execute();
                assertClose(m * n, expected, c, 1e-4f);
            }
        }
    }
}
