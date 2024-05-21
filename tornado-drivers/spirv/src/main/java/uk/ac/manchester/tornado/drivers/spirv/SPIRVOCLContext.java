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

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.common.utils.EventDescriptor;
import uk.ac.manchester.tornado.drivers.opencl.OCLCommandQueue;
import uk.ac.manchester.tornado.drivers.opencl.OCLCommandQueueTable;
import uk.ac.manchester.tornado.drivers.opencl.OCLContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLEventPool;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.OpenCLBlocking;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLMemFlags;

public class SPIRVOCLContext extends SPIRVContext {

    private OCLContextInterface oclContext;
    private List<SPIRVOCLDeviceContext> spirvoclDeviceContext;

    private final Map<Long, OCLCommandQueueTable> commmandQueueTable;
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
    public SPIRVDeviceContext getDeviceContext(int deviceIndex) {
        return spirvoclDeviceContext.get(deviceIndex);
    }

    @Override
    public SPIRVCommandQueue getCommandQueueForDevice(long executionPlanId, int deviceIndex) {
        if (!commmandQueueTable.containsKey(executionPlanId)) {
            SPIRVDevice device = devices.get(deviceIndex);
            OCLCommandQueueTable oclCommandQueueTable = new OCLCommandQueueTable();
            oclCommandQueueTable.get((OCLTargetDevice) device, (OCLContext) oclContext);
            commmandQueueTable.put(executionPlanId, oclCommandQueueTable);
        }
        return commmandQueueTable.get(executionPlanId).get(devices.get(deviceIndex), oclContext);
    }

    private OCLCommandQueue getCommandQueue(long executionPlanId, int deviceIndex) {
        executionIDs.add(executionPlanId);
        if (!commmandQueueTable.containsKey(executionPlanId)) {
            SPIRVOCLDevice device = (SPIRVOCLDevice) devices.get(deviceIndex);
            OCLCommandQueueTable oclCommandQueueTable = new OCLCommandQueueTable();
            oclCommandQueueTable.get(device, oclContext);
            commmandQueueTable.put(executionPlanId, oclCommandQueueTable);
        }
        return commmandQueueTable.get(executionPlanId).get(context.devices().get(getDeviceIndex()), context);
    }

    private OCLEventPool getOCLEventPool(long executionPlanId) {
        if (!oclEventPool.containsKey(executionPlanId)) {
            OCLEventPool eventPool = new OCLEventPool(EVENT_WINDOW);
            oclEventPool.put(executionPlanId, eventPool);
        }
        return oclEventPool.get(executionPlanId);
    }

    @Override
    public long allocateMemory(int deviceIndex, long numBytes) {
        if (oclContext instanceof OCLContext oclContext) {
            return oclContext.createBuffer(OCLMemFlags.CL_MEM_READ_WRITE, numBytes).getBuffer();
        } else {
            throw new RuntimeException("Unimplemented: " + oclContext.getClass());
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
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        OCLCommandQueue commandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        OCLEventPool eventPool = getOCLEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, value, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue)
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_FLOAT, commandQueue);
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvent, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, long value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public void enqueueBarrier(long executionPlanId, int deviceIndex) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public void flush(long executionPlanId, int deviceIndex) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public void readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, long offHeapSegmentAddress, long hostOffset, int[] waitEvents,
            ProfilerTransfer profilerTransfer) {
        throw new TornadoRuntimeException("Unimplemented");
    }
}
