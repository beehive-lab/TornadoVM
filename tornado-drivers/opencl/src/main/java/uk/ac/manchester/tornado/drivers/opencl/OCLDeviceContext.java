/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2021, 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.drivers.opencl.OCLCommandQueue.EMPTY_EVENT;
import static uk.ac.manchester.tornado.runtime.common.Tornado.EVENT_WINDOW;
import static uk.ac.manchester.tornado.runtime.common.Tornado.USE_SYNC_FLUSH;

import java.nio.ByteOrder;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.common.utils.EventDescriptor;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLMemoryManager;
import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLBufferProvider;
import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLTornadoDevice;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class OCLDeviceContext implements OCLDeviceContextInterface {

    // FIXME: <REVISIT> Check the current utility of this buffer
    private final OCLTargetDevice device;

    /**
     * Table to represent {@link uk.ac.manchester.tornado.api.TornadoExecutionPlan} -> {@link OCLCommandQueueTable}
     */
    private Map<Long, OCLCommandQueueTable> commandQueueTable;
    private final OCLContext context;
    private final OCLNvml nvml;
    private final OCLMemoryManager memoryManager;
    private final OCLCodeCache codeCache;
    private final OCLEventPool oclEventPool;
    private final TornadoBufferProvider bufferProvider;
    private boolean wasReset;

    OCLDeviceContext(OCLTargetDevice device, OCLContext context) {
        this.device = device;
        this.context = context;
        this.memoryManager = new OCLMemoryManager(this);
        this.codeCache = new OCLCodeCache(this);
        this.nvml = new OCLNvml(this);

        this.oclEventPool = new OCLEventPool(EVENT_WINDOW);
        bufferProvider = new OCLBufferProvider(this);
        commandQueueTable = new ConcurrentHashMap<>();
        this.device.setDeviceContext(this);
    }

    public static String checkKernelName(String entryPoint) {
        if (entryPoint.contains("$")) {
            return entryPoint.replace("$", "_");
        }
        return entryPoint;
    }

    @Override
    public OCLTargetDevice getDevice() {
        return device;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s", getDevice().getIndex(), getDevice().getDeviceName());
    }

    @Override
    public String getDeviceName() {
        return device.getDeviceName();
    }

    @Override
    public int getDriverIndex() {
        return TornadoRuntime.getTornadoRuntime().getBackendIndex(OCLBackendImpl.class);
    }

    @Override
    public OCLContext getPlatformContext() {
        return context;
    }

    @Override
    public OCLMemoryManager getMemoryManager() {
        return memoryManager;
    }

    @Override
    public TornadoBufferProvider getBufferProvider() {
        return bufferProvider;
    }

    @Override
    public void sync(long executionPlanId) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        if (USE_SYNC_FLUSH) {
            commandQueue.flush();
        }
        commandQueue.finish();
    }

    @Override
    public long getDeviceId() {
        return device.getId();
    }

    @Override
    public int enqueueBarrier(long executionPlanId) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        long oclEvent = commandQueue.enqueueBarrier();
        return (commandQueue.getOpenclVersion() < 120) ? -1 : oclEventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_BARRIER, commandQueue);
    }

    @Override
    public int enqueueMarker(long executionPlanId) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        long oclEvent = commandQueue.enqueueMarker();
        return commandQueue.getOpenclVersion() < 120 ? -1 : oclEventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_MARKER, commandQueue);
    }

    @Override
    public OCLProgram createProgramWithSource(byte[] source, long[] lengths) {
        return context.createProgramWithSource(source, lengths, this);
    }

    @Override
    public OCLProgram createProgramWithBinary(byte[] binary, long[] lengths) {
        return context.createProgramWithBinary(device.getId(), binary, lengths, this);
    }

    @Override
    public OCLProgram createProgramWithIL(byte[] spirvBinary, long[] lengths) {
        return context.createProgramWithIL(spirvBinary, lengths, this);
    }

    public int enqueueNDRangeKernel(long executionPlanId, OCLKernel kernel, int dim, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueNDRangeKernel(kernel, dim, globalWorkOffset, globalWorkSize, localWorkSize, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_PARALLEL_KERNEL, commandQueue);
    }

    public long getPowerUsage() {
        long[] device = new long[1];
        long[] powerUsage = new long[1];
        nvml.clNvmlDeviceGetHandleByIndex(device);
        nvml.clNvmlDeviceGetPowerUsage(device, powerUsage);
        return powerUsage[0];
    }

    public ByteOrder getByteOrder() {
        return device.isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    }

    /*
     * Asynchronous writes to device
     */
    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_INT, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_LONG, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_SHORT, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_FLOAT, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_DOUBLE, commandQueue);
    }

    private OCLCommandQueue getCommandQueue(long executionPlanId) {
        if (!commandQueueTable.containsKey(executionPlanId)) {
            OCLTargetDevice device = context.devices().get(getDeviceIndex());
            OCLCommandQueueTable oclCommandQueueTable = new OCLCommandQueueTable();
            oclCommandQueueTable.get(device, context);
            commandQueueTable.put(executionPlanId, oclCommandQueueTable);
        }
        return commandQueueTable.get(executionPlanId).get(device, context);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long deviceOffset, long bytes, long hostPointer, long hostOffset, int[] waitEvents) {
        // create command queue if needed
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, deviceOffset, bytes, hostPointer, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_SEGMENT, commandQueue);
    }

    /*
     * ASync reads from device
     *
     */
    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_INT, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_LONG, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_FLOAT, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_DOUBLE, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_SHORT, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, long hostPointer, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, hostPointer, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_SEGMENT, commandQueue);
    }

    /*
     * Synchronous writes to device
     */
    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_INT, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_LONG, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_SHORT, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_FLOAT, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_DOUBLE, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, long hostPointer, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        oclEventPool.registerEvent(commandQueue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, hostPointer, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_SEGMENT, commandQueue);
    }

    /*
     * Synchronous reads from device
     */
    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_INT, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_LONG, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_FLOAT, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_DOUBLE, commandQueue);

    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_SHORT, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, long hostPointer, long hostOffset, int[] waitEvents) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return oclEventPool.registerEvent(commandQueue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, hostPointer, hostOffset, oclEventPool.serialiseEvents(waitEvents, commandQueue)
                ? oclEventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_SEGMENT, commandQueue);
    }

    @Override
    public int enqueueBarrier(long executionPlanId, int[] events) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        long oclEvent = commandQueue.enqueueBarrier(oclEventPool.serialiseEvents(events, commandQueue) ? oclEventPool.waitEventsBuffer : null);
        return commandQueue.getOpenclVersion() < 120 ? -1 : oclEventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_BARRIER, commandQueue);
    }

    @Override
    public int enqueueMarker(long executionPlanId, int[] events) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        long oclEvent = commandQueue.enqueueMarker(oclEventPool.serialiseEvents(events, commandQueue) ? oclEventPool.waitEventsBuffer : null);
        return commandQueue.getOpenclVersion() < 120 ? -1 : oclEventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_MARKER, commandQueue);
    }

    @Override
    public void reset() {
        oclEventPool.reset();
        codeCache.reset();
        wasReset = true;
    }

    public OCLTornadoDevice asMapping() {
        return new OCLTornadoDevice(context.getPlatformIndex(), device.getIndex());
    }

    public String getId() {
        return String.format("opencl-%d-%d", context.getPlatformIndex(), device.getIndex());
    }

    public void dumpEvents() {
        List<OCLEvent> events = oclEventPool.getEvents();

        final String deviceName = "Opencl-" + context.getPlatformIndex() + "-" + device.getIndex();
        System.out.printf("Found %d events on device %s:\n", events.size(), deviceName);
        if (events.isEmpty()) {
            return;
        }

        events.sort(Comparator.comparingLong(OCLEvent::getCLSubmitTime).thenComparingLong(OCLEvent::getCLStartTime));

        long base = events.get(0).getCLSubmitTime();
        System.out.println("event: device,type,info,queued,submitted,start,end,status");
        events.forEach(event -> System.out.printf("event: %s,%s,%s,0x%x,%d,%d,%d,%s\n", deviceName, event.getName(), event.getOclEventID(), event.getCLQueuedTime() - base, event
                .getCLSubmitTime() - base, event.getCLStartTime() - base, event.getCLEndTime() - base, event.getStatus()));
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
        return getDevice().getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR && (getPlatformContext().getPlatform().getName().toLowerCase().contains("fpga") || isPlatformXilinxFPGA());
    }

    @Override
    public boolean isPlatformXilinxFPGA() {
        return getPlatformContext().getPlatform().getName().toLowerCase().contains("xilinx");
    }

    @Override
    public boolean isFP64Supported() {
        return device.isDeviceDoubleFPSupported();
    }

    @Override
    public int getDeviceIndex() {
        return device.getIndex();
    }

    @Override
    public int getDevicePlatform() {
        return context.getPlatformIndex();
    }

    public void retainEvent(int localEventId) {
        oclEventPool.retainEvent(localEventId);
    }

    @Override
    public Event resolveEvent(long executionPlanId, int event) {
        if (event == -1) {
            return EMPTY_EVENT;
        }
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return new OCLEvent(oclEventPool.getDescriptor(event).getNameDescription(), commandQueue, event, oclEventPool.getOCLEvent(event));
    }

    @Override
    public void flush(long executionPlanId) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        commandQueue.flush();
    }

    public void finish(long executionPlanId) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        commandQueue.finish();
    }

    @Override
    public void flushEvents(long executionPlanId) {
        OCLCommandQueue commandQueue = getCommandQueue(executionPlanId);
        commandQueue.flushEvents();
    }

    @Override
    public boolean isKernelAvailable() {
        return codeCache.isKernelAvailable();
    }

    public OCLInstalledCode installCode(OCLCompilationResult result) {
        return installCode(result.getMeta(), result.getId(), result.getName(), result.getTargetCode());
    }

    @Override
    public OCLInstalledCode installCode(TaskMetaData meta, String id, String entryPoint, byte[] code) {
        entryPoint = checkKernelName(entryPoint);
        return codeCache.installSource(meta, id, entryPoint, code);
    }

    @Override
    public OCLInstalledCode installCode(String id, String entryPoint, byte[] code, boolean shouldCompile, boolean printKernel) {
        return codeCache.installFPGASource(id, entryPoint, code, shouldCompile, printKernel);
    }

    @Override
    public boolean isCached(String id, String entryPoint) {
        entryPoint = checkKernelName(entryPoint);
        return codeCache.isCached(STR."\{id}-\{entryPoint}");
    }

    @Override
    public boolean isCached(String methodName, SchedulableTask task) {
        methodName = checkKernelName(methodName);
        return codeCache.isCached(STR."\{task.getId()}-\{methodName}");
    }

    public OCLInstalledCode getInstalledCode(String id, String entryPoint) {
        entryPoint = checkKernelName(entryPoint);
        return codeCache.getInstalledCode(id, entryPoint);
    }

    @Override
    public OCLCodeCache getCodeCache() {
        return this.codeCache;
    }

}
