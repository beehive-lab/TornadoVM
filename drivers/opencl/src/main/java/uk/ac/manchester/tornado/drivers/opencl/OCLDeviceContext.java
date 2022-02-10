/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2021, APT Group, Department of Computer Science,
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.drivers.opencl.OCLCommandQueue.EMPTY_EVENT;
import static uk.ac.manchester.tornado.runtime.common.Tornado.EVENT_WINDOW;
import static uk.ac.manchester.tornado.runtime.common.Tornado.USE_SYNC_FLUSH;
import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

import java.nio.ByteOrder;
import java.util.Comparator;
import java.util.List;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.drivers.common.EventDescriptor;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLMemFlags;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLMemoryManager;
import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLTornadoDevice;
import uk.ac.manchester.tornado.runtime.common.Initialisable;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class OCLDeviceContext extends TornadoLogger implements Initialisable, OCLDeviceContextInterface {

    // FIXME: <REVISIT> Check the current utility of this buffer
    private static final long BUMP_BUFFER_SIZE = Long.decode(getProperty("tornado.opencl.bump.size", "0x100000"));
    private static final String[] BUMP_DEVICES = parseDevices(getProperty("tornado.opencl.bump.devices", "Iris Pro"));

    private final OCLTargetDevice device;
    private final OCLCommandQueue queue;
    private final OCLContext context;
    private final OCLMemoryManager memoryManager;
    private final long bumpBuffer;
    private final OCLCodeCache codeCache;
    private final OCLEventPool oclEventPool;
    private boolean needsBump;
    private boolean wasReset;
    private boolean useRelativeAddresses;
    private boolean printOnce = true;

    protected OCLDeviceContext(OCLTargetDevice device, OCLCommandQueue queue, OCLContext context) {
        this.device = device;
        this.queue = queue;
        this.context = context;
        this.memoryManager = new OCLMemoryManager(this);
        this.codeCache = new OCLCodeCache(this);

        setRelativeAddressesFlag();

        this.oclEventPool = new OCLEventPool(EVENT_WINDOW);

        needsBump = false;
        for (String bumpDevice : BUMP_DEVICES) {
            if (device.getDeviceName().equalsIgnoreCase(bumpDevice.trim())) {
                needsBump = true;
                break;
            }
        }

        if (needsBump) {
            bumpBuffer = context.createBuffer(OCLMemFlags.CL_MEM_READ_WRITE, BUMP_BUFFER_SIZE);
            info("device requires bump buffer: %s", device.getDeviceName());
        } else {
            bumpBuffer = -1;
        }
        this.device.setDeviceContext(this);
    }

    private static String[] parseDevices(String str) {
        return str.split(";");
    }

    public static String checkKernelName(String entryPoint) {
        if (entryPoint.contains("$")) {
            return entryPoint.replace("$", "_");
        }
        return entryPoint;
    }

    private void setRelativeAddressesFlag() {
        if (isPlatformFPGA() && !Tornado.OPENCL_USE_RELATIVE_ADDRESSES) {
            useRelativeAddresses = true;
        } else {
            useRelativeAddresses = Tornado.OPENCL_USE_RELATIVE_ADDRESSES;
        }
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
        return TornadoRuntime.getTornadoRuntime().getDriverIndex(OCLDriver.class);
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
    public void sync() {
        if (USE_SYNC_FLUSH) {
            queue.flush();
        }
        queue.finish();
    }

    @Override
    public long getDeviceId() {
        return device.getId();
    }

    @Override
    public int enqueueBarrier() {
        long oclEvent = queue.enqueueBarrier();
        return (queue.getOpenclVersion() < 120) ? -1 : oclEventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_BARRIER, queue);
    }

    @Override
    public int enqueueMarker() {
        long oclEvent = queue.enqueueMarker();
        return queue.getOpenclVersion() < 120 ? -1 : oclEventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_MARKER, queue);
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

    public int enqueueNDRangeKernel(OCLKernel kernel, int dim, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueNDRangeKernel(kernel, dim, globalWorkOffset, globalWorkSize, localWorkSize, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_PARALLEL_KERNEL, queue);
    }

    public ByteOrder getByteOrder() {
        return device.isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    }

    /*
     * Asynchronous writes to device
     */
    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_WRITE_BYTE, queue);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_WRITE_BYTE, queue);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_WRITE_INT, queue);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_WRITE_LONG, queue);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_WRITE_SHORT, queue);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_WRITE_FLOAT, queue);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_WRITE_DOUBLE, queue);
    }

    /*
     * ASync reads from device
     *
     */
    public int enqueueReadBuffer(long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_READ_BYTE, queue);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_READ_BYTE, queue);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_READ_INT, queue);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_READ_LONG, queue);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_READ_FLOAT, queue);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_READ_DOUBLE, queue);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_READ_SHORT, queue);
    }

    /*
     * Synchronous writes to device
     */
    public void writeBuffer(long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        oclEventPool.registerEvent(
                queue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_WRITE_BYTE, queue);
    }

    public void writeBuffer(long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        oclEventPool.registerEvent(
                queue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_WRITE_BYTE, queue);
    }

    public void writeBuffer(long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        oclEventPool.registerEvent(
                queue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_WRITE_INT, queue);
    }

    public void writeBuffer(long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        oclEventPool.registerEvent(
                queue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_WRITE_LONG, queue);
    }

    public void writeBuffer(long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        oclEventPool.registerEvent(
                queue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_WRITE_SHORT, queue);
    }

    public void writeBuffer(long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        oclEventPool.registerEvent(
                queue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_WRITE_FLOAT, queue);
    }

    public void writeBuffer(long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        oclEventPool.registerEvent(
                queue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_WRITE_DOUBLE, queue);
    }

    /*
     * Synchronous reads from device
     */
    public int readBuffer(long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_READ_BYTE, queue);
    }

    public int readBuffer(long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_READ_BYTE, queue);
    }

    public int readBuffer(long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_READ_INT, queue);
    }

    public int readBuffer(long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_READ_LONG, queue);
    }

    public int readBuffer(long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_READ_FLOAT, queue);
    }

    public int readBuffer(long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_READ_DOUBLE, queue);

    }

    public int readBuffer(long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        return oclEventPool.registerEvent(
                queue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, oclEventPool.serialiseEvents(waitEvents, queue) ? oclEventPool.waitEventsBuffer : null),
                EventDescriptor.DESC_READ_SHORT, queue);
    }

    public int enqueueBarrier(int[] events) {
        long oclEvent = queue.enqueueBarrier(oclEventPool.serialiseEvents(events, queue) ? oclEventPool.waitEventsBuffer : null);
        return queue.getOpenclVersion() < 120 ? -1 : oclEventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_BARRIER, queue);
    }

    public int enqueueMarker(int[] events) {
        long oclEvent = queue.enqueueMarker(oclEventPool.serialiseEvents(events, queue) ? oclEventPool.waitEventsBuffer : null);
        return queue.getOpenclVersion() < 120 ? -1 : oclEventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_MARKER, queue);
    }

    @Override
    public boolean isInitialised() {
        return memoryManager.isInitialised();
    }

    public void reset() {
        oclEventPool.reset();
        memoryManager.reset();
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

        final String deviceName = "opencl-" + context.getPlatformIndex() + "-" + device.getIndex();
        System.out.printf("Found %d events on device %s:\n", events.size(), deviceName);
        if (events.isEmpty()) {
            return;
        }

        events.sort(Comparator.comparingLong(OCLEvent::getCLSubmitTime).thenComparingLong(OCLEvent::getCLStartTime));

        long base = events.get(0).getCLSubmitTime();
        System.out.println("event: device,type,info,queued,submitted,start,end,status");
        events.forEach(event -> System.out.printf("event: %s,%s,%s,0x%x,%d,%d,%d,%s\n", deviceName, event.getName(), event.getOclEventID(), event.getCLQueuedTime() - base,
                event.getCLSubmitTime() - base, event.getCLStartTime() - base, event.getCLEndTime() - base, event.getStatus()));
    }

    @Override
    public boolean needsBump() {
        return needsBump;
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
    public boolean useRelativeAddresses() {
        if (isPlatformFPGA() && !Tornado.OPENCL_USE_RELATIVE_ADDRESSES && printOnce) {
            System.out.println("Warning: -Dtornado.opencl.userelative was set to False. TornadoVM changed it to True because it is required for FPGA execution.");
            printOnce = false;
        }

        return useRelativeAddresses;
    }

    @Override
    public int getDeviceIndex() {
        return device.getIndex();
    }

    @Override
    public int getDevicePlatform() {
        return context.getPlatformIndex();
    }

    public long getBumpBuffer() {
        return bumpBuffer;
    }

    public void retainEvent(int localEventId) {
        oclEventPool.retainEvent(localEventId);
    }

    public Event resolveEvent(int event) {
        if (event == -1) {
            return EMPTY_EVENT;
        }
        return new OCLEvent(oclEventPool.getDescriptor(event).getNameDescription(), queue, event, oclEventPool.getOCLEvent(event));
    }

    public void flush() {
        queue.flush();
    }

    public void finish() {
        queue.finish();
    }

    public void flushEvents() {
        queue.flushEvents();
    }

    public boolean isKernelAvailable() {
        return codeCache.isKernelAvailable();
    }

    public OCLInstalledCode installCode(OCLCompilationResult result) {
        return installCode(result.getMeta(), result.getId(), result.getName(), result.getTargetCode());
    }

    public OCLInstalledCode installCode(TaskMetaData meta, String id, String entryPoint, byte[] code) {
        entryPoint = checkKernelName(entryPoint);
        return codeCache.installSource(meta, id, entryPoint, code);
    }

    public OCLInstalledCode installCode(String id, String entryPoint, byte[] code, boolean shouldCompile) {
        return codeCache.installFPGASource(id, entryPoint, code, shouldCompile);
    }

    public boolean isCached(String id, String entryPoint) {
        entryPoint = checkKernelName(entryPoint);
        return codeCache.isCached(id + "-" + entryPoint);
    }

    @Override
    public boolean isCached(String methodName, SchedulableTask task) {
        methodName = checkKernelName(methodName);
        return codeCache.isCached(task.getId() + "-" + methodName);
    }

    public OCLInstalledCode getInstalledCode(String id, String entryPoint) {
        entryPoint = checkKernelName(entryPoint);
        return codeCache.getInstalledCode(id, entryPoint);
    }

    public OCLCodeCache getCodeCache() {
        return this.codeCache;
    }
}