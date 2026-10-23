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

/**
 * Capability interface implemented by backend devices that can expose their
 * raw native command stream to external libraries (e.g., the CUDA backend
 * exposes the CUstream so cuBLAS can be bound to it via
 * {@code cublasSetStream}). Library providers query the interpreter device for
 * this interface to enqueue native library work in order with TornadoVM's own
 * kernels and transfers.
 */
public interface TornadoNativeStreamSupport {

    /**
     * Raw native stream handle (e.g., CUstream) used by the given execution
     * plan on this device.
     */
    long getNativeStream(long executionPlanId);

    /**
     * Raw native context handle (e.g., CUcontext) used by the given execution
     * plan on this device.
     */
    long getNativeContext(long executionPlanId);

    /**
     * Makes this device's native context current on the calling thread. The
     * vendor runtimes under the native libraries act on the current context,
     * so this runs before a library handle is created, used or destroyed;
     * otherwise, on a multi-GPU host, the call would land on whichever device
     * the thread last touched. Default: no-op.
     */
    default void makeNativeContextCurrent() {
    }

    /**
     * Opens a host-side NVTX range labelled {@code name} on the issuing thread,
     * so a native library task shows up as a named span in NVIDIA Nsight
     * Systems (alongside the backend's own kernel/transfer ranges). NVTX is a
     * near-zero-cost no-op when no profiler is attached. Default: no-op for
     * backends without NVTX support.
     */
    default void nvtxRangePush(String name) {
    }

    /**
     * Closes the most recently opened NVTX range on the issuing thread.
     * Default: no-op.
     */
    default void nvtxRangePop() {
    }
}
