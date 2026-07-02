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

import java.util.HashMap;
import java.util.Map;

import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
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
 * plans and the workspace are created in {@link #prepare} — before CUDA graph
 * capture starts — so {@link #dispatch} is capture-safe.
 */
public final class CuDnnLibraryProvider implements TornadoLibraryProvider {

    private static final class CuDnnContext implements LibraryContext {
        private final long handle;
        private final Map<String, Long> convPlanCache = new HashMap<>();
        private long workspacePtr;
        private long workspaceBytes;

        private CuDnnContext(long handle) {
            this.handle = handle;
        }
    }

    @Override
    public String libraryName() {
        return CuDnn.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device instanceof TornadoNativeStreamSupport;
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
        return new CuDnnContext(handle);
    }

    @Override
    public void prepare(LibraryTaskDescriptor descriptor, LibraryContext context) {
        if ("cudnnConv2d".equals(descriptor.getFunctionName())) {
            Object[] p = descriptor.getParameters();
            getOrCreateConvPlan((CuDnnContext) context, (int) p[3], (int) p[4], (int) p[5], (int) p[6], (int) p[7], (int) p[8], (int) p[9], (int) p[10], (int) p[11]);
        }
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        CuDnnContext context = (CuDnnContext) invocation.getContext();
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
            case "cudnnConv2d" -> conv2d(context, invocation);
            default -> throw new TornadoRuntimeException("[ERROR] cuDNN function not supported: " + functionName);
        };
        CuDnnNativeLib.checkStatus(status, functionName);
    }

    /** (input, output, size) */
    private static int activation(CuDnnContext context, LibraryInvocation invocation, int mode) {
        return CuDnnNativeLib.activationForward(context.handle, mode, (int) invocation.getArg(2), invocation.getDevicePointer(0), invocation.getDevicePointer(1));
    }

    /** (input, filter, output, n, c, h, w, k, r, s, pad, stride) */
    private static int conv2d(CuDnnContext context, LibraryInvocation invocation) {
        long plan = getOrCreateConvPlan(context, //
                (int) invocation.getArg(3), (int) invocation.getArg(4), (int) invocation.getArg(5), (int) invocation.getArg(6), //
                (int) invocation.getArg(7), (int) invocation.getArg(8), (int) invocation.getArg(9), //
                (int) invocation.getArg(10), (int) invocation.getArg(11));
        return CuDnnNativeLib.convForward(context.handle, plan, //
                invocation.getDevicePointer(0), invocation.getDevicePointer(1), invocation.getDevicePointer(2), //
                context.workspacePtr, context.workspaceBytes);
    }

    private static long getOrCreateConvPlan(CuDnnContext context, int n, int c, int h, int w, int k, int r, int s, int pad, int stride) {
        String planKey = n + ":" + c + ":" + h + ":" + w + ":" + k + ":" + r + ":" + s + ":" + pad + ":" + stride;
        Long plan = context.convPlanCache.get(planKey);
        if (plan == null) {
            plan = CuDnnNativeLib.createConvPlan(context.handle, n, c, h, w, k, r, s, pad, stride);
            if (plan == 0) {
                throw new TornadoRuntimeException("[ERROR] cuDNN convolution plan creation failed for " + planKey);
            }
            long required = CuDnnNativeLib.convPlanWorkspaceBytes(plan);
            if (required > context.workspaceBytes) {
                if (context.workspacePtr != 0) {
                    CuDnnNativeLib.freeDeviceMemory(context.workspacePtr);
                    context.workspacePtr = 0;
                    context.workspaceBytes = 0;
                }
                long ptr = CuDnnNativeLib.allocateDeviceMemory(required);
                if (ptr == 0) {
                    CuDnnNativeLib.destroyConvPlan(plan);
                    throw new TornadoRuntimeException("[ERROR] Unable to allocate cuDNN workspace of " + required + " bytes");
                }
                context.workspacePtr = ptr;
                context.workspaceBytes = required;
            }
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
        if (cuDnnContext.workspacePtr != 0) {
            CuDnnNativeLib.freeDeviceMemory(cuDnnContext.workspacePtr);
            cuDnnContext.workspacePtr = 0;
        }
        CuDnnNativeLib.cudnnDestroyHandle(cuDnnContext.handle);
    }
}
