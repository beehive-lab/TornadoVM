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

import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
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
 * caches cufftHandle plans per (transform, shape), each bound to the plan's
 * CUDA stream so FFTs are ordered with TornadoVM kernels and transfers.
 *
 * <p>
 * cuFFT plan creation allocates a device work area, so plans are created in
 * {@link #prepare} — which the interpreter invokes before any CUDA graph
 * capture starts — making the {@link #dispatch} path capture-safe.
 * </p>
 */
public final class CuFftLibraryProvider implements TornadoLibraryProvider {

    /** cuFFT transform directions. */
    private static final int CUFFT_FORWARD = -1;
    private static final int CUFFT_INVERSE = 1;

    private enum PlanKind {
        C2C_1D, R2C_1D, C2R_1D, Z2Z_1D, C2C_2D
    }

    private record FftCall(PlanKind kind, int direction) {
    }

    /**
     * Dispatch registry: function name -> (plan kind, direction). All entries
     * share the argument shape (input, output, dim1, dim2): dim1/dim2 are
     * (n, batch) for 1D plans and (nx, ny) for 2D plans.
     */
    private static final Map<String, FftCall> FUNCTIONS = Map.of( //
            "cufftForwardC2C", new FftCall(PlanKind.C2C_1D, CUFFT_FORWARD), //
            "cufftInverseC2C", new FftCall(PlanKind.C2C_1D, CUFFT_INVERSE), //
            "cufftForwardR2C", new FftCall(PlanKind.R2C_1D, CUFFT_FORWARD), //
            "cufftInverseC2R", new FftCall(PlanKind.C2R_1D, CUFFT_INVERSE), //
            "cufftForwardZ2Z", new FftCall(PlanKind.Z2Z_1D, CUFFT_FORWARD), //
            "cufftInverseZ2Z", new FftCall(PlanKind.Z2Z_1D, CUFFT_INVERSE), //
            "cufftForward2dC2C", new FftCall(PlanKind.C2C_2D, CUFFT_FORWARD), //
            "cufftInverse2dC2C", new FftCall(PlanKind.C2C_2D, CUFFT_INVERSE));

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
    public void prepare(LibraryTaskDescriptor descriptor, LibraryContext context) {
        FftCall call = FUNCTIONS.get(descriptor.getFunctionName());
        if (call == null) {
            return; // dispatch reports the unknown function with a clear error
        }
        Object[] parameters = descriptor.getParameters();
        getOrCreatePlan((CuFftContext) context, call.kind(), (int) parameters[2], (int) parameters[3]);
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        FftCall call = FUNCTIONS.get(functionName);
        if (call == null) {
            throw new TornadoRuntimeException("[ERROR] cuFFT function not supported: " + functionName);
        }
        CuFftContext context = (CuFftContext) invocation.getContext();
        long plan = getOrCreatePlan(context, call.kind(), (int) invocation.getArg(2), (int) invocation.getArg(3));
        long dIn = invocation.getDevicePointer(0);
        long dOut = invocation.getDevicePointer(1);

        int result = switch (call.kind()) {
            case C2C_1D, C2C_2D -> CuFftNativeLib.cufftExecC2C(plan, dIn, dOut, call.direction());
            case Z2Z_1D -> CuFftNativeLib.cufftExecZ2Z(plan, dIn, dOut, call.direction());
            case R2C_1D -> CuFftNativeLib.cufftExecR2C(plan, dIn, dOut);
            case C2R_1D -> CuFftNativeLib.cufftExecC2R(plan, dIn, dOut);
        };
        CuFftNativeLib.checkResult(result, functionName);
    }

    private static long getOrCreatePlan(CuFftContext context, PlanKind kind, int dim1, int dim2) {
        String planKey = kind + ":" + dim1 + ":" + dim2;
        Long plan = context.planCache.get(planKey);
        if (plan == null) {
            plan = switch (kind) {
                case C2C_1D -> CuFftNativeLib.cufftPlan1dOfType(dim1, dim2, CuFftNativeLib.CUFFT_C2C);
                case R2C_1D -> CuFftNativeLib.cufftPlan1dOfType(dim1, dim2, CuFftNativeLib.CUFFT_R2C);
                case C2R_1D -> CuFftNativeLib.cufftPlan1dOfType(dim1, dim2, CuFftNativeLib.CUFFT_C2R);
                case Z2Z_1D -> CuFftNativeLib.cufftPlan1dOfType(dim1, dim2, CuFftNativeLib.CUFFT_Z2Z);
                case C2C_2D -> CuFftNativeLib.cufftPlan2dOfType(dim1, dim2, CuFftNativeLib.CUFFT_C2C);
            };
            if (plan == 0) {
                throw new TornadoRuntimeException("[ERROR] cufft plan creation failed for " + planKey);
            }
            CuFftNativeLib.checkResult(CuFftNativeLib.cufftSetStream(plan, context.stream), "cufftSetStream");
            context.planCache.put(planKey, plan);
        }
        return plan;
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
