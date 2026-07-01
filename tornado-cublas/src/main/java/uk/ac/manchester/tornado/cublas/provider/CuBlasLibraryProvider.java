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
package uk.ac.manchester.tornado.cublas.provider;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} for NVIDIA cuBLAS. Discovered by the TornadoVM
 * runtime via {@link java.util.ServiceLoader}; creates one cuBLAS handle per
 * (device, execution plan) bound to the plan's CUDA stream, and dispatches
 * library-task calls with TornadoVM-managed device buffers.
 */
public final class CuBlasLibraryProvider implements TornadoLibraryProvider {

    private record CuBlasContext(long handle) implements LibraryContext {
    }

    @Override
    public String libraryName() {
        return CuBlas.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        // Only the CUDA backend exposes its native stream for library interop
        return device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        CuBlasNativeLib.load();
        long stream = ((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId);
        long handle = CuBlasNativeLib.cublasCreate();
        if (handle == 0) {
            throw new TornadoRuntimeException("[ERROR] cublasCreate failed");
        }
        CuBlasNativeLib.checkStatus(CuBlasNativeLib.cublasSetStream(handle, stream), "cublasSetStream");
        return new CuBlasContext(handle);
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        long handle = ((CuBlasContext) invocation.getContext()).handle();
        switch (functionName) {
            case "cublasSgemv" -> {
                // (trans, m, n, alpha, A, lda, x, incx, beta, y, incy)
                int status = CuBlasNativeLib.cublasSgemv(handle, //
                        (int) invocation.getArg(0), //
                        (int) invocation.getArg(1), //
                        (int) invocation.getArg(2), //
                        (float) invocation.getArg(3), //
                        invocation.getDevicePointer(4), //
                        (int) invocation.getArg(5), //
                        invocation.getDevicePointer(6), //
                        (int) invocation.getArg(7), //
                        (float) invocation.getArg(8), //
                        invocation.getDevicePointer(9), //
                        (int) invocation.getArg(10));
                CuBlasNativeLib.checkStatus(status, "cublasSgemv");
            }
            case "cublasSgemm" -> {
                // (transa, transb, m, n, k, alpha, A, lda, B, ldb, beta, C, ldc)
                int status = CuBlasNativeLib.cublasSgemm(handle, //
                        (int) invocation.getArg(0), //
                        (int) invocation.getArg(1), //
                        (int) invocation.getArg(2), //
                        (int) invocation.getArg(3), //
                        (int) invocation.getArg(4), //
                        (float) invocation.getArg(5), //
                        invocation.getDevicePointer(6), //
                        (int) invocation.getArg(7), //
                        invocation.getDevicePointer(8), //
                        (int) invocation.getArg(9), //
                        (float) invocation.getArg(10), //
                        invocation.getDevicePointer(11), //
                        (int) invocation.getArg(12));
                CuBlasNativeLib.checkStatus(status, "cublasSgemm");
            }
            default -> throw new TornadoRuntimeException("[ERROR] cuBLAS function not supported: " + functionName);
        }
    }

    @Override
    public void destroyContext(LibraryContext context) {
        CuBlasNativeLib.cublasDestroy(((CuBlasContext) context).handle());
    }
}
