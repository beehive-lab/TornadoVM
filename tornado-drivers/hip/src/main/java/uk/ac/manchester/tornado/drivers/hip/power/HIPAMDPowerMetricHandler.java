/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.drivers.hip.power;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.drivers.common.power.PowerMetric;
import uk.ac.manchester.tornado.drivers.hip.HIPDeviceContext;
import uk.ac.manchester.tornado.drivers.hip.ffm.AMDSMIAPI;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPDriverAPI;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * Power readings for the profiler from AMD SMI, the counterpart of the CUDA backend's NVML handler.
 * The GPU is found by its PCI address, because a HIP device index counts only the GPUs
 * {@code HIP_VISIBLE_DEVICES} leaves visible while AMD SMI sees all of them.
 */
public class HIPAMDPowerMetricHandler implements PowerMetric {

    private final HIPDeviceContext deviceContext;
    private final TornadoLogger logger;
    private long processor;
    private volatile boolean initialised;

    public HIPAMDPowerMetricHandler(HIPDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        this.logger = new TornadoLogger(this.getClass());
        // AMD SMI is loaded on the first power reading: only the profiler asks for one.
    }

    private synchronized void initialiseOnce() {
        if (!initialised) {
            initializePowerLibrary();
            initialised = true;
        }
    }

    @Override
    public void initializePowerLibrary() {
        if (!AMDSMIAPI.isAvailable()) {
            return;
        }
        int status = AMDSMIAPI.amdsmiInit(AMDSMIAPI.AMDSMI_INIT_AMD_GPUS);
        if (status != AMDSMIAPI.AMDSMI_STATUS_SUCCESS) {
            logger.error("amdsmi_init failed with status " + status);
            return;
        }
        int device = deviceContext.getDevice().getIndex();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = FFMSupport.allocateInt(arena);
            int[] address = new int[3];
            int[] attributes = { HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_PCI_DOMAIN_ID, HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_PCI_BUS_ID, HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_PCI_DEVICE_ID };
            for (int i = 0; i < attributes.length; i++) {
                if (HIPDriverAPI.hipDeviceGetAttribute(value, attributes[i], device) != 0) {
                    return;
                }
                address[i] = value.get(FFMSupport.C_INT, 0);
            }
            MemorySegment handle = FFMSupport.allocatePointer(arena);
            status = AMDSMIAPI.amdsmiGetProcessorHandleFromBdf(AMDSMIAPI.bdf(address[0], address[1], address[2], 0), handle);
            if (status != AMDSMIAPI.AMDSMI_STATUS_SUCCESS) {
                logger.error("amdsmi_get_processor_handle_from_bdf failed with status " + status);
                return;
            }
            processor = handle.get(FFMSupport.C_POINTER, 0).address();
        }
    }

    /**
     * Reports the GPU's draw in milliwatts, the unit the profiler prints. AMD SMI reports watts: the
     * current socket power where the GPU has it (MI300 and later), else the average, else the
     * socket power.
     */
    @Override
    public void getPowerUsage(long[] powerUsage) {
        if (!initialised) {
            initialiseOnce();
        }
        if (processor == 0 || powerUsage == null || powerUsage.length == 0) {
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(AMDSMIAPI.POWER_INFO_BYTES, 8);
            int status = AMDSMIAPI.amdsmiGetPowerInfo(processor, info);
            if (status != AMDSMIAPI.AMDSMI_STATUS_SUCCESS) {
                logger.error("amdsmi_get_power_info failed with status " + status);
                return;
            }
            int current = info.get(FFMSupport.C_INT, AMDSMIAPI.POWER_INFO_CURRENT_SOCKET_POWER);
            int average = info.get(FFMSupport.C_INT, AMDSMIAPI.POWER_INFO_AVERAGE_SOCKET_POWER);
            long watts = current != AMDSMIAPI.UNSUPPORTED ? Integer.toUnsignedLong(current)
                    : average != AMDSMIAPI.UNSUPPORTED ? Integer.toUnsignedLong(average) : info.get(FFMSupport.C_LONG, AMDSMIAPI.POWER_INFO_SOCKET_POWER);
            powerUsage[0] = watts * 1000;
        }
    }
}
