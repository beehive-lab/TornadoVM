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
package uk.ac.manchester.tornado.drivers.opencl.power;

import uk.ac.manchester.tornado.drivers.common.power.PowerMetric;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import static uk.ac.manchester.tornado.runtime.common.Tornado.error;

public class OCLNvidiaPowerMetric implements PowerMetric {
    private final OCLDeviceContext deviceContext;

    public OCLNvidiaPowerMetric(OCLDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        initializePowerLibrary();
    }

    static native long clNvmlInit() throws OCLException;

    static native long clNvmlDeviceGetHandleByIndex(long index, long[] device) throws OCLException;

    static native long clNvmlDeviceGetPowerUsage(long[] device, long[] powerUsage) throws OCLException;

    @Override
    public void initializePowerLibrary() {
        try {
            clNvmlInit();
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    @Override
    public void getHandleByIndex(long[] device) {
        try {
            clNvmlDeviceGetHandleByIndex(this.deviceContext.getDevice().getIndex(), device);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    @Override
    public void getPowerUsage(long[] device, long[] powerUsage) {
        try {
            clNvmlDeviceGetPowerUsage(device, powerUsage);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }
}
