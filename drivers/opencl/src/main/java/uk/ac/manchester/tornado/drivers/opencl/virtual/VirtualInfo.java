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

package uk.ac.manchester.tornado.drivers.opencl.virtual;

import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;

public class VirtualInfo {

    private boolean doubleFPSupport;
    private long[] maxWorkItemSizes;
    private int deviceAddressBits;
    private OCLDeviceType deviceType;

    public long[] getMaxWorkItemSizes() {
        return maxWorkItemSizes;
    }

    public void setMaxWorkItemSizes(long[] maxWorkItemSizes) {
        this.maxWorkItemSizes = maxWorkItemSizes;
    }

    public long getDoubleFPSupport() {
        return doubleFPSupport ? 1 : 0;
    }

    public void setDoubleFPSupport(boolean doubleFPSupport) {
        this.doubleFPSupport = doubleFPSupport;
    }

    public OCLDeviceType deviceType() {
        return deviceType;
    }

    public void setDeviceType(OCLDeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public int getDeviceAddressBits() {
        return deviceAddressBits;
    }

    public void setDeviceAddressBits(int deviceAddressBits) {
        this.deviceAddressBits = deviceAddressBits;
    }
}
