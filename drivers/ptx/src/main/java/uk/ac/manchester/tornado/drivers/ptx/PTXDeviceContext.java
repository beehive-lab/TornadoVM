/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.ptx;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil.buildKernelName;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.isBoxedPrimitive;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResult;
import uk.ac.manchester.tornado.drivers.ptx.mm.PTXKernelArgs;
import uk.ac.manchester.tornado.drivers.ptx.mm.PTXMemoryManager;
import uk.ac.manchester.tornado.drivers.ptx.runtime.PTXBufferProvider;
import uk.ac.manchester.tornado.drivers.ptx.runtime.PTXTornadoDevice;
import uk.ac.manchester.tornado.runtime.common.KernelArgs;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class PTXDeviceContext extends TornadoLogger implements TornadoDeviceContext {

    private final PTXDevice device;
    private final PTXMemoryManager memoryManager;
    private final PTXStream stream;
    private final PTXCodeCache codeCache;
    private final PTXScheduler scheduler;
    private boolean wasReset;

    private final TornadoBufferProvider bufferProvider;

    public PTXDeviceContext(PTXDevice device, PTXStream stream) {
        this.device = device;
        this.stream = stream;

        this.scheduler = new PTXScheduler(device);
        codeCache = new PTXCodeCache(this);
        memoryManager = new PTXMemoryManager(this);
        bufferProvider = new PTXBufferProvider(this);
        wasReset = false;
    }

    @Override
    public PTXMemoryManager getMemoryManager() {
        return memoryManager;
    }

    public TornadoBufferProvider getBufferProvider() {
        return bufferProvider;
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
    public boolean isPlatformXilinxFPGA() {
        return false;
    }

    public PTXTornadoDevice asMapping() {
        return new PTXTornadoDevice(device.getDeviceIndex());
    }

    public TornadoInstalledCode installCode(PTXCompilationResult result, String resolvedMethodName) {
        return codeCache.installSource(result.getName(), result.getTargetCode(), resolvedMethodName);
    }

    public TornadoInstalledCode installCode(String name, byte[] code, String resolvedMethodName) {
        return codeCache.installSource(name, code, resolvedMethodName);
    }

    public TornadoInstalledCode getInstalledCode(String name) {
        return codeCache.getCachedCode(name);
    }

    public PTXCodeCache getCodeCache() {
        return codeCache;
    }

    public PTXDevice getDevice() {
        return device;
    }

    @Override
    public String getDeviceName() {
        return device.getDeviceName();
    }

    @Override
    public int getDeviceIndex() {
        return device.getDeviceIndex();
    }

    @Override
    public int getDriverIndex() {
        return TornadoRuntime.getTornadoRuntime().getDriverIndex(PTXDriver.class);
    }

    @Override
    public int getDevicePlatform() {
        return 0;
    }

    public ByteOrder getByteOrder() {
        return device.getByteOrder();
    }

    public Event resolveEvent(int event) {
        return stream.resolveEvent(event);
    }

    public void flushEvents() {
        sync();
    }

    public int enqueueBarrier() {
        return stream.enqueueBarrier();
    }

    public int enqueueBarrier(int[] events) {
        return stream.enqueueBarrier(events);
    }

    public int enqueueMarker() {
        // Since streams are always in-order in CUDA there is no difference
        // between marker and barrier
        return stream.enqueueBarrier();
    }

    public int enqueueMarker(int[] events) {
        // Since streams are always in-order in CUDA there is no difference
        // between marker and barrier
        return stream.enqueueBarrier(events);
    }

    public void sync() {
        stream.sync();
    }

    public void flush() {
        // I don't think there is anything like this in CUDA so I am calling sync
        sync();
    }

    public void reset() {
        stream.reset();
        codeCache.reset();
        wasReset = true;
    }

    public int enqueueKernelLaunch(PTXModule module, KernelArgs callWrapper, TaskMetaData taskMeta, long batchThreads) {
        int[] blockDimension = { 1, 1, 1 };
        int[] gridDimension = { 1, 1, 1 };
        if (taskMeta.isWorkerGridAvailable()) {
            WorkerGrid grid = taskMeta.getWorkerGrid(taskMeta.getId());
            int[] global = Arrays.stream(grid.getGlobalWork()).mapToInt(l -> (int) l).toArray();

            if (grid.getLocalWork() != null) {
                blockDimension = Arrays.stream(grid.getLocalWork()).mapToInt(l -> (int) l).toArray();
            } else {
                blockDimension = scheduler.calculateBlockDimension(grid.getGlobalWork(), module.getMaxThreadBlocks(), grid.dimension(), module.javaName);
            }

            PTXGridInfo gridInfo = new PTXGridInfo(module, Arrays.stream(blockDimension).mapToLong(i -> i).toArray());
            boolean checkedDimensions = gridInfo.checkGridDimensions();
            if (!checkedDimensions) {
                blockDimension = scheduler.calculateBlockDimension(grid.getGlobalWork(), module.getMaxThreadBlocks(), grid.dimension(), module.javaName);
                System.out.println("Warning: TornadoVM changed the user-defined local size to the following: [" + blockDimension[0] + ", " + blockDimension[1] + ", " + blockDimension[2] + "].");
            }
            gridDimension = scheduler.calculateGridDimension(module.javaName, grid.dimension(), global, blockDimension);
        } else if (taskMeta.isParallel()) {
            scheduler.calculateGlobalWork(taskMeta, batchThreads);
            blockDimension = scheduler.calculateBlockDimension(module, taskMeta);
            gridDimension = scheduler.calculateGridDimension(module, taskMeta, blockDimension);
        }
        int kernelLaunchEvent = stream.enqueueKernelLaunch(module, taskMeta, writePTXKernelContextOnDevice((PTXKernelArgs) callWrapper, taskMeta), gridDimension, blockDimension);
        updateProfiler(kernelLaunchEvent, taskMeta);
        return kernelLaunchEvent;
    }

    private byte[] writePTXKernelContextOnDevice(PTXKernelArgs callWrapper, TaskMetaData meta) {
        ByteBuffer args = ByteBuffer.allocate(Long.BYTES + callWrapper.getCallArguments().size() * Long.BYTES);
        args.order(getByteOrder());

        // Kernel context pointer
        int kernelContextWriteEventId = callWrapper.enqueueWrite();
        updateProfilerKernelContextWrite(kernelContextWriteEventId, meta, callWrapper);
        long address = callWrapper.toAbsoluteAddress();
        args.putLong(address);

        // Parameters
        for (int argIndex = 0; argIndex < callWrapper.getCallArguments().size(); argIndex++) {
            KernelArgs.CallArgument arg = callWrapper.getCallArguments().get(argIndex);
            if (arg.getValue() instanceof KernelArgs.KernelContextArgument) {
                continue;
            }
            if (isBoxedPrimitive(arg.getValue()) || arg.getValue().getClass().isPrimitive()) {
                args.putLong(((Number) arg.getValue()).longValue());
            } else {
                shouldNotReachHere();
            }
        }

        return args.array();
    }

    private void updateProfilerKernelContextWrite(int kernelContextWriteEventId, TaskMetaData meta, PTXKernelArgs callWrapper) {
        if (TornadoOptions.isProfilerEnabled()) {
            TornadoProfiler profiler = meta.getProfiler();
            Event event = resolveEvent(kernelContextWriteEventId);
            event.waitForEvents();
            long copyInTimer = meta.getProfiler().getTimer(ProfilerType.COPY_IN_TIME);
            copyInTimer += event.getElapsedTime();
            profiler.setTimer(ProfilerType.COPY_IN_TIME, copyInTimer);
            profiler.addValueToMetric(ProfilerType.TOTAL_COPY_IN_SIZE_BYTES, meta.getId(), callWrapper.getSize());

            long dispatchValue = profiler.getTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME);
            dispatchValue += event.getDriverDispatchTime();
            profiler.setTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME, dispatchValue);
        }
    }

    private void updateProfiler(final int taskEvent, final TaskMetaData meta) {
        if (TornadoOptions.isProfilerEnabled()) {
            Event tornadoKernelEvent = resolveEvent(taskEvent);
            tornadoKernelEvent.waitForEvents();
            long timer = meta.getProfiler().getTimer(ProfilerType.TOTAL_KERNEL_TIME);
            // Register globalTime
            meta.getProfiler().setTimer(ProfilerType.TOTAL_KERNEL_TIME, timer + tornadoKernelEvent.getElapsedTime());
            // Register the time for the task
            meta.getProfiler().setTaskTimer(ProfilerType.TASK_KERNEL_TIME, meta.getId(), tornadoKernelEvent.getElapsedTime());
            // Register the dispatch time of the kernel
            long dispatchValue = meta.getProfiler().getTimer(ProfilerType.TOTAL_DISPATCH_KERNEL_TIME);
            dispatchValue += tornadoKernelEvent.getDriverDispatchTime();
            meta.getProfiler().setTimer(ProfilerType.TOTAL_DISPATCH_KERNEL_TIME, dispatchValue);
        }
    }

    @Override
    public boolean isCached(String methodName, SchedulableTask task) {
        return codeCache.isCached(buildKernelName(methodName, task));
    }

    public void cleanup() {
        stream.cleanup();
    }

    /*
     * SYNC READS
     */
    public int readBuffer(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(address, length, array, hostOffset, waitEvents);
    }

    /*
     * ASYNC READS
     */
    public int enqueueReadBuffer(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(address, length, array, hostOffset, waitEvents);
    }

    /*
     * SYNC WRITES
     */
    public void writeBuffer(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        stream.enqueueWrite(address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        stream.enqueueWrite(address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        stream.enqueueWrite(address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        stream.enqueueWrite(address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long address, long length, long[] array, int hostOffset, int[] waitEvents) {
        stream.enqueueWrite(address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long address, long length, float[] array, int hostOffset, int[] waitEvents) {
        stream.enqueueWrite(address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long address, long length, double[] array, int hostOffset, int[] waitEvents) {
        stream.enqueueWrite(address, length, array, hostOffset, waitEvents);
    }

    /*
     * ASYNC WRITES
     */
    public int enqueueWriteBuffer(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(address, length, array, hostOffset, waitEvents);
    }

    public void dumpEvents() {
        List<PTXEvent> events = stream.getEventPool().getEvents();

        final String deviceName = "PTX-" + device.getDeviceName();
        System.out.printf("Found %d events on device %s:\n", events.size(), deviceName);
        if (events.isEmpty()) {
            return;
        }

        System.out.println("event: device, type, info, status");
        events.forEach((e) -> {
            System.out.printf("event: %s, %s, %s\n", deviceName, e.getName(), e.getStatus());
        });
    }

}
