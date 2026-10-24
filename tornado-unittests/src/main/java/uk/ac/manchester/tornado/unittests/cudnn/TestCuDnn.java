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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.KernelContext;
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
import uk.ac.manchester.tornado.unittests.common.LibraryStreamPerThread;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.cudnn.provider.CuDnnLibraryProvider;
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

    @BeforeEach
    public void cuDnnMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "cuDNN library tasks require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        if (!CuDnnLibraryProvider.isAvailable()) {
            throw new TornadoVMCUDANotSupported("cuDNN is not available on this host");
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

    /** The gradients of {@link #convJava}'s input and filter for an output gradient {@code dOut}. */
    private static void convBackwardJava(FloatArray input, FloatArray filter, FloatArray dOut, FloatArray dInput, FloatArray dFilter, int n, int c, int h, int w, int k, int r, int s, int pad,
            int stride) {
        int outH = (h + 2 * pad - r) / stride + 1;
        int outW = (w + 2 * pad - s) / stride + 1;
        for (int in = 0; in < n; in++) {
            for (int ok = 0; ok < k; ok++) {
                for (int oh = 0; oh < outH; oh++) {
                    for (int ow = 0; ow < outW; ow++) {
                        float g = dOut.get(((in * k + ok) * outH + oh) * outW + ow);
                        for (int ic = 0; ic < c; ic++) {
                            for (int fr = 0; fr < r; fr++) {
                                for (int fs = 0; fs < s; fs++) {
                                    int ih = oh * stride + fr - pad;
                                    int iw = ow * stride + fs - pad;
                                    if (ih >= 0 && ih < h && iw >= 0 && iw < w) {
                                        int at = ((in * c + ic) * h + ih) * w + iw;
                                        int wt = ((ok * c + ic) * r + fr) * s + fs;
                                        dInput.set(at, dInput.get(at) + g * filter.get(wt));
                                        dFilter.set(wt, dFilter.get(wt) + g * input.get(at));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testConv2dBackward() throws TornadoExecutionPlanException {
        final int n = 2;
        final int c = 3;
        final int h = 16;
        final int w = 16;
        final int k = 4;
        final int r = 3;
        final int s = 3;
        final int pad = 1;
        final int stride = 2;
        final int outH = (h + 2 * pad - r) / stride + 1;
        final int outW = (w + 2 * pad - s) / stride + 1;

        FloatArray input = randomArray(n * c * h * w);
        FloatArray filter = randomArray(k * c * r * s);
        FloatArray dOut = randomArray(n * k * outH * outW);
        FloatArray dInput = new FloatArray(n * c * h * w);
        FloatArray dFilter = new FloatArray(k * c * r * s);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, filter, dOut) //
                .libraryTask("dx", CuDnn::cudnnConv2dBackwardData, filter, dOut, dInput, n, c, h, w, k, r, s, pad, stride) //
                .libraryTask("dw", CuDnn::cudnnConv2dBackwardFilter, input, dOut, dFilter, n, c, h, w, k, r, s, pad, stride) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dInput, dFilter);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        FloatArray expectedInput = new FloatArray(n * c * h * w);
        FloatArray expectedFilter = new FloatArray(k * c * r * s);
        convBackwardJava(input, filter, dOut, expectedInput, expectedFilter, n, c, h, w, k, r, s, pad, stride);
        // cuDNN may use TF32 for FP32 on Ampere and newer, as it does for the forward convolution
        for (int i = 0; i < expectedInput.getSize(); i++) {
            assertEquals(expectedInput.get(i), dInput.get(i), 1e-2f * Math.max(1.0f, Math.abs(expectedInput.get(i))));
        }
        for (int i = 0; i < expectedFilter.getSize(); i++) {
            assertEquals(expectedFilter.get(i), dFilter.get(i), 1e-2f * Math.max(1.0f, Math.abs(expectedFilter.get(i))));
        }
    }

    private static void checkConv2dFP16(int n, int c, int h, int w, int k, int r, int s, int pad, int stride) throws TornadoExecutionPlanException {
        final int outH = (h + 2 * pad - r) / stride + 1;
        final int outW = (w + 2 * pad - s) / stride + 1;

        HalfFloatArray input = randomHalfArray(n * c * h * w);
        HalfFloatArray filter = randomHalfArray(k * c * r * s);
        HalfFloatArray output = new HalfFloatArray(n * k * outH * outW);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, filter) //
                .libraryTask("conv", CuDnn::cudnnConv2dFP16, input, filter, output, n, c, h, w, k, r, s, pad, stride) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        // the FP32 reference sees the same FP16-rounded inputs
        FloatArray inputF32 = new FloatArray(input.getSize());
        for (int i = 0; i < input.getSize(); i++) {
            inputF32.set(i, input.get(i).getFloat32());
        }
        FloatArray filterF32 = new FloatArray(filter.getSize());
        for (int i = 0; i < filter.getSize(); i++) {
            filterF32.set(i, filter.get(i).getFloat32());
        }
        FloatArray expected = new FloatArray(output.getSize());
        convJava(inputF32, filterF32, expected, n, c, h, w, k, r, s, pad, stride);
        for (int i = 0; i < expected.getSize(); i++) {
            // FP32 accumulation, FP16 output: about 3 significant digits
            assertEquals(expected.get(i), output.get(i).getFloat32(), 1e-2f * Math.max(1.0f, Math.abs(expected.get(i))));
        }
    }

    @Test
    public void testConv2dFP16() throws TornadoExecutionPlanException {
        // channel counts that suit the tensor cores, then ones that do not
        checkConv2dFP16(2, 16, 14, 14, 32, 3, 3, 1, 1);
        checkConv2dFP16(2, 3, 16, 16, 4, 3, 3, 1, 1);
        // a strided 1x1 projection and a 7x7 stem, as in ResNet
        checkConv2dFP16(2, 64, 14, 14, 128, 1, 1, 0, 2);
        checkConv2dFP16(1, 3, 32, 32, 64, 7, 7, 3, 2);
    }

    /**
     * A channels-last convolution against the NCHW reference: the inputs are made NCHW and KCRS,
     * passed as NHWC and KRSC, and the NHWC output is compared element by element.
     */
    private static void checkConv2dNHWC(boolean half, int n, int c, int h, int w, int k, int r, int s, int pad, int stride) throws TornadoExecutionPlanException {
        final int outH = (h + 2 * pad - r) / stride + 1;
        final int outW = (w + 2 * pad - s) / stride + 1;
        FloatArray input = half ? toFloat(randomHalfArray(n * c * h * w)) : randomArray(n * c * h * w);
        FloatArray filter = half ? toFloat(randomHalfArray(k * c * r * s)) : randomArray(k * c * r * s);
        FloatArray inputLast = new FloatArray(input.getSize());
        for (int in = 0; in < n; in++) {
            for (int ic = 0; ic < c; ic++) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        inputLast.set(((in * h + y) * w + x) * c + ic, input.get(((in * c + ic) * h + y) * w + x));
                    }
                }
            }
        }
        FloatArray filterLast = new FloatArray(filter.getSize());
        for (int ok = 0; ok < k; ok++) {
            for (int ic = 0; ic < c; ic++) {
                for (int fr = 0; fr < r; fr++) {
                    for (int fs = 0; fs < s; fs++) {
                        filterLast.set(((ok * r + fr) * s + fs) * c + ic, filter.get(((ok * c + ic) * r + fr) * s + fs));
                    }
                }
            }
        }
        FloatArray outputLast;
        if (half) {
            HalfFloatArray x = toHalf(inputLast);
            HalfFloatArray f = toHalf(filterLast);
            HalfFloatArray y = new HalfFloatArray(n * k * outH * outW);
            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, f) //
                    .libraryTask("conv", CuDnn::cudnnConv2dNHWCFP16, x, f, y, n, c, h, w, k, r, s, pad, stride) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, y);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }
            outputLast = toFloat(y);
        } else {
            outputLast = new FloatArray(n * k * outH * outW);
            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputLast, filterLast) //
                    .libraryTask("conv", CuDnn::cudnnConv2dNHWC, inputLast, filterLast, outputLast, n, c, h, w, k, r, s, pad, stride) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, outputLast);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }
        }
        FloatArray expected = new FloatArray(n * k * outH * outW);
        convJava(input, filter, expected, n, c, h, w, k, r, s, pad, stride);
        // FP16 output keeps about 3 significant digits; FP32 may run as TF32 on Ampere and later
        float tolerance = half ? 1e-2f : 5e-3f;
        for (int in = 0; in < n; in++) {
            for (int ok = 0; ok < k; ok++) {
                for (int oh = 0; oh < outH; oh++) {
                    for (int ow = 0; ow < outW; ow++) {
                        float e = expected.get(((in * k + ok) * outH + oh) * outW + ow);
                        float a = outputLast.get(((in * outH + oh) * outW + ow) * k + ok);
                        assertEquals(e, a, tolerance * Math.max(1.0f, Math.abs(e)));
                    }
                }
            }
        }
    }

    private static FloatArray toFloat(HalfFloatArray a) {
        FloatArray f = new FloatArray(a.getSize());
        for (int i = 0; i < a.getSize(); i++) {
            f.set(i, a.get(i).getFloat32());
        }
        return f;
    }

    private static HalfFloatArray toHalf(FloatArray a) {
        HalfFloatArray h = new HalfFloatArray(a.getSize());
        for (int i = 0; i < a.getSize(); i++) {
            h.set(i, new HalfFloat(a.get(i)));
        }
        return h;
    }

    @Test
    public void testConv2dNHWC() throws TornadoExecutionPlanException {
        checkConv2dNHWC(false, 2, 16, 14, 14, 32, 3, 3, 1, 1);
        checkConv2dNHWC(false, 2, 3, 16, 16, 4, 3, 3, 1, 1);
        checkConv2dNHWC(false, 2, 64, 14, 14, 128, 1, 1, 0, 2);
    }

    @Test
    public void testConv2dNHWCFP16() throws TornadoExecutionPlanException {
        // channel counts that suit the tensor cores, then ones that do not
        checkConv2dNHWC(true, 2, 16, 14, 14, 32, 3, 3, 1, 1);
        checkConv2dNHWC(true, 2, 3, 16, 16, 4, 3, 3, 1, 1);
        // a strided 1x1 projection and a 7x7 stem, as in ResNet
        checkConv2dNHWC(true, 2, 64, 14, 14, 128, 1, 1, 0, 2);
        checkConv2dNHWC(true, 1, 3, 32, 32, 64, 7, 7, 3, 2);
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

    /**
     * Attention read from one packed projection [b, s, 3, h, d] (q, k, v per token) and written as
     * [b, s, h, d], without gathering into BHSD first.
     */
    @Test
    public void testSdpaForwardPackedQkv() throws TornadoExecutionPlanException {
        final int b = 2;
        final int h = 4;
        final int s = 64;
        final int d = 64;
        final float scale = (float) (1.0 / Math.sqrt(d));

        HalfFloatArray qkv = randomHalfArray(b * s * 3 * h * d);
        HalfFloatArray o = new HalfFloatArray(b * s * h * d);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, qkv) //
                .libraryTask("sdpa", CuDnn::sdpaForwardPackedQkv, qkv, o, b, h, s, d, scale, false) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, o);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        // the same problem in BHSD for the reference
        HalfFloatArray q = new HalfFloatArray(b * h * s * d);
        HalfFloatArray k = new HalfFloatArray(b * h * s * d);
        HalfFloatArray v = new HalfFloatArray(b * h * s * d);
        for (int ib = 0; ib < b; ib++) {
            for (int it = 0; it < s; it++) {
                for (int ih = 0; ih < h; ih++) {
                    for (int dd = 0; dd < d; dd++) {
                        int bhsd = ((ib * h + ih) * s + it) * d + dd;
                        int packed = (((ib * s + it) * 3) * h + ih) * d + dd;
                        q.set(bhsd, qkv.get(packed));
                        k.set(bhsd, qkv.get(packed + h * d));
                        v.set(bhsd, qkv.get(packed + 2 * h * d));
                    }
                }
            }
        }
        FloatArray expected = new FloatArray(b * h * s * d);
        attentionJava(q, k, v, expected, b, h, s, d, scale, false);
        for (int ib = 0; ib < b; ib++) {
            for (int it = 0; it < s; it++) {
                for (int ih = 0; ih < h; ih++) {
                    for (int dd = 0; dd < d; dd++) {
                        float e = expected.get(((ib * h + ih) * s + it) * d + dd);
                        float a = o.get(((ib * s + it) * h + ih) * d + dd).getFloat32();
                        assertEquals(e, a, 2e-2f * Math.max(0.1f, Math.abs(e)));
                    }
                }
            }
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
                    assertEquals(expected.get(i), output.get(i), 1e-3f * Math.max(1.0f, Math.abs(expected.get(i))), "iteration " + it);
                }
            }
        }
    }

    /**
     * Four task graphs of one plan, each with a convolution, under a CUDA graph. Each convolution
     * needs a larger workspace than the one before, and a graph captured before a larger workspace
     * is prepared must still find its own workspace when it is replayed.
     */
    @Test
    public void testConv2dWorkspaceGrowsAcrossCapturedGraphs() throws TornadoExecutionPlanException {
        // N, C, H, W, K: each convolution needs a larger workspace than the one before
        int[][] shapes = { { 2, 16, 16, 16, 16 }, { 4, 32, 32, 32, 32 }, { 8, 64, 48, 48, 64 }, { 16, 128, 56, 56, 128 } };
        int count = shapes.length;
        FloatArray[] inputs = new FloatArray[count];
        FloatArray[] filters = new FloatArray[count];
        FloatArray[] outputs = new FloatArray[count];
        FloatArray[] expected = new FloatArray[count];
        TaskGraph[] graphs = new TaskGraph[count];
        for (int g = 0; g < count; g++) {
            int n = shapes[g][0], c = shapes[g][1], h = shapes[g][2], w = shapes[g][3], k = shapes[g][4];
            inputs[g] = randomArray(n * c * h * w);
            filters[g] = randomArray(k * c * 3 * 3);
            outputs[g] = new FloatArray(n * k * h * w);
            expected[g] = new FloatArray(n * k * h * w);
            convJava(inputs[g], filters[g], expected[g], n, c, h, w, k, 3, 3, 1, 1);
            graphs[g] = new TaskGraph("workspace" + g) //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputs[g], filters[g]) //
                    .libraryTask("conv", CuDnn::cudnnConv2d, inputs[g], filters[g], outputs[g], n, c, h, w, k, 3, 3, 1, 1) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, outputs[g]);
        }

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graphs[0].snapshot(), graphs[1].snapshot(), graphs[2].snapshot(), graphs[3].snapshot())) {
            plan.withCUDAGraph();
            for (int it = 0; it < 3; it++) {
                plan.execute();
                for (int g = 0; g < count; g++) {
                    for (int i = 0; i < expected[g].getSize(); i++) {
                        assertEquals(expected[g].get(i), outputs[g].get(i), 1e-2f * Math.max(1.0f, Math.abs(expected[g].get(i))), "graph " + g + ", iteration " + it);
                    }
                }
            }
        }
    }

    /**
     * A cuDNN library task fed by an input placed on the device with an explicit upload, then
     * refreshed between executions. The input is declared {@code FIRST_EXECUTION}, so the second
     * result can only differ if the upload actually reached the device.
     */
    @Test
    public void testReluWithAdHocInputUpload() throws TornadoExecutionPlanException {
        final int size = 4096;
        FloatArray input = randomArray(size);
        FloatArray output = new FloatArray(size);

        TaskGraph taskGraph = new TaskGraph("reluUpload") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .libraryTask("relu", CuDnn::cudnnRelu, input, output, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.transferToDevice(input);
            plan.execute();
            for (int i = 0; i < size; i++) {
                assertEquals(Math.max(0.0f, input.get(i)), output.get(i), 0.001f);
            }

            for (int i = 0; i < size; i++) {
                input.set(i, -input.get(i));
            }
            plan.transferToDevice(input);
            plan.execute();
            for (int i = 0; i < size; i++) {
                assertEquals(Math.max(0.0f, input.get(i)), output.get(i), 0.001f);
            }
        }
    }

    /**
     * Two cuDNN library tasks in a pipeline that hands its intermediate over on the device, with
     * the plan's inputs uploaded before anything runs.
     */
    @Test
    public void testReluThenTanhPipelineWithUpFrontUpload() throws TornadoExecutionPlanException {
        final int size = 4096;
        FloatArray input = randomArray(size);
        FloatArray intermediate = new FloatArray(size);
        FloatArray output = new FloatArray(size);

        TaskGraph producer = new TaskGraph("dnnProducer") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .libraryTask("relu", CuDnn::cudnnRelu, input, intermediate, size) //
                .persistOnDevice(intermediate);

        TaskGraph consumer = new TaskGraph("dnnConsumer") //
                .consumeFromDevice("dnnProducer", intermediate) //
                .libraryTask("tanh", CuDnn::cudnnTanh, intermediate, output, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(producer.snapshot(), consumer.snapshot())) {
            plan.transferToDevice();
            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
        }

        for (int i = 0; i < size; i++) {
            float expected = (float) Math.tanh(Math.max(0.0f, input.get(i)));
            assertEquals(expected, output.get(i), 0.001f);
        }
    }

    /**
     * The same plan run from a second thread: TornadoVM gives each thread its own stream, and the
     * cuDNN call must follow the kernel that produces its input on that thread's stream, not on the
     * stream of the thread that first ran the plan.
     */
    @Test
    public void testReluFollowsKernelOnAnotherThread() throws Exception {
        final int size = 4096;
        FloatArray input = new FloatArray(size);
        FloatArray output = new FloatArray(size);
        FloatArray value = new FloatArray(1);
        value.set(0, 1.0f);

        TaskGraph taskGraph = new TaskGraph("streamPerThread") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, value) //
                .task("fill", LibraryStreamPerThread::slowFill, new KernelContext(), input, value, LibraryStreamPerThread.SPINS) //
                .libraryTask("relu", CuDnn::cudnnRelu, input, output, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(LibraryStreamPerThread.gridScheduler("streamPerThread.fill")).execute();
            for (int i = 0; i < size; i++) {
                assertEquals(1.0f, output.get(i), 1e-3f, "element " + i);
            }

            // The same plan from another thread, with a new value: a call that does not wait for
            // the kernel on this thread's stream reads the first one.
            value.set(0, 2.0f);
            output.init(0.0f);
            LibraryStreamPerThread.executeOnAnotherThread(plan);
            for (int i = 0; i < size; i++) {
                assertEquals(2.0f, output.get(i), 1e-3f, "element " + i);
            }
        }
    }
}
