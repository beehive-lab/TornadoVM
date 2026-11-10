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
 * {@link TornadoLibraryProvider} for {@link CuDnn} library tasks on HIP devices, with AMD MIOpen: the
 * counterpart of {@link CuDnnLibraryProvider}, with the same per-(device, execution plan) context (a
 * MIOpen handle bound to the plan's stream, convolution plans per shape and direction created in
 * {@link #prepare} before graph capture, and a grow-only workspace whose outgrown predecessors stay
 * allocated until the context is destroyed). The fused SDPA functions are not handled here: MIOpen's
 * attention operation has no FP16 solution on these GPUs.
 */
public final class MIOpenLibraryProvider implements TornadoLibraryProvider {

    private static final class MIOpenContext implements LibraryContext {
        private final long handle;
        /** The stream the handle is bound to. */
        private long stream;
        private final Map<String, Long> convPlanCache = new HashMap<>();
        private long workspacePtr;
        private long workspaceBytes;
        /** Workspaces outgrown by later plans, kept until the context is destroyed. */
        private final List<Long> retiredWorkspaces = new ArrayList<>();

        private MIOpenContext(long handle, long stream) {
            this.handle = handle;
            this.stream = stream;
        }

        private void growWorkspace(long required) {
            if (required > workspaceBytes) {
                if (workspacePtr != 0) {
                    // a HIP graph captured earlier in this plan may point at it
                    retiredWorkspaces.add(workspacePtr);
                    workspacePtr = 0;
                }
                // whole powers of two: the retired workspaces together stay smaller than the live one
                required = required <= 1 ? 1 : Long.highestOneBit(required - 1) << 1;
                long ptr = MIOpenNativeLib.allocateDeviceMemory(required);
                if (ptr == 0) {
                    throw new TornadoRuntimeException("[ERROR] Unable to allocate MIOpen workspace of " + required + " bytes");
                }
                workspacePtr = ptr;
                workspaceBytes = required;
            }
        }
    }

    /** Whether MIOpen can be reached on this host. */
    public static boolean isAvailable() {
        try {
            MIOpenNativeLib.load();
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
        return device.getTornadoVMBackend() == TornadoVMBackendType.HIP && device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        MIOpenNativeLib.load();
        long stream = ((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId);
        long handle = MIOpenNativeLib.miopenCreateHandle();
        if (handle == 0) {
            throw new TornadoRuntimeException("[ERROR] miopenCreate failed");
        }
        MIOpenNativeLib.checkStatus(MIOpenNativeLib.miopenSetStream(handle, stream), "miopenSetStream");
        return new MIOpenContext(handle, stream);
    }

    @Override
    public void prepare(LibraryTaskDescriptor descriptor, LibraryContext context) {
        Object[] p = descriptor.getParameters();
        switch (descriptor.getFunctionName()) {
            case "cudnnConv2d", "cudnnConv2dFP16", "cudnnConv2dNHWC", "cudnnConv2dNHWCFP16", "cudnnConv2dBackwardData", "cudnnConv2dBackwardDataFP16", "cudnnConv2dBackwardFilter",
                    "cudnnConv2dBackwardFilterFP16" -> getOrCreateConvPlan((MIOpenContext) context, (int) p[3], (int) p[4], (int) p[5], (int) p[6], (int) p[7], (int) p[8], (int) p[9], (int) p[10],
                            (int) p[11], descriptor.getFunctionName().endsWith("FP16"), descriptor.getFunctionName().contains("NHWC"), direction(descriptor.getFunctionName()));
            case "sdpaForward", "sdpaForwardPackedQkv" -> throw sdpaNotSupported(descriptor.getFunctionName());
            default -> {
                // simple ops have no per-shape native state
            }
        }
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        MIOpenContext context = (MIOpenContext) invocation.getContext();
        // The stream belongs to the thread that runs the plan, which need not be the one that
        // created this context: follow it, or the call runs on another thread's stream, unordered
        // with the kernels that produce its inputs.
        long stream = ((TornadoNativeStreamSupport) invocation.getDevice()).getNativeStream(invocation.getExecutionPlanId());
        if (stream != context.stream) {
            MIOpenNativeLib.checkStatus(MIOpenNativeLib.miopenSetStream(context.handle, stream), "miopenSetStream");
            context.stream = stream;
        }
        int status = switch (functionName) {
            // (input, output, rows, cols): per-row softmax as (n=rows, c=cols, 1, 1)
            case "cudnnSoftmax" -> MIOpenNativeLib.softmaxForward(context.handle, //
                    (int) invocation.getArg(2), (int) invocation.getArg(3), 1, 1, //
                    invocation.getDevicePointer(0), invocation.getDevicePointer(1));
            case "cudnnRelu" -> activation(context, invocation, MIOpenNativeLib.MIOPEN_ACTIVATION_RELU);
            case "cudnnSigmoid" -> activation(context, invocation, MIOpenNativeLib.MIOPEN_ACTIVATION_LOGISTIC);
            case "cudnnTanh" -> activation(context, invocation, MIOpenNativeLib.MIOPEN_ACTIVATION_TANH);
            // (input, output, n, c, h, w, window, stride)
            case "cudnnMaxPool2d" -> MIOpenNativeLib.poolingMaxForward(context.handle, //
                    (int) invocation.getArg(2), (int) invocation.getArg(3), (int) invocation.getArg(4), (int) invocation.getArg(5), //
                    (int) invocation.getArg(6), (int) invocation.getArg(7), //
                    invocation.getDevicePointer(0), invocation.getDevicePointer(1));
            case "cudnnConv2d" -> conv2d(context, invocation, false, false);
            case "cudnnConv2dFP16" -> conv2d(context, invocation, true, false);
            case "cudnnConv2dNHWC" -> conv2d(context, invocation, false, true);
            case "cudnnConv2dNHWCFP16" -> conv2d(context, invocation, true, true);
            case "cudnnConv2dBackwardData", "cudnnConv2dBackwardDataFP16", "cudnnConv2dBackwardFilter", "cudnnConv2dBackwardFilterFP16" -> conv2dBackward(context, invocation,
                    functionName.endsWith("FP16"), direction(functionName));
            case "sdpaForward", "sdpaForwardPackedQkv" -> throw sdpaNotSupported(functionName);
            default -> throw new TornadoRuntimeException("[ERROR] MIOpen does not implement cuDNN function: " + functionName);
        };
        MIOpenNativeLib.checkStatus(status, functionName);
    }

    /** (input, output, size). */
    private static int activation(MIOpenContext context, LibraryInvocation invocation, int mode) {
        return MIOpenNativeLib.activationForward(context.handle, mode, (int) invocation.getArg(2), invocation.getDevicePointer(0), invocation.getDevicePointer(1));
    }

    /** (input, filter, output, n, c, h, w, k, r, s, pad, stride). */
    private static int conv2d(MIOpenContext context, LibraryInvocation invocation, boolean half, boolean nhwc) {
        long plan = getOrCreateConvPlan(context, //
                (int) invocation.getArg(3), (int) invocation.getArg(4), (int) invocation.getArg(5), (int) invocation.getArg(6), //
                (int) invocation.getArg(7), (int) invocation.getArg(8), (int) invocation.getArg(9), //
                (int) invocation.getArg(10), (int) invocation.getArg(11), half, nhwc, CuDnnNativeLib.CONV_FORWARD);
        return MIOpenNativeLib.convForward(context.handle, plan, //
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
    private static int conv2dBackward(MIOpenContext context, LibraryInvocation invocation, boolean half, int direction) {
        long plan = getOrCreateConvPlan(context, //
                (int) invocation.getArg(3), (int) invocation.getArg(4), (int) invocation.getArg(5), (int) invocation.getArg(6), //
                (int) invocation.getArg(7), (int) invocation.getArg(8), (int) invocation.getArg(9), //
                (int) invocation.getArg(10), (int) invocation.getArg(11), half, false, direction);
        return direction == CuDnnNativeLib.CONV_BACKWARD_DATA
                ? MIOpenNativeLib.convBackwardData(context.handle, plan, invocation.getDevicePointer(0), invocation.getDevicePointer(1), invocation.getDevicePointer(2), context.workspacePtr,
                        context.workspaceBytes)
                : MIOpenNativeLib.convBackwardFilter(context.handle, plan, invocation.getDevicePointer(0), invocation.getDevicePointer(1), invocation.getDevicePointer(2), context.workspacePtr,
                        context.workspaceBytes);
    }

    /**
     * MIOpen's multi-head attention operation has a solution only for FP32 tensors in packed BHSD layout
     * on these GPUs; it accepts neither the FP16 tensors nor the strides these functions take.
     */
    private static TornadoLibraryTaskNotSupported sdpaNotSupported(String functionName) {
        return new TornadoLibraryTaskNotSupported("[UNSUPPORTED] " + functionName + " (fused FP16 attention) is not available on HIP devices: MIOpen has no FP16 attention solution");
    }

    private static long getOrCreateConvPlan(MIOpenContext context, int n, int c, int h, int w, int k, int r, int s, int pad, int stride, boolean half, boolean nhwc, int direction) {
        String planKey = n + ":" + c + ":" + h + ":" + w + ":" + k + ":" + r + ":" + s + ":" + pad + ":" + stride + (half ? ":f16" : ":f32") + (nhwc ? ":nhwc" : ":nchw") + ":" + direction;
        Long plan = context.convPlanCache.get(planKey);
        if (plan == null) {
            plan = MIOpenNativeLib.createConvPlan(context.handle, n, c, h, w, k, r, s, pad, stride, half, nhwc, direction);
            if (plan == 0) {
                throw new TornadoRuntimeException("[ERROR] MIOpen convolution plan creation failed for " + planKey);
            }
            context.growWorkspace(MIOpenNativeLib.convPlanWorkspaceBytes(plan));
            context.convPlanCache.put(planKey, plan);
        }
        return plan;
    }

    @Override
    public void destroyContext(LibraryContext context) {
        MIOpenContext miopenContext = (MIOpenContext) context;
        for (Long plan : miopenContext.convPlanCache.values()) {
            MIOpenNativeLib.destroyConvPlan(plan);
        }
        miopenContext.convPlanCache.clear();
        for (long retired : miopenContext.retiredWorkspaces) {
            MIOpenNativeLib.freeDeviceMemory(retired);
        }
        miopenContext.retiredWorkspaces.clear();
        if (miopenContext.workspacePtr != 0) {
            MIOpenNativeLib.freeDeviceMemory(miopenContext.workspacePtr);
            miopenContext.workspacePtr = 0;
        }
        MIOpenNativeLib.miopenDestroyHandle(miopenContext.handle);
    }
}
