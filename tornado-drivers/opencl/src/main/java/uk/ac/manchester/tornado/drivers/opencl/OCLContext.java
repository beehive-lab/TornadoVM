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
package uk.ac.manchester.tornado.drivers.opencl;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.exceptions.TornadoNoOpenCLPlatformException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLCommandQueueProperties;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.drivers.opencl.ffm.OpenCLAPI;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class OCLContext implements OCLContextInterface {

    private final long contextID;
    private final List<OCLTargetDevice> devices;
    private final List<OCLDeviceContext> deviceContexts;

    private final OCLPlatform platform;

    private final TornadoLogger logger;

    public OCLContext(OCLPlatform platform, long contextPointer, List<OCLTargetDevice> devices) {
        this.platform = platform;
        this.contextID = contextPointer;
        this.devices = devices;
        this.deviceContexts = new ArrayList<>(devices.size());
        this.logger = new TornadoLogger(this.getClass());
    }

    /**
     * The arena backing each off-heap host allocation, keyed by its address, so that
     * {@link #freeOffHeapMemory(long)} can release exactly the region it was handed.
     */
    private static final Map<Long, Arena> OFF_HEAP_ARENAS = new ConcurrentHashMap<>();

    /** Reusable per-thread native buffer the driver answers info queries into. */
    private static final FFMSupport.Staging INFO_STAGING = new FFMSupport.Staging();

    void clReleaseContext(long id) throws OCLException {
        OpenCLAPI.clReleaseContext(id);
    }

    void clGetContextInfo(long id, int info, byte[] buffer) throws OCLException {
        Arrays.fill(buffer, (byte) 0);
        MemorySegment value = INFO_STAGING.forBytes(buffer.length);
        if (OpenCLAPI.clGetContextInfo(id, info, buffer.length, value, MemorySegment.NULL) != OpenCLAPI.CL_SUCCESS) {
            return;
        }
        MemorySegment.copy(value, FFMSupport.C_CHAR, 0, buffer, 0, buffer.length);
    }

    public long clCreateCommandQueue(long contextId, long deviceId, long properties) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment status = FFMSupport.allocateInt(arena);
            long queue = OpenCLAPI.clCreateCommandQueue(contextId, deviceId, properties, status);
            int result = status.get(FFMSupport.C_INT, 0);
            if (result != OpenCLAPI.CL_SUCCESS) {
                throw new OCLException("clCreateCommandQueue failed: CL error " + result);
            }
            return queue;
        }
    }

    /**
     * Allocates zeroed, aligned off-heap host memory. The arena is remembered so the region can be
     * released again; it is shared rather than confined because the address outlives this call.
     *
     * <p>
     * The JNI version also wrote an ascending index pattern over the region after zeroing it.
     * Nothing read it back, and the CUDA clone of this code dropped it, so the region is simply
     * left zeroed here.
     */
    long allocateOffHeapMemory(long size, long alignment) {
        Arena arena = Arena.ofShared();
        try {
            MemorySegment segment = arena.allocate(size, alignment);
            long address = segment.address();
            OFF_HEAP_ARENAS.put(address, arena);
            return address;
        } catch (RuntimeException e) {
            arena.close();
            System.out.printf("OpenCL off-heap memory allocation of %d bytes failed: %s%n", size, e.getMessage());
            return 0;
        }
    }

    void freeOffHeapMemory(long address) {
        Arena arena = OFF_HEAP_ARENAS.remove(address);
        if (arena != null) {
            arena.close();
        }
    }

    ByteBuffer asByteBuffer(long address, long size) {
        return FFMSupport.asSegment(address, size).asByteBuffer();
    }

    // creates an empty buffer on the device
    OCLBufferResult createBuffer(long contextId, long flags, long size, long hostPointer) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment status = FFMSupport.allocateInt(arena);
            long buffer = OpenCLAPI.clCreateBuffer(contextId, flags, size, hostPointer, status);
            // The status is handed back rather than thrown: the caller distinguishes out-of-memory
            // from other failures itself.
            return new OCLBufferResult(buffer, hostPointer, status.get(FFMSupport.C_INT, 0));
        }
    }

    long createSubBuffer(long buffer, long flags, int createType, byte[] createInfo) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(Math.max(createInfo.length, 1), 8);
            MemorySegment.copy(createInfo, 0, info, FFMSupport.C_CHAR, 0, createInfo.length);
            MemorySegment status = FFMSupport.allocateInt(arena);
            return OpenCLAPI.clCreateSubBuffer(buffer, flags, createType, info, status);
        }
    }

    void clReleaseMemObject(long memId) throws OCLException {
        OpenCLAPI.clReleaseMemObject(memId);
    }

    long clCreateProgramWithSource(long contextId, byte[] data, long[] lengths) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            // One source string, whose length is given explicitly, so the bytes need no terminator.
            MemorySegment source = arena.allocate(Math.max(data.length, 1), 1);
            MemorySegment.copy(data, 0, source, FFMSupport.C_CHAR, 0, data.length);
            MemorySegment strings = FFMSupport.allocateArray(arena, FFMSupport.C_POINTER, 1);
            strings.set(FFMSupport.C_POINTER, 0, source);
            MemorySegment sizes = FFMSupport.allocateLongArray(arena, lengths);
            MemorySegment status = FFMSupport.allocateInt(arena);
            return OpenCLAPI.clCreateProgramWithSource(contextId, lengths.length, strings, sizes, status);
        }
    }

    long clCreateProgramWithBinary(long contextId, long deviceId, byte[] data, long[] lengths) throws OCLException {
        if (lengths.length != 1) {
            System.out.println("[TornadoVM-OpenCL] loading multiple binaries is not supported");
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment binary = arena.allocate(Math.max(data.length, 1), 1);
            MemorySegment.copy(data, 0, binary, FFMSupport.C_CHAR, 0, data.length);
            MemorySegment binaries = FFMSupport.allocateArray(arena, FFMSupport.C_POINTER, 1);
            binaries.set(FFMSupport.C_POINTER, 0, binary);
            MemorySegment devices = FFMSupport.allocateArray(arena, FFMSupport.C_POINTER, 1);
            devices.set(FFMSupport.C_POINTER, 0, MemorySegment.ofAddress(deviceId));
            MemorySegment sizes = FFMSupport.allocateLongArray(arena, lengths);
            MemorySegment binaryStatus = FFMSupport.allocateInt(arena);
            MemorySegment status = FFMSupport.allocateInt(arena);
            return OpenCLAPI.clCreateProgramWithBinary(contextId, 1, devices, sizes, binaries, binaryStatus, status);
        }
    }

    /**
     * SPIR-V ingestion needs OpenCL 2.1. {@code -1} makes the Java side treat the platform as unable
     * to take IL, matching the OpenCL &lt; 2.1 behaviour.
     */
    long clCreateProgramWithIL(long contextId, byte[] spirvBinaryCode, long[] lengths) throws OCLException {
        if (!OpenCLAPI.hasCreateProgramWithIL()) {
            return -1;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment il = arena.allocate(Math.max(spirvBinaryCode.length, 1), 8);
            MemorySegment.copy(spirvBinaryCode, 0, il, FFMSupport.C_CHAR, 0, spirvBinaryCode.length);
            MemorySegment status = FFMSupport.allocateInt(arena);
            return OpenCLAPI.clCreateProgramWithIL(contextId, il, lengths[0], status);
        }
    }

    public int getNumDevices() {
        return devices.size();
    }

    public List<OCLTargetDevice> devices() {
        return devices;
    }

    @Override
    public long getContextId() {
        return contextID;
    }

    private void createCommandQueue(int index, long properties) {
        OCLTargetDevice device = devices.get(index);
        try {

            final int platformVersion = Integer.parseInt(platform.getVersion().split(" ")[1].replace(".", "")) * 10;
            final int deviceVersion = Integer.parseInt(device.getVersion().split(" ")[1].replace(".", "")) * 10;

            logger.info("platform: version=%s (%s) on %s", platformVersion, platform.getVersion(), device.getDeviceName());
            logger.info("device  : version=%s (%s) on %s", deviceVersion, device.getVersion(), device.getDeviceName());

            clCreateCommandQueue(contextID, device.getDevicePointer(), properties);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoRuntimeException("[ERROR] OpenCL Command Queue Initialization not valid");
        }
    }

    public long getProperties() {
        long properties = 0;
        if (TornadoOptions.ENABLE_OPENCL_PROFILING) {
            properties |= OCLCommandQueueProperties.CL_QUEUE_PROFILING_ENABLE;
        }

        if (TornadoOptions.ENABLE_OOO_EXECUTION) {
            properties |= OCLCommandQueueProperties.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
        }
        return properties;
    }

    @Override
    public void createCommandQueue(int index) {
        long properties = getProperties();
        createCommandQueue(index, properties);
    }

    public OCLProgram createProgramWithSource(byte[] source, long[] lengths, OCLDeviceContext deviceContext) {
        OCLProgram program = null;

        try {
            program = new OCLProgram(clCreateProgramWithSource(contextID, source, lengths), deviceContext);
        } catch (OCLException e) {
            logger.error(e.getMessage());
        }

        return program;
    }

    public OCLProgram createProgramWithIL(byte[] spirvBinary, long[] lengths, OCLDeviceContext deviceContext) {
        OCLProgram program;
        try {
            long programID = clCreateProgramWithIL(contextID, spirvBinary, lengths);
            if (programID == -1) {
                throw new TornadoNoOpenCLPlatformException("OpenCL version <= 2.1. clCreateProgramWithIL is not supported");
            }
            program = new OCLProgram(programID, deviceContext);
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
            logger.error(e.getMessage());
        }

        return program;
    }

    public void cleanup() {

        if (TornadoOptions.DUMP_EVENTS) {
            for (OCLDeviceContext deviceContext : deviceContexts) {
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
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoRuntimeException(e.getMessage());
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
        final OCLDeviceContext deviceContext = new OCLDeviceContext(devices.get(index), this);
        deviceContexts.add(deviceContext);
        return deviceContext;
    }

    public OCLBufferResult createBuffer(long flags, long bytes) {
        return createBuffer(flags, bytes, 0L);
    }

    private OCLBufferResult createBuffer(long flags, long bytes, long hostPointer) {
        try {
            final OCLBufferResult result = createBuffer(contextID, flags, bytes, hostPointer);
            logger.info("buffer allocated %s @ 0x%x", RuntimeUtilities.humanReadableByteCount(bytes, false), result.getBuffer());
            return result;
        } catch (OCLException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    public void releaseBuffer(long bufferId) {
        try {
            clReleaseMemObject(bufferId);
            logger.info("buffer released 0x%x", bufferId);
        } catch (OCLException e) {
            logger.error(e.getMessage());
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

        /**
         * Objects of this type are created in Native Code from the JNI-OpenCL layer of TornadoVM.
         */
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
