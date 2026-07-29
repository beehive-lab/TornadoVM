/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAInstalledCode;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResult;
import uk.ac.manchester.tornado.drivers.cuda.mm.CUDAMemoryManager;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public interface CUDADeviceContextInterface extends TornadoDeviceContext {

    CUDATargetDevice getDevice();

    CUDACodeCache getCodeCache(long executionPlanId);

    boolean isCached(long executionPlanId, String id, String entryPoint);

    CUDAInstalledCode getInstalledCode(long executionPlanId, String id, String entryPoint);

    CUDAInstalledCode installCode(long executionPlanId, CUDACompilationResult result);

    CUDAInstalledCode installCode(long executionPlanId, TaskDataContext meta, String id, String entryPoint, byte[] code);

    CUDAInstalledCode installCode(long executionPlanId, String id, String entryPoint, byte[] code, boolean printKernel);

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

    /* ---- Native interop (external libraries, e.g. cuBLAS) ---- */

    default long getNativeStream(long executionPlanId) {
        throw new UnsupportedOperationException("Native stream interop is not supported on this device context");
    }

    default long getNativeContext(long executionPlanId) {
        throw new UnsupportedOperationException("Native context interop is not supported on this device context");
    }

}
