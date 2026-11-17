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
package uk.ac.manchester.tornado.cusparse.provider;

import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cusparse.Cusparse;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} that runs the {@link Cusparse} library tasks on HIP devices with ROCm's
 * hipSPARSE, mirroring {@link CusparseLibraryProvider}: a handle bound to the plan's stream and a
 * grow-only workspace pre-allocated so that small SpMV/SpMM calls do not allocate inside a HIP-graph
 * capture region.
 */
public final class HipsparseLibraryProvider implements TornadoLibraryProvider {

    /** Default workspace: comfortably covers CSR SpMV/SpMM external buffers (typically bytes to KiB). */
    private static final long DEFAULT_WORKSPACE_BYTES = 8L << 20; // 8 MiB

    private static final class HipsparseContext implements LibraryContext {
        private final long handle;
        /** The stream the handle is bound to. */
        private long stream;
        private long workspacePtr;
        private long workspaceBytes;
        /** Descriptor sets by operand set, kept for the life of the context. */
        private final java.util.Map<String, long[]> descriptors = new java.util.HashMap<>();

        private HipsparseContext(long handle, long stream) {
            this.handle = handle;
            this.stream = stream;
        }

        private void growWorkspace(long required, boolean capturing) {
            if (required > workspaceBytes) {
                if (capturing) {
                    // hipMalloc is not legal inside a stream capture region: it would
                    // abort the capture with a cryptic driver error. Fail here instead,
                    // naming the size the workspace would have to be pre-sized to.
                    throw new TornadoRuntimeException("[ERROR] hipSPARSE needs a " + required + " byte workspace but only " + workspaceBytes
                            + " bytes were pre-allocated, and the workspace cannot grow while capturing a HIP graph. Run this task graph once without graph capture, or reduce the problem size.");
                }
                if (workspacePtr != 0) {
                    HipsparseNativeLib.freeDeviceMemory(workspacePtr);
                    workspacePtr = 0;
                    workspaceBytes = 0;
                }
                long ptr = HipsparseNativeLib.allocateDeviceMemory(required);
                if (ptr == 0) {
                    throw new TornadoRuntimeException("[ERROR] Unable to allocate hipSPARSE workspace of " + required + " bytes");
                }
                workspacePtr = ptr;
                workspaceBytes = required;
            }
        }
    }


    /** Whether hipSPARSE can be reached on this host. */
    public static boolean isAvailable() {
        try {
            HipsparseNativeLib.load();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public String libraryName() {
        return Cusparse.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device.getTornadoVMBackend() == TornadoVMBackendType.HIP && device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        HipsparseNativeLib.load();
        long stream = ((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId);
        long handle = HipsparseNativeLib.hipsparseCreateHandle();
        if (handle == 0) {
            throw new TornadoRuntimeException("[ERROR] hipsparseCreate failed");
        }
        HipsparseNativeLib.checkStatus(HipsparseNativeLib.hipsparseSetStreamNative(handle, stream), "hipsparseSetStream");
        return new HipsparseContext(handle, stream);
    }

    @Override
    public void prepare(LibraryTaskDescriptor descriptor, LibraryContext context) {
        // The exact buffer size needs the operand device pointers (resolved only
        // at dispatch), so pre-allocate a default workspace here to keep dispatch
        // allocation-free and graph-capture-safe for the common small cases.
        ((HipsparseContext) context).growWorkspace(DEFAULT_WORKSPACE_BYTES, false);
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        HipsparseContext context = (HipsparseContext) invocation.getContext();
        // The stream belongs to the thread that runs the plan, which need not be the one that
        // created this context: follow it, or the call runs on another thread's stream, unordered
        // with the kernels that produce its inputs.
        long stream = ((TornadoNativeStreamSupport) invocation.getDevice()).getNativeStream(invocation.getExecutionPlanId());
        if (stream != context.stream) {
            HipsparseNativeLib.checkStatus(HipsparseNativeLib.hipsparseSetStreamNative(context.handle, stream), "hipsparseSetStream");
            context.stream = stream;
        }
        int status = switch (functionName) {
            case "cusparseSpMV" -> spmv(context, invocation);
            case "cusparseSpMM" -> spmm(context, invocation);
            default -> throw new TornadoRuntimeException("[ERROR] cuSPARSE function not supported on hipSPARSE: " + functionName);
        };
        HipsparseNativeLib.checkStatus(status, functionName);
    }

    /** The cached descriptors of an SpMV or SpMM operand set, created on first use (see {@link HipsparseNativeLib#spmvDescriptors}). */
    private static long[] descriptors(HipsparseContext context, String key, java.util.function.Supplier<long[]> create) {
        long[] descriptors = context.descriptors.get(key);
        if (descriptors == null) {
            descriptors = create.get();
            if (descriptors == null) {
                throw new TornadoRuntimeException("[ERROR] hipSPARSE refused the descriptors for " + key);
            }
            context.descriptors.put(key, descriptors);
        }
        return descriptors;
    }

    // (rows, cols, nnz, alpha, csrRowOffsets, csrColInd, csrValues, x, beta, y)
    private static int spmv(HipsparseContext context, LibraryInvocation invocation) {
        int rows = (int) invocation.getArg(0);
        int cols = (int) invocation.getArg(1);
        int nnz = (int) invocation.getArg(2);
        float alpha = (float) invocation.getArg(3);
        long dRow = invocation.getDevicePointer(4);
        long dCol = invocation.getDevicePointer(5);
        long dVal = invocation.getDevicePointer(6);
        long dX = invocation.getDevicePointer(7);
        float beta = (float) invocation.getArg(8);
        long dY = invocation.getDevicePointer(9);
        long[] d = descriptors(context, "spmv:" + rows + ":" + cols + ":" + nnz + ":" + dRow + ":" + dCol + ":" + dVal + ":" + dX + ":" + dY,
                () -> HipsparseNativeLib.spmvDescriptors(rows, cols, nnz, dRow, dCol, dVal, dX, dY));
        long needed = HipsparseNativeLib.spmvBufferSize(context.handle, d, alpha, beta);
        if (needed < 0) {
            throw new TornadoRuntimeException("[ERROR] hipsparseSpMV_bufferSize failed");
        }
        context.growWorkspace(needed, invocation.isCapturing());
        // rocSPARSE rejects a buffer when it asked for none (and the descriptors have been used before).
        return HipsparseNativeLib.spmv(context.handle, d, alpha, beta, needed > 0 ? context.workspacePtr : 0);
    }

    // (rows, k, n, nnz, alpha, csrRowOffsets, csrColInd, csrValues, b, beta, c)
    private static int spmm(HipsparseContext context, LibraryInvocation invocation) {
        int rows = (int) invocation.getArg(0);
        int k = (int) invocation.getArg(1);
        int n = (int) invocation.getArg(2);
        int nnz = (int) invocation.getArg(3);
        float alpha = (float) invocation.getArg(4);
        long dRow = invocation.getDevicePointer(5);
        long dCol = invocation.getDevicePointer(6);
        long dVal = invocation.getDevicePointer(7);
        long dB = invocation.getDevicePointer(8);
        float beta = (float) invocation.getArg(9);
        long dC = invocation.getDevicePointer(10);
        long[] d = descriptors(context, "spmm:" + rows + ":" + k + ":" + n + ":" + nnz + ":" + dRow + ":" + dCol + ":" + dVal + ":" + dB + ":" + dC,
                () -> HipsparseNativeLib.spmmDescriptors(rows, k, n, nnz, dRow, dCol, dVal, dB, dC));
        long needed = HipsparseNativeLib.spmmBufferSize(context.handle, d, alpha, beta);
        if (needed < 0) {
            throw new TornadoRuntimeException("[ERROR] hipsparseSpMM_bufferSize failed");
        }
        context.growWorkspace(needed, invocation.isCapturing());
        return HipsparseNativeLib.spmm(context.handle, d, alpha, beta, needed > 0 ? context.workspacePtr : 0);
    }

    @Override
    public void destroyContext(LibraryContext context) {
        HipsparseContext hipsparseContext = (HipsparseContext) context;
        hipsparseContext.descriptors.forEach((key, d) -> HipsparseNativeLib.destroyDescriptors(d, key.startsWith("spmm:")));
        hipsparseContext.descriptors.clear();
        if (hipsparseContext.workspacePtr != 0) {
            HipsparseNativeLib.freeDeviceMemory(hipsparseContext.workspacePtr);
            hipsparseContext.workspacePtr = 0;
        }
        HipsparseNativeLib.hipsparseDestroyHandle(hipsparseContext.handle);
    }
}
