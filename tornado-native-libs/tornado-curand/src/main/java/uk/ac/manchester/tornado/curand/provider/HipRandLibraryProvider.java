/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package uk.ac.manchester.tornado.curand.provider;

import java.util.Map;

import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.curand.CuRand;
import uk.ac.manchester.tornado.curand.enums.CuRandRngType;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} that runs the {@link CuRand} library tasks on HIP devices with ROCm's
 * hipRAND, mirroring {@link CuRandLibraryProvider}: one generator per (device, execution plan) bound to
 * the plan's stream and seeded once.
 */
public final class HipRandLibraryProvider implements TornadoLibraryProvider {

    private static final class HipRandContext implements LibraryContext {
        private final long generator;
        /** The stream the generator is bound to. */
        private long stream;
        private boolean seeded;

        private HipRandContext(long generator, long stream) {
            this.generator = generator;
            this.stream = stream;
        }
    }

    @FunctionalInterface
    private interface HipRandCall {
        int invoke(long generator, LibraryInvocation invocation);
    }

    private static final Map<String, HipRandCall> FUNCTIONS = Map.of(//
            "curandGenerateNormal", HipRandLibraryProvider::generateNormal, //
            "curandGenerateNormalDouble", HipRandLibraryProvider::generateNormalDouble, //
            "curandGenerateUniform", HipRandLibraryProvider::generateUniform, //
            "curandGenerateUniformDouble", HipRandLibraryProvider::generateUniformDouble);

    /** hipRAND's {@code hiprandRngType_t} for a cuRAND generator type (the values differ). */
    private static int hipRandRngType(CuRandRngType type) {
        return switch (type) {
            case CURAND_RNG_PSEUDO_DEFAULT -> 400;
            case CURAND_RNG_PSEUDO_XORWOW -> 401;
            case CURAND_RNG_PSEUDO_MRG32K3A -> 402;
            case CURAND_RNG_PSEUDO_MTGP32 -> 403;
            case CURAND_RNG_PSEUDO_MT19937 -> 404;
            case CURAND_RNG_PSEUDO_PHILOX4_32_10 -> 405;
        };
    }

    /** Whether hipRAND resolved on this host. */
    public static boolean isAvailable() {
        try {
            HipRandNativeLib.load();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public String libraryName() {
        return CuRand.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device.getTornadoVMBackend() == TornadoVMBackendType.HIP && device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        HipRandNativeLib.load();
        long stream = ((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId);
        long generator = HipRandNativeLib.hiprandCreateGenerator(hipRandRngType(CuRandRngType.CURAND_RNG_PSEUDO_DEFAULT));
        if (generator == 0) {
            throw new TornadoRuntimeException("[ERROR] hiprandCreateGenerator failed");
        }
        HipRandNativeLib.checkStatus(HipRandNativeLib.hiprandSetStream(generator, stream), "hiprandSetStream");
        return new HipRandContext(generator, stream);
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        HipRandCall call = FUNCTIONS.get(functionName);
        if (call == null) {
            throw new TornadoRuntimeException("[ERROR] Unknown cuRAND function for hipRAND: " + functionName);
        }
        HipRandContext context = (HipRandContext) invocation.getContext();
        // The stream belongs to the thread that runs the plan, which need not be the one that
        // created this context: follow it, or the call runs on another thread's stream, unordered
        // with the kernels around it.
        long stream = ((TornadoNativeStreamSupport) invocation.getDevice()).getNativeStream(invocation.getExecutionPlanId());
        if (stream != context.stream) {
            HipRandNativeLib.checkStatus(HipRandNativeLib.hiprandSetStream(context.generator, stream), "hiprandSetStream");
            context.stream = stream;
        }
        seedOnce(context, invocation);
        HipRandNativeLib.checkStatus(call.invoke(context.generator, invocation), functionName);
    }

    /**
     * Applies the requested seed the first time this generator is used. hipRAND restarts the
     * sequence whenever a seed is set, so seeding on every dispatch would hand out the same numbers
     * over and over.
     */
    private static void seedOnce(HipRandContext context, LibraryInvocation invocation) {
        if (context.seeded) {
            return;
        }
        long seed = ((Number) invocation.getArg(invocation.getNumArgs() - 1)).longValue();
        HipRandNativeLib.checkStatus(HipRandNativeLib.hiprandSetPseudoRandomGeneratorSeed(context.generator, seed), "hiprandSetPseudoRandomGeneratorSeed");
        context.seeded = true;
    }

    /** (output, elementOffset, count, mean, stddev, seed). */
    private static int generateNormal(long generator, LibraryInvocation invocation) {
        return HipRandNativeLib.hiprandGenerateNormal(generator, //
                invocation.getDevicePointer(0), //
                ((Number) invocation.getArg(1)).longValue(), //
                ((Number) invocation.getArg(2)).longValue(), //
                ((Number) invocation.getArg(3)).floatValue(), //
                ((Number) invocation.getArg(4)).floatValue());
    }

    /** (output, elementOffset, count, mean, stddev, seed). */
    private static int generateNormalDouble(long generator, LibraryInvocation invocation) {
        return HipRandNativeLib.hiprandGenerateNormalDouble(generator, //
                invocation.getDevicePointer(0), //
                ((Number) invocation.getArg(1)).longValue(), //
                ((Number) invocation.getArg(2)).longValue(), //
                ((Number) invocation.getArg(3)).doubleValue(), //
                ((Number) invocation.getArg(4)).doubleValue());
    }

    /** (output, elementOffset, count, seed). */
    private static int generateUniform(long generator, LibraryInvocation invocation) {
        return HipRandNativeLib.hiprandGenerateUniform(generator, //
                invocation.getDevicePointer(0), //
                ((Number) invocation.getArg(1)).longValue(), //
                ((Number) invocation.getArg(2)).longValue());
    }

    /** (output, elementOffset, count, seed). */
    private static int generateUniformDouble(long generator, LibraryInvocation invocation) {
        return HipRandNativeLib.hiprandGenerateUniformDouble(generator, //
                invocation.getDevicePointer(0), //
                ((Number) invocation.getArg(1)).longValue(), //
                ((Number) invocation.getArg(2)).longValue());
    }

    @Override
    public void prepare(LibraryTaskDescriptor descriptor, LibraryContext context) {
        // Generator type is fixed when the context is created; nothing to do per task.
    }

    @Override
    public void destroyContext(LibraryContext context) {
        if (context instanceof HipRandContext c) {
            HipRandNativeLib.hiprandDestroyGenerator(c.generator);
        }
    }
}
