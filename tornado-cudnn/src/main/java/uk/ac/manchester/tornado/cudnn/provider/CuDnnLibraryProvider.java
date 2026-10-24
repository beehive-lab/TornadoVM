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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoLibraryTaskNotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cudnn.CuDnn;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} for NVIDIA cuDNN (FP32/NCHW legacy API). The
 * per-(device, execution plan) context holds the cudnn handle bound to the
 * plan's stream, a cache of convolution plans (descriptors + algorithm +
 * workspace size) per shape, and a grow-only device workspace. Convolution
 * plans and the workspace are created in {@link #prepare} - before CUDA graph
 * capture starts - so {@link #dispatch} is capture-safe. A plan's task graphs
 * share the context and are captured one after another, so a workspace that
 * is outgrown stays allocated until the context is destroyed: a graph captured
 * earlier still uses it.
 */
public final class CuDnnLibraryProvider implements TornadoLibraryProvider {

    private static final class CuDnnContext implements LibraryContext {
        private final long handle;
        /** The stream the handle is bound to. */
        private long stream;
        private final Map<String, Long> convPlanCache = new HashMap<>();
        private final Map<String, Long> sdpaPlanCache = new HashMap<>();
        private long workspacePtr;
        private long workspaceBytes;
        /** Workspaces outgrown by later plans, kept until the context is destroyed. */
        private final List<Long> retiredWorkspaces = new ArrayList<>();

        private CuDnnContext(long handle, long stream) {
            this.handle = handle;
            this.stream = stream;
        }

        private void growWorkspace(long required) {
            if (required > workspaceBytes) {
                if (workspacePtr != 0) {
                    // a CUDA graph captured earlier in this plan may point at it
                    retiredWorkspaces.add(workspacePtr);
                    workspacePtr = 0;
                }
                // whole powers of two: the retired workspaces together stay smaller than the live one
                required = required <= 1 ? 1 : Long.highestOneBit(required - 1) << 1;
                long ptr = CuDnnNativeLib.allocateDeviceMemory(required);
                if (ptr == 0) {
                    throw new TornadoRuntimeException("[ERROR] Unable to allocate cuDNN workspace of " + required + " bytes");
                }
                workspacePtr = ptr;
                workspaceBytes = required;
            }
        }
    }


    /**
     * Whether cuDNN can actually be reached on this host. The ops surface no longer ships a JNI
     * library of its own, so this is a question about the cuDNN installation; the fused SDPA path,
     * which does still need a shim, reports its own absence when it is used.
     */
    public static boolean isAvailable() {
        try {
            CuDnnNativeLib.load();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public String libraryName() {
        return CuDnn.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        // CUDA devices only: the Metal backend also implements TornadoNativeStreamSupport
        return device.getTornadoVMBackend() == TornadoVMBackendType.CUDA && device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        CuDnnNativeLib.load();
        long stream = ((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId);
        long handle = CuDnnNativeLib.cudnnCreateHandle();
        if (handle == 0) {
            throw new TornadoRuntimeException("[ERROR] cudnnCreate failed");
        }
        CuDnnNativeLib.checkStatus(CuDnnNativeLib.cudnnSetStream(handle, stream), "cudnnSetStream");
        return new CuDnnContext(handle, stream);
    }

    @Override
    public void prepare(LibraryTaskDescriptor descriptor, LibraryContext context) {
        Object[] p = descriptor.getParameters();
        switch (descriptor.getFunctionName()) {
            case "cudnnConv2d", "cudnnConv2dFP16", "cudnnConv2dNHWC", "cudnnConv2dNHWCFP16", "cudnnConv2dBackwardData", "cudnnConv2dBackwardDataFP16", "cudnnConv2dBackwardFilter",
                    "cudnnConv2dBackwardFilterFP16" -> getOrCreateConvPlan((CuDnnContext) context, (int) p[3], (int) p[4], (int) p[5], (int) p[6], (int) p[7], (int) p[8], (int) p[9], (int) p[10],
                            (int) p[11], descriptor.getFunctionName().endsWith("FP16"), descriptor.getFunctionName().contains("NHWC"), direction(descriptor.getFunctionName()));
            case "sdpaForward" -> getOrCreateSdpaPlan((CuDnnContext) context, (int) p[4], (int) p[5], (int) p[6], (int) p[7], (int) p[8], (float) p[9], (boolean) p[10]);
            case "sdpaForwardPackedQkv" -> getOrCreatePackedSdpaPlan((CuDnnContext) context, (int) p[2], (int) p[3], (int) p[4], (int) p[5], (float) p[6], (boolean) p[7]);
            default -> {
                // simple ops have no per-shape native state
            }
        }
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        CuDnnContext context = (CuDnnContext) invocation.getContext();
        // The stream belongs to the thread that runs the plan, which need not be the one that
        // created this context: follow it, or the call runs on another thread's stream, unordered
        // with the kernels that produce its inputs.
        long stream = ((TornadoNativeStreamSupport) invocation.getDevice()).getNativeStream(invocation.getExecutionPlanId());
        if (stream != context.stream) {
            CuDnnNativeLib.checkStatus(CuDnnNativeLib.cudnnSetStream(context.handle, stream), "cudnnSetStream");
            context.stream = stream;
        }
        int status = switch (functionName) {
            // (input, output, rows, cols): per-row softmax as (n=rows, c=cols, 1, 1)
            case "cudnnSoftmax" -> CuDnnNativeLib.softmaxForward(context.handle, //
                    (int) invocation.getArg(2), (int) invocation.getArg(3), 1, 1, //
                    invocation.getDevicePointer(0), invocation.getDevicePointer(1));
            case "cudnnRelu" -> activation(context, invocation, CuDnnNativeLib.CUDNN_ACTIVATION_RELU);
            case "cudnnSigmoid" -> activation(context, invocation, CuDnnNativeLib.CUDNN_ACTIVATION_SIGMOID);
            case "cudnnTanh" -> activation(context, invocation, CuDnnNativeLib.CUDNN_ACTIVATION_TANH);
            // (input, output, n, c, h, w, window, stride)
            case "cudnnMaxPool2d" -> CuDnnNativeLib.poolingMaxForward(context.handle, //
                    (int) invocation.getArg(2), (int) invocation.getArg(3), (int) invocation.getArg(4), (int) invocation.getArg(5), //
                    (int) invocation.getArg(6), (int) invocation.getArg(7), //
                    invocation.getDevicePointer(0), invocation.getDevicePointer(1));
            case "cudnnConv2d" -> conv2d(context, invocation, false, false);
            case "cudnnConv2dFP16" -> conv2d(context, invocation, true, false);
            case "cudnnConv2dNHWC" -> conv2d(context, invocation, false, true);
            case "cudnnConv2dNHWCFP16" -> conv2d(context, invocation, true, true);
            case "cudnnConv2dBackwardData", "cudnnConv2dBackwardDataFP16", "cudnnConv2dBackwardFilter", "cudnnConv2dBackwardFilterFP16" -> conv2dBackward(context, invocation,
                    functionName.endsWith("FP16"), direction(functionName));
            case "sdpaForward" -> sdpa(context, invocation);
            case "sdpaForwardPackedQkv" -> sdpaPacked(context, invocation);
            default -> throw new TornadoRuntimeException("[ERROR] cuDNN function not supported: " + functionName);
        };
        CuDnnNativeLib.checkStatus(status, functionName);
    }

    /** (input, output, size). */
    private static int activation(CuDnnContext context, LibraryInvocation invocation, int mode) {
        return CuDnnNativeLib.activationForward(context.handle, mode, (int) invocation.getArg(2), invocation.getDevicePointer(0), invocation.getDevicePointer(1));
    }

    /** (input, filter, output, n, c, h, w, k, r, s, pad, stride). */
    private static int conv2d(CuDnnContext context, LibraryInvocation invocation, boolean half, boolean nhwc) {
        long plan = getOrCreateConvPlan(context, //
                (int) invocation.getArg(3), (int) invocation.getArg(4), (int) invocation.getArg(5), (int) invocation.getArg(6), //
                (int) invocation.getArg(7), (int) invocation.getArg(8), (int) invocation.getArg(9), //
                (int) invocation.getArg(10), (int) invocation.getArg(11), half, nhwc, CuDnnNativeLib.CONV_FORWARD);
        return CuDnnNativeLib.convForward(context.handle, plan, //
                invocation.getDevicePointer(0), invocation.getDevicePointer(1), invocation.getDevicePointer(2), //
                context.workspacePtr, context.workspaceBytes);
    }

    private static int direction(String functionName) {
        return functionName.startsWith("cudnnConv2dBackwardData") ? CuDnnNativeLib.CONV_BACKWARD_DATA
                : functionName.startsWith("cudnnConv2dBackwardFilter") ? CuDnnNativeLib.CONV_BACKWARD_FILTER : CuDnnNativeLib.CONV_FORWARD;
    }

    /**
     * Data: (filter, dOutput, dInput, n, c, h, w, k, r, s, pad, stride). Filter: (input, dOutput,
     * dFilter, ...). The sizes are those of the forward convolution.
     */
    private static int conv2dBackward(CuDnnContext context, LibraryInvocation invocation, boolean half, int direction) {
        long plan = getOrCreateConvPlan(context, //
                (int) invocation.getArg(3), (int) invocation.getArg(4), (int) invocation.getArg(5), (int) invocation.getArg(6), //
                (int) invocation.getArg(7), (int) invocation.getArg(8), (int) invocation.getArg(9), //
                (int) invocation.getArg(10), (int) invocation.getArg(11), half, false, direction);
        return direction == CuDnnNativeLib.CONV_BACKWARD_DATA
                ? CuDnnNativeLib.convBackwardData(context.handle, plan, invocation.getDevicePointer(0), invocation.getDevicePointer(1), invocation.getDevicePointer(2), context.workspacePtr,
                        context.workspaceBytes)
                : CuDnnNativeLib.convBackwardFilter(context.handle, plan, invocation.getDevicePointer(0), invocation.getDevicePointer(1), invocation.getDevicePointer(2), context.workspacePtr,
                        context.workspaceBytes);
    }

    /** (q, k, v, o, b, h, sQ, sKv, d, scale, causal). */
    private static int sdpa(CuDnnContext context, LibraryInvocation invocation) {
        long plan = getOrCreateSdpaPlan(context, //
                (int) invocation.getArg(4), (int) invocation.getArg(5), (int) invocation.getArg(6), (int) invocation.getArg(7), (int) invocation.getArg(8), //
                (float) invocation.getArg(9), (boolean) invocation.getArg(10));
        return CuDnnNativeLib.executeSdpaPlan(context.handle, plan, //
                invocation.getDevicePointer(0), invocation.getDevicePointer(1), invocation.getDevicePointer(2), invocation.getDevicePointer(3), //
                context.workspacePtr);
    }

    /** (qkv, o, b, h, s, d, scale, causal): q, k and v at element offsets 0, h d and 2 h d of each token's row. */
    private static int sdpaPacked(CuDnnContext context, LibraryInvocation invocation) {
        int h = (int) invocation.getArg(3);
        int d = (int) invocation.getArg(5);
        long plan = getOrCreatePackedSdpaPlan(context, (int) invocation.getArg(2), h, (int) invocation.getArg(4), d, (float) invocation.getArg(6), (boolean) invocation.getArg(7));
        long qkv = invocation.getDevicePointer(0);
        long headBytes = (long) h * d * Short.BYTES;
        return CuDnnNativeLib.executeSdpaPlan(context.handle, plan, qkv, qkv + headBytes, qkv + 2 * headBytes, invocation.getDevicePointer(1), context.workspacePtr);
    }

    private static long getOrCreatePackedSdpaPlan(CuDnnContext context, int b, int h, int s, int d, float scale, boolean causal) {
        String planKey = "packed:" + b + ":" + h + ":" + s + ":" + d + ":" + scale + ":" + causal;
        Long plan = context.sdpaPlanCache.get(planKey);
        if (plan == null) {
            CuDnnNativeLib.loadSdpaShim();
            long row = 3L * h * d;
            long qkvB = s * row;
            long out = (long) h * d;
            // element strides of (batch, head, token, dim) for q, k, v, then o
            long[] strides = { qkvB, d, row, 1, qkvB, d, row, 1, qkvB, d, row, 1, s * out, d, out, 1 };
            plan = CuDnnNativeLib.createSdpaPlanStrided(context.handle, b, h, s, s, d, scale, causal, strides);
            if (plan == 0) {
                if (!hasSdpaSupport(context)) {
                    throw new TornadoLibraryTaskNotSupported("[UNSUPPORTED] cuDNN fused SDPA is not available for this device/build " //
                            + "(requires an Ampere sm_80 or later GPU and libtornado-cudnn built against CUDA 12.0+). Shape: " + planKey);
                }
                throw new TornadoRuntimeException("[ERROR] cuDNN SDPA plan creation failed for " + planKey + " (requires Ampere+, head dim multiple of 8, <= 256)");
            }
            context.growWorkspace(CuDnnNativeLib.sdpaPlanWorkspaceBytes(plan));
            context.sdpaPlanCache.put(planKey, plan);
        }
        return plan;
    }

    /**
     * Whether fused SDPA works at all here, told apart from a shape cuDNN rejects by building a
     * plan for one it always accepts. Fails before Ampere, and under the stub shim that a CUDA
     * 11 build links in place of cudnn-frontend.
     */
    private static boolean hasSdpaSupport(CuDnnContext context) {
        final int dim = 64;
        long probe = CuDnnNativeLib.createSdpaPlan(context.handle, 1, 1, dim, dim, dim, 0.125f, false);
        if (probe == 0) {
            return false;
        }
        CuDnnNativeLib.destroySdpaPlan(probe);
        return true;
    }

    private static long getOrCreateSdpaPlan(CuDnnContext context, int b, int h, int sQ, int sKv, int d, float scale, boolean causal) {
        String planKey = b + ":" + h + ":" + sQ + ":" + sKv + ":" + d + ":" + scale + ":" + causal;
        Long plan = context.sdpaPlanCache.get(planKey);
        if (plan == null) {
            // Only this path needs the remaining JNI shim, so it is loaded here rather than with
            // the provider: the other cuDNN ops work without it.
            CuDnnNativeLib.loadSdpaShim();
            plan = CuDnnNativeLib.createSdpaPlan(context.handle, b, h, sQ, sKv, d, scale, causal);
            if (plan == 0) {
                if (!hasSdpaSupport(context)) {
                    throw new TornadoLibraryTaskNotSupported("[UNSUPPORTED] cuDNN fused SDPA is not available for this device/build " //
                            + "(requires an Ampere sm_80 or later GPU and libtornado-cudnn built against CUDA 12.0+). Shape: " + planKey);
                }
                throw new TornadoRuntimeException("[ERROR] cuDNN SDPA plan creation failed for " + planKey + " (requires Ampere+, head dim multiple of 8, <= 256)");
            }
            context.growWorkspace(CuDnnNativeLib.sdpaPlanWorkspaceBytes(plan));
            context.sdpaPlanCache.put(planKey, plan);
        }
        return plan;
    }

    private static long getOrCreateConvPlan(CuDnnContext context, int n, int c, int h, int w, int k, int r, int s, int pad, int stride, boolean half, boolean nhwc, int direction) {
        String planKey = n + ":" + c + ":" + h + ":" + w + ":" + k + ":" + r + ":" + s + ":" + pad + ":" + stride + (half ? ":f16" : ":f32") + (nhwc ? ":nhwc" : ":nchw") + ":" + direction;
        Long plan = context.convPlanCache.get(planKey);
        if (plan == null) {
            plan = CuDnnNativeLib.createConvPlan(context.handle, n, c, h, w, k, r, s, pad, stride, half, nhwc, direction);
            if (plan == 0) {
                throw new TornadoRuntimeException("[ERROR] cuDNN convolution plan creation failed for " + planKey);
            }
            context.growWorkspace(CuDnnNativeLib.convPlanWorkspaceBytes(plan));
            context.convPlanCache.put(planKey, plan);
        }
        return plan;
    }

    @Override
    public void destroyContext(LibraryContext context) {
        CuDnnContext cuDnnContext = (CuDnnContext) context;
        for (Long plan : cuDnnContext.convPlanCache.values()) {
            CuDnnNativeLib.destroyConvPlan(plan);
        }
        cuDnnContext.convPlanCache.clear();
        for (Long plan : cuDnnContext.sdpaPlanCache.values()) {
            CuDnnNativeLib.destroySdpaPlan(plan);
        }
        cuDnnContext.sdpaPlanCache.clear();
        for (long retired : cuDnnContext.retiredWorkspaces) {
            CuDnnNativeLib.freeDeviceMemory(retired);
        }
        cuDnnContext.retiredWorkspaces.clear();
        if (cuDnnContext.workspacePtr != 0) {
            CuDnnNativeLib.freeDeviceMemory(cuDnnContext.workspacePtr);
            cuDnnContext.workspacePtr = 0;
        }
        CuDnnNativeLib.cudnnDestroyHandle(cuDnnContext.handle);
    }
}
