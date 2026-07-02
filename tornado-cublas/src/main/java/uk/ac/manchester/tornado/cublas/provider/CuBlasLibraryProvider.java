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

import java.util.Map;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.CuBlasOptions;
import uk.ac.manchester.tornado.cublas.enums.CuBlasMathMode;
import uk.ac.manchester.tornado.cublas.enums.CublasComputeType;
import uk.ac.manchester.tornado.cublas.enums.CudaDataType;
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
 *
 * <p>
 * Adding a function = one entry in {@link #FUNCTIONS} + one marshalling method
 * + one JNI wrapper in {@link CuBlasNativeLib} (+ a factory in
 * {@link CuBlas} and a test). Per-call tuning arrives as a
 * {@link CuBlasOptions} through the descriptor's opaque tuning field.
 * </p>
 */
public final class CuBlasLibraryProvider implements TornadoLibraryProvider {

    private static final class CuBlasContext implements LibraryContext {
        private final long handle;
        private long workspacePtr;
        private long workspaceBytes;

        private CuBlasContext(long handle) {
            this.handle = handle;
        }
    }

    @FunctionalInterface
    private interface CuBlasCall {
        int invoke(long handle, LibraryInvocation invocation);
    }

    /**
     * Dispatch registry: function name -> marshalling call.
     */
    private static final Map<String, CuBlasCall> FUNCTIONS = Map.of( //
            "cublasSgemv", CuBlasLibraryProvider::sgemv, //
            "cublasSgemm", CuBlasLibraryProvider::sgemm, //
            "cublasSgemmStridedBatched", CuBlasLibraryProvider::sgemmStridedBatched, //
            "cublasGemmExFP16", (handle, inv) -> gemmEx(handle, inv, CudaDataType.CUDA_R_16F, CudaDataType.CUDA_R_16F), //
            "cublasGemmExFP16FP32", (handle, inv) -> gemmEx(handle, inv, CudaDataType.CUDA_R_16F, CudaDataType.CUDA_R_32F));

    /** cublasGemmAlgo_t CUBLAS_GEMM_DEFAULT: heuristic algorithm selection. */
    private static final int CUBLAS_GEMM_DEFAULT = -1;

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
        CuBlasCall call = FUNCTIONS.get(functionName);
        if (call == null) {
            throw new TornadoRuntimeException("[ERROR] cuBLAS function not supported: " + functionName);
        }
        CuBlasContext context = (CuBlasContext) invocation.getContext();
        CuBlasOptions options = (invocation.getTuning() instanceof CuBlasOptions cuBlasOptions) ? cuBlasOptions : null;

        applyOptions(context, options);
        try {
            CuBlasNativeLib.checkStatus(call.invoke(context.handle, invocation), functionName);
        } finally {
            resetOptions(context, options);
        }
    }

    /**
     * Applies per-call handle configuration. The math mode is restored to the
     * default after the call ({@link #resetOptions}) because the handle is
     * shared by all calls of the execution plan. The workspace is a context
     * property: allocated lazily, grow-only, freed with the context.
     */
    private void applyOptions(CuBlasContext context, CuBlasOptions options) {
        if (options == null) {
            return;
        }
        if (options.getWorkspaceBytes() > context.workspaceBytes) {
            if (context.workspacePtr != 0) {
                CuBlasNativeLib.freeDeviceMemory(context.workspacePtr);
                context.workspacePtr = 0;
                context.workspaceBytes = 0;
            }
            long ptr = CuBlasNativeLib.allocateDeviceMemory(options.getWorkspaceBytes());
            if (ptr == 0) {
                throw new TornadoRuntimeException("[ERROR] Unable to allocate cuBLAS workspace of " + options.getWorkspaceBytes() + " bytes");
            }
            context.workspacePtr = ptr;
            context.workspaceBytes = options.getWorkspaceBytes();
            CuBlasNativeLib.checkStatus(CuBlasNativeLib.cublasSetWorkspace(context.handle, context.workspacePtr, context.workspaceBytes), "cublasSetWorkspace");
        }
        if (options.getMathMode() != CuBlasMathMode.CUBLAS_DEFAULT_MATH) {
            CuBlasNativeLib.checkStatus(CuBlasNativeLib.cublasSetMathMode(context.handle, options.getMathMode().value()), "cublasSetMathMode");
        }
    }

    private void resetOptions(CuBlasContext context, CuBlasOptions options) {
        if (options != null && options.getMathMode() != CuBlasMathMode.CUBLAS_DEFAULT_MATH) {
            CuBlasNativeLib.cublasSetMathMode(context.handle, CuBlasMathMode.CUBLAS_DEFAULT_MATH.value());
        }
    }

    @Override
    public void destroyContext(LibraryContext context) {
        CuBlasContext cuBlasContext = (CuBlasContext) context;
        if (cuBlasContext.workspacePtr != 0) {
            CuBlasNativeLib.freeDeviceMemory(cuBlasContext.workspacePtr);
            cuBlasContext.workspacePtr = 0;
        }
        CuBlasNativeLib.cublasDestroy(cuBlasContext.handle);
    }

    // ------------------------------------------------------------------
    // Marshalling: one method per cuBLAS function, positional argument
    // order matching the corresponding CuBlas factory.
    // ------------------------------------------------------------------

    /** (trans, m, n, alpha, A, lda, x, incx, beta, y, incy) */
    private static int sgemv(long handle, LibraryInvocation invocation) {
        return CuBlasNativeLib.cublasSgemv(handle, //
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
    }

    /** (transa, transb, m, n, k, alpha, A, lda, strideA, B, ldb, strideB, beta, C, ldc, strideC, batchCount) */
    private static int sgemmStridedBatched(long handle, LibraryInvocation invocation) {
        return CuBlasNativeLib.cublasSgemmStridedBatched(handle, //
                (int) invocation.getArg(0), //
                (int) invocation.getArg(1), //
                (int) invocation.getArg(2), //
                (int) invocation.getArg(3), //
                (int) invocation.getArg(4), //
                (float) invocation.getArg(5), //
                invocation.getDevicePointer(6), //
                (int) invocation.getArg(7), //
                (long) invocation.getArg(8), //
                invocation.getDevicePointer(9), //
                (int) invocation.getArg(10), //
                (long) invocation.getArg(11), //
                (float) invocation.getArg(12), //
                invocation.getDevicePointer(13), //
                (int) invocation.getArg(14), //
                (long) invocation.getArg(15), //
                (int) invocation.getArg(16));
    }

    /**
     * (transa, transb, m, n, k, alpha, A, lda, B, ldb, beta, C, ldc) with FP16
     * inputs, the given output type, and FP32 Tensor Core accumulation.
     */
    private static int gemmEx(long handle, LibraryInvocation invocation, CudaDataType inputType, CudaDataType outputType) {
        return CuBlasNativeLib.cublasGemmEx(handle, //
                (int) invocation.getArg(0), //
                (int) invocation.getArg(1), //
                (int) invocation.getArg(2), //
                (int) invocation.getArg(3), //
                (int) invocation.getArg(4), //
                (float) invocation.getArg(5), //
                invocation.getDevicePointer(6), inputType.value(), //
                (int) invocation.getArg(7), //
                invocation.getDevicePointer(8), inputType.value(), //
                (int) invocation.getArg(9), //
                (float) invocation.getArg(10), //
                invocation.getDevicePointer(11), outputType.value(), //
                (int) invocation.getArg(12), //
                CublasComputeType.CUBLAS_COMPUTE_32F.value(), CUBLAS_GEMM_DEFAULT);
    }

    /** (transa, transb, m, n, k, alpha, A, lda, B, ldb, beta, C, ldc) */
    private static int sgemm(long handle, LibraryInvocation invocation) {
        return CuBlasNativeLib.cublasSgemm(handle, //
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
    }
}
