/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl.power;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.drivers.common.ffm.NVMLAPI;
import uk.ac.manchester.tornado.drivers.common.power.PowerMetric;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class OCLNvidiaPowerMetricHandler implements PowerMetric {

    private final OCLDeviceContext deviceContext;
    private final TornadoLogger logger;
    private long[] oclDevice = new long[1];
    private volatile boolean initialised;

    public OCLNvidiaPowerMetricHandler(OCLDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        this.logger = new TornadoLogger(this.getClass());
        // NVML is loaded on the first power reading: only the profiler asks for one, and loading and
        // initialising it costs about 100 ms of every application's start-up.
    }

    static long clNvmlInit() throws OCLException {
        if (!NVMLAPI.isAvailable()) {
            return -1;
        }
        return NVMLAPI.nvmlInit();
    }

    static long clNvmlDeviceGetHandleByIndex(long index, long[] device) throws OCLException {
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
    static long clNvmlDeviceGetPowerUsage(long[] device, long[] powerUsage) throws OCLException {
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

    private synchronized void initialiseOnce() {
        if (!initialised) {
            initializePowerLibrary();
            initialised = true;
        }
    }

    @Override
    public void initializePowerLibrary() {
        try {
            clNvmlInit();
            clNvmlDeviceGetHandleByIndex(this.deviceContext.getDevice().getIndex(), this.oclDevice);
        } catch (OCLException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void getPowerUsage(long[] powerUsage) {
        if (!initialised) {
            initialiseOnce();
        }
        try {
            clNvmlDeviceGetPowerUsage(this.oclDevice, powerUsage);
        } catch (OCLException e) {
            logger.error(e.getMessage());
        }
    }
}
