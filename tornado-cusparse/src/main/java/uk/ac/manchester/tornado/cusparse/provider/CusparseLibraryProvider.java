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
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cusparse.Cusparse;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} for NVIDIA cuSPARSE (FP32 sparse BLAS, CSR).
 * The per-(device, execution plan) context holds the cuSPARSE handle bound to
 * the plan's stream and a grow-only device workspace. Because the external
 * buffer size depends on the sparse structure (only known once the operand
 * device pointers are resolved at dispatch), {@link #prepare} pre-allocates a
 * default workspace so the small SpMV/SpMM buffers do not trigger an allocation
 * inside a CUDA-graph capture region.
 */
public final class CusparseLibraryProvider implements TornadoLibraryProvider {

    /** Default workspace: comfortably covers CSR SpMV/SpMM external buffers (typically bytes to KiB). */
    private static final long DEFAULT_WORKSPACE_BYTES = 8L << 20; // 8 MiB

    private static final class CusparseContext implements LibraryContext {
        private final long handle;
        private long workspacePtr;
        private long workspaceBytes;

        private CusparseContext(long handle) {
            this.handle = handle;
        }

        private void growWorkspace(long required) {
            if (required > workspaceBytes) {
                if (workspacePtr != 0) {
                    CusparseNativeLib.freeDeviceMemory(workspacePtr);
                    workspacePtr = 0;
                    workspaceBytes = 0;
                }
                long ptr = CusparseNativeLib.allocateDeviceMemory(required);
                if (ptr == 0) {
                    throw new TornadoRuntimeException("[ERROR] Unable to allocate cuSPARSE workspace of " + required + " bytes");
                }
                workspacePtr = ptr;
                workspaceBytes = required;
            }
        }
    }

    @Override
    public String libraryName() {
        return Cusparse.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        CusparseNativeLib.load();
        long stream = ((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId);
        long handle = CusparseNativeLib.cusparseCreateHandle();
        if (handle == 0) {
            throw new TornadoRuntimeException("[ERROR] cusparseCreate failed");
        }
        CusparseNativeLib.checkStatus(CusparseNativeLib.cusparseSetStreamNative(handle, stream), "cusparseSetStream");
        return new CusparseContext(handle);
    }

    @Override
    public void prepare(LibraryTaskDescriptor descriptor, LibraryContext context) {
        // The exact buffer size needs the operand device pointers (resolved only
        // at dispatch), so pre-allocate a default workspace here to keep dispatch
        // allocation-free and CUDA-graph-capture-safe for the common small cases.
        ((CusparseContext) context).growWorkspace(DEFAULT_WORKSPACE_BYTES);
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        CusparseContext context = (CusparseContext) invocation.getContext();
        int status = switch (functionName) {
            case "cusparseSpMV" -> spmv(context, invocation);
            case "cusparseSpMM" -> spmm(context, invocation);
            default -> throw new TornadoRuntimeException("[ERROR] cuSPARSE function not supported: " + functionName);
        };
        CusparseNativeLib.checkStatus(status, functionName);
    }

    // (rows, cols, nnz, csrRowOffsets, csrColInd, csrValues, x, y)
    private static int spmv(CusparseContext context, LibraryInvocation invocation) {
        int rows = (int) invocation.getArg(0);
        int cols = (int) invocation.getArg(1);
        int nnz = (int) invocation.getArg(2);
        long dRow = invocation.getDevicePointer(3);
        long dCol = invocation.getDevicePointer(4);
        long dVal = invocation.getDevicePointer(5);
        long dX = invocation.getDevicePointer(6);
        long dY = invocation.getDevicePointer(7);
        long needed = CusparseNativeLib.spmvBufferSize(context.handle, rows, cols, nnz, dRow, dCol, dVal, dX, dY, 1.0f, 0.0f);
        if (needed < 0) {
            throw new TornadoRuntimeException("[ERROR] cusparseSpMV_bufferSize failed");
        }
        context.growWorkspace(needed);
        return CusparseNativeLib.spmv(context.handle, rows, cols, nnz, dRow, dCol, dVal, dX, dY, 1.0f, 0.0f, context.workspacePtr);
    }

    // (rows, k, n, nnz, csrRowOffsets, csrColInd, csrValues, b, c)
    private static int spmm(CusparseContext context, LibraryInvocation invocation) {
        int rows = (int) invocation.getArg(0);
        int k = (int) invocation.getArg(1);
        int n = (int) invocation.getArg(2);
        int nnz = (int) invocation.getArg(3);
        long dRow = invocation.getDevicePointer(4);
        long dCol = invocation.getDevicePointer(5);
        long dVal = invocation.getDevicePointer(6);
        long dB = invocation.getDevicePointer(7);
        long dC = invocation.getDevicePointer(8);
        long needed = CusparseNativeLib.spmmBufferSize(context.handle, rows, k, n, nnz, dRow, dCol, dVal, dB, dC, 1.0f, 0.0f);
        if (needed < 0) {
            throw new TornadoRuntimeException("[ERROR] cusparseSpMM_bufferSize failed");
        }
        context.growWorkspace(needed);
        return CusparseNativeLib.spmm(context.handle, rows, k, n, nnz, dRow, dCol, dVal, dB, dC, 1.0f, 0.0f, context.workspacePtr);
    }

    @Override
    public void destroyContext(LibraryContext context) {
        CusparseContext cusparseContext = (CusparseContext) context;
        if (cusparseContext.workspacePtr != 0) {
            CusparseNativeLib.freeDeviceMemory(cusparseContext.workspacePtr);
            cusparseContext.workspacePtr = 0;
        }
        CusparseNativeLib.cusparseDestroyHandle(cusparseContext.handle);
    }
}
