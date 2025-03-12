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
import uk.ac.manchester.tornado.drivers.common.power.PowerMetric;
import uk.ac.manchester.tornado.drivers.common.utils.EventDescriptor;
import uk.ac.manchester.tornado.drivers.opencl.OCLCommandQueue;
import uk.ac.manchester.tornado.drivers.opencl.OCLEvent;
import uk.ac.manchester.tornado.drivers.opencl.OCLEventPool;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResult;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVMemoryManager;
import uk.ac.manchester.tornado.drivers.spirv.power.SPIRVLevelZeroPowerMetricHandler;
import uk.ac.manchester.tornado.drivers.spirv.power.SPIRVOCLPowerMetricHandler;
import uk.ac.manchester.tornado.drivers.spirv.runtime.SPIRVBufferProvider;
import uk.ac.manchester.tornado.drivers.spirv.runtime.SPIRVTornadoDevice;
import uk.ac.manchester.tornado.drivers.spirv.timestamps.LevelZeroTransferTimeStamp;
import uk.ac.manchester.tornado.drivers.spirv.timestamps.TimeStamp;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

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
    protected boolean wasReset;
    protected Map<Long, SPIRVEventPool> spirvEventPool;
    private TornadoBufferProvider bufferProvider;
    protected PowerMetric powerMetricHandler;
    private final Set<Long> executionIds;

    /**
     * Map table to represent the compiled-code per execution plan. Each entry in the execution plan has its own
     * code cache. The code cache manages the compilation and the cache for each task within an execution plan.
     */
    protected Map<Long, SPIRVCodeCache> codeCache;

    protected SPIRVDeviceContext(SPIRVDevice device, SPIRVContext context) {
        init(device);
        this.spirvContext = context;
        this.executionIds = Collections.synchronizedSet(new HashSet<>());
        if (isDeviceContextLevelZero()) {
            this.powerMetricHandler = new SPIRVLevelZeroPowerMetricHandler(this);
        } else {
            this.powerMetricHandler = new SPIRVOCLPowerMetricHandler();
        }
    }

    private void init(SPIRVDevice device) {
        this.device = device;
        this.tornadoDevice = new SPIRVTornadoDevice(device);
        this.memoryManager = new SPIRVMemoryManager(this);
        this.codeCache = new ConcurrentHashMap<>();
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

    public PowerMetric getPowerMetric() {
        return this.powerMetricHandler;
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

    private boolean isDeviceContextLevelZero() {
        return this instanceof SPIRVLevelZeroDeviceContext;
    }

    private boolean isDeviceContextOCL() {
        return this instanceof SPIRVOCLDeviceContext;
    }

    public SPIRVTornadoDevice toDevice() {
        return tornadoDevice;
    }

    @Override
    public void reset(long executionPlanId) {
        spirvContext.reset(executionPlanId, getDeviceIndex());
        spirvEventPool.remove(executionPlanId);

        getMemoryManager().releaseKernelStackFrame(executionPlanId);
        SPIRVCodeCache spirvCodeCache = getSPIRVCodeCache(executionPlanId);
        spirvCodeCache.reset();
        codeCache.remove(executionPlanId);

        executionIds.remove(executionPlanId);
        wasReset = true;
    }

    private SPIRVCodeCache getSPIRVCodeCache(long executionPlanId) {
        if (!codeCache.containsKey(executionPlanId)) {
            SPIRVCodeCache spirvCodeCache;
            if (this instanceof SPIRVLevelZeroDeviceContext) {
                spirvCodeCache = new SPIRVLevelZeroCodeCache(this);
            } else {
                spirvCodeCache = new SPIRVOCLCodeCache(this);
            }
            codeCache.put(executionPlanId, spirvCodeCache);
        }
        return codeCache.get(executionPlanId);
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
        if (isDeviceContextLevelZero() && TornadoOptions.isProfilerEnabled()) {
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

    public TornadoInstalledCode installBinary(long executionPlanId, SPIRVCompilationResult result) {
        return installBinary(executionPlanId, result.getMeta(), result.getId(), result.getName(), result.getSPIRVBinary());
    }

    public SPIRVInstalledCode installBinary(long executionPlanId, TaskDataContext meta, String id, String entryPoint, byte[] code) {
        SPIRVCodeCache spirvCodeCache = getSPIRVCodeCache(executionPlanId);
        return spirvCodeCache.installSPIRVBinary(meta, id, entryPoint, code);
    }

    public SPIRVInstalledCode installBinary(long executionPlanId, TaskDataContext meta, String id, String entryPoint, String pathToFile) {
        SPIRVCodeCache spirvCodeCache = getSPIRVCodeCache(executionPlanId);
        return spirvCodeCache.installSPIRVBinary(meta, id, entryPoint, pathToFile);
    }

    public boolean isCached(long executionPlanId, String id, String entryPoint) {
        SPIRVCodeCache spirvCodeCache = getSPIRVCodeCache(executionPlanId);
        return spirvCodeCache.isCached(id + "-" + entryPoint);
    }

    @Override
    public boolean isCached(long executionPlanId, String methodName, SchedulableTask task) {
        SPIRVCodeCache spirvCodeCache = getSPIRVCodeCache(executionPlanId);
        return spirvCodeCache.isCached(task.getId() + "-" + methodName);
    }

    public SPIRVInstalledCode getInstalledCode(long executionPlanId, String id, String entryPoint) {
        SPIRVCodeCache spirvCodeCache = getSPIRVCodeCache(executionPlanId);
        return spirvCodeCache.getInstalledCode(id, entryPoint);
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
        if (isDeviceContextLevelZero()) {
            SPIRVEventPool eventPool = getEventPool(executionPlanId);
            LinkedList<TimeStamp> list = eventPool.getTimers(eventId);
            EventDescriptor eventDescriptor = eventPool.getDescriptor(eventId);
            if (TornadoOptions.isProfilerEnabled()) {
                return new SPIRVLevelZeroEvent(eventDescriptor, eventId, list.get(0), list.get(1));
            } else {
                return new SPIRVLevelZeroEvent(eventDescriptor, eventId, null, null);
            }
        } else if (isDeviceContextOCL()) {
            SPIRVOCLContext context = (SPIRVOCLContext) this.getSpirvContext();
            OCLCommandQueue commandQueue = context.getCommandQueue(executionPlanId, this.getDeviceIndex());
            OCLEventPool eventPool = context.getOCLEventPool(executionPlanId);
            return new OCLEvent(eventPool.getDescriptor(eventId).getNameDescription(), commandQueue, eventId, eventPool.getOCLEvent(eventId));
        } else {
            throw new TornadoRuntimeException("[Error] SPIR-V Device Context Class not implemented yet.");
        }
    }

    @Override
    public Set<Long> getRegisteredPlanIds() {
        return executionIds;
    }

    public long getPowerUsage() {
        if (isDeviceContextLevelZero()) {
            long[] powerUsage = new long[1];
            powerMetricHandler.getPowerUsage(powerUsage);
            return powerUsage[0];
        }
        return 0;
    }

    public long mapOnDeviceMemoryRegion(long executionPlanId, long destBuffer, long srcBuffer, long offset, int sizeOfType, long sizeSource, long sizeDest) {
        return spirvContext.mapOnDeviceMemoryRegion(executionPlanId, getDeviceIndex(), destBuffer, srcBuffer, offset, sizeOfType, sizeSource, sizeDest);
    }
}
