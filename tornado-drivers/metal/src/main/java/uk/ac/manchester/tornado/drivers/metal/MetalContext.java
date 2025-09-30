/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, 2023, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoNoMetalPlatformException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalCommandQueueProperties;
import uk.ac.manchester.tornado.drivers.metal.exceptions.MetalException;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class MetalContext implements MetalContextInterface {

    private final long contextID;
    private final List<MetalTargetDevice> devices;
    private final List<MetalDeviceContext> deviceContexts;

    private final MetalPlatform platform;

    private final TornadoLogger logger;

    public MetalContext(MetalPlatform platform, long contextPointer, List<MetalTargetDevice> devices) {
        this.platform = platform;
        this.contextID = contextPointer;
        this.devices = devices;
        this.deviceContexts = new ArrayList<>(devices.size());
        this.logger = new TornadoLogger(this.getClass());
    }

    native void clReleaseContext(long id) throws MetalException;

    native void clGetContextInfo(long id, int info, byte[] buffer) throws MetalException;

    public native long clCreateCommandQueue(long deviceId, int maxInFlight) throws MetalException;
    public native long clReleaseCommandQueue(long queue_id) throws MetalException;

    native long allocateOffHeapMemory(long size, long alignment);

    native void freeOffHeapMemory(long address);

    native ByteBuffer asByteBuffer(long address, long size);

    // creates an empty buffer on the device
    public native MetalBufferResult createBuffer(long contextId, long flags, long size, long hostPointer) throws MetalException;

    native long createSubBuffer(long buffer, long flags, int createType, byte[] createInfo) throws MetalException;

    native void clReleaseMemObject(long memId) throws MetalException;

    native long clCreateProgramWithSource(long contextId, byte[] data, long[] lengths) throws MetalException;

    native long clCreateProgramWithBinary(long contextId, long deviceId, byte[] data, long[] lengths) throws MetalException;

    native long clCreateProgramWithIL(long contextId, byte[] spirvBinaryCode, long[] lengths) throws MetalException;

    public int getNumDevices() {
        return devices.size();
    }

    public List<MetalTargetDevice> devices() {
        return devices;
    }

    @Override
    public long getContextId() {
        return contextID;
    }

    private void createCommandQueue(int index, long properties) {
        MetalTargetDevice device = devices.get(index);
        try {

            final int platformVersion = Integer.parseInt(platform.getVersion().split(" ")[1].replace(".", "")) * 10;
            System.out.println("Device Version: " + device.getVersion());
            final int deviceVersion = Integer.parseInt(device.getVersion().split(" ")[1].replace(".", "")) * 10;

            logger.info("platform: version=%s (%s) on %s", platformVersion, platform.getVersion(), device.getDeviceName());
            logger.info("device  : version=%s (%s) on %s", deviceVersion, device.getVersion(), device.getDeviceName());

            // TODO: set max in flight here if needed
            clCreateCommandQueue(device.getDevicePointer(), 0 /* maxInFlight */);
        } catch (MetalException e) {
            logger.error(e.getMessage());
            throw new TornadoRuntimeException("[ERROR] Metal Command Queue Initialization not valid");
        }
    }

    public long getProperties() {
        long properties = 0;
        if (TornadoOptions.ENABLE_METAL_PROFILING) {
            properties |= MetalCommandQueueProperties.CL_QUEUE_PROFILING_ENABLE;
        }

        if (TornadoOptions.ENABLE_OOO_EXECUTION) {
            properties |= MetalCommandQueueProperties.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
        }
        return properties;
    }

    @Override
    public void createCommandQueue(int index) {
        long properties = getProperties();
        createCommandQueue(index, properties);
    }

    public MetalProgram createProgramWithSource(byte[] source, long[] lengths, MetalDeviceContext deviceContext) {
        MetalProgram program = null;

        try {
            program = new MetalProgram(clCreateProgramWithSource(contextID, source, lengths), deviceContext);
        } catch (MetalException e) {
            logger.error(e.getMessage());
        }

        return program;
    }

    public MetalProgram createProgramWithIL(byte[] spirvBinary, long[] lengths, MetalDeviceContext deviceContext) {
        MetalProgram program;
        try {
            long programID = clCreateProgramWithIL(contextID, spirvBinary, lengths);
            if (programID == -1) {
                throw new TornadoNoMetalPlatformException("Metal version <= 2.1. clCreateProgramWithIL is not supported");
            }
            program = new MetalProgram(programID, deviceContext);
        } catch (MetalException e) {
            throw new TornadoRuntimeException(e);
        }

        return program;
    }

    public MetalProgram createProgramWithBinary(long deviceId, byte[] binary, long[] lengths, MetalDeviceContext deviceContext) {
        MetalProgram program = null;

        try {
            program = new MetalProgram(clCreateProgramWithBinary(contextID, deviceId, binary, lengths), deviceContext);
        } catch (MetalException e) {
            logger.error(e.getMessage());
        }

        return program;
    }

    public void cleanup() {

        if (TornadoOptions.DUMP_EVENTS) {
            for (MetalDeviceContext deviceContext : deviceContexts) {
                deviceContext.dumpEvents();
            }
        }

        try {
            long t1 = System.nanoTime();
            clReleaseContext(contextID);
            long t2 = System.nanoTime();

            if (TornadoOptions.FULL_DEBUG) {
                System.out.printf("cleanup: %-10s..........%.9f s%n", "context", (t2 - t1) * 1e-9);
            }
        } catch (MetalException e) {
            logger.error(e.getMessage());
            throw new TornadoRuntimeException(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return String.format("id=0x%x, device count=%d", contextID, getNumDevices());
    }

    @Override
    public MetalDeviceContext createDeviceContext(int index) {
        logger.debug("creating device context for device: %s", devices.get(index).toString());
        createCommandQueue(index);
        final MetalDeviceContext deviceContext = new MetalDeviceContext(devices.get(index), this);
        deviceContexts.add(deviceContext);
        return deviceContext;
    }

    public MetalBufferResult createBuffer(long flags, long bytes) {
        return createBuffer(flags, bytes, 0L);
    }

    private MetalBufferResult createBuffer(long flags, long bytes, long hostPointer) {
        try {
            final MetalBufferResult result = createBuffer(contextID, flags, bytes, hostPointer);
            logger.info("buffer allocated %s @ 0x%x", RuntimeUtilities.humanReadableByteCount(bytes, false), result.getBuffer());
            return result;
        } catch (MetalException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    public void releaseBuffer(long bufferId) {
        try {
            clReleaseMemObject(bufferId);
            logger.info("buffer released 0x%x", bufferId);
        } catch (MetalException e) {
            logger.error(e.getMessage());
        }
    }

    public int getPlatformIndex() {
        return platform.getIndex();
    }

    public MetalPlatform getPlatform() {
        return platform;
    }

    public static class MetalBufferResult {

        private final long oclBuffer;
        private final long address;
        private final int result;

        /**
         * Objects of this type are created in Native Code from the JNI-Metal layer of TornadoVM.
         */
        public MetalBufferResult(long oclBuffer, long address, int result) {
            this.oclBuffer = oclBuffer;
            this.address = address;
            this.result = result;
        }

        public long getBuffer() {
            return oclBuffer;
        }

        public long getAddress() {
            return address;
        }

        public int getResult() {
            return result;
        }
    }
}
