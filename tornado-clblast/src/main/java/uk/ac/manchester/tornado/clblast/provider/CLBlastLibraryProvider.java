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
package uk.ac.manchester.tornado.clblast.provider;

import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.clblast.CLBlast;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/** Enqueues CLBlast operations on TornadoVM's OpenCL command queue. */
public final class CLBlastLibraryProvider implements TornadoLibraryProvider {

    // Loading a provider through ServiceLoader must not require CLBlast to be installed.
    private static final class NativeLibrary {
        private static final CLBlastNativeLib INSTANCE = CLBlastNativeLib.load();
    }

    private static final class CLBlastContext implements LibraryContext {
        // The device owns the queue. Resolve it at dispatch time because queues are per thread.
    }

    public static boolean isAvailable() {
        return NativeLibrary.INSTANCE != null && NativeLibrary.INSTANCE.isAvailable();
    }

    @Override
    public String libraryName() {
        return CLBlast.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device.getTornadoVMBackend() == TornadoVMBackendType.OPENCL && device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        if (!canHandle(device)) {
            throw new TornadoRuntimeException("CLBlast requires an OpenCL device exposing its native command queue");
        }
        requireLibrary();
        ((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId);
        return new CLBlastContext();
    }

    private static CLBlastNativeLib requireLibrary() {
        CLBlastNativeLib library = NativeLibrary.INSTANCE;
        if (library == null || !library.isAvailable()) {
            throw new TornadoRuntimeException("Unable to load CLBlast. Install the CLBlast shared library and make it available on the native library path");
        }
        return library;
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        if (!(invocation.getContext() instanceof CLBlastContext) || !canHandle(invocation.getDevice())) {
            throw new TornadoRuntimeException("Invalid OpenCL context for CLBlast");
        }
        long queue = ((TornadoNativeStreamSupport) invocation.getDevice()).getNativeStream(invocation.getExecutionPlanId());
        requireLibrary().dispatch(functionName, invocation, queue);
    }

    @Override
    public void destroyContext(LibraryContext context) {
        // No owned native resources: queues and buffers belong to the OpenCL backend.
    }
}
