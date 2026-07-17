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
package uk.ac.manchester.tornado.unittests.cudnn;

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
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.cudnn.CuDnn;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Unit tests for cuDNN library tasks (FP32, NCHW). Skipped ([UNSUPPORTED])
 * unless the default device is on the CUDA backend and libtornado-cudnn is
 * loadable.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.cudnn.TestCuDnn
 * </code>
 */
public class TestCuDnn extends TornadoTestBase {

    private static final Random random = new Random(42);

    @Before
    public void cuDnnMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "cuDNN library tasks require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, PTX, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        try {
            System.loadLibrary("tornado-cudnn");
        } catch (UnsatisfiedLinkError e) {
            throw new TornadoVMCUDANotSupported("libtornado-cudnn is not available: " + e.getMessage());
        }
    }

    private static FloatArray randomArray(int size) {
        FloatArray array = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            array.set(i, random.nextFloat() - 0.5f);
        }
        return array;
    }

    public static void addOne(FloatArray array) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, array.get(i) + 1.0f);
        }
    }

    @Test
    public void testSoftmax() throws TornadoExecutionPlanException {
        final int rows = 64;
        final int cols = 128;
        FloatArray input = randomArray(rows * cols);
        FloatArray output = new FloatArray(rows * cols);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .libraryTask("softmax", CuDnn::cudnnSoftmax, input, output, rows, cols) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < rows; i++) {
            float max = Float.NEGATIVE_INFINITY;
            for (int j = 0; j < cols; j++) {
                max = Math.max(max, input.get(i * cols + j));
            }
            float sum = 0.0f;
            for (int j = 0; j < cols; j++) {
                sum += (float) Math.exp(input.get(i * cols + j) - max);
            }
            for (int j = 0; j < cols; j++) {
                float expected = (float) Math.exp(input.get(i * cols + j) - max) / sum;
                assertEquals(expected, output.get(i * cols + j), 1e-5f);
            }
        }
    }

    @Test
    public void testRelu() throws TornadoExecutionPlanException {
        final int size = 4096;
        FloatArray input = randomArray(size);
        FloatArray output = new FloatArray(size);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .libraryTask("relu", CuDnn::cudnnRelu, input, output, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(Math.max(0.0f, input.get(i)), output.get(i), 0.0f);
        }
    }

    @Test
    public void testTanh() throws TornadoExecutionPlanException {
        final int size = 4096;
        FloatArray input = randomArray(size);
        FloatArray output = new FloatArray(size);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .libraryTask("tanh", CuDnn::cudnnTanh, input, output, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals((float) Math.tanh(input.get(i)), output.get(i), 1e-6f);
        }
    }

    @Test
    public void testMaxPool2d() throws TornadoExecutionPlanException {
        final int n = 2;
        final int c = 3;
        final int h = 16;
        final int w = 16;
        final int window = 2;
        final int stride = 2;
        final int outH = (h - window) / stride + 1;
        final int outW = (w - window) / stride + 1;

        FloatArray input = randomArray(n * c * h * w);
        FloatArray output = new FloatArray(n * c * outH * outW);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .libraryTask("pool", CuDnn::cudnnMaxPool2d, input, output, n, c, h, w, window, stride) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int in = 0; in < n; in++) {
            for (int ic = 0; ic < c; ic++) {
                for (int oh = 0; oh < outH; oh++) {
                    for (int ow = 0; ow < outW; ow++) {
                        float max = Float.NEGATIVE_INFINITY;
                        for (int kh = 0; kh < window; kh++) {
                            for (int kw = 0; kw < window; kw++) {
                                int ih = oh * stride + kh;
                                int iw = ow * stride + kw;
                                max = Math.max(max, input.get(((in * c + ic) * h + ih) * w + iw));
                            }
                        }
                        assertEquals(max, output.get(((in * c + ic) * outH + oh) * outW + ow), 0.0f);
                    }
                }
            }
        }
    }

    private static void convJava(FloatArray input, FloatArray filter, FloatArray expected, int n, int c, int h, int w, int k, int r, int s, int pad, int stride) {
        int outH = (h + 2 * pad - r) / stride + 1;
        int outW = (w + 2 * pad - s) / stride + 1;
        for (int in = 0; in < n; in++) {
            for (int ok = 0; ok < k; ok++) {
                for (int oh = 0; oh < outH; oh++) {
                    for (int ow = 0; ow < outW; ow++) {
                        float sum = 0.0f;
                        for (int ic = 0; ic < c; ic++) {
                            for (int fr = 0; fr < r; fr++) {
                                for (int fs = 0; fs < s; fs++) {
                                    int ih = oh * stride + fr - pad;
                                    int iw = ow * stride + fs - pad;
                                    if (ih >= 0 && ih < h && iw >= 0 && iw < w) {
                                        sum += input.get(((in * c + ic) * h + ih) * w + iw) * filter.get(((ok * c + ic) * r + fr) * s + fs);
                                    }
                                }
                            }
                        }
                        expected.set(((in * k + ok) * outH + oh) * outW + ow, sum);
                    }
                }
            }
        }
    }

    @Test
    public void testConv2d() throws TornadoExecutionPlanException {
        final int n = 2;
        final int c = 3;
        final int h = 16;
        final int w = 16;
        final int k = 4;
        final int r = 3;
        final int s = 3;
        final int pad = 1;
        final int stride = 1;
        final int outH = (h + 2 * pad - r) / stride + 1;
        final int outW = (w + 2 * pad - s) / stride + 1;

        FloatArray input = randomArray(n * c * h * w);
        FloatArray filter = randomArray(k * c * r * s);
        FloatArray output = new FloatArray(n * k * outH * outW);
        FloatArray expected = new FloatArray(n * k * outH * outW);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, filter) //
                .libraryTask("conv", CuDnn::cudnnConv2d, input, filter, output, n, c, h, w, k, r, s, pad, stride) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        convJava(input, filter, expected, n, c, h, w, k, r, s, pad, stride);
        for (int i = 0; i < expected.getSize(); i++) {
            assertEquals(expected.get(i), output.get(i), 1e-3f * Math.max(1.0f, Math.abs(expected.get(i))));
        }
    }

    /**
     * CNN-style mixed pipeline: cuDNN convolution -> JIT bias-like task ->
     * cuDNN ReLU -> cuDNN max pooling, all on shared device buffers.
     */
    @Test
    public void testConvReluPoolPipeline() throws TornadoExecutionPlanException {
        final int n = 1;
        final int c = 3;
        final int h = 16;
        final int w = 16;
        final int k = 4;
        final int r = 3;
        final int s = 3;
        final int pad = 1;
        final int stride = 1;
        final int outH = h;
        final int outW = w;
        final int pooledH = outH / 2;
        final int pooledW = outW / 2;

        FloatArray input = randomArray(n * c * h * w);
        FloatArray filter = randomArray(k * c * r * s);
        FloatArray convOut = new FloatArray(n * k * outH * outW);
        FloatArray reluOut = new FloatArray(n * k * outH * outW);
        FloatArray pooled = new FloatArray(n * k * pooledH * pooledW);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, filter) //
                .libraryTask("conv", CuDnn::cudnnConv2d, input, filter, convOut, n, c, h, w, k, r, s, pad, stride) //
                .task("bias", TestCuDnn::addOne, convOut) //
                .libraryTask("relu", CuDnn::cudnnRelu, convOut, reluOut, n * k * outH * outW) //
                .libraryTask("pool", CuDnn::cudnnMaxPool2d, reluOut, pooled, n, k, outH, outW, 2, 2) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, pooled);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        // Java reference: conv + 1, relu, maxpool 2x2
        FloatArray expectedConv = new FloatArray(n * k * outH * outW);
        convJava(input, filter, expectedConv, n, c, h, w, k, r, s, pad, stride);
        for (int i = 0; i < expectedConv.getSize(); i++) {
            expectedConv.set(i, Math.max(0.0f, expectedConv.get(i) + 1.0f));
        }
        for (int ik = 0; ik < n * k; ik++) {
            for (int oh = 0; oh < pooledH; oh++) {
                for (int ow = 0; ow < pooledW; ow++) {
                    float max = Float.NEGATIVE_INFINITY;
                    for (int kh = 0; kh < 2; kh++) {
                        for (int kw = 0; kw < 2; kw++) {
                            max = Math.max(max, expectedConv.get((ik * outH + oh * 2 + kh) * outW + ow * 2 + kw));
                        }
                    }
                    assertEquals(max, pooled.get((ik * pooledH + oh) * pooledW + ow), 1e-3f * Math.max(1.0f, Math.abs(max)));
                }
            }
        }
    }

    /**
     * Reference attention on FP16-rounded inputs, FP32 math:
     * O = softmax(Q K^T * scale [+ causal mask]) V, BHSD packed.
     */
    private static void attentionJava(HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, FloatArray out, int b, int h, int s, int d, float scale, boolean causal) {
        for (int ib = 0; ib < b; ib++) {
            for (int ih = 0; ih < h; ih++) {
                int base = (ib * h + ih) * s * d;
                float[][] scores = new float[s][s];
                for (int i = 0; i < s; i++) {
                    for (int j = 0; j < s; j++) {
                        if (causal && j > i) {
                            scores[i][j] = Float.NEGATIVE_INFINITY;
                            continue;
                        }
                        float sum = 0.0f;
                        for (int dd = 0; dd < d; dd++) {
                            sum += q.get(base + i * d + dd).getFloat32() * k.get(base + j * d + dd).getFloat32();
                        }
                        scores[i][j] = sum * scale;
                    }
                }
                for (int i = 0; i < s; i++) {
                    float max = Float.NEGATIVE_INFINITY;
                    for (int j = 0; j < s; j++) {
                        max = Math.max(max, scores[i][j]);
                    }
                    float sum = 0.0f;
                    for (int j = 0; j < s; j++) {
                        scores[i][j] = (float) Math.exp(scores[i][j] - max);
                        sum += scores[i][j];
                    }
                    for (int dd = 0; dd < d; dd++) {
                        float acc = 0.0f;
                        for (int j = 0; j < s; j++) {
                            acc += (scores[i][j] / sum) * v.get(base + j * d + dd).getFloat32();
                        }
                        out.set(base + i * d + dd, acc);
                    }
                }
            }
        }
    }

    private static HalfFloatArray randomHalfArray(int size) {
        HalfFloatArray array = new HalfFloatArray(size);
        for (int i = 0; i < size; i++) {
            array.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }
        return array;
    }

    private void runSdpa(boolean causal) throws TornadoExecutionPlanException {
        final int b = 2;
        final int h = 4;
        final int s = 64;
        final int d = 64;
        final float scale = (float) (1.0 / Math.sqrt(d));

        HalfFloatArray q = randomHalfArray(b * h * s * d);
        HalfFloatArray k = randomHalfArray(b * h * s * d);
        HalfFloatArray v = randomHalfArray(b * h * s * d);
        HalfFloatArray o = new HalfFloatArray(b * h * s * d);
        FloatArray expected = new FloatArray(b * h * s * d);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, q, k, v) //
                .libraryTask("sdpa", CuDnn::sdpaForward, q, k, v, o, b, h, s, s, d, scale, causal) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, o);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        attentionJava(q, k, v, expected, b, h, s, d, scale, causal);
        for (int i = 0; i < expected.getSize(); i++) {
            assertEquals(expected.get(i), o.get(i).getFloat32(), 2e-2f * Math.max(0.1f, Math.abs(expected.get(i))));
        }
    }

    @Test
    public void testSdpaForward() throws TornadoExecutionPlanException {
        runSdpa(false);
    }

    @Test
    public void testSdpaForwardCausal() throws TornadoExecutionPlanException {
        runSdpa(true);
    }

    /**
     * Convolution under CUDA Graph capture/replay: the conv plan and its
     * device workspace are created in the provider's prepare() hook
     * (pre-capture), so the capture contains only the enqueued kernels.
     */
    @Test
    public void testConv2dWithCudaGraph() throws TornadoExecutionPlanException {
        final int n = 2;
        final int c = 3;
        final int h = 16;
        final int w = 16;
        final int k = 4;
        final int r = 3;
        final int s = 3;
        final int pad = 1;
        final int stride = 1;
        final int outH = h;
        final int outW = w;

        FloatArray input = randomArray(n * c * h * w);
        FloatArray filter = randomArray(k * c * r * s);
        FloatArray output = new FloatArray(n * k * outH * outW);
        FloatArray expected = new FloatArray(n * k * outH * outW);
        convJava(input, filter, expected, n, c, h, w, k, r, s, pad, stride);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, filter) //
                .libraryTask("conv", CuDnn::cudnnConv2d, input, filter, output, n, c, h, w, k, r, s, pad, stride) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withCUDAGraph();
            for (int it = 0; it < 5; it++) {
                plan.execute();
                for (int i = 0; i < expected.getSize(); i++) {
                    assertEquals("iteration " + it, expected.get(i), output.get(i), 1e-3f * Math.max(1.0f, Math.abs(expected.get(i))));
                }
            }
        }
    }
}
