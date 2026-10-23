/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
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


import uk.ac.manchester.tornado.runtime.TornadoAcceleratorBackend;
import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;
import uk.ac.manchester.tornado.runtime.TornadoVMConfigAccess;
import uk.ac.manchester.tornado.runtime.common.enums.TornadoBackends;

public class OCLTornadoDriverProvider implements TornadoBackendProvider {

    /**
     * Check {@link TornadoBackendProvider} for documentation on priority.
     */
    private final TornadoBackends priority = TornadoBackends.OpenCL;

    @Override
    public String getName() {
        return "OpenCL Driver";
    }

    @Override
    public TornadoAcceleratorBackend createBackend(TornadoVMConfigAccess vmConfig) {
        return new OCLBackendImpl(vmConfig);
    }

    @Override
    public TornadoBackends getDevicePriority() {
        return priority;
    }

    @Override
    public int compareTo(TornadoBackendProvider o) {
        return o.getDevicePriority().value() - priority.value();
    }
}
