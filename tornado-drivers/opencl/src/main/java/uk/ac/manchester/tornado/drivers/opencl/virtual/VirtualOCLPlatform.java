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
package uk.ac.manchester.tornado.drivers.opencl.virtual;

import uk.ac.manchester.tornado.drivers.opencl.OCLContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.TornadoPlatformInterface;

public class VirtualOCLPlatform implements TornadoPlatformInterface {

    private final int index;
    private final OCLTargetDevice device;
    private VirtualOCLContext context;

    public VirtualOCLPlatform(VirtualDeviceDescriptor info) {
        this.index = 0;
        this.device = new VirtualOCLDevice(info);
    }

    public OCLContextInterface createContext() {
        context = new VirtualOCLContext(this, device);
        return context;
    }

    public void cleanup() {
        if (context != null) {
            context.cleanup();
        }
    }

    public String getName() {
        return getClass().getName();
    }

    @Override
    public String getVendor() {
        return null;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public String toString() {
        return getClass().getName();
    }

    public int getIndex() {
        return index;
    }

}
