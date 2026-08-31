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
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.curand.CuRand;
import uk.ac.manchester.tornado.curand.enums.CuRandRngType;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * Serves {@link CuRand} library tasks by calling NVIDIA cuRAND.
 *
 * <p>
 * One generator is created per execution plan and bound to that plan's CUDA stream, so generation
 * is ordered against the surrounding kernels and transfers. The generator carries its own sequence
 * position, which is what makes repeated dispatches produce fresh numbers rather than the same
 * block again: the seed is applied once, when the generator is created, and re-applying it would
 * reset the sequence.
 * </p>
 */
public final class CuRandLibraryProvider implements TornadoLibraryProvider {

    private static final class CuRandContext implements LibraryContext {
        private final long generator;
        private boolean seeded;

        private CuRandContext(long generator) {
            this.generator = generator;
        }
    }

    @FunctionalInterface
    private interface CuRandCall {
        int invoke(long generator, LibraryInvocation invocation);
    }

    private static final Map<String, CuRandCall> FUNCTIONS = Map.of(//
            "curandGenerateNormal", CuRandLibraryProvider::generateNormal, //
            "curandGenerateNormalDouble", CuRandLibraryProvider::generateNormalDouble, //
            "curandGenerateUniform", CuRandLibraryProvider::generateUniform, //
            "curandGenerateUniformDouble", CuRandLibraryProvider::generateUniformDouble);

    @Override
    public String libraryName() {
        return CuRand.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        // Only the CUDA backend exposes its native stream for library interop
        return device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        CuRandNativeLib.load();
        long stream = ((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId);
        long generator = CuRandNativeLib.curandCreateGenerator(CuRandRngType.CURAND_RNG_PSEUDO_DEFAULT.value());
        if (generator == 0) {
            throw new TornadoRuntimeException("[ERROR] curandCreateGenerator failed");
        }
        CuRandNativeLib.checkStatus(CuRandNativeLib.curandSetStream(generator, stream), "curandSetStream");
        return new CuRandContext(generator);
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        CuRandCall call = FUNCTIONS.get(functionName);
        if (call == null) {
            throw new TornadoRuntimeException("[ERROR] Unknown cuRAND function: " + functionName);
        }
        CuRandContext context = (CuRandContext) invocation.getContext();
        seedOnce(context, invocation);
        CuRandNativeLib.checkStatus(call.invoke(context.generator, invocation), functionName);
    }

    /**
     * Applies the requested seed the first time this generator is used. cuRAND restarts the
     * sequence whenever a seed is set, so seeding on every dispatch would hand out the same numbers
     * over and over.
     */
    private static void seedOnce(CuRandContext context, LibraryInvocation invocation) {
        if (context.seeded) {
            return;
        }
        long seed = ((Number) invocation.getArg(invocation.getNumArgs() - 1)).longValue();
        CuRandNativeLib.checkStatus(CuRandNativeLib.curandSetPseudoRandomGeneratorSeed(context.generator, seed), "curandSetPseudoRandomGeneratorSeed");
        context.seeded = true;
    }

    /** (output, elementOffset, count, mean, stddev, seed). */
    private static int generateNormal(long generator, LibraryInvocation invocation) {
        return CuRandNativeLib.curandGenerateNormal(generator, //
                invocation.getDevicePointer(0), //
                ((Number) invocation.getArg(1)).longValue(), //
                ((Number) invocation.getArg(2)).longValue(), //
                ((Number) invocation.getArg(3)).floatValue(), //
                ((Number) invocation.getArg(4)).floatValue());
    }

    /** (output, elementOffset, count, mean, stddev, seed). */
    private static int generateNormalDouble(long generator, LibraryInvocation invocation) {
        return CuRandNativeLib.curandGenerateNormalDouble(generator, //
                invocation.getDevicePointer(0), //
                ((Number) invocation.getArg(1)).longValue(), //
                ((Number) invocation.getArg(2)).longValue(), //
                ((Number) invocation.getArg(3)).doubleValue(), //
                ((Number) invocation.getArg(4)).doubleValue());
    }

    /** (output, elementOffset, count, seed). */
    private static int generateUniform(long generator, LibraryInvocation invocation) {
        return CuRandNativeLib.curandGenerateUniform(generator, //
                invocation.getDevicePointer(0), //
                ((Number) invocation.getArg(1)).longValue(), //
                ((Number) invocation.getArg(2)).longValue());
    }

    /** (output, elementOffset, count, seed). */
    private static int generateUniformDouble(long generator, LibraryInvocation invocation) {
        return CuRandNativeLib.curandGenerateUniformDouble(generator, //
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
        if (context instanceof CuRandContext c) {
            CuRandNativeLib.curandDestroyGenerator(c.generator);
        }
    }
}
