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
package uk.ac.manchester.tornado.cutlass.provider;

import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cutlass.Cutlass;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} for NVIDIA CUTLASS GEMM kernels. The
 * per-(device, execution plan) context holds the plan's CUstream (CUTLASS has
 * no library handle - the stream is passed per launch) and a grow-only device
 * workspace. The workspace is sized in {@link #prepare} - before CUDA graph
 * capture starts - so {@link #dispatch} allocates nothing and is capture-safe.
 *
 * TornadoVM device pointers are the array base plus a 24-byte header, so their
 * guaranteed alignment is 8 bytes; the FP32 SIMT kernel (alignment 1) has no
 * constraint, while the tensor-op kernels enforce their own in the factory.
 */
public final class CutlassLibraryProvider implements TornadoLibraryProvider {

    private static final class CutlassContext implements LibraryContext {
        private final long stream;
        private long workspacePtr;
        private long workspaceBytes;

        private CutlassContext(long stream) {
            this.stream = stream;
        }

        private void growWorkspace(long required) {
            if (required > workspaceBytes) {
                if (workspacePtr != 0) {
                    CutlassNativeLib.freeDeviceMemory(workspacePtr);
                    workspacePtr = 0;
                    workspaceBytes = 0;
                }
                long ptr = CutlassNativeLib.allocateDeviceMemory(required);
                if (ptr == 0) {
                    throw new TornadoRuntimeException("[ERROR] Unable to allocate CUTLASS workspace of " + required + " bytes");
                }
                workspacePtr = ptr;
                workspaceBytes = required;
            }
        }
    }

    @Override
    public String libraryName() {
        return Cutlass.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        CutlassNativeLib.load();
        long stream = ((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId);
        return new CutlassContext(stream);
    }

    @Override
    public void prepare(LibraryTaskDescriptor descriptor, LibraryContext context) {
        Object[] p = descriptor.getParameters();
        CutlassContext ctx = (CutlassContext) context;
        int m = (int) p[0];
        int n = (int) p[1];
        int k = (int) p[2];
        switch (descriptor.getFunctionName()) {
            case "cutlassSgemm" -> ctx.growWorkspace(CutlassNativeLib.sgemmWorkspace(m, n, k));
            case "cutlassHgemm" -> ctx.growWorkspace(CutlassNativeLib.hgemmWorkspace(m, n, k));
            case "cutlassBgemm" -> ctx.growWorkspace(CutlassNativeLib.bgemmWorkspace(m, n, k));
            case "cutlassGemmBiasRelu" -> ctx.growWorkspace(CutlassNativeLib.gemmBiasReluWorkspace(m, n, k));
            case "cutlassGemmBiasGelu" -> ctx.growWorkspace(CutlassNativeLib.gemmBiasGeluWorkspace(m, n, k));
            case "cutlassGemmBiasSilu" -> ctx.growWorkspace(CutlassNativeLib.gemmBiasSiluWorkspace(m, n, k));
            case "cutlassGemmBiasSigmoid" -> ctx.growWorkspace(CutlassNativeLib.gemmBiasSigmoidWorkspace(m, n, k));
            case "cutlassGemmBiasTanh" -> ctx.growWorkspace(CutlassNativeLib.gemmBiasTanhWorkspace(m, n, k));
            case "cutlassGemmBiasHardSwish" -> ctx.growWorkspace(CutlassNativeLib.gemmBiasHardSwishWorkspace(m, n, k));
            case "cutlassHgemmBatched" -> ctx.growWorkspace(CutlassNativeLib.hgemmBatchedWorkspace(m, n, k, (int) p[8]));
            default -> {
                // no per-shape native state
            }
        }
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        CutlassContext context = (CutlassContext) invocation.getContext();
        int status = switch (functionName) {
            // (m, n, k, alpha, a, b, beta, c)
            case "cutlassSgemm" -> CutlassNativeLib.sgemm((int) invocation.getArg(0), (int) invocation.getArg(1), (int) invocation.getArg(2), //
                    (float) invocation.getArg(3), //
                    invocation.getDevicePointer(4), invocation.getDevicePointer(5), //
                    (float) invocation.getArg(6), invocation.getDevicePointer(7), //
                    context.workspacePtr, context.stream);
            // (m, n, k, alpha, a, b, beta, d)
            case "cutlassHgemm" -> CutlassNativeLib.hgemm((int) invocation.getArg(0), (int) invocation.getArg(1), (int) invocation.getArg(2), //
                    (float) invocation.getArg(3), //
                    invocation.getDevicePointer(4), invocation.getDevicePointer(5), //
                    (float) invocation.getArg(6), invocation.getDevicePointer(7), //
                    context.workspacePtr, context.stream);
            // (m, n, k, alpha, a, b, beta, c)
            case "cutlassBgemm" -> CutlassNativeLib.bgemm((int) invocation.getArg(0), (int) invocation.getArg(1), (int) invocation.getArg(2), //
                    (float) invocation.getArg(3), //
                    invocation.getDevicePointer(4), invocation.getDevicePointer(5), //
                    (float) invocation.getArg(6), invocation.getDevicePointer(7), //
                    context.workspacePtr, context.stream);
            // (m, n, k, a, b, bias, d)
            case "cutlassGemmBiasRelu" -> CutlassNativeLib.gemmBiasRelu((int) invocation.getArg(0), (int) invocation.getArg(1), (int) invocation.getArg(2), //
                    invocation.getDevicePointer(3), invocation.getDevicePointer(4), invocation.getDevicePointer(5), invocation.getDevicePointer(6), //
                    context.workspacePtr, context.stream);
            case "cutlassGemmBiasGelu" -> CutlassNativeLib.gemmBiasGelu((int) invocation.getArg(0), (int) invocation.getArg(1), (int) invocation.getArg(2), //
                    invocation.getDevicePointer(3), invocation.getDevicePointer(4), invocation.getDevicePointer(5), invocation.getDevicePointer(6), //
                    context.workspacePtr, context.stream);
            case "cutlassGemmBiasSilu" -> CutlassNativeLib.gemmBiasSilu((int) invocation.getArg(0), (int) invocation.getArg(1), (int) invocation.getArg(2), //
                    invocation.getDevicePointer(3), invocation.getDevicePointer(4), invocation.getDevicePointer(5), invocation.getDevicePointer(6), //
                    context.workspacePtr, context.stream);
            case "cutlassGemmBiasSigmoid" -> CutlassNativeLib.gemmBiasSigmoid((int) invocation.getArg(0), (int) invocation.getArg(1), (int) invocation.getArg(2), //
                    invocation.getDevicePointer(3), invocation.getDevicePointer(4), invocation.getDevicePointer(5), invocation.getDevicePointer(6), //
                    context.workspacePtr, context.stream);
            case "cutlassGemmBiasTanh" -> CutlassNativeLib.gemmBiasTanh((int) invocation.getArg(0), (int) invocation.getArg(1), (int) invocation.getArg(2), //
                    invocation.getDevicePointer(3), invocation.getDevicePointer(4), invocation.getDevicePointer(5), invocation.getDevicePointer(6), //
                    context.workspacePtr, context.stream);
            case "cutlassGemmBiasHardSwish" -> CutlassNativeLib.gemmBiasHardSwish((int) invocation.getArg(0), (int) invocation.getArg(1), (int) invocation.getArg(2), //
                    invocation.getDevicePointer(3), invocation.getDevicePointer(4), invocation.getDevicePointer(5), invocation.getDevicePointer(6), //
                    context.workspacePtr, context.stream);
            // (m, n, k, alpha, a, b, beta, c, batchCount)
            case "cutlassHgemmBatched" -> CutlassNativeLib.hgemmBatched((int) invocation.getArg(0), (int) invocation.getArg(1), (int) invocation.getArg(2), //
                    (float) invocation.getArg(3), //
                    invocation.getDevicePointer(4), invocation.getDevicePointer(5), //
                    (float) invocation.getArg(6), invocation.getDevicePointer(7), (int) invocation.getArg(8), //
                    context.workspacePtr, context.stream);
            default -> throw new TornadoRuntimeException("[ERROR] CUTLASS function not supported: " + functionName);
        };
        CutlassNativeLib.checkStatus(status, functionName);
    }

    @Override
    public void destroyContext(LibraryContext context) {
        CutlassContext cutlassContext = (CutlassContext) context;
        if (cutlassContext.workspacePtr != 0) {
            CutlassNativeLib.freeDeviceMemory(cutlassContext.workspacePtr);
            cutlassContext.workspacePtr = 0;
        }
    }
}
