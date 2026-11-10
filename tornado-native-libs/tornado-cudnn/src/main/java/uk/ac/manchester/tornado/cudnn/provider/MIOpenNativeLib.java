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
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.ffm.ROCmLibraries;

/**
 * Panama bindings to MIOpen, the ROCm counterpart of {@link CuDnnNativeLib} for HIP devices, with the
 * same entry points. Convolutions use MIOpen's immediate mode: a plan holds the descriptors and the
 * solution MIOpen ranks first for the shape, compiled when the plan is created.
 */
final class MIOpenNativeLib {

    /** {@code miopenActivationMode_t} values. */
    static final int MIOPEN_ACTIVATION_LOGISTIC = 1;
    static final int MIOPEN_ACTIVATION_TANH = 2;
    static final int MIOPEN_ACTIVATION_RELU = 3;

    private static final int MIOPEN_STATUS_SUCCESS = 0;
    private static final int MIOPEN_STATUS_INTERNAL_ERROR = 5;
    private static final int MIOPEN_HALF = 0;
    private static final int MIOPEN_FLOAT = 1;
    private static final int MIOPEN_SOFTMAX_ACCURATE = 1;
    private static final int MIOPEN_SOFTMAX_MODE_INSTANCE = 0;
    private static final int MIOPEN_POOLING_MAX = 0;
    /** {@code miopenConvolution}: cross-correlation, the deep-learning convention. */
    private static final int MIOPEN_CONVOLUTION = 0;
    private static final int MIOPEN_CONVOLUTION_ATTRIB_DETERMINISTIC = 1;

    private static final SymbolLookup LIBMIOPEN = ROCmLibraries.load(List.of("libMIOpen.so.1", "libMIOpen.so"), List.of("MIOpen.dll"));

    /** The workspace comes from {@code hipMalloc}, as MIOpen's own allocations do. */
    private static final SymbolLookup LIBHIP = ROCmLibraries.load(List.of("libamdhip64.so.7", "libamdhip64.so.6", "libamdhip64.so"), List.of("amdhip64_7.dll", "amdhip64_6.dll"));

    private static final MethodHandle MIOPEN_CREATE;
    private static final MethodHandle MIOPEN_DESTROY;
    private static final MethodHandle MIOPEN_SET_STREAM;
    private static final MethodHandle MIOPEN_GET_VERSION;
    private static final MethodHandle CREATE_TENSOR_DESCRIPTOR;
    private static final MethodHandle DESTROY_TENSOR_DESCRIPTOR;
    private static final MethodHandle SET_TENSOR_DESCRIPTOR;
    private static final MethodHandle CREATE_ACTIVATION_DESCRIPTOR;
    private static final MethodHandle DESTROY_ACTIVATION_DESCRIPTOR;
    private static final MethodHandle SET_ACTIVATION_DESCRIPTOR;
    private static final MethodHandle CREATE_POOLING_DESCRIPTOR;
    private static final MethodHandle DESTROY_POOLING_DESCRIPTOR;
    private static final MethodHandle SET_POOLING_2D_DESCRIPTOR;
    private static final MethodHandle CREATE_CONVOLUTION_DESCRIPTOR;
    private static final MethodHandle DESTROY_CONVOLUTION_DESCRIPTOR;
    private static final MethodHandle INIT_CONVOLUTION_DESCRIPTOR;
    private static final MethodHandle SET_CONVOLUTION_ATTRIBUTE;
    private static final MethodHandle GET_CONVOLUTION_FORWARD_OUTPUT_DIM;
    private static final MethodHandle SOFTMAX_FORWARD;
    private static final MethodHandle ACTIVATION_FORWARD;
    private static final MethodHandle POOLING_FORWARD;
    /** Indexed by direction: forward, backward data, backward weights. */
    private static final MethodHandle[] GET_SOLUTION_COUNT = new MethodHandle[3];
    private static final MethodHandle[] GET_SOLUTION = new MethodHandle[3];
    private static final MethodHandle[] COMPILE_SOLUTION = new MethodHandle[3];
    private static final MethodHandle[] IMMEDIATE = new MethodHandle[3];
    private static final MethodHandle HIP_MALLOC;
    private static final MethodHandle HIP_FREE;

    static {
        if (LIBMIOPEN == null) {
            MIOPEN_CREATE = null;
            MIOPEN_DESTROY = null;
            MIOPEN_SET_STREAM = null;
            MIOPEN_GET_VERSION = null;
            CREATE_TENSOR_DESCRIPTOR = null;
            DESTROY_TENSOR_DESCRIPTOR = null;
            SET_TENSOR_DESCRIPTOR = null;
            CREATE_ACTIVATION_DESCRIPTOR = null;
            DESTROY_ACTIVATION_DESCRIPTOR = null;
            SET_ACTIVATION_DESCRIPTOR = null;
            CREATE_POOLING_DESCRIPTOR = null;
            DESTROY_POOLING_DESCRIPTOR = null;
            SET_POOLING_2D_DESCRIPTOR = null;
            CREATE_CONVOLUTION_DESCRIPTOR = null;
            DESTROY_CONVOLUTION_DESCRIPTOR = null;
            INIT_CONVOLUTION_DESCRIPTOR = null;
            SET_CONVOLUTION_ATTRIBUTE = null;
            GET_CONVOLUTION_FORWARD_OUTPUT_DIM = null;
            SOFTMAX_FORWARD = null;
            ACTIVATION_FORWARD = null;
            POOLING_FORWARD = null;
        } else {
            MIOPEN_CREATE = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_POINTER), "miopenCreate");
            MIOPEN_DESTROY = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG), "miopenDestroy");
            MIOPEN_SET_STREAM = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "miopenSetStream");
            MIOPEN_GET_VERSION = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER), "miopenGetVersion");
            CREATE_TENSOR_DESCRIPTOR = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_POINTER), "miopenCreateTensorDescriptor");
            DESTROY_TENSOR_DESCRIPTOR = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG), "miopenDestroyTensorDescriptor");
            SET_TENSOR_DESCRIPTOR = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_POINTER, C_POINTER), "miopenSetTensorDescriptor");
            CREATE_ACTIVATION_DESCRIPTOR = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_POINTER), "miopenCreateActivationDescriptor");
            DESTROY_ACTIVATION_DESCRIPTOR = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG), "miopenDestroyActivationDescriptor");
            SET_ACTIVATION_DESCRIPTOR = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_DOUBLE, C_DOUBLE, C_DOUBLE), "miopenSetActivationDescriptor");
            CREATE_POOLING_DESCRIPTOR = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_POINTER), "miopenCreatePoolingDescriptor");
            DESTROY_POOLING_DESCRIPTOR = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG), "miopenDestroyPoolingDescriptor");
            SET_POOLING_2D_DESCRIPTOR = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT), "miopenSet2dPoolingDescriptor");
            CREATE_CONVOLUTION_DESCRIPTOR = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_POINTER), "miopenCreateConvolutionDescriptor");
            DESTROY_CONVOLUTION_DESCRIPTOR = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG), "miopenDestroyConvolutionDescriptor");
            INIT_CONVOLUTION_DESCRIPTOR = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT), "miopenInitConvolutionDescriptor");
            SET_CONVOLUTION_ATTRIBUTE = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT), "miopenSetConvolutionAttribute");
            GET_CONVOLUTION_FORWARD_OUTPUT_DIM = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_POINTER, C_POINTER, C_POINTER, C_POINTER),
                    "miopenGetConvolutionForwardOutputDim");
            SOFTMAX_FORWARD = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_INT, C_INT), "miopenSoftmaxForward_V2");
            ACTIVATION_FORWARD = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG), "miopenActivationForward");
            POOLING_FORWARD = FFMSupport.downcall(LIBMIOPEN,
                    FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, ValueLayout.JAVA_BOOLEAN, C_LONG, C_LONG), "miopenPoolingForward");
            // the three directions take the same argument types, the descriptors in their own order
            String[] directions = { "Forward", "BackwardData", "BackwardWeights" };
            for (int i = 0; i < directions.length; i++) {
                String prefix = "miopenConvolution" + directions[i];
                GET_SOLUTION_COUNT[i] = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_POINTER), prefix + "GetSolutionCount");
                GET_SOLUTION[i] = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_POINTER, C_POINTER), prefix + "GetSolution");
                COMPILE_SOLUTION[i] = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG), prefix + "CompileSolution");
                IMMEDIATE[i] = FFMSupport.downcall(LIBMIOPEN, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG),
                        prefix + "Immediate");
            }
        }
        if (LIBHIP == null) {
            HIP_MALLOC = null;
            HIP_FREE = null;
        } else {
            HIP_MALLOC = FFMSupport.downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG), "hipMalloc");
            HIP_FREE = FFMSupport.downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipFree");
        }
    }

    /** Scratch for the host-side alpha and beta scalars every forward op passes by pointer. */
    private static final FFMSupport.Staging SCALARS = new FFMSupport.Staging();

    private MIOpenNativeLib() {
    }

    /** Checks that MIOpen is reachable. */
    static synchronized void load() {
        if (LIBMIOPEN == null || MIOPEN_CREATE == null || HIP_MALLOC == null) {
            throw new TornadoRuntimeException("[ERROR] Unable to load MIOpen. Install ROCm with MIOpen and set ROCM_PATH (or HIP_PATH) to its root.");
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
        return (int) constructor.invokeExact(out) == MIOPEN_STATUS_SUCCESS ? out.get(C_POINTER, 0).address() : 0;
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

    /**
     * Sets a 4D descriptor with sizes {@code (n, c, h, w)}: packed NCHW, or with {@code nhwc} the strides
     * of channels-last data (MIOpen infers the layout from the strides).
     */
    private static int setTensor4d(Arena arena, long descriptor, int dataType, int n, int c, int h, int w, boolean nhwc) throws Throwable {
        MemorySegment dims = FFMSupport.allocateArray(arena, C_INT, 4);
        MemorySegment strides = FFMSupport.allocateArray(arena, C_INT, 4);
        int[] d = { n, c, h, w };
        int[] s = nhwc ? new int[] { h * w * c, 1, w * c, c } : new int[] { c * h * w, h * w, w, 1 };
        for (int i = 0; i < 4; i++) {
            dims.setAtIndex(C_INT, i, d[i]);
            strides.setAtIndex(C_INT, i, s[i]);
        }
        return (int) SET_TENSOR_DESCRIPTOR.invokeExact(descriptor, dataType, 4, dims, strides);
    }

    static long miopenCreateHandle() {
        if (MIOPEN_CREATE == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = FFMSupport.allocatePointer(arena);
            if ((int) MIOPEN_CREATE.invokeExact(handle) != MIOPEN_STATUS_SUCCESS) {
                return 0;
            }
            return handle.get(C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int miopenSetStream(long handle, long streamPtr) {
        try {
            return (int) MIOPEN_SET_STREAM.invokeExact(handle, streamPtr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void miopenDestroyHandle(long handle) {
        if (handle == 0 || MIOPEN_DESTROY == null) {
            return;
        }
        try {
            int ignored = (int) MIOPEN_DESTROY.invokeExact(handle);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** MIOpen's version as {@code major * 10000 + minor * 100 + patch}, or 0 when unavailable. */
    static long miopenGetVersionNative() {
        if (MIOPEN_GET_VERSION == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment parts = FFMSupport.allocateArray(arena, C_LONG, 3);
            int status = (int) MIOPEN_GET_VERSION.invokeExact(parts.asSlice(0, Long.BYTES), parts.asSlice(8, Long.BYTES), parts.asSlice(16, Long.BYTES));
            return status == MIOPEN_STATUS_SUCCESS ? parts.get(C_LONG, 0) * 10000 + parts.get(C_LONG, 8) * 100 + parts.get(C_LONG, 16) : 0;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code MIOPEN_SOFTMAX_ACCURATE} with {@code MODE_INSTANCE}: softmax over C*H*W per N. */
    static int softmaxForward(long handle, int n, int c, int h, int w, long dIn, long dOut) {
        if (CREATE_TENSOR_DESCRIPTOR == null) {
            return MIOPEN_STATUS_INTERNAL_ERROR;
        }
        long descriptor = 0;
        try (Arena arena = Arena.ofConfined()) {
            descriptor = createDescriptor(arena, CREATE_TENSOR_DESCRIPTOR);
            if (descriptor == 0) {
                return MIOPEN_STATUS_INTERNAL_ERROR;
            }
            int status = setTensor4d(arena, descriptor, MIOPEN_FLOAT, n, c, h, w, false);
            if (status == MIOPEN_STATUS_SUCCESS) {
                MemorySegment scalars = unitScalars();
                status = (int) SOFTMAX_FORWARD.invokeExact(handle, scalars.asSlice(0, Float.BYTES), descriptor, dIn, scalars.asSlice(Float.BYTES, Float.BYTES), descriptor, dOut,
                        MIOPEN_SOFTMAX_ACCURATE, MIOPEN_SOFTMAX_MODE_INSTANCE);
            }
            return status;
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, descriptor);
        }
    }

    /**
     * {@code mode} is a {@code miopenActivationMode_t}; the data is treated as a flat
     * {@code (1,1,1,size)} tensor. Tanh is {@code beta * tanh(alpha * x)}, so both are 1.
     */
    static int activationForward(long handle, int mode, long size, long dIn, long dOut) {
        if (CREATE_TENSOR_DESCRIPTOR == null) {
            return MIOPEN_STATUS_INTERNAL_ERROR;
        }
        long descriptor = 0;
        long activation = 0;
        try (Arena arena = Arena.ofConfined()) {
            descriptor = createDescriptor(arena, CREATE_TENSOR_DESCRIPTOR);
            activation = descriptor == 0 ? 0 : createDescriptor(arena, CREATE_ACTIVATION_DESCRIPTOR);
            if (activation == 0) {
                return MIOPEN_STATUS_INTERNAL_ERROR;
            }
            int status = setTensor4d(arena, descriptor, MIOPEN_FLOAT, 1, 1, 1, (int) size, false);
            if (status == MIOPEN_STATUS_SUCCESS) {
                status = (int) SET_ACTIVATION_DESCRIPTOR.invokeExact(activation, mode, 1.0d, 1.0d, 1.0d);
            }
            if (status == MIOPEN_STATUS_SUCCESS) {
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
     * Square window and stride max pooling, no padding, inference only (no index workspace). Output dims:
     * {@code (h - window) / stride + 1}, {@code (w - window) / stride + 1}.
     */
    static int poolingMaxForward(long handle, int n, int c, int h, int w, int window, int stride, long dIn, long dOut) {
        if (CREATE_TENSOR_DESCRIPTOR == null) {
            return MIOPEN_STATUS_INTERNAL_ERROR;
        }
        long inputDesc = 0;
        long outputDesc = 0;
        long poolingDesc = 0;
        try (Arena arena = Arena.ofConfined()) {
            inputDesc = createDescriptor(arena, CREATE_TENSOR_DESCRIPTOR);
            outputDesc = inputDesc == 0 ? 0 : createDescriptor(arena, CREATE_TENSOR_DESCRIPTOR);
            poolingDesc = outputDesc == 0 ? 0 : createDescriptor(arena, CREATE_POOLING_DESCRIPTOR);
            if (poolingDesc == 0) {
                return MIOPEN_STATUS_INTERNAL_ERROR;
            }
            int outH = (h - window) / stride + 1;
            int outW = (w - window) / stride + 1;
            int status = setTensor4d(arena, inputDesc, MIOPEN_FLOAT, n, c, h, w, false);
            if (status == MIOPEN_STATUS_SUCCESS) {
                status = setTensor4d(arena, outputDesc, MIOPEN_FLOAT, n, c, outH, outW, false);
            }
            if (status == MIOPEN_STATUS_SUCCESS) {
                status = (int) SET_POOLING_2D_DESCRIPTOR.invokeExact(poolingDesc, MIOPEN_POOLING_MAX, window, window, 0, 0, stride, stride);
            }
            if (status == MIOPEN_STATUS_SUCCESS) {
                MemorySegment scalars = unitScalars();
                status = (int) POOLING_FORWARD.invokeExact(handle, poolingDesc, scalars.asSlice(0, Float.BYTES), inputDesc, dIn, scalars.asSlice(Float.BYTES, Float.BYTES), outputDesc, dOut,
                        false, 0L, 0L);
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

    /** The descriptors and compiled solution one convolution shape and direction needs. */
    private record ConvPlan(long inputDesc, long filterDesc, long convDesc, long outputDesc, int direction, long solutionId, long workspaceBytes) {
    }

    private static final AtomicLong NEXT_CONV_PLAN = new AtomicLong(1);

    private static final ConcurrentHashMap<Long, ConvPlan> CONV_PLANS = new ConcurrentHashMap<>();

    /** Size of {@code miopenConvSolution_t}, and the offsets of the fields read from it. */
    private static final int SOLUTION_BYTES = 32;
    private static final int SOLUTION_WORKSPACE = 8;
    private static final int SOLUTION_ID = 16;
    private static final int SOLUTION_COUNT = 8;

    /**
     * {@link CuDnnNativeLib#createConvPlan(long, int, int, int, int, int, int, int, int, int, boolean, boolean, int)}
     * on MIOpen: input {@code (n,c,h,w)}, filter {@code (k,c,r,s)}, NCHW/KCRS or, with {@code nhwc},
     * NHWC/KRSC. FP16 tensors accumulate in FP32. The solution is the one MIOpen ranks first for the
     * shape and direction (with {@code -Dtornado.cudnn.deterministic=true}, among deterministic ones),
     * compiled here so that running it later builds no kernels. Returns 0 on failure.
     */
    static long createConvPlan(long handle, int n, int c, int h, int w, int k, int r, int s, int pad, int stride, boolean half, boolean nhwc, int direction) {
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
            filterDesc = createDescriptor(arena, CREATE_TENSOR_DESCRIPTOR);
            convDesc = createDescriptor(arena, CREATE_CONVOLUTION_DESCRIPTOR);
            outputDesc = createDescriptor(arena, CREATE_TENSOR_DESCRIPTOR);
            if (inputDesc == 0 || filterDesc == 0 || convDesc == 0 || outputDesc == 0) {
                return 0;
            }
            int dataType = half ? MIOPEN_HALF : MIOPEN_FLOAT;
            int status = setTensor4d(arena, inputDesc, dataType, n, c, h, w, nhwc);
            if (status == MIOPEN_STATUS_SUCCESS) {
                status = setTensor4d(arena, filterDesc, dataType, k, c, r, s, nhwc);
            }
            if (status == MIOPEN_STATUS_SUCCESS) {
                status = (int) INIT_CONVOLUTION_DESCRIPTOR.invokeExact(convDesc, MIOPEN_CONVOLUTION, pad, pad, stride, stride, 1, 1);
            }
            if (status == MIOPEN_STATUS_SUCCESS && Boolean.getBoolean("tornado.cudnn.deterministic")) {
                status = (int) SET_CONVOLUTION_ATTRIBUTE.invokeExact(convDesc, MIOPEN_CONVOLUTION_ATTRIB_DETERMINISTIC, 1);
            }
            MemorySegment dims = FFMSupport.allocateArray(arena, C_INT, 4);
            if (status == MIOPEN_STATUS_SUCCESS) {
                status = (int) GET_CONVOLUTION_FORWARD_OUTPUT_DIM.invokeExact(convDesc, inputDesc, filterDesc, dims.asSlice(0, Integer.BYTES), dims.asSlice(4, Integer.BYTES),
                        dims.asSlice(8, Integer.BYTES), dims.asSlice(12, Integer.BYTES));
            }
            if (status == MIOPEN_STATUS_SUCCESS) {
                status = setTensor4d(arena, outputDesc, dataType, dims.get(C_INT, 0), dims.get(C_INT, 4), dims.get(C_INT, 8), dims.get(C_INT, 12), nhwc);
            }
            if (status != MIOPEN_STATUS_SUCCESS) {
                return 0;
            }
            // each direction's queries take the descriptors in the order of its immediate call
            long[] order = switch (direction) {
                case CuDnnNativeLib.CONV_BACKWARD_DATA -> new long[] { outputDesc, filterDesc, inputDesc };
                case CuDnnNativeLib.CONV_BACKWARD_FILTER -> new long[] { outputDesc, inputDesc, filterDesc };
                default -> new long[] { filterDesc, inputDesc, outputDesc };
            };
            MemorySegment count = FFMSupport.allocateLong(arena);
            MemorySegment solutions = arena.allocate((long) SOLUTION_BYTES * SOLUTION_COUNT, 8);
            status = (int) GET_SOLUTION[direction].invokeExact(handle, order[0], order[1], convDesc, order[2], (long) SOLUTION_COUNT, count, solutions);
            if (status != MIOPEN_STATUS_SUCCESS || count.get(C_LONG, 0) == 0) {
                return 0;
            }
            long solutionId = solutions.get(C_LONG, SOLUTION_ID);
            long workspaceBytes = solutions.get(C_LONG, SOLUTION_WORKSPACE);
            status = (int) COMPILE_SOLUTION[direction].invokeExact(handle, order[0], order[1], convDesc, order[2], solutionId);
            if (status != MIOPEN_STATUS_SUCCESS) {
                return 0;
            }
            long planHandle = NEXT_CONV_PLAN.getAndIncrement();
            CONV_PLANS.put(planHandle, new ConvPlan(inputDesc, filterDesc, convDesc, outputDesc, direction, solutionId, workspaceBytes));
            keep = true;
            return planHandle;
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            if (!keep) {
                destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, outputDesc);
                destroyDescriptor(DESTROY_CONVOLUTION_DESCRIPTOR, convDesc);
                destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, filterDesc);
                destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, inputDesc);
            }
        }
    }

    static long convPlanWorkspaceBytes(long planPtr) {
        ConvPlan plan = CONV_PLANS.get(planPtr);
        return plan == null ? 0 : plan.workspaceBytes;
    }

    private static int immediate(long handle, long planPtr, int direction, long desc1, long desc2, long desc3, long ptr1, long ptr2, long ptr3, long workspacePtr, long workspaceBytes) {
        ConvPlan plan = CONV_PLANS.get(planPtr);
        if (plan == null || plan.direction != direction) {
            return MIOPEN_STATUS_INTERNAL_ERROR;
        }
        try {
            return (int) IMMEDIATE[direction].invokeExact(handle, desc1, ptr1, desc2, ptr2, plan.convDesc, desc3, ptr3, workspacePtr, workspaceBytes, plan.solutionId);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int convForward(long handle, long planPtr, long dIn, long dFilter, long dOut, long workspacePtr, long workspaceBytes) {
        ConvPlan plan = CONV_PLANS.get(planPtr);
        if (plan == null) {
            return MIOPEN_STATUS_INTERNAL_ERROR;
        }
        return immediate(handle, planPtr, CuDnnNativeLib.CONV_FORWARD, plan.filterDesc, plan.inputDesc, plan.outputDesc, dFilter, dIn, dOut, workspacePtr, workspaceBytes);
    }

    /** dInput = the gradient of the input of the plan's forward convolution, from its filter and dOutput. */
    static int convBackwardData(long handle, long planPtr, long dFilter, long dOutput, long dInput, long workspacePtr, long workspaceBytes) {
        ConvPlan plan = CONV_PLANS.get(planPtr);
        if (plan == null) {
            return MIOPEN_STATUS_INTERNAL_ERROR;
        }
        return immediate(handle, planPtr, CuDnnNativeLib.CONV_BACKWARD_DATA, plan.outputDesc, plan.filterDesc, plan.inputDesc, dOutput, dFilter, dInput, workspacePtr, workspaceBytes);
    }

    /** dFilter = the gradient of the filter of the plan's forward convolution, from its input and dOutput. */
    static int convBackwardFilter(long handle, long planPtr, long dInput, long dOutput, long dFilter, long workspacePtr, long workspaceBytes) {
        ConvPlan plan = CONV_PLANS.get(planPtr);
        if (plan == null) {
            return MIOPEN_STATUS_INTERNAL_ERROR;
        }
        return immediate(handle, planPtr, CuDnnNativeLib.CONV_BACKWARD_FILTER, plan.outputDesc, plan.inputDesc, plan.filterDesc, dOutput, dInput, dFilter, workspacePtr, workspaceBytes);
    }

    static void destroyConvPlan(long planPtr) {
        ConvPlan plan = CONV_PLANS.remove(planPtr);
        if (plan == null) {
            return;
        }
        destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, plan.outputDesc);
        destroyDescriptor(DESTROY_CONVOLUTION_DESCRIPTOR, plan.convDesc);
        destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, plan.filterDesc);
        destroyDescriptor(DESTROY_TENSOR_DESCRIPTOR, plan.inputDesc);
    }

    static long allocateDeviceMemory(long bytes) {
        if (HIP_MALLOC == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pointer = FFMSupport.allocatePointer(arena);
            if ((int) HIP_MALLOC.invokeExact(pointer, bytes) != 0) {
                return 0;
            }
            return pointer.get(C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void freeDeviceMemory(long ptr) {
        if (ptr == 0 || HIP_FREE == null) {
            return;
        }
        try {
            int ignored = (int) HIP_FREE.invokeExact(ptr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static String decodeStatus(int status) {
        return switch (status) {
            case 0 -> "miopenStatusSuccess";
            case 1 -> "miopenStatusNotInitialized";
            case 2 -> "miopenStatusInvalidValue";
            case 3 -> "miopenStatusBadParm";
            case 4 -> "miopenStatusAllocFailed";
            case 5 -> "miopenStatusInternalError";
            case 6 -> "miopenStatusNotImplemented";
            case 7 -> "miopenStatusUnknownError";
            case 8 -> "miopenStatusUnsupportedOp";
            case 9 -> "miopenStatusGpuOperationsSkipped";
            case 10 -> "miopenStatusVersionMismatch";
            default -> "UNKNOWN_MIOPEN_STATUS (" + status + ")";
        };
    }

    static void checkStatus(int status, String function) {
        if (status != 0) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed with status: " + decodeStatus(status));
        }
    }
}
