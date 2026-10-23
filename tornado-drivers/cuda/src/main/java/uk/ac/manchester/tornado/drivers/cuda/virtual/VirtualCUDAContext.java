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
package uk.ac.manchester.tornado.drivers.cuda.virtual;

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.drivers.cuda.CUDAContextInterface;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class VirtualCUDAContext implements CUDAContextInterface {

    private final List<CUDATargetDevice> devices;
    private final VirtualCUDAPlatform platform;

    public VirtualCUDAContext(VirtualCUDAPlatform platform, CUDATargetDevice device) {
        this.platform = platform;
        this.devices = new ArrayList<>();
        devices.add(device);
    }

    public int getNumDevices() {
        return 1;
    }

    public List<CUDATargetDevice> devices() {
        return devices;
    }

    @Override
    public long getContextId(int deviceIndex) {
        return 0;
    }

    public void cleanup() {
    }

    @Override
    public String toString() {
        return getClass().getName();
    }

    @Override
    public VirtualCUDADeviceContext createDeviceContext(int index) {
        new TornadoLogger().debug("creating device context for device: %s", devices.get(index).toString());
        return new VirtualCUDADeviceContext(devices.get(index), this);
    }

    public int getPlatformIndex() {
        return platform.getIndex();
    }

    public VirtualCUDAPlatform getPlatform() {
        return platform;
    }

    @Override
    public void createCommandQueue(int index) {
    }
}
