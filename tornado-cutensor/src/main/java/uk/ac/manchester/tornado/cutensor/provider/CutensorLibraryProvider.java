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
package uk.ac.manchester.tornado.cutensor.provider;

import java.util.HashMap;
import java.util.Map;

import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cutensor.Cutensor;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} for NVIDIA cuTENSOR (FP32 tensor contractions).
 * The per-(device, execution plan) context holds the cuTENSOR handle, the
 * plan's CUstream, a cache of contraction plans per shape, and a grow-only
 * device workspace. Plans and the workspace are created in {@link #prepare} -
 * before CUDA graph capture starts - so {@link #dispatch} is capture-safe.
 */
public final class CutensorLibraryProvider implements TornadoLibraryProvider {

    private static final class CutensorContext implements LibraryContext {
        private final long handle;
        private final long stream;
        private final Map<String, Long> planCache = new HashMap<>();
        private long workspacePtr;
        private long workspaceBytes;

        private CutensorContext(long handle, long stream) {
            this.handle = handle;
            this.stream = stream;
        }

        private void growWorkspace(long required) {
            if (required > workspaceBytes) {
                if (workspacePtr != 0) {
                    CutensorNativeLib.freeDeviceMemory(workspacePtr);
                    workspacePtr = 0;
                    workspaceBytes = 0;
                }
                long ptr = CutensorNativeLib.allocateDeviceMemory(required);
                if (ptr == 0) {
                    throw new TornadoRuntimeException("[ERROR] Unable to allocate cuTENSOR workspace of " + required + " bytes");
                }
                workspacePtr = ptr;
                workspaceBytes = required;
            }
        }
    }

    @Override
    public String libraryName() {
        return Cutensor.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        CutensorNativeLib.load();
        long stream = ((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId);
        long handle = CutensorNativeLib.cutensorCreateHandle();
        if (handle == 0) {
            throw new TornadoRuntimeException("[ERROR] cutensorCreate failed");
        }
        return new CutensorContext(handle, stream);
    }

    @Override
    public void prepare(LibraryTaskDescriptor descriptor, LibraryContext context) {
        Object[] p = descriptor.getParameters();
        getOrCreatePlan((CutensorContext) context, descriptor.getFunctionName(), p);
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        CutensorContext context = (CutensorContext) invocation.getContext();
        long plan = getOrCreatePlan(context, functionName, invocation);
        int outputIndex = switch (functionName) {
            case "cutensorContraction" -> 5;
            case "cutensorContraction2" -> 6;
            default -> throw new TornadoRuntimeException("[ERROR] cuTENSOR function not supported: " + functionName);
        };
        long dA = invocation.getDevicePointer(outputIndex - 2);
        long dB = invocation.getDevicePointer(outputIndex - 1);
        long dC = invocation.getDevicePointer(outputIndex);
        int status = CutensorNativeLib.contract(context.handle, plan, 1.0f, dA, dB, 0.0f, dC, context.workspacePtr, context.workspaceBytes, context.stream);
        CutensorNativeLib.checkStatus(status, functionName);
    }

    /** Reads the shape scalars from either a descriptor's parameters or a live invocation. */
    private interface Shape {
        int at(int index);
    }

    private static long getOrCreatePlan(CutensorContext context, String functionName, Object[] params) {
        return getOrCreatePlan(context, functionName, i -> (int) params[i]);
    }

    private static long getOrCreatePlan(CutensorContext context, String functionName, LibraryInvocation invocation) {
        return getOrCreatePlan(context, functionName, i -> (int) invocation.getArg(i));
    }

    private static long getOrCreatePlan(CutensorContext context, String functionName, Shape s) {
        int[] modesA;
        long[] extentA;
        int[] modesB;
        long[] extentB;
        int[] modesC;
        long[] extentC;
        String key;
        switch (functionName) {
            case "cutensorContraction" -> {
                // C[m,n] = A[m,k] * B[k,n]; modes: m=0, n=2, k=1
                int m = s.at(0);
                int n = s.at(1);
                int k = s.at(2);
                modesA = new int[] { 0, 1 };
                extentA = new long[] { m, k };
                modesB = new int[] { 1, 2 };
                extentB = new long[] { k, n };
                modesC = new int[] { 0, 2 };
                extentC = new long[] { m, n };
                key = "c1:" + m + ":" + n + ":" + k;
            }
            case "cutensorContraction2" -> {
                // C[i,j] = A[i,k,l] * B[k,l,j]; modes: i=0, j=1, k=2, l=3
                int i = s.at(0);
                int j = s.at(1);
                int k = s.at(2);
                int l = s.at(3);
                modesA = new int[] { 0, 2, 3 };
                extentA = new long[] { i, k, l };
                modesB = new int[] { 2, 3, 1 };
                extentB = new long[] { k, l, j };
                modesC = new int[] { 0, 1 };
                extentC = new long[] { i, j };
                key = "c2:" + i + ":" + j + ":" + k + ":" + l;
            }
            default -> throw new TornadoRuntimeException("[ERROR] cuTENSOR function not supported: " + functionName);
        }
        Long plan = context.planCache.get(key);
        if (plan == null) {
            plan = CutensorNativeLib.createContractionPlan(context.handle, modesA, extentA, modesB, extentB, modesC, extentC);
            if (plan == 0) {
                throw new TornadoRuntimeException("[ERROR] cuTENSOR contraction plan creation failed for " + key);
            }
            context.growWorkspace(CutensorNativeLib.planWorkspaceBytes(plan));
            context.planCache.put(key, plan);
        }
        return plan;
    }

    @Override
    public void destroyContext(LibraryContext context) {
        CutensorContext cutensorContext = (CutensorContext) context;
        for (Long plan : cutensorContext.planCache.values()) {
            CutensorNativeLib.destroyPlan(plan);
        }
        cutensorContext.planCache.clear();
        if (cutensorContext.workspacePtr != 0) {
            CutensorNativeLib.freeDeviceMemory(cutensorContext.workspacePtr);
            cutensorContext.workspacePtr = 0;
        }
        CutensorNativeLib.cutensorDestroyHandle(cutensorContext.handle);
    }
}
