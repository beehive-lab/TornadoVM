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
package uk.ac.manchester.tornado.cudnn.provider;

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_DOUBLE;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * JNI bindings to libtornado-cudnn (FP32/NCHW legacy cuDNN API). Convolutions
 * use opaque plans (descriptors + algorithm + workspace size) created once per
 * shape; simple ops create their host-side descriptors inline.
 */
final class CuDnnNativeLib {

    /** cudnnActivationMode_t values. */
    static final int CUDNN_ACTIVATION_SIGMOID = 0;
    static final int CUDNN_ACTIVATION_RELU = 1;
    static final int CUDNN_ACTIVATION_TANH = 2;

    /** cudnnStatus_t / descriptor-enum values the wrappers below pin. */
    private static final int CUDNN_STATUS_SUCCESS = 0;
    private static final int CUDNN_STATUS_INTERNAL_ERROR = 4;
    private static final int CUDNN_TENSOR_NCHW = 0;
    private static final int CUDNN_DATA_FLOAT = 0;
    private static final int CUDNN_SOFTMAX_ACCURATE = 1;
    private static final int CUDNN_SOFTMAX_MODE_INSTANCE = 0;
    private static final int CUDNN_NOT_PROPAGATE_NAN = 0;
    private static final int CUDNN_POOLING_MAX = 0;
    private static final int CUDNN_CROSS_CORRELATION = 1;
    private static final int CUDNN_CONVOLUTION_FWD_ALGO_IMPLICIT_PRECOMP_GEMM = 1;

    private static final SymbolLookup LIBCUDNN = FFMSupport.loadLibrary("libcudnn.so.9", "libcudnn.so.8", "libcudnn.so", "cudnn64_9.dll", "cudnn64_8.dll", "libcudnn.dylib");

    /** The workspace allocation goes through the CUDA runtime, as cuDNN itself does. */
    private static final SymbolLookup LIBCUDART = FFMSupport.loadLibrary("libcudart.so.12", "libcudart.so.11.0", "libcudart.so", "cudart64_12.dll", "libcudart.dylib");

    private static final MethodHandle CUDNN_CREATE;
    private static final MethodHandle CUDNN_DESTROY;
    private static final MethodHandle CUDNN_SET_STREAM;
    private static final MethodHandle CUDNN_GET_VERSION;
    private static final MethodHandle CREATE_TENSOR_DESCRIPTOR;
    private static final MethodHandle DESTROY_TENSOR_DESCRIPTOR;
    private static final MethodHandle SET_TENSOR_4D_DESCRIPTOR;
    private static final MethodHandle CREATE_ACTIVATION_DESCRIPTOR;
    private static final MethodHandle DESTROY_ACTIVATION_DESCRIPTOR;
    private static final MethodHandle SET_ACTIVATION_DESCRIPTOR;
    private static final MethodHandle CREATE_POOLING_DESCRIPTOR;
    private static final MethodHandle DESTROY_POOLING_DESCRIPTOR;
    private static final MethodHandle SET_POOLING_2D_DESCRIPTOR;
    private static final MethodHandle CREATE_FILTER_DESCRIPTOR;
    private static final MethodHandle DESTROY_FILTER_DESCRIPTOR;
    private static final MethodHandle SET_FILTER_4D_DESCRIPTOR;
    private static final MethodHandle CREATE_CONVOLUTION_DESCRIPTOR;
    private static final MethodHandle DESTROY_CONVOLUTION_DESCRIPTOR;
    private static final MethodHandle SET_CONVOLUTION_2D_DESCRIPTOR;
    private static final MethodHandle GET_CONVOLUTION_2D_FORWARD_OUTPUT_DIM;
    private static final MethodHandle GET_CONVOLUTION_FORWARD_WORKSPACE_SIZE;
    private static final MethodHandle SOFTMAX_FORWARD;
    private static final MethodHandle ACTIVATION_FORWARD;
    private static final MethodHandle POOLING_FORWARD;
    private static final MethodHandle CONVOLUTION_FORWARD;
    private static final MethodHandle CUDA_MALLOC;
    private static final MethodHandle CUDA_FREE;

    static {
        if (LIBCUDNN == null) {
            CUDNN_CREATE = null;
            CUDNN_DESTROY = null;
            CUDNN_SET_STREAM = null;
            CUDNN_GET_VERSION = null;
            CREATE_TENSOR_DESCRIPTOR = null;
            DESTROY_TENSOR_DESCRIPTOR = null;
            SET_TENSOR_4D_DESCRIPTOR = null;
            CREATE_ACTIVATION_DESCRIPTOR = null;
            DESTROY_ACTIVATION_DESCRIPTOR = null;
            SET_ACTIVATION_DESCRIPTOR = null;
            CREATE_POOLING_DESCRIPTOR = null;
            DESTROY_POOLING_DESCRIPTOR = null;
            SET_POOLING_2D_DESCRIPTOR = null;
            CREATE_FILTER_DESCRIPTOR = null;
            DESTROY_FILTER_DESCRIPTOR = null;
            SET_FILTER_4D_DESCRIPTOR = null;
            CREATE_CONVOLUTION_DESCRIPTOR = null;
            DESTROY_CONVOLUTION_DESCRIPTOR = null;
            SET_CONVOLUTION_2D_DESCRIPTOR = null;
            GET_CONVOLUTION_2D_FORWARD_OUTPUT_DIM = null;
            GET_CONVOLUTION_FORWARD_WORKSPACE_SIZE = null;
            SOFTMAX_FORWARD = null;
            ACTIVATION_FORWARD = null;
            POOLING_FORWARD = null;
            CONVOLUTION_FORWARD = null;
        } else {
            CUDNN_CREATE = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_POINTER), "cudnnCreate");
            CUDNN_DESTROY = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG), "cudnnDestroy");
            CUDNN_SET_STREAM = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "cudnnSetStream");
            CUDNN_GET_VERSION = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_LONG), "cudnnGetVersion");
            CREATE_TENSOR_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_POINTER), "cudnnCreateTensorDescriptor");
            DESTROY_TENSOR_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG), "cudnnDestroyTensorDescriptor");
            SET_TENSOR_4D_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT), "cudnnSetTensor4dDescriptor");
            CREATE_ACTIVATION_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_POINTER), "cudnnCreateActivationDescriptor");
            DESTROY_ACTIVATION_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG), "cudnnDestroyActivationDescriptor");
            SET_ACTIVATION_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_DOUBLE), "cudnnSetActivationDescriptor");
            CREATE_POOLING_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_POINTER), "cudnnCreatePoolingDescriptor");
            DESTROY_POOLING_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG), "cudnnDestroyPoolingDescriptor");
            SET_POOLING_2D_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT), "cudnnSetPooling2dDescriptor");
            CREATE_FILTER_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_POINTER), "cudnnCreateFilterDescriptor");
            DESTROY_FILTER_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG), "cudnnDestroyFilterDescriptor");
            SET_FILTER_4D_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT), "cudnnSetFilter4dDescriptor");
            CREATE_CONVOLUTION_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_POINTER), "cudnnCreateConvolutionDescriptor");
            DESTROY_CONVOLUTION_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG), "cudnnDestroyConvolutionDescriptor");
            SET_CONVOLUTION_2D_DESCRIPTOR = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT),
                    "cudnnSetConvolution2dDescriptor");
            GET_CONVOLUTION_2D_FORWARD_OUTPUT_DIM = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_POINTER, C_POINTER, C_POINTER, C_POINTER),
                    "cudnnGetConvolution2dForwardOutputDim");
            GET_CONVOLUTION_FORWARD_WORKSPACE_SIZE = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_INT, C_POINTER),
                    "cudnnGetConvolutionForwardWorkspaceSize");
            SOFTMAX_FORWARD = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG), "cudnnSoftmaxForward");
            ACTIVATION_FORWARD = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG), "cudnnActivationForward");
            POOLING_FORWARD = FFMSupport.downcall(LIBCUDNN, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG), "cudnnPoolingForward");
            CONVOLUTION_FORWARD = FFMSupport.downcall(LIBCUDNN,
                    FunctionDescriptor.of(C_INT, C_LONG, C_POINTER, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_INT, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG), "cudnnConvolutionForward");
        }
        if (LIBCUDART == null) {
            CUDA_MALLOC = null;
            CUDA_FREE = null;
        } else {
            CUDA_MALLOC = FFMSupport.downcall(LIBCUDART, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG), "cudaMalloc");
            CUDA_FREE = FFMSupport.downcall(LIBCUDART, FunctionDescriptor.of(C_INT, C_LONG), "cudaFree");
        }
    }

    /** Scratch for the host-side alpha and beta scalars every forward op passes by pointer. */
    private static final FFMSupport.Staging SCALARS = new FFMSupport.Staging();

    private static boolean sdpaShimLoaded = false;

    private CuDnnNativeLib() {
    }

    /**
     * Checks that cuDNN itself is reachable. Everything except the fused SDPA path talks to cuDNN
     * through the bindings above, so this is a question about the cuDNN installation and no longer
     * about a TornadoVM artifact.
     */
    static synchronized void load() {
        if (LIBCUDNN == null || CUDNN_CREATE == null) {
            throw new TornadoRuntimeException("[ERROR] Unable to load cuDNN. Install cuDNN and make sure libcudnn is on the library path.");
        }
    }

    /**
     * Loads the small remaining JNI shim, which exists only for the fused scaled-dot-product
     * attention path.
     *
     * <p>
     * SDPA is built on cudnn-frontend, a header-only C++ graph API, so it cannot be called through
     * java.lang.foreign the way the rest of cuDNN can -- there is no C ABI to bind. It is loaded
     * lazily and separately so that the ops above keep working on a build where the shim is absent,
     * which is the case whenever the toolkit is too old for cudnn-frontend.
     */
    static synchronized void loadSdpaShim() {
        if (sdpaShimLoaded) {
            return;
        }
        try {
            System.loadLibrary("tornado-cudnn");
            sdpaShimLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            throw new TornadoRuntimeException("[ERROR] Unable to load libtornado-cudnn, which provides the fused SDPA path. "
                    + "Build TornadoVM with the CUDA backend against CUDA 12 or newer: " + e.getMessage());
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException e) {
            throw e;
        }
        if (t instanceof Error e) {
            throw e;
        }
        throw new IllegalStateException(t);
    }

    /** alpha = 1, beta = 0, the scaling every op here uses, in one reusable native pair. */
    private static MemorySegment unitScalars() {
        MemorySegment segment = SCALARS.forBytes(2L * Float.BYTES);
        segment.set(FFMSupport.C_FLOAT, 0, 1.0f);
        segment.set(FFMSupport.C_FLOAT, Float.BYTES, 0.0f);
        return segment;
    }

    private static long createDescriptor(Arena arena, MethodHandle constructor) throws Throwable {
        MemorySegment out = FFMSupport.allocatePointer(arena);
        return (int) constructor.invokeExact(out) == CUDNN_STATUS_SUCCESS ? out.get(C_POINTER, 0).address() : 0;
    }

    private static void destroyDescriptor(MethodHandle destructor, long descriptor) {
        if (descriptor == 0) {
            return;
        }
        try {
            int ignored = (int) destructor.invokeExact(descriptor);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static long cudnnCreateHandle() {
        if (CUDNN_CREATE == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = FFMSupport.allocatePointer(arena);
            if ((int) CUDNN_CREATE.invokeExact(handle) != CUDNN_STATUS_SUCCESS) {
                return 0;
            }
            return handle.get(C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int cudnnSetStream(long handle, long streamPtr) {
        try {
            return (int) CUDNN_SET_STREAM.invokeExact(handle, streamPtr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void cudnnDestroyHandle(long handle) {
        if (handle == 0 || CUDNN_DESTROY == null) {
            return;
        }
        try {
            int ignored = (int) CUDNN_DESTROY.invokeExact(handle);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static long cudnnGetVersionNative() {
        if (CUDNN_GET_VERSION == null) {
            return 0;
        }
        try {
            return (long) CUDNN_GET_VERSION.invokeExact();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * {@code CUDNN_SOFTMAX_ACCURATE} with {@code MODE_INSTANCE}: softmax over C*H*W per N -- with
     * {@code (n=rows, c=cols, h=w=1)} this is a per-row softmax.
     */
    static int softmaxForward(long handle, int n, int c, int h, int w, long dIn, long dOut) {
        if (CREATE_TENSOR_DESCRIPTOR == null) {
            return CUDNN_STATUS_INTERNAL_ERROR;
        }
        long descriptor = 0;
        try (Arena arena = Arena.ofConfined()) {
            descriptor = createDescriptor(arena, CREATE_TENSOR_DESCRIPTOR);
            if (descriptor == 0) {
                return CUDNN_STATUS_INTERNAL_ERROR;
            }
            int status = (int) SET_TENSOR_4D_DESCRIPTOR.invokeExact(descriptor, CUDNN_TENSOR_NCHW, CUDNN_DATA_FLOAT, n, c, h, w);
            if (status == CUDNN_STATUS_SUCCESS) {
                MemorySegment scalars = unitScalars();
                status = (int) SOFTMAX_FORWARD.invokeExact(handle, CUDNN_SOFTMAX_ACCURATE, CUDNN_SOFTMAX_MODE_INSTANCE, scalars.asSlice(0, Float.BYTES), descriptor, dIn,
                        scalars.asSlice(Float.BYTES, Float.BYTES), descriptor, dOut);
            }
            return status;
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, descriptor);
        }
    }

    /**
     * {@code mode} maps onto {@code cudnnActivationMode_t} (SIGMOID=0, RELU=1, TANH=2, ...); the
     * data is treated as a flat {@code (1,1,1,size)} tensor.
     */
    static int activationForward(long handle, int mode, long size, long dIn, long dOut) {
        if (CREATE_TENSOR_DESCRIPTOR == null) {
            return CUDNN_STATUS_INTERNAL_ERROR;
        }
        long descriptor = 0;
        long activation = 0;
        try (Arena arena = Arena.ofConfined()) {
            descriptor = createDescriptor(arena, CREATE_TENSOR_DESCRIPTOR);
            activation = descriptor == 0 ? 0 : createDescriptor(arena, CREATE_ACTIVATION_DESCRIPTOR);
            if (activation == 0) {
                return CUDNN_STATUS_INTERNAL_ERROR;
            }
            int status = (int) SET_TENSOR_4D_DESCRIPTOR.invokeExact(descriptor, CUDNN_TENSOR_NCHW, CUDNN_DATA_FLOAT, 1, 1, 1, (int) size);
            if (status == CUDNN_STATUS_SUCCESS) {
                status = (int) SET_ACTIVATION_DESCRIPTOR.invokeExact(activation, mode, CUDNN_NOT_PROPAGATE_NAN, 0.0d);
            }
            if (status == CUDNN_STATUS_SUCCESS) {
                MemorySegment scalars = unitScalars();
                status = (int) ACTIVATION_FORWARD.invokeExact(handle, activation, scalars.asSlice(0, Float.BYTES), descriptor, dIn, scalars.asSlice(Float.BYTES, Float.BYTES), descriptor, dOut);
            }
            return status;
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            destroyDescriptor(DESTROY_ACTIVATION_DESCRIPTOR, activation);
            destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, descriptor);
        }
    }

    /**
     * Square window and stride max pooling, no padding. Output dims:
     * {@code (h - window) / stride + 1}, {@code (w - window) / stride + 1}.
     */
    static int poolingMaxForward(long handle, int n, int c, int h, int w, int window, int stride, long dIn, long dOut) {
        if (CREATE_TENSOR_DESCRIPTOR == null) {
            return CUDNN_STATUS_INTERNAL_ERROR;
        }
        long inputDesc = 0;
        long outputDesc = 0;
        long poolingDesc = 0;
        try (Arena arena = Arena.ofConfined()) {
            inputDesc = createDescriptor(arena, CREATE_TENSOR_DESCRIPTOR);
            outputDesc = inputDesc == 0 ? 0 : createDescriptor(arena, CREATE_TENSOR_DESCRIPTOR);
            poolingDesc = outputDesc == 0 ? 0 : createDescriptor(arena, CREATE_POOLING_DESCRIPTOR);
            if (poolingDesc == 0) {
                return CUDNN_STATUS_INTERNAL_ERROR;
            }
            int outH = (h - window) / stride + 1;
            int outW = (w - window) / stride + 1;
            int status = (int) SET_TENSOR_4D_DESCRIPTOR.invokeExact(inputDesc, CUDNN_TENSOR_NCHW, CUDNN_DATA_FLOAT, n, c, h, w);
            if (status == CUDNN_STATUS_SUCCESS) {
                status = (int) SET_TENSOR_4D_DESCRIPTOR.invokeExact(outputDesc, CUDNN_TENSOR_NCHW, CUDNN_DATA_FLOAT, n, c, outH, outW);
            }
            if (status == CUDNN_STATUS_SUCCESS) {
                status = (int) SET_POOLING_2D_DESCRIPTOR.invokeExact(poolingDesc, CUDNN_POOLING_MAX, CUDNN_NOT_PROPAGATE_NAN, window, window, 0, 0, stride, stride);
            }
            if (status == CUDNN_STATUS_SUCCESS) {
                MemorySegment scalars = unitScalars();
                status = (int) POOLING_FORWARD.invokeExact(handle, poolingDesc, scalars.asSlice(0, Float.BYTES), inputDesc, dIn, scalars.asSlice(Float.BYTES, Float.BYTES), outputDesc, dOut);
            }
            return status;
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            destroyDescriptor(DESTROY_POOLING_DESCRIPTOR, poolingDesc);
            destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, outputDesc);
            destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, inputDesc);
        }
    }

    /** The descriptors and algorithm one convolution shape needs, built once and replayed. */
    private static final class ConvPlan {

        private final long inputDesc;
        private final long filterDesc;
        private final long convDesc;
        private final long outputDesc;
        private final int algo;
        private final long workspaceBytes;

        private ConvPlan(long inputDesc, long filterDesc, long convDesc, long outputDesc, int algo, long workspaceBytes) {
            this.inputDesc = inputDesc;
            this.filterDesc = filterDesc;
            this.convDesc = convDesc;
            this.outputDesc = outputDesc;
            this.algo = algo;
            this.workspaceBytes = workspaceBytes;
        }
    }

    private static final AtomicLong NEXT_CONV_PLAN = new AtomicLong(1);

    private static final ConcurrentHashMap<Long, ConvPlan> CONV_PLANS = new ConcurrentHashMap<>();

    /**
     * Builds the descriptors for a 2D cross-correlation (the deep-learning convention): input NCHW
     * {@code (n,c,h,w)}, filter KCRS {@code (k,c,r,s)}, square pad and stride, FP32, with the
     * IMPLICIT_PRECOMP_GEMM algorithm, and queries the workspace size. Returns 0 on failure.
     */
    static long createConvPlan(long handle, int n, int c, int h, int w, int k, int r, int s, int pad, int stride) {
        if (CREATE_TENSOR_DESCRIPTOR == null) {
            return 0;
        }
        long inputDesc = 0;
        long filterDesc = 0;
        long convDesc = 0;
        long outputDesc = 0;
        boolean keep = false;
        try (Arena arena = Arena.ofConfined()) {
            inputDesc = createDescriptor(arena, CREATE_TENSOR_DESCRIPTOR);
            filterDesc = createDescriptor(arena, CREATE_FILTER_DESCRIPTOR);
            convDesc = createDescriptor(arena, CREATE_CONVOLUTION_DESCRIPTOR);
            outputDesc = createDescriptor(arena, CREATE_TENSOR_DESCRIPTOR);
            if (inputDesc == 0 || filterDesc == 0 || convDesc == 0 || outputDesc == 0) {
                return 0;
            }
            int status = (int) SET_TENSOR_4D_DESCRIPTOR.invokeExact(inputDesc, CUDNN_TENSOR_NCHW, CUDNN_DATA_FLOAT, n, c, h, w);
            if (status == CUDNN_STATUS_SUCCESS) {
                status = (int) SET_FILTER_4D_DESCRIPTOR.invokeExact(filterDesc, CUDNN_DATA_FLOAT, CUDNN_TENSOR_NCHW, k, c, r, s);
            }
            if (status == CUDNN_STATUS_SUCCESS) {
                status = (int) SET_CONVOLUTION_2D_DESCRIPTOR.invokeExact(convDesc, pad, pad, stride, stride, 1, 1, CUDNN_CROSS_CORRELATION, CUDNN_DATA_FLOAT);
            }
            MemorySegment dims = FFMSupport.allocateArray(arena, C_INT, 4);
            if (status == CUDNN_STATUS_SUCCESS) {
                status = (int) GET_CONVOLUTION_2D_FORWARD_OUTPUT_DIM.invokeExact(convDesc, inputDesc, filterDesc, dims.asSlice(0, Integer.BYTES), dims.asSlice(4, Integer.BYTES),
                        dims.asSlice(8, Integer.BYTES), dims.asSlice(12, Integer.BYTES));
            }
            if (status == CUDNN_STATUS_SUCCESS) {
                status = (int) SET_TENSOR_4D_DESCRIPTOR.invokeExact(outputDesc, CUDNN_TENSOR_NCHW, CUDNN_DATA_FLOAT, dims.get(C_INT, 0), dims.get(C_INT, 4), dims.get(C_INT, 8), dims.get(C_INT, 12));
            }
            MemorySegment workspace = FFMSupport.allocateLong(arena);
            if (status == CUDNN_STATUS_SUCCESS) {
                status = (int) GET_CONVOLUTION_FORWARD_WORKSPACE_SIZE.invokeExact(handle, inputDesc, filterDesc, convDesc, outputDesc, CUDNN_CONVOLUTION_FWD_ALGO_IMPLICIT_PRECOMP_GEMM, workspace);
            }
            if (status != CUDNN_STATUS_SUCCESS) {
                return 0;
            }
            long planHandle = NEXT_CONV_PLAN.getAndIncrement();
            CONV_PLANS.put(planHandle, new ConvPlan(inputDesc, filterDesc, convDesc, outputDesc, CUDNN_CONVOLUTION_FWD_ALGO_IMPLICIT_PRECOMP_GEMM, workspace.get(C_LONG, 0)));
            keep = true;
            return planHandle;
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            if (!keep) {
                destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, outputDesc);
                destroyDescriptor(DESTROY_CONVOLUTION_DESCRIPTOR, convDesc);
                destroyDescriptor(DESTROY_FILTER_DESCRIPTOR, filterDesc);
                destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, inputDesc);
            }
        }
    }

    static long convPlanWorkspaceBytes(long planPtr) {
        ConvPlan plan = CONV_PLANS.get(planPtr);
        return plan == null ? 0 : plan.workspaceBytes;
    }

    static int convForward(long handle, long planPtr, long dIn, long dFilter, long dOut, long workspacePtr, long workspaceBytes) {
        ConvPlan plan = CONV_PLANS.get(planPtr);
        if (plan == null || CONVOLUTION_FORWARD == null) {
            return CUDNN_STATUS_INTERNAL_ERROR;
        }
        MemorySegment scalars = unitScalars();
        try {
            return (int) CONVOLUTION_FORWARD.invokeExact(handle, scalars.asSlice(0, Float.BYTES), plan.inputDesc, dIn, plan.filterDesc, dFilter, plan.convDesc, plan.algo, workspacePtr,
                    workspaceBytes, scalars.asSlice(Float.BYTES, Float.BYTES), plan.outputDesc, dOut);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void destroyConvPlan(long planPtr) {
        ConvPlan plan = CONV_PLANS.remove(planPtr);
        if (plan == null) {
            return;
        }
        destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, plan.outputDesc);
        destroyDescriptor(DESTROY_CONVOLUTION_DESCRIPTOR, plan.convDesc);
        destroyDescriptor(DESTROY_FILTER_DESCRIPTOR, plan.filterDesc);
        destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, plan.inputDesc);
    }

    /*
     * The four methods below are the only ones still crossing into a TornadoVM JNI library. They
     * are built on cudnn-frontend, a header-only C++ graph API with no C ABI, so java.lang.foreign
     * cannot reach them; see loadSdpaShim.
     */

    static native long createSdpaPlan(long handle, int b, int h, int sQ, int sKv, int d, float scale, boolean causal);

    static native long sdpaPlanWorkspaceBytes(long planPtr);

    /** Returns 0 on success. */
    static native int executeSdpaPlan(long handle, long planPtr, long dQ, long dK, long dV, long dO, long workspacePtr);

    static native void destroySdpaPlan(long planPtr);

    static long allocateDeviceMemory(long bytes) {
        if (CUDA_MALLOC == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pointer = FFMSupport.allocatePointer(arena);
            if ((int) CUDA_MALLOC.invokeExact(pointer, bytes) != 0) {
                return 0;
            }
            return pointer.get(C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void freeDeviceMemory(long ptr) {
        if (ptr == 0 || CUDA_FREE == null) {
            return;
        }
        try {
            int ignored = (int) CUDA_FREE.invokeExact(ptr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static String decodeStatus(int status) {
        return switch (status) {
            case 0 -> "CUDNN_STATUS_SUCCESS";
            case 1 -> "CUDNN_STATUS_NOT_INITIALIZED";
            case 2 -> "CUDNN_STATUS_ALLOC_FAILED";
            case 3 -> "CUDNN_STATUS_BAD_PARAM";
            case 4 -> "CUDNN_STATUS_INTERNAL_ERROR";
            case 5 -> "CUDNN_STATUS_INVALID_VALUE";
            case 6 -> "CUDNN_STATUS_ARCH_MISMATCH";
            case 7 -> "CUDNN_STATUS_MAPPING_ERROR";
            case 8 -> "CUDNN_STATUS_EXECUTION_FAILED";
            case 9 -> "CUDNN_STATUS_NOT_SUPPORTED";
            default -> "UNKNOWN_CUDNN_STATUS (" + status + ")";
        };
    }

    static void checkStatus(int status, String function) {
        if (status != 0) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed with status: " + decodeStatus(status));
        }
    }
}
