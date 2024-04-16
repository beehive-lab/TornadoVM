/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl;

import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class OCLNvml extends TornadoLogger {
    private final OCLDeviceContextInterface deviceContext;
    private final boolean isNmvlSupportedForDevice;

    public OCLNvml(OCLDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        this.isNmvlSupportedForDevice = isDeviceContextNvidia();
        clNvmlInit();
    }

    static native long nvmlInit() throws OCLException;

    static native long nvmlDeviceGetHandleByIndex(long index, long[] device) throws OCLException;

    static native long nvmlDeviceGetPowerUsage(long[] device, long[] powerUsage) throws OCLException;

    private boolean isDeviceContextNvidia() {
        return this.deviceContext.getPlatformContext().getPlatform().getName().toLowerCase().contains("nvidia");
    }

    public long clNvmlInit() {
        try {
            if (this.isNmvlSupportedForDevice) {
                return nvmlInit();
            }
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return -1;
    }

    public long clNvmlDeviceGetHandleByIndex(long[] device) {
        try {
            if (this.isNmvlSupportedForDevice) {
                return nvmlDeviceGetHandleByIndex(deviceContext.getDevice().getIndex(), device);
            }
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return -1;
    }

    public long clNvmlDeviceGetPowerUsage(long[] device, long[] powerUsage) {
        try {
            if (this.isNmvlSupportedForDevice) {
                return nvmlDeviceGetPowerUsage(device, powerUsage);
            }
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return -1;
    }
}
