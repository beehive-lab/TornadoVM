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

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cublas.CuBlasLt;
import uk.ac.manchester.tornado.cublas.enums.CuBlasLtEpilogue;
import uk.ac.manchester.tornado.cublas.enums.CublasComputeType;
import uk.ac.manchester.tornado.cublas.enums.CudaDataType;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} for NVIDIA cuBLASLt (matmul with fused
 * epilogues). The per-(device, execution plan) context holds the cublasLt
 * handle, the CUDA stream, a device workspace, and a cache of matmul plans
 * (descriptors + heuristic algorithm) keyed by problem shape — descriptors are
 * created once per shape and replayed on every execution.
 */
public final class CuBlasLtLibraryProvider implements TornadoLibraryProvider {

    /** Default workspace recommended for cublasLtMatmul heuristics (32 MiB). */
    private static final long WORKSPACE_BYTES = 32L * 1024 * 1024;

    private static final class CuBlasLtContext implements LibraryContext {
        private final long handle;
        private final long stream;
        private final long workspacePtr;
        private final Map<String, Long> planCache = new HashMap<>();

        private CuBlasLtContext(long handle, long stream, long workspacePtr) {
            this.handle = handle;
            this.stream = stream;
            this.workspacePtr = workspacePtr;
        }
    }

    private record LtCall(CudaDataType inputType, CudaDataType outputType, CuBlasLtEpilogue epilogue, boolean hasBias) {
    }

    /**
     * Dispatch registry: function name -> type/epilogue configuration. All
     * configurations share one marshaller ({@code ltMatmul}).
     */
    private static final Map<String, LtCall> FUNCTIONS = Map.of( //
            "ltMatmulFP32", new LtCall(CudaDataType.CUDA_R_32F, CudaDataType.CUDA_R_32F, CuBlasLtEpilogue.CUBLASLT_EPILOGUE_DEFAULT, false), //
            "ltMatmulFP16", new LtCall(CudaDataType.CUDA_R_16F, CudaDataType.CUDA_R_16F, CuBlasLtEpilogue.CUBLASLT_EPILOGUE_DEFAULT, false), //
            "ltMatmulBiasFP16", new LtCall(CudaDataType.CUDA_R_16F, CudaDataType.CUDA_R_16F, CuBlasLtEpilogue.CUBLASLT_EPILOGUE_BIAS, true), //
            "ltMatmulGeluBiasFP16", new LtCall(CudaDataType.CUDA_R_16F, CudaDataType.CUDA_R_16F, CuBlasLtEpilogue.CUBLASLT_EPILOGUE_GELU_BIAS, true));

    @Override
    public String libraryName() {
        return CuBlasLt.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        CuBlasNativeLib.load();
        long stream = ((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId);
        long handle = CuBlasLtNativeLib.ltCreate();
        if (handle == 0) {
            throw new TornadoRuntimeException("[ERROR] cublasLtCreate failed");
        }
        long workspacePtr = CuBlasNativeLib.allocateDeviceMemory(WORKSPACE_BYTES);
        if (workspacePtr == 0) {
            CuBlasLtNativeLib.ltDestroy(handle);
            throw new TornadoRuntimeException("[ERROR] Unable to allocate cuBLASLt workspace of " + WORKSPACE_BYTES + " bytes");
        }
        return new CuBlasLtContext(handle, stream, workspacePtr);
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        LtCall call = FUNCTIONS.get(functionName);
        if (call == null) {
            throw new TornadoRuntimeException("[ERROR] cuBLASLt function not supported: " + functionName);
        }
        ltMatmul((CuBlasLtContext) invocation.getContext(), functionName, call, invocation);
    }

    /**
     * (transa, transb, m, n, k, alpha, A, lda, B, ldb, beta, C, ldc [, bias])
     */
    private static void ltMatmul(CuBlasLtContext context, String functionName, LtCall call, LibraryInvocation invocation) {
        final int transa = (int) invocation.getArg(0);
        final int transb = (int) invocation.getArg(1);
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
            plan = CuBlasLtNativeLib.ltCreatePlan(context.handle, transa, transb, m, n, k, lda, ldb, ldc, //
                    call.inputType().value(), call.inputType().value(), call.outputType().value(), //
                    CublasComputeType.CUBLAS_COMPUTE_32F.value(), CudaDataType.CUDA_R_32F.value(), //
                    call.epilogue().value(), WORKSPACE_BYTES);
            if (plan == 0) {
                throw new TornadoRuntimeException("[ERROR] cublasLtMatmul plan creation failed for " + functionName + " (" + planKey + ")");
            }
            context.planCache.put(planKey, plan);
        }

        int status = CuBlasLtNativeLib.ltExecutePlan(context.handle, plan, context.stream, alpha, //
                invocation.getDevicePointer(6), invocation.getDevicePointer(8), beta, //
                invocation.getDevicePointer(11), biasPtr, context.workspacePtr, WORKSPACE_BYTES);
        CuBlasNativeLib.checkStatus(status, functionName);
    }

    @Override
    public void destroyContext(LibraryContext context) {
        CuBlasLtContext ltContext = (CuBlasLtContext) context;
        for (Long plan : ltContext.planCache.values()) {
            CuBlasLtNativeLib.ltDestroyPlan(plan);
        }
        ltContext.planCache.clear();
        CuBlasNativeLib.freeDeviceMemory(ltContext.workspacePtr);
        CuBlasLtNativeLib.ltDestroy(ltContext.handle);
    }
}
