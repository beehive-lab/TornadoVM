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
package uk.ac.manchester.tornado.drivers.opencl;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLMemoryManager;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public interface OCLDeviceContextInterface extends TornadoDeviceContext {

    OCLTargetDevice getDevice();

    OCLCodeCache getCodeCache(long executionPlanId);

    boolean isCached(long executionPlanId, String id, String entryPoint);

    OCLInstalledCode getInstalledCode(long executionPlanId, String id, String entryPoint);

    OCLInstalledCode installCode(long executionPlanId, TaskDataContext meta, String id, String entryPoint, byte[] code);

    boolean isKernelAvailable(long executionPlanId);

    void reset(long executionPlanId);

    TornadoXPUDevice toDevice();

    void dumpEvents();

    void flush(long executionPlanId);

    OCLMemoryManager getMemoryManager();

    TornadoBufferProvider getBufferProvider();

    void sync(long executionPlanId);

    int enqueueBarrier(long executionPlanId);

    int enqueueBarrier(long executionPlanId, int[] events);

    int enqueueMarker(long executionPlanId);

    int enqueueMarker(long executionPlanId, int[] events);

    Event resolveEvent(long executionPlanId, int event);

    void flushEvents(long executionPlanId);

    OCLContextInterface getPlatformContext();

    long getDeviceId();

    OCLProgram createProgramWithSource(byte[] source, long[] lengths);

    OCLProgram createProgramWithBinary(byte[] binary, long[] lengths);

    OCLProgram createProgramWithIL(byte[] binary, long[] lengths);

}
