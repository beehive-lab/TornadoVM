/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022 APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.spirv;

import java.lang.reflect.Array;
import java.util.LinkedList;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.drivers.common.EventDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResult;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVMemoryManager;
import uk.ac.manchester.tornado.drivers.spirv.runtime.SPIRVTornadoDevice;
import uk.ac.manchester.tornado.drivers.spirv.timestamps.LevelZeroTransferTimeStamp;
import uk.ac.manchester.tornado.drivers.spirv.timestamps.TimeStamp;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.Initialisable;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

/**
 * Class to map a SPIR-V device (Device represented either in LevelZero or an
 * OpenCL device) with an SPIR-V Context.
 */
public abstract class SPIRVDeviceContext implements Initialisable, TornadoDeviceContext {

    protected static final Event EMPTY_EVENT = new EmptyEvent();

    protected SPIRVDevice device;
    protected SPIRVCommandQueue queue;
    protected SPIRVContext spirvContext;
    protected SPIRVTornadoDevice tornadoDevice;
    protected SPIRVMemoryManager memoryManager;
    protected SPIRVCodeCache codeCache;
    protected boolean wasReset;
    protected SPIRVEventPool spirvEventPool;

    protected SPIRVDeviceContext(SPIRVDevice device, SPIRVCommandQueue queue, SPIRVContext context) {
        init(device, queue);
        this.spirvContext = context;
    }

    private void init(SPIRVDevice device, SPIRVCommandQueue queue) {
        this.device = device;
        this.queue = queue;
        this.tornadoDevice = new SPIRVTornadoDevice(device);
        this.memoryManager = new SPIRVMemoryManager(this);
        if (this instanceof SPIRVLevelZeroDeviceContext) {
            this.codeCache = new SPIRVLevelZeroCodeCache(this);
        } else {
            this.codeCache = new SPIRVOCLCodeCache(this);
        }
        this.wasReset = false;
        this.spirvEventPool = new SPIRVEventPool(Tornado.EVENT_WINDOW);

    }

    public SPIRVContext getSpirvContext() {
        return this.spirvContext;
    }

    public SPIRVDevice getDevice() {
        return device;
    }

    @Override
    public boolean isInitialised() {
        return memoryManager.isInitialised();
    }

    @Override
    public SPIRVMemoryManager getMemoryManager() {
        return this.memoryManager;
    }

    @Override
    public boolean needsBump() {
        return false;
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
    public boolean useRelativeAddresses() {
        return false;
    }

    private String buildKernelName(String methodName, SchedulableTask task) {
        StringBuilder sb = new StringBuilder(methodName);

        for (Object arg : task.getArguments()) {
            // Object is either array or primitive
            sb.append('_');
            Class<?> argClass = arg.getClass();
            if (RuntimeUtilities.isBoxedPrimitiveClass(argClass)) {
                // Only need to append value.
                // If negative value, remove the minus sign in front
                sb.append(arg.toString().replace('.', '_').replaceAll("-", ""));
            } else if (argClass.isArray() && RuntimeUtilities.isPrimitiveArray(argClass)) {
                // Need to append type and length
                sb.append(argClass.getComponentType().getName());
                sb.append(Array.getLength(arg));
            } else {
                sb.append(argClass.getName().replace('.', '_'));

                // Since with objects there is no way to know what will be a
                // constant differentiate using the hashcode of the object
                sb.append('_');
                sb.append(arg.hashCode());
            }
        }

        return sb.toString();
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
        return TornadoRuntime.getTornadoRuntime().getDriverIndex(SPIRVDriver.class);
    }

    public SPIRVTornadoDevice asMapping() {
        return tornadoDevice;
    }

    public void reset() {
        memoryManager.reset();
        codeCache.reset();
        wasReset = true;
    }

    public int readBuffer(long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        spirvContext.readBuffer(getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        return spirvEventPool.registerEvent(EventDescriptor.DESC_READ_BYTE, profilerTransfer);
    }

    public int readBuffer(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        spirvContext.readBuffer(getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        return spirvEventPool.registerEvent(EventDescriptor.DESC_READ_INT, profilerTransfer);
    }

    public int readBuffer(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        spirvContext.readBuffer(getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        return spirvEventPool.registerEvent(EventDescriptor.DESC_READ_FLOAT, profilerTransfer);
    }

    public int readBuffer(long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        spirvContext.readBuffer(getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        return spirvEventPool.registerEvent(EventDescriptor.DESC_READ_DOUBLE, profilerTransfer);
    }

    public int readBuffer(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        spirvContext.readBuffer(getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        return spirvEventPool.registerEvent(EventDescriptor.DESC_READ_LONG, profilerTransfer);
    }

    public int readBuffer(long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        spirvContext.readBuffer(getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        return spirvEventPool.registerEvent(EventDescriptor.DESC_READ_SHORT, profilerTransfer);
    }

    public int readBuffer(long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        spirvContext.readBuffer(getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        return spirvEventPool.registerEvent(EventDescriptor.DESC_READ_BYTE, profilerTransfer);
    }

    public void writeBuffer(long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public void writeBuffer(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public void writeBuffer(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public void writeBuffer(long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public void writeBuffer(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public void writeBuffer(long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public void writeBuffer(long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    private ProfilerTransfer createStartAndStopBufferTimers() {
        if (TornadoOptions.isProfilerEnabled()) {
            LevelZeroTransferTimeStamp start = new LevelZeroTransferTimeStamp(spirvContext, (LevelZeroDevice) device.getDevice());
            LevelZeroTransferTimeStamp stop = new LevelZeroTransferTimeStamp(spirvContext, (LevelZeroDevice) device.getDevice());
            return new ProfilerTransfer(start, stop);
        }
        return null;
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        return spirvEventPool.registerEvent(EventDescriptor.DESC_WRITE_BYTE, profilerTransfer);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        return spirvEventPool.registerEvent(EventDescriptor.DESC_WRITE_INT, profilerTransfer);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        return spirvEventPool.registerEvent(EventDescriptor.DESC_WRITE_FLOAT, profilerTransfer);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        return spirvEventPool.registerEvent(EventDescriptor.DESC_WRITE_DOUBLE, profilerTransfer);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        return spirvEventPool.registerEvent(EventDescriptor.DESC_WRITE_LONG, profilerTransfer);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        return spirvEventPool.registerEvent(EventDescriptor.DESC_WRITE_SHORT, profilerTransfer);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        ProfilerTransfer profilerTransfer = createStartAndStopBufferTimers();
        spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents, profilerTransfer);
        return spirvEventPool.registerEvent(EventDescriptor.DESC_WRITE_BYTE, profilerTransfer);
    }

    public void enqueueBarrier(int deviceIndex) {
        spirvContext.enqueueBarrier(deviceIndex);
    }

    public void flush(int deviceIndex) {
        spirvContext.flush(deviceIndex);
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
        return codeCache.isCached(id + "-" + entryPoint);
    }

    @Override
    public boolean isCached(String methodName, SchedulableTask task) {
        return codeCache.isCached(task.getId() + "-" + methodName);
    }

    public SPIRVInstalledCode getInstalledCode(String id, String entryPoint) {
        return codeCache.getInstalledCode(id, entryPoint);
    }

    public int enqueueMarker() {
        spirvContext.enqueueBarrier(getDeviceIndex());
        return 0;
    }

    @Override
    public boolean isPlatformXilinxFPGA() {
        return false;
    }

    public Event resolveEvent(int eventId) {
        if (eventId == -1) {
            return EMPTY_EVENT;
        }

        LinkedList<TimeStamp> list = spirvEventPool.getTimers(eventId);
        EventDescriptor eventDescriptor = spirvEventPool.getDescriptor(eventId);
        if (TornadoOptions.USE_LEVELZERO_FOR_SPIRV) {
            if (!TornadoOptions.isProfilerEnabled()) {
                return new SPIRVLevelZeroEvent(eventDescriptor, eventId, null, null);
            } else {
                return new SPIRVLevelZeroEvent(eventDescriptor, eventId, list.get(0), list.get(1));
            }
        }
        throw new RuntimeException("Not implemented yet");
    }
}
