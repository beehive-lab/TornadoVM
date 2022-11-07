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
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class OCLContext implements OCLExecutionEnvironment {

    private final long contextID;
    private final List<OCLTargetDevice> devices;
    private final List<OCLDeviceContext> deviceContexts;
    private final OCLCommandQueue[] queues;
    private final List<OCLProgram> programs;
    private final OCLPlatform platform;

    public OCLContext(OCLPlatform platform, long id, List<OCLTargetDevice> devices) {
        this.platform = platform;
        this.contextID = id;
        this.devices = devices;
        this.deviceContexts = new ArrayList<>(devices.size());
        this.queues = new OCLCommandQueue[devices.size()];
        this.programs = new ArrayList<>();
    }

    static native void clReleaseContext(long id) throws OCLException;

    static native void clGetContextInfo(long id, int info, byte[] buffer) throws OCLException;

    static native long clCreateCommandQueue(long contextId, long deviceId, long properties) throws OCLException;

    static native long allocateOffHeapMemory(long size, long alignment);

    static native void freeOffHeapMemory(long address);

    static native ByteBuffer asByteBuffer(long address, long size);

    // creates an empty buffer on the device
    static native OCLBufferResult createBuffer(long contextId, long flags, long size, long hostPointer) throws OCLException;

    static native long createSubBuffer(long buffer, long flags, int createType, byte[] createInfo) throws OCLException;

    static native void clReleaseMemObject(long memId) throws OCLException;

    static native long clCreateProgramWithSource(long contextId, byte[] data, long[] lengths) throws OCLException;

    static native long clCreateProgramWithBinary(long contextId, long deviceId, byte[] data, long[] lengths) throws OCLException;

    static native long clCreateProgramWithIL(long contextId, byte[] spirvBinaryCode, long[] lengths) throws OCLException;

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
            TornadoLogger.info("platform: version=%s (%s) on %s", platformVersion, platform.getVersion(), device.getDeviceName());
            TornadoLogger.info("device  : version=%s (%s) on %s", deviceVersion, device.getVersion(), device.getDeviceName());

            queues[index] = new OCLCommandQueue(queueId, properties, deviceVersion);
        } catch (OCLException e) {
            TornadoLogger.error(e.getMessage());
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
            TornadoLogger.error(e.getMessage());
        }

        return program;
    }

    public OCLProgram createProgramWithIL(byte[] spirvBinary, long[] lengths, OCLDeviceContext deviceContext) {
        OCLProgram program;
        try {
            long programID = clCreateProgramWithIL(contextID, spirvBinary, lengths);
            if (programID == -1) {
                throw new TornadoRuntimeException("OpenCL version <= 2.1. clCreateProgramWithIL is not supported");
            }
            program = new OCLProgram(programID, deviceContext);
            programs.add(program);
        } catch (OCLException e) {
            throw new TornadoRuntimeException(e);
        }

        return program;
    }

    public OCLProgram createProgramWithBinary(long deviceId, byte[] binary, long[] lengths, OCLDeviceContext deviceContext) {
        OCLProgram program = null;

        try {
            program = new OCLProgram(clCreateProgramWithBinary(contextID, deviceId, binary, lengths), deviceContext);
        } catch (OCLException e) {
            TornadoLogger.error(e.getMessage());
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

            for (OCLCommandQueue queue : queues) {
                if (queue != null) {
                    queue.cleanup();
                }
            }

            long t2 = System.nanoTime();
            clReleaseContext(contextID);
            long t3 = System.nanoTime();

            if (Tornado.FULL_DEBUG) {
                System.out.printf("cleanup: %-10s..........%.9f s%n", "programs", (t1 - t0) * 1e-9);
                System.out.printf("cleanup: %-10s..........%.9f s%n", "queues", (t2 - t1) * 1e-9);
                System.out.printf("cleanup: %-10s..........%.9f s%n", "context", (t3 - t2) * 1e-9);
                System.out.printf("cleanup: %-10s..........%.9f s%n", "total", (t3 - t0) * 1e-9);
            }
        } catch (OCLException e) {
            TornadoLogger.error(e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return String.format("id=0x%x, device count=%d", contextID, getNumDevices());
    }

    @Override
    public OCLDeviceContext createDeviceContext(int index) {
        TornadoLogger.debug("creating device context for device: %s", devices.get(index).toString());
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

    public OCLBufferResult createBuffer(long flags, long bytes) {
        return createBuffer(flags, bytes, 0L);
    }

    private OCLBufferResult createBuffer(long flags, long bytes, long hostPointer) {
        try {
            final OCLBufferResult result = createBuffer(contextID, flags, bytes, hostPointer);
            TornadoLogger.info("buffer allocated %s @ 0x%x", RuntimeUtilities.humanReadableByteCount(bytes, false), result.getBuffer());
            return result;
        } catch (OCLException e) {
            TornadoLogger.error(e.getMessage());
        }
        return null;
    }

    public void releaseBuffer(long bufferId) {
        try {
            clReleaseMemObject(bufferId);
            TornadoLogger.info("buffer released 0x%x", bufferId);
        } catch (OCLException e) {
            TornadoLogger.error(e.getMessage());
        }
    }

    public int getPlatformIndex() {
        return platform.getIndex();
    }

    public OCLPlatform getPlatform() {
        return platform;
    }

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
}
