/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

import static uk.ac.manchester.tornado.runtime.common.Tornado.USE_SYNC_FLUSH;
import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

import java.nio.ByteOrder;
import java.util.List;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLMemFlags;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLMemoryManager;
import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLTornadoDevice;
import uk.ac.manchester.tornado.runtime.common.Initialisable;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class OCLDeviceContext extends TornadoLogger implements Initialisable, TornadoDeviceContext {

    private static final long BUMP_BUFFER_SIZE = Long.decode(getProperty("tornado.opencl.bump.size", "0x100000"));
    private static final String[] BUMP_DEVICES = parseDevices(getProperty("tornado.opencl.bump.devices", "Iris Pro"));
    private static final boolean PRINT_OCL_KERNEL_TIME = Boolean.parseBoolean(getProperty("tornado.opencl.timer.kernel", "False").toLowerCase());

    private final OCLDevice device;
    private final OCLCommandQueue queue;
    private final OCLContext context;
    private final OCLMemoryManager memoryManager;
    private boolean needsBump;
    private final long bumpBuffer;

    private final OCLCodeCache codeCache;

    protected OCLDeviceContext(OCLDevice device, OCLCommandQueue queue, OCLContext context) {
        this.device = device;
        this.queue = queue;
        this.context = context;
        this.memoryManager = new OCLMemoryManager(this);
        this.codeCache = new OCLCodeCache(this);

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
    }

    private static String[] parseDevices(String str) {
        return str.split(";");
    }

    boolean printOCLKernelTime() {
        return PRINT_OCL_KERNEL_TIME;
    }

    public List<OCLEvent> events() {
        return queue.getEvents();
    }

    public OCLDevice getDevice() {
        return device;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s", getDevice().getIndex(), getDevice().getDeviceName());
    }

    public OCLContext getPlatformContext() {
        return context;
    }

    @Override
    public OCLMemoryManager getMemoryManager() {
        return memoryManager;
    }

    public void sync() {
        if (USE_SYNC_FLUSH) {
            queue.flush();
        }
        queue.finish();
    }

    public long getDeviceId() {
        return device.getId();
    }

    public int enqueueBarrier() {
        return queue.enqueueBarrier();
    }

    public int enqueueMarker() {
        return queue.enqueueMarker();
    }

    public OCLProgram createProgramWithSource(byte[] source, long[] lengths) {
        return context.createProgramWithSource(source, lengths, this);
    }

    public OCLProgram createProgramWithBinary(byte[] binary, long[] lengths) {
        return context.createProgramWithBinary(device.getId(), binary, lengths, this);
    }

    public void printEvents() {
        queue.printEvents();
    }

    public int enqueueTask(OCLKernel kernel, int[] events) {
        return queue.enqueueTask(kernel, events);
    }

    public int enqueueTask(OCLKernel kernel) {
        return queue.enqueueTask(kernel, null);
    }

    public int enqueueNDRangeKernel(OCLKernel kernel, int dim, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize, int[] waitEvents) {
        return queue.enqueueNDRangeKernel(kernel, dim, globalWorkOffset, globalWorkSize, localWorkSize, waitEvents);
    }

    public ByteOrder getByteOrder() {
        return device.isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    }

    /*
     * Asynchronous writes to device
     */
    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueWrite(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, waitEvents);
    }

    /*
     * ASync reads from device
     * 
     */
    public int enqueueReadBuffer(long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, waitEvents);

    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, waitEvents);

    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueRead(bufferId, OpenCLBlocking.FALSE, offset, bytes, array, hostOffset, waitEvents);

    }

    /*
     * Synchronous writes to device
     */
    public void writeBuffer(long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        queue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        queue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        queue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        queue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        queue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        queue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        queue.enqueueWrite(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, waitEvents);
    }

    /*
     * Synchronous reads from device
     */
    public int readBuffer(long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueRead(bufferId, true, offset, bytes, array, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, waitEvents);

    }

    public int readBuffer(long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        return queue.enqueueRead(bufferId, OpenCLBlocking.TRUE, offset, bytes, array, hostOffset, waitEvents);
    }

    public int enqueueBarrier(int[] events) {
        return queue.enqueueBarrier(events);
    }

    public int enqueueMarker(int[] events) {
        return queue.enqueueMarker(events);
    }

    @Override
    public boolean isInitialised() {
        return memoryManager.isInitialised();
    }

    public void reset() {
        queue.reset();
        memoryManager.reset();
        codeCache.reset();
    }

    public OCLTornadoDevice asMapping() {
        return new OCLTornadoDevice(context.getPlatformIndex(), device.getIndex());
    }

    public String getId() {
        return String.format("opencl-%d-%d", context.getPlatformIndex(), device.getIndex());
    }

    public void dumpEvents() {
        List<OCLEvent> events = queue.getEvents();

        final String deviceName = "opencl-" + context.getPlatformIndex() + "-" + device.getIndex();
        System.out.printf("Found %d events on device %s:\n", events.size(), deviceName);
        if (events.isEmpty()) {
            return;
        }

        events.sort((OCLEvent o1, OCLEvent o2) -> {
            int result = Long.compare(o1.getCLSubmitTime(), o2.getCLSubmitTime());
            if (result == 0) {
                result = Long.compare(o1.getCLStartTime(), o2.getCLStartTime());
            }
            return result;
        });

        long base = events.get(0).getCLSubmitTime();
        System.out.println("event: device,type,info,submitted,start,end,status");
        events.stream().forEach((e) -> {
            System.out.printf("event: %s,%s,0x%x,%d,%d,%d,%s\n", deviceName, e.getName(), e.getOclEventID(), e.getCLSubmitTime() - base, e.getCLStartTime() - base, e.getCLEndTime() - base,
                    e.getStatus());
        });

    }

    @Override
    public boolean needsBump() {
        return needsBump;
    }

    public long getBumpBuffer() {
        return bumpBuffer;
    }

    public void retainEvent(int event) {
        queue.retainEvent(event);
    }

    public void releaseEvent(int event) {
        queue.releaseEvent(event);
    }

    public Event resolveEvent(int event) {
        return queue.resolveEvent(event);
    }

    public void markEvent() {
        queue.markEvent();
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
        return codeCache.installSource(meta, id, entryPoint, code);
    }

    public OCLInstalledCode installCode(String id, String entryPoint, byte[] code) {
        return codeCache.installFPGASource(id, entryPoint, code);
    }

    public boolean isCached(String id, String entryPoint) {
        return codeCache.isCached(id, entryPoint);
    }

    public OCLInstalledCode getCode(String id, String entryPoint) {
        return codeCache.getCode(id, entryPoint);
    }
}
