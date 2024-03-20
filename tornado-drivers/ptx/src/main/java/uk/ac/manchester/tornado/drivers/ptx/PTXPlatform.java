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
package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class PTXPlatform {
    private final PTXDevice[] devices;

    public PTXPlatform() {
        devices = new PTXDevice[cuDeviceGetCount()];

        if (devices.length == 0) {
            throw new TornadoBailoutRuntimeException("[WARNING] No CUDA devices found. Deoptimizing to sequential execution.");
        }

        for (int i = 0; i < devices.length; i++) {
            devices[i] = new PTXDevice(i);
        }
    }

    public static native int cuDeviceGetCount();

    public void cleanup() {
        for (PTXDevice device : devices) {
            if (device != null) {
                device.getPTXContext().cleanup();
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("name=%s, num. devices=%d", getName(), devices.length));

        return sb.toString().trim();
    }

    public int getDeviceCount() {
        return devices.length;
    }

    public PTXDevice getDevice(int deviceIndex) {
        if (deviceIndex >= devices.length) {
            throw new TornadoBailoutRuntimeException("[ERROR] Device index is invalid " + deviceIndex);
        }
        return devices[deviceIndex];
    }

    public String getName() {
        return "PTX";
    }
}
