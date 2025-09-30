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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package uk.ac.manchester.tornado.drivers.metal.virtual;

import uk.ac.manchester.tornado.drivers.metal.enums.MetalDeviceType;

public class VirtualDeviceDescriptor {

    private final String deviceName;
    private final boolean doubleFPSupport;
    private final long[] maxWorkItemSizes;
    private final int deviceAddressBits;
    private final MetalDeviceType deviceType;
    private final String deviceExtensions;
    private final int availableProcessors;

    public VirtualDeviceDescriptor(String deviceName, boolean doubleFPSupport, long[] maxWorkItemSizes, int deviceAddressBits, MetalDeviceType deviceType, String deviceExtensions, int availableProcessors) {
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

    public MetalDeviceType deviceType() {
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
