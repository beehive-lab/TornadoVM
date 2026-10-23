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

import uk.ac.manchester.tornado.drivers.cuda.enums.CUDADeviceType;

public class VirtualDeviceDescriptor {

    private final String deviceName;
    private final boolean doubleFPSupport;
    private final long[] maxWorkItemSizes;
    private final int deviceAddressBits;
    private final CUDADeviceType deviceType;
    private final String deviceExtensions;
    private final int availableProcessors;

    public VirtualDeviceDescriptor(String deviceName, boolean doubleFPSupport, long[] maxWorkItemSizes, int deviceAddressBits, CUDADeviceType deviceType, String deviceExtensions, int availableProcessors) {
        this.deviceName = deviceName;
        this.doubleFPSupport = doubleFPSupport;
        this.maxWorkItemSizes = maxWorkItemSizes;
        this.deviceAddressBits = deviceAddressBits;
        this.deviceType = deviceType;
        this.deviceExtensions = deviceExtensions;
        this.availableProcessors = availableProcessors;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public long[] getMaxWorkItemSizes() {
        return maxWorkItemSizes;
    }

    public boolean getDoubleFPSupport() {
        return doubleFPSupport;
    }

    public CUDADeviceType deviceType() {
        return deviceType;
    }

    public int getDeviceAddressBits() {
        return deviceAddressBits;
    }

    public String getDeviceExtensions() {
        return deviceExtensions;
    }

    public int getAvailableProcessors() {
        return availableProcessors;
    }
}
