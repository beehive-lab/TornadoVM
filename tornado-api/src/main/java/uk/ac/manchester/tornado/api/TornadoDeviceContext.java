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

    /**
     * Whether {@link #reset(long)} has torn this execution plan's device state down.
     *
     * <p>Scoped to the plan, not to the device. A device context is shared by every execution plan
     * running on that device in this JVM, and {@link #reset(long)} has always been per-plan -- it
     * cleans that plan's queues, its code cache and its event pool and leaves every other plan's
     * alone. The flag it set did not follow it: it was one boolean on the device, so a plan being
     * torn down marked the device as reset for everybody, and the next plan to start cleared the
     * mark for everybody. Two plans on one device therefore interfered in both directions, and the
     * symptom was a healthy warmed-up plan failing with "reset() was called after warmup()" because
     * an unrelated plan had just been closed.
     */
    boolean wasReset(long executionPlanId);

    void reset(long executionPlanId);

    void setResetToFalse(long executionPlanId);

    boolean isFP64Supported();

    boolean isCached(long executionPlanId, String methodName, SchedulableTask task);

    int getDeviceIndex();

    int getDevicePlatform();

    String getDeviceName();

    int getDriverIndex();

    Set<Long> getRegisteredPlanIds();
}
