/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.cuda.mm.CUDAMemoryManager;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public interface CUDADeviceContextInterface extends TornadoDeviceContext {

    CUDATargetDevice getDevice();

    CUDACodeCache getCodeCache(long executionPlanId);

    boolean isCached(long executionPlanId, String id, String entryPoint);

    CUDAInstalledCode getInstalledCode(long executionPlanId, String id, String entryPoint);

    CUDAInstalledCode installCode(long executionPlanId, TaskDataContext meta, String id, String entryPoint, byte[] code);

    boolean isKernelAvailable(long executionPlanId);

    void reset(long executionPlanId);

    TornadoXPUDevice toDevice();

    void dumpEvents();

    void flush(long executionPlanId);

    CUDAMemoryManager getMemoryManager();

    TornadoBufferProvider getBufferProvider();

    void sync(long executionPlanId);

    int enqueueBarrier(long executionPlanId);

    int enqueueBarrier(long executionPlanId, int[] events);

    int enqueueMarker(long executionPlanId);

    int enqueueMarker(long executionPlanId, int[] events);

    Event resolveEvent(long executionPlanId, int event);

    void flushEvents(long executionPlanId);

    CUDAContextInterface getPlatformContext();

    long getDeviceId();

    CUDAProgram createProgramWithSource(byte[] source, long[] lengths);

    CUDAProgram createProgramWithBinary(byte[] binary, long[] lengths);

    CUDAProgram createProgramWithIL(byte[] binary, long[] lengths);

    /**
     * Records, per execution plan, whether intra-plan concurrency (multi-queue issue) is enabled.
     */
    default void setIntraPlanConcurrency(long executionPlanId, boolean enabled) {
        // no-op by default
    }

    /**
     * Records whether large one-shot H2D uploads are routed through the pinned staging ring.
     */
    default void setStagedTransfers(boolean enabled) {
        // no-op by default
    }

    /**
     * Sets how many kernel launches from device code may be pending at once on this device
     * ({@code withCUDAPendingLaunchCount}). No-op by default.
     */
    default void setDevicePendingLaunchCount(int count) {
        // no-op by default
    }

    /**
     * Whether large one-shot H2D uploads are routed through the pinned staging ring.
     */
    default boolean isStagedTransfersEnabled() {
        return TornadoOptions.ENABLE_STAGED_TRANSFERS;
    }

    /**
     * Releases the pinned host block backing the staging ring. Called at device teardown, once every
     * plan has been reset.
     */
    default void releaseStagedRing() {
        // no-op by default
    }

    /* ---- CUDA Graph (stream capture) support ---- */

    default void beginExecutionGraphCapture(long executionPlanId) {
        throw new UnsupportedOperationException("CUDA graph capture is not supported on this device context");
    }

    default long endExecutionGraphCaptureAndInstantiate(long executionPlanId) {
        throw new UnsupportedOperationException("CUDA graph capture is not supported on this device context");
    }

    default int launchExecutionGraph(long executionPlanId, long executionGraphHandle) {
        throw new UnsupportedOperationException("CUDA graph launch is not supported on this device context");
    }

    default void destroyExecutionGraph(long executionGraphHandle) {
        // no-op by default
    }

    default void abortExecutionGraphCapture(long executionPlanId) {
        // no-op by default
    }

    /* ---- Native interop (external libraries, e.g. cuBLAS) ---- */

    default long getNativeStream(long executionPlanId) {
        throw new UnsupportedOperationException("Native stream interop is not supported on this device context");
    }

    default long getNativeContext(long executionPlanId) {
        throw new UnsupportedOperationException("Native context interop is not supported on this device context");
    }

    /** Warms up the device's transfer path before its first execution; no-op without a native context. */
    default void warmUp() {
    }

    /** Makes this device's context current on the calling thread; no-op without a native context. */
    default void makeCurrent() {
    }


    /**
     * Compiles {@code source} in the background into the in-process image cache (unless the module
     * cache on disk has it already), so that installing it later only loads the module.
     */
    default void prefetchSource(long executionPlanId, TaskDataContext meta, String entryPoint, byte[] source) {
    }
}
