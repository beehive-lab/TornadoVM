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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.metal.virtual;

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.drivers.metal.MetalContextInterface;
import uk.ac.manchester.tornado.drivers.metal.MetalTargetDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class VirtualMetalContext implements MetalContextInterface {

    private final List<MetalTargetDevice> devices;
    private final VirtualMetalPlatform platform;

    public VirtualMetalContext(VirtualMetalPlatform platform, MetalTargetDevice device) {
        this.platform = platform;
        this.devices = new ArrayList<>();
        devices.add(device);
    }

    public int getNumDevices() {
        return 1;
    }

    public List<MetalTargetDevice> devices() {
        return devices;
    }

    @Override
    public long getContextId() {
        return 0;
    }

    public void cleanup() {
    }

    @Override
    public String toString() {
        return getClass().getName();
    }

    @Override
    public VirtualMetalDeviceContext createDeviceContext(int index) {
        new TornadoLogger().debug("creating device context for device: %s", devices.get(index).toString());
        return new VirtualMetalDeviceContext(devices.get(index), this);
    }

    public int getPlatformIndex() {
        return platform.getIndex();
    }

    public VirtualMetalPlatform getPlatform() {
        return platform;
    }

    @Override
    public void createCommandQueue(int index) {
    }
}
