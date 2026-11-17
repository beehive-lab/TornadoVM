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
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cufft.CuFft;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} that runs the {@link CuFft} library tasks on HIP devices with ROCm's
 * hipFFT, mirroring {@link CuFftLibraryProvider}: plans cached per shape and bound to the plan's stream.
 */
public final class HipFftLibraryProvider implements TornadoLibraryProvider {

    /** hipFFT transform directions. */
    private static final int HIPFFT_FORWARD = -1;
    private static final int HIPFFT_BACKWARD = 1;

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
    private static final Map<String, FftCall> FUNCTIONS = Map.of(//
            "cufftForwardC2C", new FftCall(PlanKind.C2C_1D, HIPFFT_FORWARD), //
            "cufftInverseC2C", new FftCall(PlanKind.C2C_1D, HIPFFT_BACKWARD), //
            "cufftForwardR2C", new FftCall(PlanKind.R2C_1D, HIPFFT_FORWARD), //
            "cufftInverseC2R", new FftCall(PlanKind.C2R_1D, HIPFFT_BACKWARD), //
            "cufftForwardZ2Z", new FftCall(PlanKind.Z2Z_1D, HIPFFT_FORWARD), //
            "cufftInverseZ2Z", new FftCall(PlanKind.Z2Z_1D, HIPFFT_BACKWARD), //
            "cufftForward2dC2C", new FftCall(PlanKind.C2C_2D, HIPFFT_FORWARD), //
            "cufftInverse2dC2C", new FftCall(PlanKind.C2C_2D, HIPFFT_BACKWARD));

    private static final class HipFftContext implements LibraryContext {
        /** The stream every cached plan is bound to. */
        private long stream;
        private final Map<String, Long> planCache = new HashMap<>();

        private HipFftContext(long stream) {
            this.stream = stream;
        }
    }


    /** Whether hipFFT can be reached on this host. */
    public static boolean isAvailable() {
        try {
            HipFftNativeLib.load();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public String libraryName() {
        return CuFft.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device.getTornadoVMBackend() == TornadoVMBackendType.HIP && device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        HipFftNativeLib.load();
        return new HipFftContext(((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId));
    }

    @Override
    public void prepare(LibraryTaskDescriptor descriptor, LibraryContext context) {
        FftCall call = FUNCTIONS.get(descriptor.getFunctionName());
        if (call == null) {
            return; // dispatch reports the unknown function with a clear error
        }
        Object[] parameters = descriptor.getParameters();
        getOrCreatePlan((HipFftContext) context, call.kind(), (int) parameters[2], (int) parameters[3]);
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        FftCall call = FUNCTIONS.get(functionName);
        if (call == null) {
            throw new TornadoRuntimeException("[ERROR] cuFFT function not supported on hipFFT: " + functionName);
        }
        HipFftContext context = (HipFftContext) invocation.getContext();
        // The stream belongs to the thread that runs the plan, which need not be the one that
        // created this context: follow it, or the call runs on another thread's stream, unordered
        // with the kernels that produce its inputs.
        long stream = ((TornadoNativeStreamSupport) invocation.getDevice()).getNativeStream(invocation.getExecutionPlanId());
        if (stream != context.stream) {
            for (Long cached : context.planCache.values()) {
                HipFftNativeLib.checkResult(HipFftNativeLib.hipfftSetStream(cached, stream), "hipfftSetStream");
            }
            context.stream = stream;
        }
        long plan = getOrCreatePlan(context, call.kind(), (int) invocation.getArg(2), (int) invocation.getArg(3));
        long dIn = invocation.getDevicePointer(0);
        long dOut = invocation.getDevicePointer(1);

        int result = switch (call.kind()) {
            case C2C_1D, C2C_2D -> HipFftNativeLib.hipfftExecC2C(plan, dIn, dOut, call.direction());
            case Z2Z_1D -> HipFftNativeLib.hipfftExecZ2Z(plan, dIn, dOut, call.direction());
            case R2C_1D -> HipFftNativeLib.hipfftExecR2C(plan, dIn, dOut);
            case C2R_1D -> HipFftNativeLib.hipfftExecC2R(plan, dIn, dOut);
        };
        HipFftNativeLib.checkResult(result, functionName);
    }

    private static long getOrCreatePlan(HipFftContext context, PlanKind kind, int dim1, int dim2) {
        String planKey = kind + ":" + dim1 + ":" + dim2;
        Long plan = context.planCache.get(planKey);
        if (plan == null) {
            plan = switch (kind) {
                case C2C_1D -> HipFftNativeLib.hipfftPlan1dOfType(dim1, dim2, HipFftNativeLib.HIPFFT_C2C);
                case R2C_1D -> HipFftNativeLib.hipfftPlan1dOfType(dim1, dim2, HipFftNativeLib.HIPFFT_R2C);
                case C2R_1D -> HipFftNativeLib.hipfftPlan1dOfType(dim1, dim2, HipFftNativeLib.HIPFFT_C2R);
                case Z2Z_1D -> HipFftNativeLib.hipfftPlan1dOfType(dim1, dim2, HipFftNativeLib.HIPFFT_Z2Z);
                case C2C_2D -> HipFftNativeLib.hipfftPlan2dOfType(dim1, dim2, HipFftNativeLib.HIPFFT_C2C);
            };
            if (plan == 0) {
                throw new TornadoRuntimeException("[ERROR] hipfft plan creation failed for " + planKey);
            }
            HipFftNativeLib.checkResult(HipFftNativeLib.hipfftSetStream(plan, context.stream), "hipfftSetStream");
            context.planCache.put(planKey, plan);
        }
        return plan;
    }

    @Override
    public void destroyContext(LibraryContext context) {
        HipFftContext hipFftContext = (HipFftContext) context;
        for (Long plan : hipFftContext.planCache.values()) {
            HipFftNativeLib.hipfftDestroy(plan);
        }
        hipFftContext.planCache.clear();
    }
}
