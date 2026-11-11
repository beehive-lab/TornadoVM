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

import java.util.HashMap;
import java.util.Map;

import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP8NotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cublas.CuBlasLt;
import uk.ac.manchester.tornado.cublas.enums.CuBlasLtEpilogue;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;
import uk.ac.manchester.tornado.cublas.enums.CudaDataType;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} that runs the {@link CuBlasLt} library tasks on HIP devices with
 * ROCm's hipBLASLt (matmul with fused epilogues), mirroring {@link CuBlasLtLibraryProvider}: one
 * hipBLASLt handle, workspace and per-shape plan cache per (device, execution plan), the stream passed
 * per call. cuBLAS's operations and compute type are translated; data types and epilogues share their
 * values.
 */
public final class HipBlasLtLibraryProvider implements TornadoLibraryProvider {

    /** Workspace for the hipBLASLt heuristics (32 MiB, as for cuBLASLt). */
    private static final long WORKSPACE_BYTES = 32L * 1024 * 1024;

    private static final class HipBlasLtContext implements LibraryContext {
        private final long handle;
        private final long workspacePtr;
        private final Map<String, Long> planCache = new HashMap<>();

        private HipBlasLtContext(long handle, long workspacePtr) {
            this.handle = handle;
            this.workspacePtr = workspacePtr;
        }
    }

    private record LtCall(CudaDataType inputType, CudaDataType outputType, CuBlasLtEpilogue epilogue, boolean hasBias) {
    }

    /**
     * Dispatch registry: function name -> type/epilogue configuration. All
     * configurations share one marshaller ({@code ltMatmul}).
     */
    private static final Map<String, LtCall> FUNCTIONS = Map.of(//
            "ltMatmulFP32", new LtCall(CudaDataType.CUDA_R_32F, CudaDataType.CUDA_R_32F, CuBlasLtEpilogue.CUBLASLT_EPILOGUE_DEFAULT, false), //
            "ltMatmulFP16", new LtCall(CudaDataType.CUDA_R_16F, CudaDataType.CUDA_R_16F, CuBlasLtEpilogue.CUBLASLT_EPILOGUE_DEFAULT, false), //
            "ltMatmulFP8", new LtCall(CudaDataType.CUDA_R_8F_E4M3, CudaDataType.CUDA_R_16F, CuBlasLtEpilogue.CUBLASLT_EPILOGUE_DEFAULT, false), //
            "ltMatmulBiasFP16", new LtCall(CudaDataType.CUDA_R_16F, CudaDataType.CUDA_R_16F, CuBlasLtEpilogue.CUBLASLT_EPILOGUE_BIAS, true), //
            "ltMatmulGeluBiasFP16", new LtCall(CudaDataType.CUDA_R_16F, CudaDataType.CUDA_R_16F, CuBlasLtEpilogue.CUBLASLT_EPILOGUE_GELU_BIAS, true), //
            "ltMatmulReluFP16", new LtCall(CudaDataType.CUDA_R_16F, CudaDataType.CUDA_R_16F, CuBlasLtEpilogue.CUBLASLT_EPILOGUE_RELU, false), //
            "ltMatmulGeluFP16", new LtCall(CudaDataType.CUDA_R_16F, CudaDataType.CUDA_R_16F, CuBlasLtEpilogue.CUBLASLT_EPILOGUE_GELU, false), //
            "ltMatmulReluBiasFP16", new LtCall(CudaDataType.CUDA_R_16F, CudaDataType.CUDA_R_16F, CuBlasLtEpilogue.CUBLASLT_EPILOGUE_RELU_BIAS, true));

    /** {@code HIPBLAS_COMPUTE_32F}: FP32 accumulation. */
    private static final int HIPBLAS_COMPUTE_32F = 2;

    /** cuBLAS's {@code CUBLAS_OP_N/T/C} (0, 1, 2) as {@code HIPBLAS_OP_N/T/C} (111, 112, 113). */
    private static int op(int cublasOperation) {
        return 111 + cublasOperation;
    }

    @Override
    public String libraryName() {
        return CuBlasLt.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        // HIP devices only: the Metal backend also implements TornadoNativeStreamSupport
        return device.getTornadoVMBackend() == TornadoVMBackendType.HIP && device instanceof TornadoNativeStreamSupport;
    }

    /** Whether hipBLASLt, and hipBLAS (whose bindings allocate the workspace), can be reached on this host. */
    public static boolean isAvailable() {
        return HipBlasLtNativeLib.isAvailable() && HipBlasLibraryProvider.isAvailable();
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        HipBlasNativeLib.load();
        HipBlasLtNativeLib.load();
        long handle = HipBlasLtNativeLib.ltCreate();
        if (handle == 0) {
            throw new TornadoRuntimeException("[ERROR] hipblasLtCreate failed");
        }
        long workspacePtr = HipBlasNativeLib.allocateDeviceMemory(WORKSPACE_BYTES);
        if (workspacePtr == 0) {
            HipBlasLtNativeLib.ltDestroy(handle);
            throw new TornadoRuntimeException("[ERROR] Unable to allocate hipBLASLt workspace of " + WORKSPACE_BYTES + " bytes");
        }
        return new HipBlasLtContext(handle, workspacePtr);
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        LtCall call = FUNCTIONS.get(functionName);
        if (call == null) {
            throw new TornadoRuntimeException("[ERROR] cuBLASLt function not supported on hipBLASLt: " + functionName);
        }
        ltMatmul((HipBlasLtContext) invocation.getContext(), functionName, call, invocation);
    }

    private static boolean isFP8(CudaDataType type) {
        return type == CudaDataType.CUDA_R_8F_E4M3 || type == CudaDataType.CUDA_R_8F_E5M2;
    }

    /**
     * Probes whether hipBLASLt can plan a minimal FP8 GEMM (TN, E4M3 in, FP16 out, FP32 compute), to
     * tell a GPU/library pair without OCP FP8 kernels (gfx942 has FNUZ FP8 only) from a malformed
     * request, as {@link CuBlasLtLibraryProvider} does.
     */
    private static boolean hasFP8Support(HipBlasLtContext context) {
        final int dim = 16;
        long probe = HipBlasLtNativeLib.ltCreatePlan(context.handle, //
                op(CuBlasOperation.CUBLAS_OP_T.operation()), op(CuBlasOperation.CUBLAS_OP_N.operation()), //
                dim, dim, dim, dim, dim, dim, //
                CudaDataType.CUDA_R_8F_E4M3.value(), CudaDataType.CUDA_R_8F_E4M3.value(), CudaDataType.CUDA_R_16F.value(), //
                HIPBLAS_COMPUTE_32F, CudaDataType.CUDA_R_32F.value(), //
                CuBlasLtEpilogue.CUBLASLT_EPILOGUE_DEFAULT.value(), WORKSPACE_BYTES);
        if (probe == 0) {
            return false;
        }
        HipBlasLtNativeLib.ltDestroyPlan(probe);
        return true;
    }

    /**
     * (transa, transb, m, n, k, alpha, A, lda, B, ldb, beta, C, ldc [, bias]).
     */
    private static void ltMatmul(HipBlasLtContext context, String functionName, LtCall call, LibraryInvocation invocation) {
        final int transa = op((int) invocation.getArg(0));
        final int transb = op((int) invocation.getArg(1));
        final int m = (int) invocation.getArg(2);
        final int n = (int) invocation.getArg(3);
        final int k = (int) invocation.getArg(4);
        final float alpha = (float) invocation.getArg(5);
        final int lda = (int) invocation.getArg(7);
        final int ldb = (int) invocation.getArg(9);
        final float beta = (float) invocation.getArg(10);
        final int ldc = (int) invocation.getArg(12);
        final long biasPtr = call.hasBias() ? invocation.getDevicePointer(13) : 0;

        String planKey = functionName + ":" + transa + ":" + transb + ":" + m + ":" + n + ":" + k + ":" + lda + ":" + ldb + ":" + ldc;
        Long plan = context.planCache.get(planKey);
        if (plan == null) {
            plan = HipBlasLtNativeLib.ltCreatePlan(context.handle, transa, transb, m, n, k, lda, ldb, ldc, //
                    call.inputType().value(), call.inputType().value(), call.outputType().value(), //
                    HIPBLAS_COMPUTE_32F, CudaDataType.CUDA_R_32F.value(), //
                    call.epilogue().value(), WORKSPACE_BYTES);
            if (plan == 0) {
                if (isFP8(call.inputType()) && !hasFP8Support(context)) {
                    throw new TornadoDeviceFP8NotSupported("[UNSUPPORTED] OCP FP8 matmul is not available for this device/hipBLASLt pair " //
                            + "(gfx942 has FNUZ FP8 only; OCP E4M3 needs gfx950 or RDNA4). Function: " + functionName);
                }
                throw new TornadoRuntimeException("[ERROR] hipblasLtMatmul plan creation failed for " + functionName + " (" + planKey + ")");
            }
            context.planCache.put(planKey, plan);
        }

        // The stream belongs to the thread that runs the plan, which need not be the one that
        // created this context: follow it, or the call runs on another thread's stream, unordered
        // with the kernels that produce its inputs.
        long stream = ((TornadoNativeStreamSupport) invocation.getDevice()).getNativeStream(invocation.getExecutionPlanId());
        int status = HipBlasLtNativeLib.ltExecutePlan(context.handle, plan, stream, alpha, //
                invocation.getDevicePointer(6), invocation.getDevicePointer(8), beta, //
                invocation.getDevicePointer(11), biasPtr, context.workspacePtr, WORKSPACE_BYTES);
        HipBlasNativeLib.checkStatus(status, functionName);
    }

    @Override
    public void destroyContext(LibraryContext context) {
        HipBlasLtContext ltContext = (HipBlasLtContext) context;
        for (Long plan : ltContext.planCache.values()) {
            HipBlasLtNativeLib.ltDestroyPlan(plan);
        }
        ltContext.planCache.clear();
        HipBlasNativeLib.freeDeviceMemory(ltContext.workspacePtr);
        HipBlasLtNativeLib.ltDestroy(ltContext.handle);
    }
}
