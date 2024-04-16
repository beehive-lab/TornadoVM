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
package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.drivers.common.power.PowerMetric;

import static uk.ac.manchester.tornado.runtime.common.Tornado.error;

public class PTXNvml extends PowerMetric {

    public PTXNvml(PTXDeviceContext deviceContext) {
        super(deviceContext);
        nvmlInit();
    }

    static native long ptxNvmlInit() throws RuntimeException;

    static native long ptxNvmlDeviceGetHandleByIndex(long index, long[] device) throws RuntimeException;

    static native long ptxNnvmlDeviceGetPowerUsage(long[] device, long[] powerUsage) throws RuntimeException;

    @Override
    public long nvmlInit() throws RuntimeException{
        try {
            return ptxNvmlInit();
        } catch (RuntimeException e) {
            error(e.getMessage());
        }

        return -1;
    }
    @Override
    public long nvmlDeviceGetHandleByIndex(long[] device) {
        try {
            return ptxNvmlDeviceGetHandleByIndex(((PTXDeviceContext) deviceContext).getDevice().getDeviceIndex(), device);
        } catch (RuntimeException e) {
            error(e.getMessage());
        }

        return -1;
    }

    @Override
    public long nvmlDeviceGetPowerUsage(long[] device, long[] powerUsage) {
        try {
            return ptxNnvmlDeviceGetPowerUsage(device, powerUsage);
        } catch (RuntimeException e) {
            error(e.getMessage());
        }

        return -1;
    }
}
