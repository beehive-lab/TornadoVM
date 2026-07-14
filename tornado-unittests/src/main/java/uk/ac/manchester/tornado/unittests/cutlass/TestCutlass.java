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
import static org.junit.Assert.fail;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
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

    // -------------------------------------------------------------------------
    // FP16 tensor-core kernels (require k, n multiples of 4).
    // -------------------------------------------------------------------------

    private static HalfFloatArray randomHalfArray(int size) {
        HalfFloatArray array = new HalfFloatArray(size);
        for (int i = 0; i < size; i++) {
            array.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }
        return array;
    }

    /** Row-major reference GEMM over the FP16 inputs, accumulated in float. */
    private static void hgemmJava(int m, int n, int k, HalfFloatArray a, HalfFloatArray b, float[] out) {
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float acc = 0.0f;
                for (int p = 0; p < k; p++) {
                    acc += a.get(i * k + p).getFloat32() * b.get(p * n + j).getFloat32();
                }
                out[i * n + j] = acc;
            }
        }
    }

    @Test
    public void testHgemm() throws TornadoExecutionPlanException {
        final int m = 128;
        final int n = 96;
        final int k = 64;
        HalfFloatArray a = randomHalfArray(m * k);
        HalfFloatArray b = randomHalfArray(k * n);
        HalfFloatArray d = new HalfFloatArray(m * n);

        float[] expected = new float[m * n];
        hgemmJava(m, n, k, a, b, expected);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("hgemm", Cutlass::cutlassHgemm, m, n, k, 1.0f, a, b, 0.0f, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, d);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < m * n; i++) {
            assertEquals(expected[i], d.get(i).getFloat32(), 2e-2f * Math.max(1.0f, Math.abs(expected[i])));
        }
    }

    @Test
    public void testGemmBiasRelu() throws TornadoExecutionPlanException {
        final int m = 64;
        final int n = 64;
        final int k = 64;
        HalfFloatArray a = randomHalfArray(m * k);
        HalfFloatArray b = randomHalfArray(k * n);
        HalfFloatArray bias = randomHalfArray(n);
        HalfFloatArray d = new HalfFloatArray(m * n);

        float[] gemm = new float[m * n];
        hgemmJava(m, n, k, a, b, gemm);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, bias) //
                .libraryTask("fused", Cutlass::cutlassGemmBiasRelu, m, n, k, a, b, bias, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, d);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float expected = Math.max(0.0f, gemm[i * n + j] + bias.get(j).getFloat32());
                assertEquals(expected, d.get(i * n + j).getFloat32(), 0.05f);
            }
        }
    }

    @Test
    public void testGemmBiasGelu() throws TornadoExecutionPlanException {
        final int m = 64;
        final int n = 64;
        final int k = 64;
        HalfFloatArray a = randomHalfArray(m * k);
        HalfFloatArray b = randomHalfArray(k * n);
        HalfFloatArray bias = randomHalfArray(n);
        HalfFloatArray d = new HalfFloatArray(m * n);

        float[] gemm = new float[m * n];
        hgemmJava(m, n, k, a, b, gemm);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, bias) //
                .libraryTask("fused", Cutlass::cutlassGemmBiasGelu, m, n, k, a, b, bias, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, d);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float x = gemm[i * n + j] + bias.get(j).getFloat32();
                float expected = 0.5f * x * (1.0f + (float) erf(x / Math.sqrt(2.0)));
                assertEquals(expected, d.get(i * n + j).getFloat32(), 0.05f);
            }
        }
    }

    /** Abramowitz-Stegun 7.1.26 erf approximation (reference only). */
    private static double erf(double x) {
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return Math.signum(x) * y;
    }

    @Test
    public void testHgemmBadAlignmentShape() {
        // k = 62 is not a multiple of 4: the factory must reject it up front.
        try {
            Cutlass.cutlassHgemm(64, 64, 62, 1.0f, new HalfFloatArray(64 * 62), new HalfFloatArray(62 * 64), 0.0f, new HalfFloatArray(64 * 64));
            fail("Expected a TornadoRuntimeException for k not a multiple of 4");
        } catch (TornadoRuntimeException e) {
            // expected
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

    // =========================================================================
    // Integration / stress tests: deeper interaction with the TornadoVM
    // pipeline - rectangular shapes, library-task -> library-task chains,
    // alternating JIT/CUTLASS stages, shared buffers across task graphs,
    // multiple shapes in one provider context, mixed precision, and repeated
    // execution.
    // =========================================================================

    public static void scale(FloatArray array, float factor) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, array.get(i) * factor);
        }
    }

    public static void reluF(FloatArray array) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, Math.max(0.0f, array.get(i)));
        }
    }

    /** Rectangular FP32 GEMM (m, n, k all different) to stress non-square tiling. */
    @Test
    public void testRectangularSgemm() throws TornadoExecutionPlanException {
        final int m = 200;
        final int n = 120;
        final int k = 88;
        FloatArray a = randomArray(m * k);
        FloatArray b = randomArray(k * n);
        FloatArray c = new FloatArray(m * n);

        FloatArray expected = new FloatArray(m * n);
        gemmJava(m, n, k, 1.0f, a, b, 0.0f, expected);

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("gemm", Cutlass::cutlassSgemm, m, n, k, 1.0f, a, b, 0.0f, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.execute();
        }
        assertClose(m * n, expected, c, 1e-4f);
    }

    /** Rectangular FP16 tensor-core GEMM (k, n multiples of 4). */
    @Test
    public void testRectangularHgemm() throws TornadoExecutionPlanException {
        final int m = 130;
        final int n = 96;
        final int k = 64;
        HalfFloatArray a = randomHalfArray(m * k);
        HalfFloatArray b = randomHalfArray(k * n);
        HalfFloatArray d = new HalfFloatArray(m * n);

        float[] expected = new float[m * n];
        hgemmJava(m, n, k, a, b, expected);

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("hgemm", Cutlass::cutlassHgemm, m, n, k, 1.0f, a, b, 0.0f, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, d);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < m * n; i++) {
            assertEquals(expected[i], d.get(i).getFloat32(), 2e-2f * Math.max(1.0f, Math.abs(expected[i])));
        }
    }

    /**
     * Two CUTLASS GEMM library tasks chained in one graph: {@code E = (A*B)*C}.
     * The intermediate is written by the first library task and read by the
     * second - stresses library-task -> library-task dependency handling.
     */
    @Test
    public void testChainedSgemm() throws TornadoExecutionPlanException {
        final int m = 64;
        final int n = 64;
        final int k = 64;
        FloatArray a = randomArray(m * k);
        FloatArray b = randomArray(k * n);
        FloatArray c = randomArray(n * n);
        FloatArray ab = new FloatArray(m * n);
        FloatArray e = new FloatArray(m * n);

        FloatArray refAb = new FloatArray(m * n);
        gemmJava(m, n, k, 1.0f, a, b, 0.0f, refAb);
        FloatArray expected = new FloatArray(m * n);
        gemmJava(m, n, n, 1.0f, refAb, c, 0.0f, expected);

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c) //
                .libraryTask("gemm1", Cutlass::cutlassSgemm, m, n, k, 1.0f, a, b, 0.0f, ab) //
                .libraryTask("gemm2", Cutlass::cutlassSgemm, m, n, n, 1.0f, ab, c, 0.0f, e) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, e);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.execute();
        }
        assertClose(m * n, expected, e, 1e-3f);
    }

    /**
     * Transformer-style two-layer FFN block built from two fused CUTLASS tasks:
     * {@code h = relu(x*W1 + b1)}, {@code y = gelu(h*W2 + b2)}. Exercises two
     * different fused epilogues chained, each needing a per-shape workspace in
     * the same provider context.
     */
    @Test
    public void testTwoLayerFusedMlp() throws TornadoExecutionPlanException {
        final int m = 64;
        final int k1 = 64;
        final int n1 = 128;
        final int n2 = 64;
        HalfFloatArray x = randomHalfArray(m * k1);
        HalfFloatArray w1 = randomHalfArray(k1 * n1);
        HalfFloatArray b1 = randomHalfArray(n1);
        HalfFloatArray h = new HalfFloatArray(m * n1);
        HalfFloatArray w2 = randomHalfArray(n1 * n2);
        HalfFloatArray b2 = randomHalfArray(n2);
        HalfFloatArray y = new HalfFloatArray(m * n2);

        TaskGraph g = new TaskGraph("mlp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, w1, b1, w2, b2) //
                .libraryTask("layer1", Cutlass::cutlassGemmBiasRelu, m, n1, k1, x, w1, b1, h) //
                .libraryTask("layer2", Cutlass::cutlassGemmBiasGelu, m, n2, n1, h, w2, b2, y) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.execute();
        }

        // Reference: layer1 relu, layer2 gelu (tanh-approx tolerant).
        float[] gemm1 = new float[m * n1];
        hgemmJava(m, n1, k1, x, w1, gemm1);
        HalfFloatArray hRef = new HalfFloatArray(m * n1);
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n1; j++) {
                hRef.set(i * n1 + j, new HalfFloat(Math.max(0.0f, gemm1[i * n1 + j] + b1.get(j).getFloat32())));
            }
        }
        float[] gemm2 = new float[m * n2];
        hgemmJava(m, n2, n1, hRef, w2, gemm2);
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n2; j++) {
                float xv = gemm2[i * n2 + j] + b2.get(j).getFloat32();
                float expected = 0.5f * xv * (1.0f + (float) erf(xv / Math.sqrt(2.0)));
                assertEquals(expected, y.get(i * n2 + j).getFloat32(), 0.1f);
            }
        }
    }

    /**
     * Deep alternating pipeline JIT -> CUTLASS -> JIT -> CUTLASS -> JIT sharing
     * TornadoVM-managed device buffers throughout.
     * {@code relu( ((scale(A,0.5) * B) + 1) * C )}.
     */
    @Test
    public void testDeepMixedPipeline() throws TornadoExecutionPlanException {
        final int m = 64;
        final int n = 64;
        final int k = 64;
        FloatArray a = randomArray(m * k);
        FloatArray b = randomArray(k * n);
        FloatArray c = randomArray(n * n);
        FloatArray t1 = new FloatArray(m * n);
        FloatArray t2 = new FloatArray(m * n);

        TaskGraph g = new TaskGraph("deep") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c) //
                .task("scale", TestCutlass::scale, a, 0.5f) //
                .libraryTask("gemm1", Cutlass::cutlassSgemm, m, n, k, 1.0f, a, b, 0.0f, t1) //
                .task("addOne", TestCutlass::addOne, t1) //
                .libraryTask("gemm2", Cutlass::cutlassSgemm, m, n, n, 1.0f, t1, c, 0.0f, t2) //
                .task("relu", TestCutlass::reluF, t2) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, t2);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.execute();
        }

        // Reference follows the same order. The device-side "scale" task halves
        // a, but a is not transferred back to the host, so the host copy is still
        // pristine - apply the 0.5 factor here.
        FloatArray aScaled = new FloatArray(m * k);
        for (int i = 0; i < m * k; i++) {
            aScaled.set(i, a.get(i) * 0.5f);
        }
        FloatArray refT1 = new FloatArray(m * n);
        gemmJava(m, n, k, 1.0f, aScaled, b, 0.0f, refT1);
        for (int i = 0; i < m * n; i++) {
            refT1.set(i, refT1.get(i) + 1.0f);
        }
        FloatArray refT2 = new FloatArray(m * n);
        gemmJava(m, n, n, 1.0f, refT1, c, 0.0f, refT2);
        for (int i = 0; i < m * n; i++) {
            assertEquals(Math.max(0.0f, refT2.get(i)), t2.get(i), 1e-3f * Math.max(1.0f, Math.abs(refT2.get(i))));
        }
    }

    /**
     * A CUTLASS GEMM result persisted on the device by one task graph and
     * consumed by a second graph's CUTLASS GEMM, without a host round trip.
     */
    @Test
    public void testSharedBufferAcrossTaskGraphs() throws TornadoExecutionPlanException {
        final int m = 64;
        final int n = 64;
        final int k = 64;
        FloatArray a = randomArray(m * k);
        FloatArray b = randomArray(k * n);
        FloatArray c = randomArray(n * n);
        FloatArray ab = new FloatArray(m * n);
        FloatArray e = new FloatArray(m * n);

        TaskGraph producer = new TaskGraph("producer") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .libraryTask("gemm1", Cutlass::cutlassSgemm, m, n, k, 1.0f, a, b, 0.0f, ab) //
                .persistOnDevice(ab);

        TaskGraph consumer = new TaskGraph("consumer") //
                .consumeFromDevice(producer.getTaskGraphName(), ab) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, c) //
                .libraryTask("gemm2", Cutlass::cutlassSgemm, m, n, n, 1.0f, ab, c, 0.0f, e) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, e);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(producer.snapshot(), consumer.snapshot())) {
            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
        }

        FloatArray refAb = new FloatArray(m * n);
        gemmJava(m, n, k, 1.0f, a, b, 0.0f, refAb);
        FloatArray expected = new FloatArray(m * n);
        gemmJava(m, n, n, 1.0f, refAb, c, 0.0f, expected);
        assertClose(m * n, expected, e, 1e-3f);
    }

    /**
     * Three CUTLASS GEMMs of different shapes in one graph - the provider's
     * per-plan context and grow-only workspace must serve every shape.
     */
    @Test
    public void testMultipleShapesOneContext() throws TornadoExecutionPlanException {
        int[][] shapes = { { 64, 64, 64 }, { 128, 96, 64 }, { 96, 128, 96 } };
        for (int[] s : shapes) {
            int m = s[0];
            int n = s[1];
            int k = s[2];
            FloatArray a = randomArray(m * k);
            FloatArray b = randomArray(k * n);
            FloatArray c = new FloatArray(m * n);
            FloatArray expected = new FloatArray(m * n);
            gemmJava(m, n, k, 1.0f, a, b, 0.0f, expected);

            String id = m + "x" + n + "x" + k;
            TaskGraph g = new TaskGraph(id) //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                    .libraryTask("gemm", Cutlass::cutlassSgemm, m, n, k, 1.0f, a, b, 0.0f, c) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
                plan.execute();
            }
            assertClose(m * n, expected, c, 1e-4f);
        }
    }

    /** FP32 (SIMT) and FP16 (tensor-core) library tasks in the same graph. */
    @Test
    public void testMixedPrecisionGraph() throws TornadoExecutionPlanException {
        final int m = 64;
        final int n = 64;
        final int k = 64;
        FloatArray a = randomArray(m * k);
        FloatArray b = randomArray(k * n);
        FloatArray cF = new FloatArray(m * n);
        HalfFloatArray aH = randomHalfArray(m * k);
        HalfFloatArray bH = randomHalfArray(k * n);
        HalfFloatArray dH = new HalfFloatArray(m * n);

        TaskGraph g = new TaskGraph("mixed") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, aH, bH) //
                .libraryTask("sgemm", Cutlass::cutlassSgemm, m, n, k, 1.0f, a, b, 0.0f, cF) //
                .libraryTask("hgemm", Cutlass::cutlassHgemm, m, n, k, 1.0f, aH, bH, 0.0f, dH) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cF, dH);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.execute();
        }

        FloatArray expF = new FloatArray(m * n);
        gemmJava(m, n, k, 1.0f, a, b, 0.0f, expF);
        assertClose(m * n, expF, cF, 1e-4f);
        float[] expH = new float[m * n];
        hgemmJava(m, n, k, aH, bH, expH);
        for (int i = 0; i < m * n; i++) {
            assertEquals(expH[i], dH.get(i).getFloat32(), 2e-2f * Math.max(1.0f, Math.abs(expH[i])));
        }
    }

    /** Repeated execution of one plan (buffer reuse) stays numerically stable. */
    @Test
    public void testRepeatedExecutionStability() throws TornadoExecutionPlanException {
        final int m = 128;
        final int n = 128;
        final int k = 128;
        FloatArray a = randomArray(m * k);
        FloatArray b = randomArray(k * n);
        FloatArray c = new FloatArray(m * n);

        FloatArray expected = new FloatArray(m * n);
        gemmJava(m, n, k, 1.0f, a, b, 0.0f, expected);

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .libraryTask("gemm", Cutlass::cutlassSgemm, m, n, k, 1.0f, a, b, 0.0f, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            for (int it = 0; it < 50; it++) {
                plan.execute();
                assertClose(m * n, expected, c, 1e-4f);
            }
        }
    }

    /** Fused GEMM+bias+ReLU under CUDA-graph capture, replayed. */
    @Test
    public void testFusedMlpWithCudaGraph() throws TornadoExecutionPlanException {
        final int m = 64;
        final int n = 128;
        final int k = 64;
        HalfFloatArray a = randomHalfArray(m * k);
        HalfFloatArray b = randomHalfArray(k * n);
        HalfFloatArray bias = randomHalfArray(n);
        HalfFloatArray d = new HalfFloatArray(m * n);

        float[] gemm = new float[m * n];
        hgemmJava(m, n, k, a, b, gemm);

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, bias) //
                .libraryTask("fused", Cutlass::cutlassGemmBiasRelu, m, n, k, a, b, bias, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, d);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.withCUDAGraph();
            for (int it = 0; it < 5; it++) {
                plan.execute();
                for (int i = 0; i < m; i++) {
                    for (int j = 0; j < n; j++) {
                        float expected = Math.max(0.0f, gemm[i * n + j] + bias.get(j).getFloat32());
                        assertEquals("iteration " + it, expected, d.get(i * n + j).getFloat32(), 0.05f);
                    }
                }
            }
        }
    }
}
