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
package uk.ac.manchester.tornado.drivers.cuda.virtual;

import uk.ac.manchester.tornado.drivers.cuda.CUDAContextInterface;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDevice;
import uk.ac.manchester.tornado.drivers.cuda.TornadoPlatformInterface;

public class VirtualCUDAPlatform implements TornadoPlatformInterface {

    private final int index;
    private final CUDATargetDevice device;
    private VirtualCUDAContext context;

    public VirtualCUDAPlatform(VirtualDeviceDescriptor info) {
        this.index = 0;
        this.device = new VirtualCUDADevice(info);
    }

    public CUDAContextInterface createContext() {
        context = new VirtualCUDAContext(this, device);
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
