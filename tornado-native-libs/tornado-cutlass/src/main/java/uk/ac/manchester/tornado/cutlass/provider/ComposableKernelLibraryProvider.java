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
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoLibraryTaskNotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cutlass.Cutlass;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} for {@link Cutlass} library tasks on HIP devices, with the GEMM
 * kernels TornadoVM builds from AMD Composable Kernel into libtornado-ck: the counterpart of
 * {@link CutlassLibraryProvider}. CDNA GPUs run CK's XDL (matrix-core) operations for every type;
 * RDNA GPUs run its WMMA operations for FP16 and BF16 and its DL operation for FP32. As with
 * CUTLASS, the per-(device, execution plan) context holds only a grow-only workspace, sized in
 * {@link #prepare} before graph capture starts, so {@link #dispatch} allocates nothing.
 * {@code cutlassHgemmBatched} launches one GEMM per batch entry.
 */
public final class ComposableKernelLibraryProvider implements TornadoLibraryProvider {

    private static final class CkContext implements LibraryContext {
        private long workspacePtr;
        private long workspaceBytes;

        private void growWorkspace(long required) {
            if (required > workspaceBytes) {
                if (workspacePtr != 0) {
                    ComposableKernelNativeLib.freeDeviceMemory(workspacePtr);
                    workspacePtr = 0;
                    workspaceBytes = 0;
                }
                long ptr = ComposableKernelNativeLib.allocateDeviceMemory(required);
                if (ptr == 0) {
                    throw new TornadoRuntimeException("[ERROR] Unable to allocate Composable Kernel workspace of " + required + " bytes");
                }
                workspacePtr = ptr;
                workspaceBytes = required;
            }
        }
    }

    /** Whether libtornado-ck and the HIP runtime can be reached on this host. */
    public static boolean isAvailable() {
        return ComposableKernelNativeLib.isAvailable();
    }

    @Override
    public String libraryName() {
        return Cutlass.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device.getTornadoVMBackend() == TornadoVMBackendType.HIP && device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        ComposableKernelNativeLib.load();
        return new CkContext();
    }

    @Override
    public void prepare(LibraryTaskDescriptor descriptor, LibraryContext context) {
        Object[] p = descriptor.getParameters();
        String functionName = descriptor.getFunctionName();
        int m = (int) p[0];
        int n = (int) p[1];
        int k = (int) p[2];
        long workspace = switch (functionName) {
            // (m, n, k, alpha, a, b, beta, c)
            case "cutlassSgemm" -> ComposableKernelNativeLib.gemmWorkspace(ComposableKernelNativeLib.F32, m, n, k, (float) p[6]);
            case "cutlassHgemm" -> ComposableKernelNativeLib.gemmWorkspace(ComposableKernelNativeLib.F16, m, n, k, (float) p[6]);
            case "cutlassBgemm" -> ComposableKernelNativeLib.gemmWorkspace(ComposableKernelNativeLib.BF16, m, n, k, (float) p[6]);
            case "cutlassGemmBiasRelu", "cutlassGemmBiasGelu", "cutlassGemmBiasSilu", "cutlassGemmBiasSigmoid", "cutlassGemmBiasTanh", "cutlassGemmBiasHardSwish" -> //
                    ComposableKernelNativeLib.gemmBiasActWorkspace(activation(functionName), m, n, k);
            // (m, n, k, alpha, a, b, beta, c, batchCount)
            case "cutlassHgemmBatched" -> ComposableKernelNativeLib.hgemmBatchedWorkspace(m, n, k, (float) p[6], (int) p[8]);
            default -> throw new TornadoRuntimeException("[ERROR] CUTLASS function not supported: " + functionName);
        };
        if (workspace < 0) {
            checkStatus((int) -workspace, functionName);
        }
        ((CkContext) context).growWorkspace(workspace);
    }

    private static int activation(String functionName) {
        return switch (functionName) {
            case "cutlassGemmBiasRelu" -> ComposableKernelNativeLib.RELU;
            case "cutlassGemmBiasGelu" -> ComposableKernelNativeLib.GELU;
            case "cutlassGemmBiasSilu" -> ComposableKernelNativeLib.SILU;
            case "cutlassGemmBiasSigmoid" -> ComposableKernelNativeLib.SIGMOID;
            case "cutlassGemmBiasTanh" -> ComposableKernelNativeLib.TANH;
            default -> ComposableKernelNativeLib.HARDSWISH;
        };
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        CkContext context = (CkContext) invocation.getContext();
        // The stream belongs to the thread that runs the plan, which need not be the one that
        // created this context: follow it, or the call runs on another thread's stream, unordered
        // with the kernels that produce its inputs.
        long stream = ((TornadoNativeStreamSupport) invocation.getDevice()).getNativeStream(invocation.getExecutionPlanId());
        int m = (int) invocation.getArg(0);
        int n = (int) invocation.getArg(1);
        int k = (int) invocation.getArg(2);
        int status = switch (functionName) {
            // (m, n, k, alpha, a, b, beta, c)
            case "cutlassSgemm", "cutlassHgemm", "cutlassBgemm" -> ComposableKernelNativeLib.gemm(stream, type(functionName), m, n, k, (float) invocation.getArg(3), //
                    invocation.getDevicePointer(4), invocation.getDevicePointer(5), (float) invocation.getArg(6), invocation.getDevicePointer(7), context.workspacePtr);
            // (m, n, k, a, b, bias, d)
            case "cutlassGemmBiasRelu", "cutlassGemmBiasGelu", "cutlassGemmBiasSilu", "cutlassGemmBiasSigmoid", "cutlassGemmBiasTanh", "cutlassGemmBiasHardSwish" -> //
                    ComposableKernelNativeLib.gemmBiasAct(stream, activation(functionName), m, n, k, invocation.getDevicePointer(3), invocation.getDevicePointer(4),
                            invocation.getDevicePointer(5), invocation.getDevicePointer(6), context.workspacePtr);
            // (m, n, k, alpha, a, b, beta, c, batchCount)
            case "cutlassHgemmBatched" -> ComposableKernelNativeLib.hgemmBatched(stream, m, n, k, (float) invocation.getArg(3), invocation.getDevicePointer(4),
                    invocation.getDevicePointer(5), (float) invocation.getArg(6), invocation.getDevicePointer(7), (int) invocation.getArg(8), context.workspacePtr);
            default -> throw new TornadoRuntimeException("[ERROR] CUTLASS function not supported: " + functionName);
        };
        checkStatus(status, functionName);
    }

    private static int type(String functionName) {
        return switch (functionName) {
            case "cutlassHgemm" -> ComposableKernelNativeLib.F16;
            case "cutlassBgemm" -> ComposableKernelNativeLib.BF16;
            default -> ComposableKernelNativeLib.F32;
        };
    }

    private static void checkStatus(int status, String functionName) {
        if (status == ComposableKernelNativeLib.NO_KERNEL) {
            throw new TornadoLibraryTaskNotSupported("[UNSUPPORTED] " + functionName + ": libtornado-ck has no Composable Kernel GEMM for this GPU");
        }
        if (status != ComposableKernelNativeLib.SUCCESS) {
            throw new TornadoRuntimeException("[ERROR] " + functionName + " failed on Composable Kernel: " + ComposableKernelNativeLib.decodeStatus(status));
        }
    }

    @Override
    public void destroyContext(LibraryContext context) {
        CkContext ckContext = (CkContext) context;
        if (ckContext.workspacePtr != 0) {
            ComposableKernelNativeLib.freeDeviceMemory(ckContext.workspacePtr);
            ckContext.workspacePtr = 0;
        }
    }
}
