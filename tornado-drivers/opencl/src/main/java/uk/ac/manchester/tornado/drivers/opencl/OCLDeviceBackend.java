/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.VIRTUAL_DEVICE_ENABLED;

/**
 * One OpenCL device as the runtime uses it: its device context and the TornadoVM memory regions on
 * it.
 */
public final class OCLDeviceBackend {

    private final OCLDeviceContextInterface deviceContext;
    private volatile boolean initialised;

    OCLDeviceBackend(OCLDeviceContextInterface deviceContext) {
        this.deviceContext = deviceContext;
    }

    public OCLDeviceContextInterface getDeviceContext() {
        return deviceContext;
    }

    public boolean isInitialised() {
        return initialised;
    }

    /** Allocates the TornadoVM memory regions on the device (once). */
    public synchronized void init() {
        if (initialised) {
            return;
        }
        if (!VIRTUAL_DEVICE_ENABLED) {
            deviceContext.getMemoryManager().allocateDeviceMemoryRegions();
        }
        initialised = true;
    }
}
