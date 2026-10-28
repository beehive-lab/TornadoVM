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
package uk.ac.manchester.tornado.cudnn;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

/**
 * Factory methods for NVIDIA cuDNN library tasks (NCHW layout unless named NHWC; FP32 unless named FP16).
 *
 * <pre>
 * taskGraph.libraryTask("conv", CuDnn::cudnnConv2d,
 *         input, filter, output, n, c, h, w, k, r, s, pad, stride);
 * </pre>
 *
 * Convolution follows the deep-learning convention (cross-correlation);
 * per-shape descriptors, the algorithm, and the device workspace are prepared
 * once and cached in the per-(device, execution plan) context.
 */
public final class CuDnn {

    public static final String LIBRARY_NAME = "nvidia/cudnn";

    private CuDnn() {
    }

    private static Access[] accessInOut(int numArgs) {
        Access[] accesses = new Access[numArgs];
        java.util.Arrays.fill(accesses, Access.READ_ONLY);
        accesses[1] = Access.WRITE_ONLY;
        return accesses;
    }

    /**
     * Per-row softmax (numerically stable, {@code CUDNN_SOFTMAX_ACCURATE}):
     * each of the {@code rows} rows of length {@code cols} is normalized
     * independently - the attention-score shape.
     */
    public static LibraryTaskDescriptor cudnnSoftmax(FloatArray input, FloatArray output, int rows, int cols) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cudnnSoftmax") //
                .withParameters(new Object[] { input, output, rows, cols }) //
                .withAccess(accessInOut(4));
    }

    /** Element-wise ReLU. */
    public static LibraryTaskDescriptor cudnnRelu(FloatArray input, FloatArray output, int size) {
        return activation("cudnnRelu", input, output, size);
    }

    /** Element-wise sigmoid. */
    public static LibraryTaskDescriptor cudnnSigmoid(FloatArray input, FloatArray output, int size) {
        return activation("cudnnSigmoid", input, output, size);
    }

    /** Element-wise tanh. */
    public static LibraryTaskDescriptor cudnnTanh(FloatArray input, FloatArray output, int size) {
        return activation("cudnnTanh", input, output, size);
    }

    private static LibraryTaskDescriptor activation(String function, FloatArray input, FloatArray output, int size) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction(function) //
                .withParameters(new Object[] { input, output, size }) //
                .withAccess(accessInOut(3));
    }

    /**
     * 2D max pooling on an NCHW tensor with a square window and stride, no
     * padding. Output spatial dims: {@code (h - window) / stride + 1}.
     */
    public static LibraryTaskDescriptor cudnnMaxPool2d(FloatArray input, FloatArray output, int n, int c, int h, int w, int window, int stride) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cudnnMaxPool2d") //
                .withParameters(new Object[] { input, output, n, c, h, w, window, stride }) //
                .withAccess(accessInOut(8));
    }

    /**
     * Fused scaled-dot-product attention (flash attention) forward, inference:
     * {@code O = softmax(Q * K^T * scale [+ causal mask]) * V}, executed as a
     * single fused cuDNN graph-API kernel. Tensors are FP16, packed BHSD
     * layout: {@code Q/O = [b, h, sQ, d]}, {@code K/V = [b, h, sKv, d]},
     * flattened into {@link HalfFloatArray}s. Constraints (cuDNN): d multiple
     * of 8, d <= 256, Ampere or newer.
     */
    public static LibraryTaskDescriptor sdpaForward(HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, HalfFloatArray o, //
            int b, int h, int sQ, int sKv, int d, float scale, boolean causal) {
        Access[] accesses = new Access[11];
        java.util.Arrays.fill(accesses, Access.READ_ONLY);
        accesses[3] = Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("sdpaForward") //
                .withParameters(new Object[] { q, k, v, o, b, h, sQ, sKv, d, scale, causal }) //
                .withAccess(accesses);
    }

    /**
     * 2D convolution forward (cross-correlation): input NCHW {@code (n,c,h,w)},
     * filter KCRS {@code (k,c,r,s)}, square {@code pad}/{@code stride}. Output
     * is NCHW {@code (n, k, (h+2*pad-r)/stride+1, (w+2*pad-s)/stride+1)}.
     */
    public static LibraryTaskDescriptor cudnnConv2d(FloatArray input, FloatArray filter, FloatArray output, //
            int n, int c, int h, int w, int k, int r, int s, int pad, int stride) {
        Access[] accesses = new Access[12];
        java.util.Arrays.fill(accesses, Access.READ_ONLY);
        accesses[2] = Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cudnnConv2d") //
                .withParameters(new Object[] { input, filter, output, n, c, h, w, k, r, s, pad, stride }) //
                .withAccess(accesses);
    }

    /**
     * {@link #cudnnConv2d} on FP16 tensors: NCHW input and output, KCRS filter, accumulated in FP32
     * on the tensor cores where the device has them.
     */
    public static LibraryTaskDescriptor cudnnConv2dFP16(HalfFloatArray input, HalfFloatArray filter, HalfFloatArray output, //
            int n, int c, int h, int w, int k, int r, int s, int pad, int stride) {
        Access[] accesses = new Access[12];
        java.util.Arrays.fill(accesses, Access.READ_ONLY);
        accesses[2] = Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cudnnConv2dFP16") //
                .withParameters(new Object[] { input, filter, output, n, c, h, w, k, r, s, pad, stride }) //
                .withAccess(accesses);
    }

    /**
     * {@link #cudnnConv2d} on channels-last tensors: input NHWC (memory order {@code n, h, w, c}),
     * filter KRSC ({@code k, r, s, c}), output NHWC {@code (n, (h+2*pad-r)/stride+1,
     * (w+2*pad-s)/stride+1, k)}. The sizes are passed as for {@link #cudnnConv2d}.
     */
    public static LibraryTaskDescriptor cudnnConv2dNHWC(FloatArray input, FloatArray filter, FloatArray output, //
            int n, int c, int h, int w, int k, int r, int s, int pad, int stride) {
        Access[] accesses = new Access[12];
        java.util.Arrays.fill(accesses, Access.READ_ONLY);
        accesses[2] = Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cudnnConv2dNHWC") //
                .withParameters(new Object[] { input, filter, output, n, c, h, w, k, r, s, pad, stride }) //
                .withAccess(accesses);
    }

    /**
     * {@link #cudnnConv2dNHWC} on FP16 tensors, accumulated in FP32 on the tensor cores where the
     * device has them: the layout tensor cores read without a transpose, so usually the fastest
     * FP16 convolution.
     */
    public static LibraryTaskDescriptor cudnnConv2dNHWCFP16(HalfFloatArray input, HalfFloatArray filter, HalfFloatArray output, //
            int n, int c, int h, int w, int k, int r, int s, int pad, int stride) {
        Access[] accesses = new Access[12];
        java.util.Arrays.fill(accesses, Access.READ_ONLY);
        accesses[2] = Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cudnnConv2dNHWCFP16") //
                .withParameters(new Object[] { input, filter, output, n, c, h, w, k, r, s, pad, stride }) //
                .withAccess(accesses);
    }

    /**
     * The gradient of the input of a {@link #cudnnConv2d}: {@code dInput (n,c,h,w)} from
     * the filter {@code (k,c,r,s)} and {@code dOutput}, NCHW. The sizes are those of the forward
     * convolution.
     */
    public static LibraryTaskDescriptor cudnnConv2dBackwardData(FloatArray filter, FloatArray dOutput, FloatArray dInput, //
            int n, int c, int h, int w, int k, int r, int s, int pad, int stride) {
        Access[] accesses = new Access[12];
        java.util.Arrays.fill(accesses, Access.READ_ONLY);
        accesses[2] = Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cudnnConv2dBackwardData") //
                .withParameters(new Object[] { filter, dOutput, dInput, n, c, h, w, k, r, s, pad, stride }) //
                .withAccess(accesses);
    }

    /**
     * The gradient of the filter of a {@link #cudnnConv2d}: {@code dFilter (k,c,r,s)}
     * from the input {@code (n,c,h,w)} and {@code dOutput}, NCHW. The sizes are those of the
     * forward convolution.
     */
    public static LibraryTaskDescriptor cudnnConv2dBackwardFilter(FloatArray input, FloatArray dOutput, FloatArray dFilter, //
            int n, int c, int h, int w, int k, int r, int s, int pad, int stride) {
        Access[] accesses = new Access[12];
        java.util.Arrays.fill(accesses, Access.READ_ONLY);
        accesses[2] = Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cudnnConv2dBackwardFilter") //
                .withParameters(new Object[] { input, dOutput, dFilter, n, c, h, w, k, r, s, pad, stride }) //
                .withAccess(accesses);
    }

    /**
     * The gradient of the input of a {@link #cudnnConv2dFP16} on FP16 tensors, accumulated in FP32 on the tensor cores where the device has them: {@code dInput (n,c,h,w)} from
     * the filter {@code (k,c,r,s)} and {@code dOutput}, NCHW. The sizes are those of the forward
     * convolution.
     */
    public static LibraryTaskDescriptor cudnnConv2dBackwardDataFP16(HalfFloatArray filter, HalfFloatArray dOutput, HalfFloatArray dInput, //
            int n, int c, int h, int w, int k, int r, int s, int pad, int stride) {
        Access[] accesses = new Access[12];
        java.util.Arrays.fill(accesses, Access.READ_ONLY);
        accesses[2] = Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cudnnConv2dBackwardDataFP16") //
                .withParameters(new Object[] { filter, dOutput, dInput, n, c, h, w, k, r, s, pad, stride }) //
                .withAccess(accesses);
    }

    /**
     * The gradient of the filter of a {@link #cudnnConv2dFP16} on FP16 tensors, accumulated in FP32 on the tensor cores where the device has them: {@code dFilter (k,c,r,s)}
     * from the input {@code (n,c,h,w)} and {@code dOutput}, NCHW. The sizes are those of the
     * forward convolution.
     */
    public static LibraryTaskDescriptor cudnnConv2dBackwardFilterFP16(HalfFloatArray input, HalfFloatArray dOutput, HalfFloatArray dFilter, //
            int n, int c, int h, int w, int k, int r, int s, int pad, int stride) {
        Access[] accesses = new Access[12];
        java.util.Arrays.fill(accesses, Access.READ_ONLY);
        accesses[2] = Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cudnnConv2dBackwardFilterFP16") //
                .withParameters(new Object[] { input, dOutput, dFilter, n, c, h, w, k, r, s, pad, stride }) //
                .withAccess(accesses);
    }

    /**
     * {@link #sdpaForward} for self-attention on one packed projection: {@code qkv} holds, per batch
     * and token, q, k and v for every head ({@code [b, s, 3, h, d]}), and {@code o} is written as
     * {@code [b, s, h, d]}, the layout the output projection reads. cuDNN reads and writes these
     * strides directly, so no gather into BHSD and no merge back are needed. Constraints as for
     * {@link #sdpaForward}; {@code h * d} must be a multiple of 8.
     */
    public static LibraryTaskDescriptor sdpaForwardPackedQkv(HalfFloatArray qkv, HalfFloatArray o, //
            int b, int h, int s, int d, float scale, boolean causal) {
        Access[] accesses = new Access[8];
        java.util.Arrays.fill(accesses, Access.READ_ONLY);
        accesses[1] = Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("sdpaForwardPackedQkv") //
                .withParameters(new Object[] { qkv, o, b, h, s, d, scale, causal }) //
                .withAccess(accesses);
    }
}
