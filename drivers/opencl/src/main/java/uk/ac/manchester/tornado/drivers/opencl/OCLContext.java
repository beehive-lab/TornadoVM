/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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

import static uk.ac.manchester.tornado.drivers.opencl.OpenCL.DUMP_OPENCL_EVENTS;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLCommandQueueProperties.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLCommandQueueProperties.CL_QUEUE_PROFILING_ENABLE;
import static uk.ac.manchester.tornado.runtime.common.Tornado.DEBUG;
import static uk.ac.manchester.tornado.runtime.common.Tornado.ENABLE_OOO_EXECUTION;
import static uk.ac.manchester.tornado.runtime.common.Tornado.ENABLE_PROFILING;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class OCLContext extends TornadoLogger {

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

    private final long id;
    private final List<OCLDevice> devices;
    private final List<OCLDeviceContext> deviceContexts;
    private final OCLCommandQueue[] queues;
    private final List<OCLProgram> programs;
    private final long[] allocatedRegions;
    private int allocatedRegionCount;
    private final OCLPlatform platform;

    private static final int MAX_ALLOCATED_REGIONS = 64;
    private static final int BUFFER_CAPACITY = 128;

    public OCLContext(OCLPlatform platform, long id, List<OCLDevice> devices) {
        this.platform = platform;
        this.id = id;
        this.devices = devices;
        this.deviceContexts = new ArrayList<>(devices.size());
        this.queues = new OCLCommandQueue[devices.size()];
        this.programs = new ArrayList<>();
        this.allocatedRegions = new long[MAX_ALLOCATED_REGIONS];
        this.allocatedRegionCount = 0;
        Arrays.fill(this.allocatedRegions, -1);
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

    native static long createArrayOnDevice(long contextId, long flags, byte[] buffer) throws OCLException;

    native static long createArrayOnDevice(long contextId, long flags, int[] buffer) throws OCLException;

    native static long createArrayOnDevice(long contextId, long flags, float[] buffer) throws OCLException;

    native static long createArrayOnDevice(long contextId, long flags, double[] buffer) throws OCLException;

    native static long clCreateProgramWithSource(long contextId, byte[] data, long lengths[]) throws OCLException;

    native static long clCreateProgramWithBinary(long contextId, long deviceId, byte[] data, long lengths[]) throws OCLException;

    public int getNumDevices() {
        return devices.size();
    }

    public List<OCLDevice> devices() {
        return devices;
    }

    public OCLCommandQueue[] queues() {
        return queues;
    }

    public void createCommandQueue(int index, long properties) {
        OCLDevice device = devices.get(index);
        long queueId;
        try {
            queueId = clCreateCommandQueue(id, device.getId(), properties);

            final int platformVersion = Integer.parseInt(platform.getVersion().split(" ")[1].replace(".", "")) * 10;
            final int deviceVersion = Integer.parseInt(device.getVersion().split(" ")[1].replace(".", "")) * 10;
            info("platform: version=%s (%s) on %s", platformVersion, platform.getVersion(), device.getName());
            info("device  : version=%s (%s) on %s", deviceVersion, device.getVersion(), device.getName());

            queues[index] = new OCLCommandQueue(queueId, properties, deviceVersion);
        } catch (OCLException e) {
            error(e.getMessage());
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
            program = new OCLProgram(clCreateProgramWithSource(id, source, lengths), deviceContext);
            programs.add(program);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return program;
    }

    public OCLProgram createProgramWithBinary(long deviceId, byte[] binary, long[] lengths, OCLDeviceContext deviceContext) {
        OCLProgram program = null;

        try {
            program = new OCLProgram(clCreateProgramWithBinary(id, deviceId, binary, lengths), deviceContext);
            programs.add(program);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return program;
    }

    public void cleanup() {

        if (DUMP_OPENCL_EVENTS) {
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

            for (int i = 0; i < allocatedRegionCount; i++) {
                clReleaseMemObject(allocatedRegions[i]);
            }
            long t2 = System.nanoTime();

            for (OCLCommandQueue queue : queues) {
                if (queue != null) {
                    queue.cleanup();
                }
            }

            long t3 = System.nanoTime();
            clReleaseContext(id);

            long t4 = System.nanoTime();

            if (DEBUG) {
                System.out.printf("cleanup: %-10s..........%.9f s\n", "programs", (t1 - t0) * 1e-9);
                System.out.printf("cleanup: %-10s..........%.9f s\n", "memory", (t2 - t1) * 1e-9);
                System.out.printf("cleanup: %-10s..........%.9f s\n", "queues", (t3 - t2) * 1e-9);
                System.out.printf("cleanup: %-10s..........%.9f s\n", "context", (t4 - t3) * 1e-9);
                System.out.printf("cleanup: %-10s..........%.9f s\n", "total", (t4 - t0) * 1e-9);
            }
        } catch (OCLException e) {
            error(e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return String.format("id=0x%x, device count=%d", id, getNumDevices());
    }

    public OCLDeviceContext createDeviceContext(int index) {
        debug("creating device context for device: %s", devices.get(index).toString());
        createCommandQueue(index);
        final OCLDeviceContext deviceContext = new OCLDeviceContext(devices.get(index), queues[index], this);
        deviceContexts.add(deviceContext);
        return deviceContext;
    }

    /**
     * Allocates off-heap memory
     *
     * @param bytes
     *
     * @return
     */
    public long allocate(long bytes, long alignment) {
        System.out.println("Allocate off heal memory: alignment ------->" + alignment + "\n");
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

    public long createBuffer(long flags, long bytes, long hostPointer) {
        long devicePtr = 0;
        try {
            final OCLBufferResult result = createBuffer(id, flags, bytes, hostPointer);
            devicePtr = result.getBuffer();
            allocatedRegions[allocatedRegionCount] = devicePtr;
            allocatedRegionCount++;
            info("buffer allocated %s @ 0x%x", RuntimeUtilities.humanReadableByteCount(bytes, false), devicePtr);
        } catch (OCLException e) {
            error(e.getMessage());
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
