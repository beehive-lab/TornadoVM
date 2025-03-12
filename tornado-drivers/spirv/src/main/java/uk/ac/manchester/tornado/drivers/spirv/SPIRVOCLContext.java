/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv;

import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.EVENT_WINDOW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.drivers.common.CommandQueue;
import uk.ac.manchester.tornado.drivers.common.utils.EventDescriptor;
import uk.ac.manchester.tornado.drivers.opencl.OCLCommandQueue;
import uk.ac.manchester.tornado.drivers.opencl.OCLContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLEventPool;
import uk.ac.manchester.tornado.drivers.opencl.OpenCLBlocking;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLMemFlags;

public class SPIRVOCLContext extends SPIRVContext {

    private OCLContextInterface oclContext;
    private List<SPIRVOCLDeviceContext> spirvoclDeviceContext;

    private final Map<Long, SPIRVOCLCommandQueueTable> commmandQueueTable;
    private final Map<Long, OCLEventPool> oclEventPool;
    private Set<Long> executionIDs;

    public SPIRVOCLContext(SPIRVPlatform platform, List<SPIRVDevice> devices, OCLContextInterface context) {
        super(platform, devices);
        this.oclContext = context;

        commmandQueueTable = new ConcurrentHashMap<>();
        oclEventPool = new ConcurrentHashMap<>();
        executionIDs = Collections.synchronizedSet(new HashSet<>());

        // Create a command queue per device;
        for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
            context.createCommandQueue(deviceIndex);
        }

        spirvoclDeviceContext = new ArrayList<>();
        for (SPIRVDevice device : devices) {
            SPIRVOCLDeviceContext deviceContext = new SPIRVOCLDeviceContext(device, this);
            device.setDeviceContext(deviceContext);
            spirvoclDeviceContext.add(deviceContext);
        }
    }

    @Override
    public OCLContextInterface getOpenCLLayer() {
        return oclContext;
    }

    @Override
    public SPIRVDeviceContext getDeviceContext(int deviceIndex) {
        return spirvoclDeviceContext.get(deviceIndex);
    }

    @Override
    public CommandQueue getCommandQueueForDevice(long executionPlanId, int deviceIndex) {
        return getCommandQueue(executionPlanId, deviceIndex);
    }

    public OCLCommandQueue getCommandQueue(long executionPlanId, int deviceIndex) {
        if (!commmandQueueTable.containsKey(executionPlanId)) {
            SPIRVDevice device = devices.get(deviceIndex);
            SPIRVOCLCommandQueueTable oclCommandQueueTable = new SPIRVOCLCommandQueueTable();
            oclCommandQueueTable.get((SPIRVOCLDevice) device, (OCLContext) oclContext);
            commmandQueueTable.put(executionPlanId, oclCommandQueueTable);
        }
        return commmandQueueTable.get(executionPlanId).get((SPIRVOCLDevice) devices.get(deviceIndex), (OCLContext) oclContext);
    }

    public OCLEventPool getOCLEventPool(long executionPlanId) {
        if (!oclEventPool.containsKey(executionPlanId)) {
            OCLEventPool eventPool = new OCLEventPool(EVENT_WINDOW);
            oclEventPool.put(executionPlanId, eventPool);
        }
        return oclEventPool.get(executionPlanId);
    }

    @Override
    public long allocateMemory(int deviceIndex, long numBytes, Access access) {
        if (oclContext instanceof OCLContext oclContext) {
            long oclMemFlags = getOCLMemFlagForAccess(access);
            return oclContext.createBuffer(oclMemFlags, numBytes).getBuffer();
        } else {
            throw new RuntimeException("Unimplemented: " + oclContext.getClass());
        }
    }

    private static long getOCLMemFlagForAccess(Access access) {
        switch (access) {
            case READ_ONLY:
                return OCLMemFlags.CL_MEM_READ_ONLY;
            case WRITE_ONLY:
                return OCLMemFlags.CL_MEM_WRITE_ONLY;
            case READ_WRITE:
                return OCLMemFlags.CL_MEM_READ_WRITE;
            default:
                // if access has not been deducted by sketcher set it as RW
                return OCLMemFlags.CL_MEM_READ_WRITE;
        }
    }

    @Override
    public void freeMemory(long buffer, int deviceIndex) {
        if (oclContext instanceof OCLContext oclContext) {
            oclContext.releaseBuffer(buffer);
        } else {
            throw new RuntimeException("Unimplemented: " + oclContext.getClass());
        }
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransferp) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvent, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvent, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, long value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    @Override
    public void enqueueBarrier(long executionPlanId, int deviceIndex) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        long oclEvent = commandQueue.enqueueBarrier();
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        eventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_BARRIER, commandQueue);
    }

    @Override
    public void flush(long executionPlanId, int deviceIndex) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        commandQueue.flushEvents();
    }

    @Override
    public void readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, long offHeapSegmentAddress, long hostOffset, int[] waitEvents,
            ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, offHeapSegmentAddress, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    @Override
    public void reset(long executionPlanId, int deviceIndex) {
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        eventPool.reset();
        oclEventPool.remove(executionPlanId);
        SPIRVOCLCommandQueueTable table = commmandQueueTable.get(executionPlanId);
        if (table != null) {
            SPIRVDevice device = devices.get(deviceIndex);
            table.cleanup((SPIRVOCLDevice) device);
            if (table.size() == 0) {
                commmandQueueTable.remove(executionPlanId);
            }
            executionIDs.remove(executionPlanId);
        }
    }

    @Override
    public long mapOnDeviceMemoryRegion(long executionPlanId, int deviceIndex, long dstBuffer, long srcBuffer, long offset, int sizeOfType, long sizeSource, long sizeDest) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId, deviceIndex);
        return commandQueue.mapOnDeviceMemoryRegion(commandQueue.getCommandQueuePtr(), dstBuffer, srcBuffer, offset, sizeOfType, sizeSource, sizeDest);
    }
}
