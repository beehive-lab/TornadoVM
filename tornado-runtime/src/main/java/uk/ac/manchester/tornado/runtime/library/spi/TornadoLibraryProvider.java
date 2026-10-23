/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.library.spi;

import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

/**
 * Service-provider interface for external native library bindings (e.g.,
 * NVIDIA cuBLAS, cuDNN, cuFFT). Library binding modules implement this
 * interface and register it via {@link java.util.ServiceLoader}
 * ({@code provides} in their module descriptor), so new libraries plug in
 * without changes to the TornadoVM core runtime.
 *
 * <p>
 * A provider is responsible for loading its native binding, creating an opaque
 * per-(device, execution-plan) {@link LibraryContext} (e.g., a cuBLAS handle
 * bound to the device's native stream), and dispatching function calls with
 * arguments already resolved to device pointers by the interpreter.
 * </p>
 */
public interface TornadoLibraryProvider {

    /**
     * Unique library identifier matched against
     * {@link uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor#getLibraryName()},
     * e.g. {@code "nvidia/cublas"}.
     */
    String libraryName();

    /**
     * Returns true if this provider can execute on the given device (e.g.,
     * requires a CUDA backend device).
     */
    boolean canHandle(TornadoXPUDevice device);

    /**
     * Creates the native execution context for the given device and execution
     * plan (e.g., creates a cuBLAS handle and binds it to the device's stream).
     */
    LibraryContext createContext(TornadoXPUDevice device, long executionPlanId);

    /**
     * Optional hook invoked before every launch region, after the context
     * exists - in particular BEFORE CUDA graph capture starts on the first
     * execution. Providers whose per-shape plans allocate device memory (e.g.,
     * cuFFT work areas, cuDNN convolution workspaces) create them here so the
     * later {@link #dispatch} is capture-safe. Called repeatedly:
     * implementations must be idempotent (typically a plan-cache lookup).
     */
    default void prepare(uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor descriptor, LibraryContext context) {
    }

    /**
     * Executes the given library function. Arguments are provided resolved to
     * device pointers via the {@link LibraryInvocation}.
     */
    void dispatch(String functionName, LibraryInvocation invocation);

    /**
     * Releases native resources held by a context previously returned by
     * {@link #createContext}.
     */
    void destroyContext(LibraryContext context);
}
