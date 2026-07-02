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
package uk.ac.manchester.tornado.cufft.provider;

import java.util.HashMap;
import java.util.Map;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cufft.CuFft;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} for NVIDIA cuFFT: the second provider after
 * cuBLAS, demonstrating that a new library is a self-contained module pair
 * with zero core-runtime changes. The per-(device, execution plan) context
 * caches cufftHandle plans per (n, batch) shape, each bound to the plan's CUDA
 * stream so FFTs are ordered with TornadoVM kernels and transfers.
 */
public final class CuFftLibraryProvider implements TornadoLibraryProvider {

    /** cuFFT transform directions. */
    private static final int CUFFT_FORWARD = -1;
    private static final int CUFFT_INVERSE = 1;

    private static final class CuFftContext implements LibraryContext {
        private final long stream;
        private final Map<String, Long> planCache = new HashMap<>();

        private CuFftContext(long stream) {
            this.stream = stream;
        }
    }

    @Override
    public String libraryName() {
        return CuFft.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        CuFftNativeLib.load();
        return new CuFftContext(((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId));
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        int direction = switch (functionName) {
            case "cufftForwardC2C" -> CUFFT_FORWARD;
            case "cufftInverseC2C" -> CUFFT_INVERSE;
            default -> throw new TornadoRuntimeException("[ERROR] cuFFT function not supported: " + functionName);
        };
        execC2C((CuFftContext) invocation.getContext(), invocation, direction);
    }

    /** (input, output, n, batch) */
    private static void execC2C(CuFftContext context, LibraryInvocation invocation, int direction) {
        final int n = (int) invocation.getArg(2);
        final int batch = (int) invocation.getArg(3);

        String planKey = n + ":" + batch;
        Long plan = context.planCache.get(planKey);
        if (plan == null) {
            // Note: plan creation allocates a device work area, so the first
            // execution of a shape must happen outside CUDA graph capture.
            plan = CuFftNativeLib.cufftPlan1dC2C(n, batch);
            if (plan == 0) {
                throw new TornadoRuntimeException("[ERROR] cufftPlan1d failed for n=" + n + ", batch=" + batch);
            }
            CuFftNativeLib.checkResult(CuFftNativeLib.cufftSetStream(plan, context.stream), "cufftSetStream");
            context.planCache.put(planKey, plan);
        }

        CuFftNativeLib.checkResult(CuFftNativeLib.cufftExecC2C(plan, invocation.getDevicePointer(0), invocation.getDevicePointer(1), direction), "cufftExecC2C");
    }

    @Override
    public void destroyContext(LibraryContext context) {
        CuFftContext cuFftContext = (CuFftContext) context;
        for (Long plan : cuFftContext.planCache.values()) {
            CuFftNativeLib.cufftDestroy(plan);
        }
        cuFftContext.planCache.clear();
    }
}
