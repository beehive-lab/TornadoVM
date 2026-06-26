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

    /**
     * Request CUDA Managed (Unified) Memory allocation for the execution plan
     * currently being serviced by this device context. Backends other than CUDA
     * ignore this request.
     *
     * @param enabled
     *     {@code true} to allocate device buffers as managed memory.
     */
    default void setUnifiedMemoryEnabled(boolean enabled) {
        // Default: no-op. Only the CUDA backend honours Unified Memory.
    }

    /**
     * @return {@code true} when the current execution plan requested CUDA Unified
     *     Memory through this device context.
     */
    default boolean isUnifiedMemoryEnabled() {
        return false;
    }
}
