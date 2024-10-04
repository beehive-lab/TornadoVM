/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api;

import java.util.Set;

import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;

public interface TornadoDeviceContext {

    TornadoTargetDevice getDevice();

    TornadoMemoryProvider getMemoryManager();

    boolean wasReset();

    void reset(long executionPlanId);

    void setResetToFalse();

    boolean isPlatformFPGA();

    boolean isPlatformXilinxFPGA();

    boolean isFP64Supported();

    boolean isCached(long executionPlanId, String methodName, SchedulableTask task);

    int getDeviceIndex();

    int getDevicePlatform();

    String getDeviceName();

    int getDriverIndex();

    Set<Long> getRegisteredPlanIds();
}
