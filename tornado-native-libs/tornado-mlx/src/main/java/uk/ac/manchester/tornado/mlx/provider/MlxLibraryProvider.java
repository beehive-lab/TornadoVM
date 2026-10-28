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
package uk.ac.manchester.tornado.mlx.provider;

import java.util.StringJoiner;

import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.mlx.Mlx;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;

/**
 * {@link TornadoLibraryProvider} for Apple MLX. Discovered by the TornadoVM runtime through
 * {@link java.util.ServiceLoader}.
 *
 * <p>
 * Every operation runs as MLX's own Metal kernels from {@code mlx.metallib} ({@link MlxMetalKernels}),
 * on TornadoVM's command queue with the TornadoVM buffers bound in place, as a JIT-compiled kernel
 * would run, so there is neither an MLX array nor a copy. {@link MlxKernelRoutes} maps each operation
 * onto those kernels; a call whose arguments no route reproduces exactly fails.
 * </p>
 */
public final class MlxLibraryProvider implements TornadoLibraryProvider {

    /** In-place kernels keep no per-plan state. */
    private static final LibraryContext CONTEXT = new LibraryContext() {
    };

    /** Whether MLX's kernel library and the Objective-C runtime can be loaded on this host. */
    public static boolean isAvailable() {
        return MlxMetalKernels.isAvailable();
    }

    /** How many operations ran as in-place MLX kernels, since start-up. */
    public static long kernelDispatches() {
        return MlxKernelRoutes.dispatches();
    }

    @Override
    public String libraryName() {
        return Mlx.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device.getTornadoVMBackend() == TornadoVMBackendType.METAL && isAvailable();
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        if (!isAvailable()) {
            throw new TornadoRuntimeException("[ERROR] Unable to load mlx.metallib. Install MLX (e.g. `brew install mlx`) or set -Dtornado.mlx.metallib.");
        }
        return CONTEXT;
    }

    @Override
    public void destroyContext(LibraryContext context) {
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        if (!MlxKernelRoutes.hasRoute(functionName)) {
            throw new TornadoRuntimeException("[ERROR] Unknown MLX function: " + functionName);
        }
        if (!MlxKernelRoutes.dispatch(functionName, invocation)) {
            throw new TornadoRuntimeException("[ERROR] MLX " + functionName + ": no TornadoVM/MLX kernel takes these arguments " + describe(invocation));
        }
    }

    /** The arguments of a call, e.g. {@code (float32[1024], float32[1024], 3)}. */
    private static String describe(LibraryInvocation invocation) {
        StringJoiner args = new StringJoiner(", ", "(", ")");
        for (int i = 0; i < invocation.getNumArgs(); i++) {
            Object arg = invocation.getArg(i);
            args.add(arg instanceof TornadoNativeArray array ? array.getClass().getSimpleName() + "[" + array.getSize() + "]" : String.valueOf(arg));
        }
        return args.toString();
    }
}
