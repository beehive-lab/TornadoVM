/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.ptx;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.utils.TornadoAPIUtils.isBoxedPrimitive;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.common.power.PowerMetric;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResult;
import uk.ac.manchester.tornado.drivers.ptx.mm.PTXKernelStackFrame;
import uk.ac.manchester.tornado.drivers.ptx.mm.PTXMemoryManager;
import uk.ac.manchester.tornado.drivers.ptx.power.PTXNvidiaPowerMetricHandler;
import uk.ac.manchester.tornado.drivers.ptx.runtime.PTXBufferProvider;
import uk.ac.manchester.tornado.drivers.ptx.runtime.PTXTornadoDevice;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.UpsMeterReader;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class PTXDeviceContext implements TornadoDeviceContext {

    private final PTXDevice device;
    private final PTXMemoryManager memoryManager;
    private final PTXScheduler scheduler;
    private final TornadoBufferProvider bufferProvider;
    private final PowerMetric powerMetricHandler;
    private final Map<Long, PTXStreamTable> streamTable;
    private boolean wasReset;
    private final Set<Long> executionIDs;

    /**
     * Map table to represent the compiled-code per execution plan. Each entry in the execution plan has its own
     * code cache. The code cache manages the compilation and the cache for each task within an execution plan.
     */
    private final Map<Long, PTXCodeCache> codeCache;

    public PTXDeviceContext(PTXDevice device) {
        this.device = device;
        streamTable = new ConcurrentHashMap<>();
        this.scheduler = new PTXScheduler(device);
        this.powerMetricHandler = new PTXNvidiaPowerMetricHandler(this);
        codeCache = new ConcurrentHashMap<>();
        memoryManager = new PTXMemoryManager(this);
        bufferProvider = new PTXBufferProvider(this);
        wasReset = false;
        executionIDs = Collections.synchronizedSet(new HashSet<>());
    }

    @Override
    public PTXMemoryManager getMemoryManager() {
        return memoryManager;
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
    public boolean isPlatformXilinxFPGA() {
        return false;
    }

    @Override
    public boolean isFP64Supported() {
        return true;
    }

    public PTXTornadoDevice toDevice() {
        return new PTXTornadoDevice(device.getDeviceIndex());
    }

    public TornadoInstalledCode installCode(long executionPlanId, PTXCompilationResult result, String resolvedMethodName) {
        PTXCodeCache ptxCodeCache = getPTXCodeCache(executionPlanId);
        return ptxCodeCache.installSource(result.getName(), result.getTargetCode(), resolvedMethodName, result.metaData().isPrintKernelEnabled());
    }

    public TornadoInstalledCode installCode(long executionPlanId, String name, byte[] code, String resolvedMethodName, boolean printKernel) {
        PTXCodeCache ptxCodeCache = getPTXCodeCache(executionPlanId);
        return ptxCodeCache.installSource(name, code, resolvedMethodName, printKernel);
    }

    public TornadoInstalledCode getInstalledCode(long executionPlanId, String name) {
        PTXCodeCache ptxCodeCache = getPTXCodeCache(executionPlanId);
        return ptxCodeCache.getCachedCode(name);
    }

    public PTXCodeCache getCodeCache(long executionPlanId) {
        return getPTXCodeCache(executionPlanId);
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
        return TornadoRuntimeProvider.getTornadoRuntime().getBackendIndex(PTXBackendImpl.class);
    }

    @Override
    public Set<Long> getRegisteredPlanIds() {
        return executionIDs;
    }

    @Override
    public int getDevicePlatform() {
        return 0;
    }

    public long getPowerUsage() {
        long[] powerUsage = new long[1];
        powerMetricHandler.getPowerUsage(powerUsage);
        return powerUsage[0];
    }

    public ByteOrder getByteOrder() {
        return device.getByteOrder();
    }

    public Event resolveEvent(long executionPlanId, int event) {
        PTXStream stream = getStream(executionPlanId);
        return stream.resolveEvent(event);
    }

    public void flushEvents(long executionPlanId) {
        sync(executionPlanId);
    }

    /**
     * Sync the CUDA Stream only if the Stream Exists
     *
     * @param executionPlanId
     */
    public void flushEventsIfNeeded(long executionPlanId) {
        syncIfNeeded(executionPlanId);
    }

    public int enqueueBarrier(long executionPlanId) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueBarrier(executionPlanId);
    }

    public int enqueueBarrier(long executionPlanId, int[] events) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueBarrier(executionPlanId, events);
    }

    public int enqueueMarker(long executionPlanId) {
        // Since streams are always in-order in CUDA there is no difference
        // between marker and barrier
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueBarrier(executionPlanId);
    }

    public int enqueueMarker(long executionPlanId, int[] events) {
        // Since streams are always in-order in CUDA there is no difference
        // between marker and barrier
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueBarrier(executionPlanId, events);
    }

    public void sync(long executionPlanId) {
        PTXStream stream = getStream(executionPlanId);
        stream.sync();
    }

    /**
     * Sync the CUDA Stream only if the Stream Exists
     *
     * @param executionPlanId
     */
    public void syncIfNeeded(long executionPlanId) {
        PTXStream stream = getStreamIfNeeded(executionPlanId);
        if (stream != null) {
            stream.sync();
        }
    }

    public void flush(long executionPlanId) {
        // I don't think there is anything like this in CUDA, so I am calling sync
        sync(executionPlanId);
    }

    @Override
    public synchronized void reset(long executionPlanId) {
        PTXStreamTable table = streamTable.get(executionPlanId);
        if (table != null) {
            table.cleanup(device);
            if (table.size() == 0) {
                streamTable.remove(executionPlanId);
            }
            executionIDs.remove(executionPlanId);
        }
        getMemoryManager().releaseKernelStackFrame(executionPlanId);
        PTXCodeCache ptxCodeCache = getPTXCodeCache(executionPlanId);
        ptxCodeCache.reset();
        wasReset = true;
    }

    public int enqueueKernelLaunch(long executionPlanId, PTXModule module, KernelStackFrame kernelArgs, TaskDataContext taskMeta, long batchThreads) {
        int[] blockDimension = { 1, 1, 1 };
        int[] gridDimension = { 1, 1, 1 };
        if (taskMeta.isWorkerGridAvailable()) {
            WorkerGrid grid = taskMeta.getWorkerGrid(taskMeta.getId());
            int[] global = Arrays.stream(grid.getGlobalWork()).mapToInt(l -> (int) l).toArray();
            if (grid.getLocalWork() != null) {
                blockDimension = Arrays.stream(grid.getLocalWork()).mapToInt(l -> (int) l).toArray();
            } else {

                blockDimension = scheduler.calculateBlockDimension(grid.getGlobalWork(), module.getPotentialBlockSizeMaxOccupancy(), grid.dimension(), module.javaName);
            }

            PTXGridInfo gridInfo = new PTXGridInfo(module, Arrays.stream(blockDimension).mapToLong(i -> i).toArray());
            boolean checkedDimensions = gridInfo.checkGridDimensions();
            if (!checkedDimensions) {
                blockDimension = scheduler.calculateBlockDimension(grid.getGlobalWork(), module.getPotentialBlockSizeMaxOccupancy(), grid.dimension(), module.javaName);
                System.out.println("Warning: TornadoVM changed the user-defined local size to the following: [" + blockDimension[0] + ", " + blockDimension[1] + ", " + blockDimension[2] + "].");
            }
            gridDimension = scheduler.calculateGridDimension(module.javaName, grid.dimension(), global, blockDimension);
        } else if (taskMeta.isParallel()) {
            scheduler.calculateGlobalWork(taskMeta, batchThreads);
            blockDimension = scheduler.calculateBlockDimension(module, taskMeta);
            gridDimension = scheduler.calculateGridDimension(module, taskMeta, blockDimension);
        }

        PTXStream stream = getStream(executionPlanId);
        int kernelLaunchEvent = stream.enqueueKernelLaunch(executionPlanId, module, taskMeta, writePTXKernelContextOnDevice(executionPlanId, (PTXKernelStackFrame) kernelArgs, taskMeta), gridDimension,
                blockDimension);
        updateProfiler(executionPlanId, kernelLaunchEvent, taskMeta);
        return kernelLaunchEvent;
    }

    private byte[] writePTXKernelContextOnDevice(long executionPlanId, PTXKernelStackFrame ptxKernelArgs, TaskDataContext meta) {
        int capacity = Long.BYTES + ptxKernelArgs.getCallArguments().size() * Long.BYTES;
        ByteBuffer args = ByteBuffer.allocate(capacity);
        args.order(getByteOrder());

        // Kernel context pointer
        int kernelContextWriteEventId = ptxKernelArgs.enqueueWrite(executionPlanId);
        updateProfilerKernelContextWrite(executionPlanId, kernelContextWriteEventId, meta, ptxKernelArgs);
        long address = ptxKernelArgs.toAbsoluteAddress();
        args.putLong(address);

        // Parameters
        for (int argIndex = 0; argIndex < ptxKernelArgs.getCallArguments().size(); argIndex++) {
            KernelStackFrame.CallArgument arg = ptxKernelArgs.getCallArguments().get(argIndex);
            if (arg.getValue() instanceof KernelStackFrame.KernelContextArgument) {
                args.putLong(address);
                continue;
            } else if (isBoxedPrimitive(arg.getValue()) || arg.getValue().getClass().isPrimitive()) {
                if (arg.getValue() instanceof HalfFloat) {
                    short halfFloat = ((HalfFloat) arg.getValue()).getHalfFloatValue();
                    args.putLong(((Number) halfFloat).longValue());
                } else if (arg.getValue() instanceof Number) {
                    args.putLong(((Number) arg.getValue()).longValue());
                } else if (arg.getValue() instanceof Character) {
                    args.putLong((char) arg.getValue());
                } else {
                    shouldNotReachHere();
                }
            } else {
                shouldNotReachHere();
            }
        }

        return args.array();
    }

    private void updateProfilerKernelContextWrite(long executionPlanId, int kernelContextWriteEventId, TaskDataContext meta, PTXKernelStackFrame callWrapper) {
        if (TornadoOptions.isProfilerEnabled()) {
            TornadoProfiler profiler = meta.getProfiler();
            Event event = resolveEvent(executionPlanId, kernelContextWriteEventId);
            event.waitForEvents(executionPlanId);
            long copyInTimer = meta.getProfiler().getTimer(ProfilerType.COPY_IN_TIME);
            copyInTimer += event.getElapsedTime();
            profiler.setTimer(ProfilerType.COPY_IN_TIME, copyInTimer);
            profiler.addValueToMetric(ProfilerType.TOTAL_COPY_IN_SIZE_BYTES, meta.getId(), callWrapper.getSize());

            long dispatchValue = profiler.getTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME);
            dispatchValue += event.getDriverDispatchTime();
            profiler.setTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME, dispatchValue);
        }
    }

    private void updateProfiler(long executionPlanId, final int taskEvent, final TaskDataContext meta) {
        if (TornadoOptions.isProfilerEnabled()) {
            // Metrics captured before blocking
            meta.getProfiler().setTaskPowerUsage(ProfilerType.POWER_USAGE_mW, meta.getId(), getPowerUsage());
            if (TornadoOptions.isUpsReaderEnabled()) {
                meta.getProfiler().setSystemPowerConsumption(ProfilerType.SYSTEM_POWER_CONSUMPTION_W, meta.getId(), (UpsMeterReader.getOutputPowerMetric() != null)
                        ? Long.parseLong(UpsMeterReader.getOutputPowerMetric())
                        : -1);
                meta.getProfiler().setSystemVoltage(ProfilerType.SYSTEM_VOLTAGE_V, meta.getId(), (UpsMeterReader.getOutputVoltageMetric() != null)
                        ? Long.parseLong(UpsMeterReader.getOutputVoltageMetric())
                        : -1);
            }

            Event tornadoKernelEvent = resolveEvent(executionPlanId, taskEvent);
            tornadoKernelEvent.waitForEvents(executionPlanId);
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
    public boolean isCached(long executionPlanId, String methodName, SchedulableTask task) {
        PTXCodeCache ptxCodeCache = getPTXCodeCache(executionPlanId);
        return ptxCodeCache.isCached(PTXCodeUtil.buildKernelName(methodName, task));
    }

    public void destroyStream(long executionPlanId) {
        PTXStream stream = getStream(executionPlanId);
        if (stream != null && !stream.isDestroy()) {
            stream.cuDestroyStream();
        }
    }

    /*
     * SYNC READS
     */

    public int readBuffer(long executionPlanId, long address, long length, long hostPointer, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, hostPointer, hostOffset, waitEvents);
    }

    public int readBuffer(long executionPlanId, long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long executionPlanId, long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long executionPlanId, long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long executionPlanId, long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long executionPlanId, long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long executionPlanId, long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long executionPlanId, long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    /*
     * ASYNC READS
     */

    public int enqueueReadBuffer(long executionPlanId, long address, long length, long hostPointer, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, hostPointer, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long executionPlanId, long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long executionPlanId, long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long executionPlanId, long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long executionPlanId, long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long executionPlanId, long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long executionPlanId, long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long executionPlanId, long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    /*
     * SYNC WRITES
     */
    public void writeBuffer(long executionPlanId, long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long executionPlanId, long address, long length, long hostPointer, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        stream.enqueueWrite(executionPlanId, address, length, hostPointer, hostOffset, waitEvents);
    }

    public void writeBuffer(long executionPlanId, long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long executionPlanId, long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long executionPlanId, long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long executionPlanId, long address, long length, long[] array, int hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long executionPlanId, long address, long length, float[] array, int hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long executionPlanId, long address, long length, double[] array, int hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    /*
     * ASYNC WRITES
     */

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, long hostPointer, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, hostPointer, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public void dumpEvents(long executionPlanId) {
        PTXStream stream = getStream(executionPlanId);
        List<PTXEvent> events = stream.getEventPool().getEvents();

        final String deviceName = "PTX-" + device.getDeviceName();

        System.out.printf("Found %d events on device %s:\n", events.size(), deviceName);
        if (events.isEmpty()) {
            return;
        }

        System.out.println("event: device, type, info, status");
        events.forEach((e) -> System.out.printf("event: %s, %s, %s\n", deviceName, e.getName(), e.getStatus()));
    }

    private PTXStream getStream(long executionPlanId) {
        executionIDs.add(executionPlanId);
        if (!streamTable.containsKey(executionPlanId)) {
            PTXStreamTable ptxStreamTable = new PTXStreamTable();
            ptxStreamTable.get(device);
            streamTable.put(executionPlanId, ptxStreamTable);
        }
        return streamTable.get(executionPlanId).get(device);
    }

    private PTXCodeCache getPTXCodeCache(long executionPlanId) {
        if (!codeCache.containsKey(executionPlanId)) {
            codeCache.put(executionPlanId, new PTXCodeCache(this));
        }
        return codeCache.get(executionPlanId);
    }

    private PTXStream getStreamIfNeeded(long executionPlanId) {
        if (!streamTable.containsKey(executionPlanId)) {
            return null;
        }
        return streamTable.get(executionPlanId).get(device);
    }

    public long mapOnDeviceMemoryRegion(long executionPlanId, long destDevicePtr, long srcDevicePtr, long offset, int sizeOfType) {
        PTXStream ptxStream = getStream(executionPlanId);
        return ptxStream.mapOnDeviceMemoryRegion(destDevicePtr, srcDevicePtr, offset, sizeOfType);
    }
}
