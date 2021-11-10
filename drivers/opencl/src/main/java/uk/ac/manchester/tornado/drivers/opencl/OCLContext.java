/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLCommandQueueProperties.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLCommandQueueProperties.CL_QUEUE_PROFILING_ENABLE;
import static uk.ac.manchester.tornado.runtime.common.Tornado.ENABLE_OOO_EXECUTION;
import static uk.ac.manchester.tornado.runtime.common.Tornado.ENABLE_PROFILING;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DUMP_EVENTS;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class OCLContext implements OCLExecutionEnvironment {

    private TornadoLogger logger = new TornadoLogger();

    public static class OCLBufferResult {

        private final long oclBuffer;
        private final long address;
        private final int result;

        public OCLBufferResult(long oclBuffer, long address, int result) {
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

    private final long contextID;
    private final List<OCLTargetDevice> devices;
    private final List<OCLDeviceContext> deviceContexts;
    private final OCLCommandQueue[] queues;
    private final List<OCLProgram> programs;
    private final ArrayList<Long> allocatedRegions;
    private final OCLPlatform platform;

    public OCLContext(OCLPlatform platform, long id, List<OCLTargetDevice> devices) {
        this.platform = platform;
        this.contextID = id;
        this.devices = devices;
        this.deviceContexts = new ArrayList<>(devices.size());
        this.queues = new OCLCommandQueue[devices.size()];
        this.programs = new ArrayList<>();
        this.allocatedRegions = new ArrayList<>();
    }

    native static void clReleaseContext(long id) throws OCLException;

    native static void clGetContextInfo(long id, int info, byte[] buffer) throws OCLException;

    native static long clCreateCommandQueue(long contextId, long deviceId, long properties) throws OCLException;

    native static long allocateOffHeapMemory(long size, long alignment);

    native static void freeOffHeapMemory(long address);

    native static ByteBuffer asByteBuffer(long address, long size);

    // creates an empty buffer on the device
    native static OCLBufferResult createBuffer(long contextId, long flags, long size, long hostPointer) throws OCLException;

    native static long createSubBuffer(long buffer, long flags, int createType, byte[] createInfo) throws OCLException;

    native static void clReleaseMemObject(long memId) throws OCLException;

    native static long clCreateProgramWithSource(long contextId, byte[] data, long lengths[]) throws OCLException;

    native static long clCreateProgramWithBinary(long contextId, long deviceId, byte[] data, long lengths[]) throws OCLException;

    public int getNumDevices() {
        return devices.size();
    }

    public List<OCLTargetDevice> devices() {
        return devices;
    }

    public OCLCommandQueue[] queues() {
        return queues;
    }

    public void createCommandQueue(int index, long properties) {
        OCLTargetDevice device = devices.get(index);
        long queueId;
        try {
            queueId = clCreateCommandQueue(contextID, device.getId(), properties);

            final int platformVersion = Integer.parseInt(platform.getVersion().split(" ")[1].replace(".", "")) * 10;
            final int deviceVersion = Integer.parseInt(device.getVersion().split(" ")[1].replace(".", "")) * 10;
            logger.info("platform: version=%s (%s) on %s", platformVersion, platform.getVersion(), device.getDeviceName());
            logger.info("device  : version=%s (%s) on %s", deviceVersion, device.getVersion(), device.getDeviceName());

            queues[index] = new OCLCommandQueue(queueId, properties, deviceVersion);
        } catch (OCLException e) {
            logger.error(e.getMessage());
        }
    }

    public void createCommandQueue(int index) {
        long properties = 0;
        if (ENABLE_PROFILING) {
            properties |= CL_QUEUE_PROFILING_ENABLE;
        }

        if (ENABLE_OOO_EXECUTION) {
            properties |= CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
        }
        createCommandQueue(index, properties);
    }

    public void createAllCommandQueues(long properties) {
        for (int i = 0; i < devices.size(); i++) {
            createCommandQueue(i, properties);
        }
    }

    public void createAllCommandQueues() {
        long properties = 0;
        properties |= CL_QUEUE_PROFILING_ENABLE;
        if (ENABLE_OOO_EXECUTION) {
            properties |= CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
        }
        createAllCommandQueues(properties);
    }

    public OCLProgram createProgramWithSource(byte[] source, long[] lengths, OCLDeviceContext deviceContext) {
        OCLProgram program = null;

        try {
            program = new OCLProgram(clCreateProgramWithSource(contextID, source, lengths), deviceContext);
            programs.add(program);
        } catch (OCLException e) {
            logger.error(e.getMessage());
        }

        return program;
    }

    public OCLProgram createProgramWithBinary(long deviceId, byte[] binary, long[] lengths, OCLDeviceContext deviceContext) {
        OCLProgram program = null;

        try {
            program = new OCLProgram(clCreateProgramWithBinary(contextID, deviceId, binary, lengths), deviceContext);
        } catch (OCLException e) {
            logger.error(e.getMessage());
        }

        return program;
    }

    public void cleanup() {

        if (DUMP_EVENTS) {
            for (OCLDeviceContext deviceContext : deviceContexts) {
                deviceContext.dumpEvents();
            }
        }

        try {
            long t0 = System.nanoTime();
            for (OCLProgram program : programs) {
                program.cleanup();
            }
            long t1 = System.nanoTime();

            for (Long allocatedRegion : allocatedRegions) {
                clReleaseMemObject(allocatedRegion);
            }
            long t2 = System.nanoTime();

            for (OCLCommandQueue queue : queues) {
                if (queue != null) {
                    queue.cleanup();
                }
            }

            long t3 = System.nanoTime();
            clReleaseContext(contextID);
            long t4 = System.nanoTime();

            if (Tornado.FULL_DEBUG) {
                System.out.printf("cleanup: %-10s..........%.9f s\n", "programs", (t1 - t0) * 1e-9);
                System.out.printf("cleanup: %-10s..........%.9f s\n", "memory", (t2 - t1) * 1e-9);
                System.out.printf("cleanup: %-10s..........%.9f s\n", "queues", (t3 - t2) * 1e-9);
                System.out.printf("cleanup: %-10s..........%.9f s\n", "context", (t4 - t3) * 1e-9);
                System.out.printf("cleanup: %-10s..........%.9f s\n", "total", (t4 - t0) * 1e-9);
            }
        } catch (OCLException e) {
            logger.error(e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return String.format("id=0x%x, device count=%d", contextID, getNumDevices());
    }

    @Override
    public OCLDeviceContext createDeviceContext(int index) {
        logger.debug("creating device context for device: %s", devices.get(index).toString());
        createCommandQueue(index);
        final OCLDeviceContext deviceContext = new OCLDeviceContext(devices.get(index), queues[index], this);
        deviceContexts.add(deviceContext);
        return deviceContext;
    }

    /**
     * Allocates off-heap memory.
     *
     * @param bytes
     *            to be allocated.
     * @param alignment
     *            alignment
     *
     * @return base address
     */
    public long allocate(long bytes, long alignment) {
        final long address = allocateOffHeapMemory(bytes, alignment);
        if (address == 0) {
            throw new TornadoInternalError("Unable to allocate off-heap memory");
        }
        return address;
    }

    public ByteBuffer toByteBuffer(long address, long bytes) {
        final ByteBuffer buffer = asByteBuffer(address, bytes);
        buffer.order(OpenCL.BYTE_ORDER);
        return buffer;
    }

    public long createBuffer(long flags, long bytes) {
        return createBuffer(flags, bytes, 0L);
    }

    private long createBuffer(long flags, long bytes, long hostPointer) {
        long devicePtr = 0;
        try {
            final OCLBufferResult result = createBuffer(contextID, flags, bytes, hostPointer);
            devicePtr = result.getBuffer();
            allocatedRegions.add(devicePtr);
            logger.info("buffer allocated %s @ 0x%x", RuntimeUtilities.humanReadableByteCount(bytes, false), devicePtr);
        } catch (OCLException e) {
            logger.error(e.getMessage());
        }
        return devicePtr;
    }

    public int getPlatformIndex() {
        return platform.getIndex();
    }

    public OCLPlatform getPlatform() {
        return platform;
    }
}
