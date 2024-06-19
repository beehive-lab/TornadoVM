/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2023, APT Group, Department of Computer Science,
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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.common.utils.EventDescriptor;
import uk.ac.manchester.tornado.drivers.opencl.OCLCommandQueue;
import uk.ac.manchester.tornado.drivers.opencl.OCLEvent;
import uk.ac.manchester.tornado.drivers.opencl.OCLEventPool;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResult;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVMemoryManager;
import uk.ac.manchester.tornado.drivers.spirv.runtime.SPIRVBufferProvider;
import uk.ac.manchester.tornado.drivers.spirv.runtime.SPIRVTornadoDevice;
import uk.ac.manchester.tornado.drivers.spirv.timestamps.LevelZeroTransferTimeStamp;
import uk.ac.manchester.tornado.drivers.spirv.timestamps.TimeStamp;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

/**
 * Class to map a SPIR-V device (Device represented either in LevelZero or an
 * OpenCL device) with an SPIR-V Context.
 */
public abstract class SPIRVDeviceContext implements TornadoDeviceContext {

    protected static final Event EMPTY_EVENT = new EmptyEvent();

    protected SPIRVDevice device;
    protected SPIRVContext spirvContext;
    protected SPIRVTornadoDevice tornadoDevice;
    protected SPIRVMemoryManager memoryManager;
    protected SPIRVCodeCache codeCache;
    protected boolean wasReset;
    protected Map<Long, SPIRVEventPool> spirvEventPool;
    private TornadoBufferProvider bufferProvider;

    private Set<Long> executionIds;

    protected SPIRVDeviceContext(SPIRVDevice device, SPIRVContext context) {
        init(device);
        this.spirvContext = context;
        this.executionIds = Collections.synchronizedSet(new HashSet<>());
    }

    private void init(SPIRVDevice device) {
        this.device = device;
        this.tornadoDevice = new SPIRVTornadoDevice(device);
        this.memoryManager = new SPIRVMemoryManager(this);
        if (this instanceof SPIRVLevelZeroDeviceContext) {
            this.codeCache = new SPIRVLevelZeroCodeCache(this);
        } else {
            this.codeCache = new SPIRVOCLCodeCache(this);
        }
        this.wasReset = false;
        this.spirvEventPool = new ConcurrentHashMap<>();
        this.bufferProvider = new SPIRVBufferProvider(this);
    }

    public SPIRVContext getSpirvContext() {
        return this.spirvContext;
    }

    public SPIRVDevice getDevice() {
        return device;
    }

    @Override
    public SPIRVMemoryManager getMemoryManager() {
        return this.memoryManager;
    }

    public TornadoBufferProvider getBufferProvider() {
        return bufferProvider;
    }

    @Override
    public boolean wasReset() {
        return wasReset;
    }

    @Override
    public void setResetToFalse() {
        wasReset = false;
    }

    @Override
    public boolean isPlatformFPGA() {
        return false;
    }

    @Override
    public boolean isFP64Supported() {
        return device.isDeviceDoubleFPSupported();
    }

    @Override
    public int getDeviceIndex() {
        return device.getDeviceIndex();
    }

    @Override
    public int getDevicePlatform() {
        return device.getPlatformIndex();
    }

    @Override
    public String getDeviceName() {
        return device.getDeviceName();
    }

    @Override
    public int getDriverIndex() {
        return TornadoRuntimeProvider.getTornadoRuntime().getBackendIndex(SPIRVBackendImpl.class);
    }

    public SPIRVTornadoDevice asMapping() {
        return tornadoDevice;
    }

    public void reset(long executionPlanId) {
        spirvEventPool.put(executionPlanId, new SPIRVEventPool(TornadoOptions.EVENT_WINDOW));
        codeCache.reset();
        wasReset = true;
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.readBuffer(executionPlanId, getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_READ_BYTE, profilerTransfer);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.readBuffer(executionPlanId, getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_READ_INT, profilerTransfer);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.readBuffer(executionPlanId, getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_READ_FLOAT, profilerTransfer);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.readBuffer(executionPlanId, getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_READ_DOUBLE, profilerTransfer);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.readBuffer(executionPlanId, getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_READ_LONG, profilerTransfer);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.readBuffer(executionPlanId, getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_READ_SHORT, profilerTransfer);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.readBuffer(executionPlanId, getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_READ_BYTE, profilerTransfer);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, long offHeapSegmentAddress, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.readBuffer(executionPlanId, getDeviceIndex(), bufferId, offset, bytes, offHeapSegmentAddress, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_READ_BYTE, profilerTransfer);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, long panamaOffHeapPointer, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, long offHeapSegmentPointer, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    private ProfilerTransfer createStartAndStopBufferTimers() {
        if (this instanceof SPIRVLevelZeroDeviceContext && TornadoOptions.isProfilerEnabled()) {
            LevelZeroTransferTimeStamp start = new LevelZeroTransferTimeStamp(spirvContext, (LevelZeroDevice) device.getDeviceRuntime());
            LevelZeroTransferTimeStamp stop = new LevelZeroTransferTimeStamp(spirvContext, (LevelZeroDevice) device.getDeviceRuntime());
            return new ProfilerTransfer(start, stop);
        }
        return null;
    }

    private SPIRVEventPool getEventPool(long executionPlanId) {
        if (!spirvEventPool.containsKey(executionPlanId)) {
            SPIRVEventPool eventPool = new SPIRVEventPool(TornadoOptions.EVENT_WINDOW);
            spirvEventPool.put(executionPlanId, eventPool);
        }
        return spirvEventPool.get(executionPlanId);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.enqueueWriteBuffer(executionPlanId, device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_WRITE_BYTE, profilerTransfer);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.enqueueWriteBuffer(executionPlanId, device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_WRITE_INT, profilerTransfer);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.enqueueWriteBuffer(executionPlanId, device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_WRITE_FLOAT, profilerTransfer);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.enqueueWriteBuffer(executionPlanId, device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_WRITE_DOUBLE, profilerTransfer);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.enqueueWriteBuffer(executionPlanId, device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_WRITE_LONG, profilerTransfer);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.enqueueWriteBuffer(executionPlanId, device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_WRITE_SHORT, profilerTransfer);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.enqueueWriteBuffer(executionPlanId, device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_WRITE_BYTE, profilerTransfer);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, long value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        executionIds.add(executionPlanId);
        spirvContext.enqueueWriteBuffer(executionPlanId, device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        SPIRVEventPool eventPool = getEventPool(executionPlanId);
        return eventPool.registerEvent(EventDescriptor.DESC_WRITE_BYTE, profilerTransfer);
    }

    public void enqueueBarrier(long executionPlanId, int deviceIndex) {
        executionIds.add(executionPlanId);
        spirvContext.enqueueBarrier(executionPlanId, deviceIndex);
    }

    public void flush(long executionPlanId, int deviceIndex) {
        executionIds.add(executionPlanId);
        spirvContext.flush(executionPlanId, deviceIndex);
    }

    public TornadoInstalledCode installBinary(SPIRVCompilationResult result) {
        return installBinary(result.getMeta(), result.getId(), result.getName(), result.getSPIRVBinary());
    }

    public SPIRVInstalledCode installBinary(TaskMetaData meta, String id, String entryPoint, byte[] code) {
        return codeCache.installSPIRVBinary(meta, id, entryPoint, code);
    }

    public SPIRVInstalledCode installBinary(TaskMetaData meta, String id, String entryPoint, String pathToFile) {
        return codeCache.installSPIRVBinary(meta, id, entryPoint, pathToFile);
    }

    public boolean isCached(String id, String entryPoint) {
        return codeCache.isCached(STR."\{id}-\{entryPoint}");
    }

    @Override
    public boolean isCached(String methodName, SchedulableTask task) {
        return codeCache.isCached(STR."\{task.getId()}-\{methodName}");
    }

    public SPIRVInstalledCode getInstalledCode(String id, String entryPoint) {
        return codeCache.getInstalledCode(id, entryPoint);
    }

    public int enqueueMarker(long executionPlanId) {
        executionIds.add(executionPlanId);
        spirvContext.enqueueBarrier(executionPlanId, getDeviceIndex());
        return 0;
    }

    @Override
    public boolean isPlatformXilinxFPGA() {
        return false;
    }

    public Event resolveEvent(long executionPlanId, int eventId) {
        if (eventId == -1) {
            return EMPTY_EVENT;
        }
        if (this instanceof SPIRVLevelZeroDeviceContext) {
            SPIRVEventPool eventPool = getEventPool(executionPlanId);
            LinkedList<TimeStamp> list = eventPool.getTimers(eventId);
            EventDescriptor eventDescriptor = eventPool.getDescriptor(eventId);
            if (TornadoOptions.isProfilerEnabled()) {
                return new SPIRVLevelZeroEvent(eventDescriptor, eventId, list.get(0), list.get(1));
            } else {
                return new SPIRVLevelZeroEvent(eventDescriptor, eventId, null, null);
            }
        } else if (this instanceof SPIRVOCLDeviceContext spirvoclDeviceContext) {
            SPIRVOCLContext context = (SPIRVOCLContext) spirvoclDeviceContext.getSpirvContext();
            OCLCommandQueue commandQueue = context.getCommandQueue(executionPlanId, spirvoclDeviceContext.getDeviceIndex());
            OCLEventPool eventPool = context.getOCLEventPool(executionPlanId);
            return new OCLEvent(eventPool.getDescriptor(eventId).getNameDescription(), commandQueue, eventId, eventPool.getOCLEvent(eventId));
        } else {
            throw new RuntimeException("Not implemented yet");
        }
    }

    @Override
    public Set<Long> getRegisteredPlanIds() {
        return executionIds;
    }

    public long getPowerUsage() {
        return 0;
    }
}
