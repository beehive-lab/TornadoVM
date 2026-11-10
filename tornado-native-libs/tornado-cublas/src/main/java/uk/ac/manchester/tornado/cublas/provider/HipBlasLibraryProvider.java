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

import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.CuBlasOptions;
import uk.ac.manchester.tornado.cublas.enums.CuBlasMathMode;
import uk.ac.manchester.tornado.cublas.enums.CudaDataType;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} that runs the {@link CuBlas} library tasks on HIP devices with ROCm's
 * hipBLAS, so a task graph written against cuBLAS runs unchanged on an AMD GPU. It mirrors
 * {@link CuBlasLibraryProvider}: one hipBLAS handle per (device, execution plan) bound to the plan's
 * HIP stream. cuBLAS's operation, compute-type and algorithm enums are translated to hipBLAS's; the
 * data types share their values.
 */
public final class HipBlasLibraryProvider implements TornadoLibraryProvider {

    private static final class HipBlasContext implements LibraryContext {
        private final long handle;
        /** The stream the handle is bound to. */
        private long stream;
        private long workspacePtr;
        private long workspaceBytes;

        private HipBlasContext(long handle, long stream) {
            this.handle = handle;
            this.stream = stream;
        }
    }

    @FunctionalInterface
    private interface HipBlasCall {
        int invoke(long handle, LibraryInvocation invocation);
    }

    /**
     * Dispatch registry: function name -> marshalling call.
     */
    private static final Map<String, HipBlasCall> FUNCTIONS = Map.ofEntries(//
            Map.entry("cublasSgemv", HipBlasLibraryProvider::sgemv), //
            Map.entry("cublasSdot", HipBlasLibraryProvider::sdot), //
            Map.entry("cublasSnrm2", HipBlasLibraryProvider::snrm2), //
            Map.entry("cublasSasum", HipBlasLibraryProvider::sasum), //
            Map.entry("cublasIsamax", HipBlasLibraryProvider::isamax), //
            Map.entry("cublasIsamin", HipBlasLibraryProvider::isamin), //
            Map.entry("cublasSgemm", HipBlasLibraryProvider::sgemm), //
            Map.entry("cublasDgemm", HipBlasLibraryProvider::dgemm), //
            Map.entry("cublasSgemmStridedBatched", HipBlasLibraryProvider::sgemmStridedBatched), //
            Map.entry("cublasGemmExFP16", (handle, inv) -> gemmEx(handle, inv, CudaDataType.CUDA_R_16F, CudaDataType.CUDA_R_16F)), //
            Map.entry("cublasGemmExFP16FP32", (handle, inv) -> gemmEx(handle, inv, CudaDataType.CUDA_R_16F, CudaDataType.CUDA_R_32F)), //
            Map.entry("cublasGemmExBF16", (handle, inv) -> gemmEx(handle, inv, CudaDataType.CUDA_R_16BF, CudaDataType.CUDA_R_16BF)), //
            Map.entry("cublasGemmExBF16FP32", (handle, inv) -> gemmEx(handle, inv, CudaDataType.CUDA_R_16BF, CudaDataType.CUDA_R_32F)));

    /** {@code HIPBLAS_GEMM_DEFAULT}: the library's algorithm selection. */
    private static final int HIPBLAS_GEMM_DEFAULT = 160;

    /** {@code HIPBLAS_COMPUTE_32F}: FP32 accumulation. */
    private static final int HIPBLAS_COMPUTE_32F = 2;

    /** {@code hipblasMath_t}: HIPBLAS_DEFAULT_MATH, HIPBLAS_XF32_XDL_MATH and HIPBLAS_PEDANTIC_MATH. */
    private static final int HIPBLAS_DEFAULT_MATH = 0;
    private static final int HIPBLAS_XF32_XDL_MATH = 1;
    private static final int HIPBLAS_PEDANTIC_MATH = 2;

    /** cuBLAS's {@code CUBLAS_OP_N/T/C} (0, 1, 2) as {@code HIPBLAS_OP_N/T/C} (111, 112, 113). */
    private static int op(Object cublasOperation) {
        return 111 + (int) cublasOperation;
    }


    /** Whether hipBLAS can be reached on this host. */
    public static boolean isAvailable() {
        try {
            HipBlasNativeLib.load();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public String libraryName() {
        return CuBlas.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device.getTornadoVMBackend() == TornadoVMBackendType.HIP && device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        HipBlasNativeLib.load();
        long stream = ((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId);
        long handle = HipBlasNativeLib.hipblasCreate();
        if (handle == 0) {
            throw new TornadoRuntimeException("[ERROR] hipblasCreate failed");
        }
        HipBlasNativeLib.checkStatus(HipBlasNativeLib.hipblasSetStream(handle, stream), "hipblasSetStream");
        return new HipBlasContext(handle, stream);
    }

    /**
     * Pre-allocates the workspace requested through the descriptor's tuning options.
     * hipMalloc is not legal inside a HIP-graph capture region, so the allocation
     * has to happen here rather than on the first {@link #dispatch}.
     */
    @Override
    public void prepare(LibraryTaskDescriptor descriptor, LibraryContext context) {
        if (descriptor.getTuning() instanceof CuBlasOptions options && options.getWorkspaceBytes() > 0) {
            ensureWorkspace((HipBlasContext) context, options.getWorkspaceBytes());
        }
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        HipBlasCall call = FUNCTIONS.get(functionName);
        if (call == null) {
            throw new TornadoRuntimeException("[ERROR] cuBLAS function not supported on hipBLAS: " + functionName);
        }
        HipBlasContext context = (HipBlasContext) invocation.getContext();
        CuBlasOptions options = (invocation.getTuning() instanceof CuBlasOptions hipBlasOptions) ? hipBlasOptions : null;

        // The stream belongs to the thread that runs the plan, which need not be the one that
        // created this context: follow it, or the call runs on another thread's stream, unordered
        // with the kernels that produce its inputs.
        long stream = ((TornadoNativeStreamSupport) invocation.getDevice()).getNativeStream(invocation.getExecutionPlanId());
        if (stream != context.stream) {
            HipBlasNativeLib.checkStatus(HipBlasNativeLib.hipblasSetStream(context.handle, stream), "hipblasSetStream");
            context.stream = stream;
        }

        applyOptions(context, options);
        try {
            HipBlasNativeLib.checkStatus(call.invoke(context.handle, invocation), functionName);
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
    private void applyOptions(HipBlasContext context, CuBlasOptions options) {
        if (options == null) {
            return;
        }
        ensureWorkspace(context, options.getWorkspaceBytes());
        if (options.getMathMode() != CuBlasMathMode.CUBLAS_DEFAULT_MATH) {
            // TF32 is a permission to compute in reduced precision, which hipBLAS spells XF32 and only
            // some GPUs have: where it is not supported, full FP32 is what runs.
            int status = HipBlasNativeLib.hipblasSetMathMode(context.handle, options.getMathMode() == CuBlasMathMode.CUBLAS_TF32_TENSOR_OP_MATH ? HIPBLAS_XF32_XDL_MATH : HIPBLAS_PEDANTIC_MATH);
            if (status != HipBlasNativeLib.HIPBLAS_STATUS_NOT_SUPPORTED) {
                HipBlasNativeLib.checkStatus(status, "hipblasSetMathMode");
            }
        }
    }

    /**
     * Grow-only device workspace bound to the handle. A no-op once the context
     * already holds at least {@code bytes}, which is what keeps {@link #dispatch}
     * allocation-free after {@link #prepare} has run.
     */
    private void ensureWorkspace(HipBlasContext context, long bytes) {
        if (bytes <= context.workspaceBytes) {
            return;
        }
        if (context.workspacePtr != 0) {
            HipBlasNativeLib.freeDeviceMemory(context.workspacePtr);
            context.workspacePtr = 0;
            context.workspaceBytes = 0;
        }
        long ptr = HipBlasNativeLib.allocateDeviceMemory(bytes);
        if (ptr == 0) {
            throw new TornadoRuntimeException("[ERROR] Unable to allocate hipBLAS workspace of " + bytes + " bytes");
        }
        context.workspacePtr = ptr;
        context.workspaceBytes = bytes;
        int status = HipBlasNativeLib.hipblasSetWorkspace(context.handle, context.workspacePtr, context.workspaceBytes);
        if (status != HipBlasNativeLib.HIPBLAS_STATUS_NOT_SUPPORTED) {
            HipBlasNativeLib.checkStatus(status, "hipblasSetWorkspace");
        }
    }

    private void resetOptions(HipBlasContext context, CuBlasOptions options) {
        if (options != null && options.getMathMode() != CuBlasMathMode.CUBLAS_DEFAULT_MATH) {
            HipBlasNativeLib.hipblasSetMathMode(context.handle, HIPBLAS_DEFAULT_MATH);
        }
    }

    @Override
    public void destroyContext(LibraryContext context) {
        HipBlasContext hipBlasContext = (HipBlasContext) context;
        if (hipBlasContext.workspacePtr != 0) {
            HipBlasNativeLib.freeDeviceMemory(hipBlasContext.workspacePtr);
            hipBlasContext.workspacePtr = 0;
        }
        HipBlasNativeLib.hipblasDestroy(hipBlasContext.handle);
    }

    // ------------------------------------------------------------------
    // Marshalling: one method per cuBLAS function, positional argument
    // order matching the corresponding CuBlas factory; operations translated.
    // ------------------------------------------------------------------

    /** (trans, m, n, alpha, A, lda, x, incx, beta, y, incy). */
    private static int sgemv(long handle, LibraryInvocation invocation) {
        return HipBlasNativeLib.hipblasSgemv(handle, //
                op(invocation.getArg(0)), //
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

    /** (transa, transb, m, n, k, alpha, A, lda, strideA, B, ldb, strideB, beta, C, ldc, strideC, batchCount). */
    private static int sgemmStridedBatched(long handle, LibraryInvocation invocation) {
        return HipBlasNativeLib.hipblasSgemmStridedBatched(handle, //
                op(invocation.getArg(0)), //
                op(invocation.getArg(1)), //
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
        return HipBlasNativeLib.hipblasGemmEx(handle, //
                op(invocation.getArg(0)), //
                op(invocation.getArg(1)), //
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
                HIPBLAS_COMPUTE_32F, HIPBLAS_GEMM_DEFAULT);
    }

    /** (transa, transb, m, n, k, alpha, A, lda, B, ldb, beta, C, ldc). */
    // (n, x, incx, y, incy, result)
    private static int sdot(long handle, LibraryInvocation invocation) {
        return HipBlasNativeLib.hipblasSdot(handle, (int) invocation.getArg(0), invocation.getDevicePointer(1), (int) invocation.getArg(2), invocation.getDevicePointer(3),
                (int) invocation.getArg(4), invocation.getDevicePointer(5));
    }

    // (n, x, incx, result)
    private static int snrm2(long handle, LibraryInvocation invocation) {
        return HipBlasNativeLib.hipblasSnrm2(handle, (int) invocation.getArg(0), invocation.getDevicePointer(1), (int) invocation.getArg(2), invocation.getDevicePointer(3));
    }

    // (n, x, incx, result)
    private static int sasum(long handle, LibraryInvocation invocation) {
        return HipBlasNativeLib.hipblasSasum(handle, (int) invocation.getArg(0), invocation.getDevicePointer(1), (int) invocation.getArg(2), invocation.getDevicePointer(3));
    }

    // (n, x, incx, result)
    private static int isamax(long handle, LibraryInvocation invocation) {
        return HipBlasNativeLib.hipblasIsamax(handle, (int) invocation.getArg(0), invocation.getDevicePointer(1), (int) invocation.getArg(2), invocation.getDevicePointer(3));
    }

    // (n, x, incx, result)
    private static int isamin(long handle, LibraryInvocation invocation) {
        return HipBlasNativeLib.hipblasIsamin(handle, (int) invocation.getArg(0), invocation.getDevicePointer(1), (int) invocation.getArg(2), invocation.getDevicePointer(3));
    }

    private static int sgemm(long handle, LibraryInvocation invocation) {
        return HipBlasNativeLib.hipblasSgemm(handle, //
                op(invocation.getArg(0)), //
                op(invocation.getArg(1)), //
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

    private static int dgemm(long handle, LibraryInvocation invocation) {
        return HipBlasNativeLib.hipblasDgemm(handle, //
                op(invocation.getArg(0)), //
                op(invocation.getArg(1)), //
                (int) invocation.getArg(2), //
                (int) invocation.getArg(3), //
                (int) invocation.getArg(4), //
                (double) invocation.getArg(5), //
                invocation.getDevicePointer(6), //
                (int) invocation.getArg(7), //
                invocation.getDevicePointer(8), //
                (int) invocation.getArg(9), //
                (double) invocation.getArg(10), //
                invocation.getDevicePointer(11), //
                (int) invocation.getArg(12));
    }
}
