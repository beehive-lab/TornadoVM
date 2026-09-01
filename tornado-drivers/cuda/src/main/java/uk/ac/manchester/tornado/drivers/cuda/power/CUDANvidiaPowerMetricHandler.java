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
package uk.ac.manchester.tornado.drivers.cuda.power;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.drivers.common.ffm.NVMLAPI;
import uk.ac.manchester.tornado.drivers.common.power.PowerMetric;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.exceptions.CUDAException;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

public class CUDANvidiaPowerMetricHandler implements PowerMetric {

    private final CUDADeviceContext deviceContext;
    private final TornadoLogger logger;
    private long[] nvmlDevice = new long[1];

    public CUDANvidiaPowerMetricHandler(CUDADeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        this.logger = new TornadoLogger(this.getClass());
        initializePowerLibrary();
    }

    static long nvmlInit() throws CUDAException {
        if (!NVMLAPI.isAvailable()) {
            return -1;
        }
        return NVMLAPI.nvmlInit();
    }

    static long nvmlDeviceGetHandleByIndex(long index, long[] device) throws CUDAException {
        if (!NVMLAPI.isAvailable() || device == null || device.length == 0) {
            return -1;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = FFMSupport.allocatePointer(arena);
            int result = NVMLAPI.nvmlDeviceGetHandleByIndex((int) index, handle);
            device[0] = handle.get(FFMSupport.C_POINTER, 0).address();
            return result;
        }
    }

    /** Reports the device's current draw in milliwatts, which is the unit the profiler prints. */
    static long nvmlDeviceGetPowerUsage(long[] device, long[] powerUsage) throws CUDAException {
        if (!NVMLAPI.isAvailable() || device == null || device.length == 0) {
            return -1;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment milliwatts = FFMSupport.allocateInt(arena);
            int result = NVMLAPI.nvmlDeviceGetPowerUsage(device[0], milliwatts);
            if (powerUsage != null && powerUsage.length > 0) {
                // An unsigned int, but no NVIDIA GPU draws anywhere near 2^31 milliwatts.
                powerUsage[0] = Integer.toUnsignedLong(milliwatts.get(FFMSupport.C_INT, 0));
            }
            return result;
        }
    }

    @Override
    public void initializePowerLibrary() {
        try {
            nvmlInit();
            nvmlDeviceGetHandleByIndex(this.deviceContext.getDevice().getIndex(), this.nvmlDevice);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void getPowerUsage(long[] powerUsage) {
        try {
            nvmlDeviceGetPowerUsage(this.nvmlDevice, powerUsage);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }
    }
}
