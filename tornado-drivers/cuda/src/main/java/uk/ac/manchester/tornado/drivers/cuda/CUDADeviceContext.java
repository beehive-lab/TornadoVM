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
package uk.ac.manchester.tornado.drivers.cuda;

import static uk.ac.manchester.tornado.drivers.cuda.CUDACommandQueue.EMPTY_EVENT;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.EVENT_WINDOW;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.EnumMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.common.power.PowerMetric;
import uk.ac.manchester.tornado.drivers.common.utils.EventDescriptor;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDADeviceType;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAInstalledCode;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResult;
import uk.ac.manchester.tornado.drivers.cuda.mm.CUDAMemoryManager;
import uk.ac.manchester.tornado.drivers.cuda.power.CUDAEmptyPowerMetricHandler;
import uk.ac.manchester.tornado.drivers.cuda.power.CUDANvidiaPowerMetricHandler;
import uk.ac.manchester.tornado.drivers.cuda.runtime.CUDABufferProvider;
import uk.ac.manchester.tornado.drivers.cuda.runtime.CUDATornadoDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class CUDADeviceContext implements CUDADeviceContextInterface {

    private final CUDATargetDevice device;

    /**
     * Table to represent {@link uk.ac.manchester.tornado.api.TornadoExecutionPlan} -> {@link CUDACommandQueueTable}
     */
    private final Map<Long, CUDACommandQueueTable> commandQueueTable;
    private final CUDAContext context;
    private final PowerMetric powerMetricHandler;
    private final CUDAMemoryManager memoryManager;
    private final Map<Long, CUDAEventPool> oclEventPool;
    private final TornadoBufferProvider bufferProvider;
    private boolean wasReset;
    private final Set<Long> executionIDs;

    /** Execution plans that requested intra-plan concurrency (multi-queue issue). */
    private final Set<Long> intraPlanConcurrencyPlans = Collections.synchronizedSet(new HashSet<>());
    /** Guards the one-time warning emitted when concurrency is requested on hardware that cannot overlap. */
    private boolean warnedUnsupportedConcurrency;
    /** Per-plan single-instance role queues (H2D / D2H) used when intra-plan concurrency is enabled. */
    private final Map<Long, EnumMap<CUDAStreamType, CUDACommandQueue>> roleQueueTable = new ConcurrentHashMap<>();

    /**
     * Number of CUDA streams in the per-plan COMPUTE pool. DAG-independent kernels are round-robined
     * across these so they can execute concurrently (CUDA serialises within a single stream). A single
     * COMPUTE queue would serialise every kernel, which is why kernel/kernel overlap needs a pool.
     * Configurable via {@code -Dtornado.cuda.compute.streams}; defaults to 4 (mirrors the PTX backend).
     */
    private static final int COMPUTE_POOL_SIZE = Math.max(1, Integer.getInteger("tornado.cuda.compute.streams", 4));
    /** Per-plan COMPUTE stream pool, grown lazily up to {@link #COMPUTE_POOL_SIZE}. */
    private final Map<Long, List<CUDACommandQueue>> computePool = new ConcurrentHashMap<>();
    /** Per-plan round-robin cursor for assigning kernels to COMPUTE-pool streams. */
    private final Map<Long, AtomicInteger> computeCursor = new ConcurrentHashMap<>();

    /**
     * Kernel stack-frame writes deferred during CUDA graph capture. The stack
     * frame holds device buffer addresses and grid metadata that are constant
     * across graph replays, so it is written once outside the captured graph
     * rather than recorded as a graph node (which would pin a transient Java
     * heap array). Flushed when capture ends, before the first graph launch.
     */
    private final java.util.List<uk.ac.manchester.tornado.drivers.cuda.mm.CUDAKernelStackFrame> pendingKernelContextWrites = new java.util.ArrayList<>();

    /**
     * Map table to represent the compiled-code per execution plan. Each entry in the execution plan has its own
     * code cache. The code cache manages the compilation and the cache for each task within an execution plan.
     */
    private final Map<Long, CUDACodeCache> codeCache;

    public CUDADeviceContext(CUDATargetDevice device, CUDAContext context) {
        this.device = device;
        this.context = context;
        this.memoryManager = new CUDAMemoryManager(this);
        this.oclEventPool = new ConcurrentHashMap<>();
        this.bufferProvider = new CUDABufferProvider(this);
        this.commandQueueTable = new ConcurrentHashMap<>();
        this.device.setDeviceContext(this);
        this.executionIDs = Collections.synchronizedSet(new HashSet<>());
        if (isDeviceContextOfNvidia()) {
            this.powerMetricHandler = new CUDANvidiaPowerMetricHandler(this);
        } else {
            this.powerMetricHandler = new CUDAEmptyPowerMetricHandler();
        }
        codeCache = new ConcurrentHashMap<>();
    }

    private boolean isDeviceContextOfNvidia() {
        return this.getPlatformContext().getPlatform().getName().toLowerCase().contains("nvidia");
    }

    public static String checkKernelName(String entryPoint) {
        if (entryPoint.contains("$")) {
            return entryPoint.replace("$", "_");
        }
        return entryPoint;
    }

    @Override
    public CUDATargetDevice getDevice() {
        return device;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s", this.getDevice().getIndex(), this.getDevice().getDeviceName());
    }

    @Override
    public String getDeviceName() {
        return device.getDeviceName();
    }

    @Override
    public int getDriverIndex() {
        return TornadoRuntimeProvider.getTornadoRuntime().getBackendIndex(CUDABackendImpl.class);
    }

    @Override
    public Set<Long> getRegisteredPlanIds() {
        return executionIDs;
    }

    @Override
    public CUDAContext getPlatformContext() {
        return context;
    }

    @Override
    public CUDAMemoryManager getMemoryManager() {
        return memoryManager;
    }

    @Override
    public TornadoBufferProvider getBufferProvider() {
        return bufferProvider;
    }

    @Override
    public void sync(long executionPlanId) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId);
        if (TornadoOptions.USE_SYNC_FLUSH) {
            commandQueue.flush();
        }
        commandQueue.finish();
        for (CUDACommandQueue roleQueue : secondaryQueues(executionPlanId)) {
            roleQueue.finish();
        }
    }

    @Override
    public long getDeviceId() {
        return device.getDevicePointer();
    }

    @Override
    public int enqueueBarrier(long executionPlanId) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId);
        joinRoleQueues(executionPlanId, commandQueue);
        long oclEvent = commandQueue.enqueueBarrier();
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return (commandQueue.getOpenclVersion() < 120) ? -1 : eventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_BARRIER, commandQueue);
    }

    @Override
    public int enqueueMarker(long executionPlanId) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId);
        joinRoleQueues(executionPlanId, commandQueue);
        long oclEvent = commandQueue.enqueueMarker();
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return commandQueue.getOpenclVersion() < 120 ? -1 : eventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_MARKER, commandQueue);
    }

    @Override
    public CUDAProgram createProgramWithSource(byte[] source, long[] lengths) {
        return context.createProgramWithSource(source, lengths, this);
    }

    @Override
    public CUDAProgram createProgramWithBinary(byte[] binary, long[] lengths) {
        return context.createProgramWithBinary(device.getDevicePointer(), binary, lengths, this);
    }

    @Override
    public CUDAProgram createProgramWithIL(byte[] spirvBinary, long[] lengths) {
        return context.createProgramWithIL(spirvBinary, lengths, this);
    }

    public int enqueueNDRangeKernel(long executionPlanId, CUDAKernel kernel, int dim, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize, int[] waitEvents) {
        CUDACommandQueue commandQueue = getComputeQueue(executionPlanId);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueNDRangeKernel(kernel, dim, globalWorkOffset, globalWorkSize, localWorkSize, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_PARALLEL_KERNEL, commandQueue);
    }

    public long getPowerUsage() {
        long[] powerUsage = new long[1];
        powerMetricHandler.getPowerUsage(powerUsage);
        return powerUsage[0];
    }

    public ByteOrder getByteOrder() {
        return device.isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    }

    /*
     * Asynchronous writes to device
     */
    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_INT, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_LONG, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_SHORT, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_FLOAT, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_DOUBLE, commandQueue);
    }

    private CUDACommandQueue getCommandQueue(long executionPlanId) {
        executionIDs.add(executionPlanId);
        if (!commandQueueTable.containsKey(executionPlanId)) {
            CUDATargetDevice device = context.devices().get(getDeviceIndex());
            CUDACommandQueueTable oclCommandQueueTable = new CUDACommandQueueTable();
            oclCommandQueueTable.get(device, context);
            commandQueueTable.put(executionPlanId, oclCommandQueueTable);
        }
        return commandQueueTable.get(executionPlanId).get(context.devices().get(getDeviceIndex()), context);
    }

    private CUDAEventPool getCUDAEventPool(long executionPlanId) {
        if (!oclEventPool.containsKey(executionPlanId)) {
            CUDAEventPool eventPool = new CUDAEventPool(EVENT_WINDOW);
            oclEventPool.put(executionPlanId, eventPool);
        }
        return oclEventPool.get(executionPlanId);
    }

    /**
     * Records, per execution plan, whether intra-plan concurrency (multi-queue issue) is enabled.
     * Pushed by the runtime before issuing a plan's bytecodes.
     */
    public void setIntraPlanConcurrency(long executionPlanId, boolean enabled) {
        if (enabled && deviceSupportsIntraPlanConcurrency()) {
            intraPlanConcurrencyPlans.add(executionPlanId);
        } else {
            if (enabled && !warnedUnsupportedConcurrency) {
                System.err.printf("Warning: intra-plan concurrency requested but device '%s' cannot overlap work "
                        + "(asyncEngineCount=%d, concurrentKernels=%b); falling back to single-queue execution.%n",
                        device.getDeviceName(), device.getAsyncEngineCount(), device.supportsConcurrentKernels());
                warnedUnsupportedConcurrency = true;
            }
            intraPlanConcurrencyPlans.remove(executionPlanId);
        }
    }

    /**
     * Intra-plan concurrency (multi-queue issue) is only beneficial when the device can actually overlap
     * work: either run kernels concurrently or overlap copies with compute (async copy engines). On hardware
     * that supports neither, role queues add cross-queue event overhead for no gain, so we fall back to
     * single-queue execution - mirroring the PTX backend.
     */
    private boolean deviceSupportsIntraPlanConcurrency() {
        return device.supportsConcurrentKernels() || device.getAsyncEngineCount() >= 1;
    }

    /**
     * Multi-queue issue is disabled while the plan's stream is capturing into a CUDA graph:
     * capture records a single stream, and touching other streams would invalidate it.
     */
    private boolean isMultiStreamEnabled(long executionPlanId) {
        return intraPlanConcurrencyPlans.contains(executionPlanId) && !isStreamCapturing(executionPlanId);
    }

    /**
     * Returns the queue that should execute an operation of the given role: a dedicated per-plan
     * role queue under intra-plan concurrency, the plan's default queue otherwise. Cross-queue
     * ordering is preserved by serialising the dependency wait lists into CUevent waits (see
     * {@link CUDAEventPool#serialiseEvents(int[], CUDACommandQueue, boolean)}).
     */
    private CUDACommandQueue getCommandQueue(long executionPlanId, CUDAStreamType streamType) {
        if (streamType == CUDAStreamType.DEFAULT || !isMultiStreamEnabled(executionPlanId)) {
            return getCommandQueue(executionPlanId);
        }
        return roleQueueTable.computeIfAbsent(executionPlanId, id -> new EnumMap<>(CUDAStreamType.class)).computeIfAbsent(streamType, type -> createQueue());
    }

    /**
     * Returns the COMPUTE queue a kernel should run on: round-robined across the per-plan COMPUTE pool
     * under intra-plan concurrency so DAG-independent kernels overlap, or the plan's default queue
     * otherwise. During CUDA-graph capture {@link #isMultiStreamEnabled} is already false, so capture
     * records on the single default queue. Mirrors the PTX COMPUTE stream pool.
     */
    private CUDACommandQueue getComputeQueue(long executionPlanId) {
        if (!isMultiStreamEnabled(executionPlanId)) {
            return getCommandQueue(executionPlanId);
        }
        int index = nextComputeIndex(executionPlanId);
        List<CUDACommandQueue> pool = computePool.computeIfAbsent(executionPlanId, id -> new ArrayList<>());
        synchronized (pool) {
            while (pool.size() <= index) {
                pool.add(createQueue());
            }
            return pool.get(index);
        }
    }

    /** Round-robin index of the next COMPUTE-pool stream for this plan (0..COMPUTE_POOL_SIZE-1). */
    private int nextComputeIndex(long executionPlanId) {
        AtomicInteger cursor = computeCursor.computeIfAbsent(executionPlanId, id -> new AtomicInteger());
        return Math.floorMod(cursor.getAndIncrement(), COMPUTE_POOL_SIZE);
    }

    /**
     * All secondary queues for a plan under intra-plan concurrency: the single-instance role queues
     * (H2D / D2H) plus the whole COMPUTE pool. Excludes the plan's default queue, which is the join target.
     */
    private List<CUDACommandQueue> secondaryQueues(long executionPlanId) {
        List<CUDACommandQueue> all = new ArrayList<>();
        EnumMap<CUDAStreamType, CUDACommandQueue> roles = roleQueueTable.get(executionPlanId);
        if (roles != null) {
            all.addAll(roles.values());
        }
        List<CUDACommandQueue> pool = computePool.get(executionPlanId);
        if (pool != null) {
            synchronized (pool) {
                all.addAll(pool);
            }
        }
        return all;
    }

    private CUDACommandQueue createQueue() {
        CUDATargetDevice targetDevice = context.devices().get(getDeviceIndex());
        try {
            long queuePtr = context.clCreateCommandQueue(context.getContextId(), targetDevice.getDevicePointer(), context.getProperties());
            return new CUDACommandQueue(queuePtr, context.getProperties(), targetDevice.deviceVersion());
        } catch (uk.ac.manchester.tornado.drivers.cuda.exceptions.CUDAException e) {
            throw new uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException(e);
        }
    }

    /**
     * Joins the plan's role queues into its default queue: records a marker event on each role
     * queue and makes the default queue wait on all of them, so a subsequent marker/barrier on the
     * default queue covers the whole plan.
     */
    private void joinRoleQueues(long executionPlanId, CUDACommandQueue defaultQueue) {
        List<CUDACommandQueue> queues = secondaryQueues(executionPlanId);
        if (queues.isEmpty()) {
            return;
        }
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        long[] buffer = new long[queues.size() + 1];
        int index = 0;
        for (CUDACommandQueue queue : queues) {
            long event = queue.enqueueMarker();
            eventPool.registerEvent(event, EventDescriptor.DESC_SYNC_MARKER, queue);
            buffer[++index] = event;
        }
        buffer[0] = index;
        defaultQueue.enqueueBarrier(buffer);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long deviceOffset, long bytes, long hostPointer, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        long eventId = commandQueue.enqueueWrite(bufferId, CUDABlocking.FALSE, deviceOffset, bytes, hostPointer, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null);
        return eventPool.registerEvent(eventId, EventDescriptor.DESC_WRITE_SEGMENT, commandQueue);
    }

    /*
     * ASync reads from device
     *
     */
    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_INT, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_LONG, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_FLOAT, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_DOUBLE, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_SHORT, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, long hostPointer, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.FALSE, offset, bytes, hostPointer, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_SEGMENT, commandQueue);
    }

    /*
     * Synchronous writes to device
     */
    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_INT, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_LONG, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_SHORT, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_FLOAT, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_DOUBLE, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, long hostPointer, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_H2D);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, CUDABlocking.TRUE, offset, bytes, hostPointer, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_SEGMENT, commandQueue);
    }

    /*
     * Synchronous reads from device
     */
    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_INT, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_LONG, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_FLOAT, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_DOUBLE, commandQueue);

    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_SHORT, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, long hostPointer, long hostOffset, int[] waitEvents) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId, CUDAStreamType.DATA_TRANSFER_D2H);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, CUDABlocking.TRUE, offset, bytes, hostPointer, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_SEGMENT, commandQueue);
    }

    @Override
    public int enqueueBarrier(long executionPlanId, int[] events) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId);
        joinRoleQueues(executionPlanId, commandQueue);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        long oclEvent = commandQueue.enqueueBarrier(eventPool.serialiseEvents(events, commandQueue, isMultiStreamEnabled(executionPlanId)) ? eventPool.waitEventsBuffer : null);
        return commandQueue.getOpenclVersion() < 120 ? -1 : eventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_BARRIER, commandQueue);
    }

    @Override
    public int enqueueMarker(long executionPlanId, int[] events) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId);
        joinRoleQueues(executionPlanId, commandQueue);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        long oclEvent = commandQueue.enqueueMarker(eventPool.serialiseEvents(events, commandQueue, isMultiStreamEnabled(executionPlanId)) ? eventPool.waitEventsBuffer : null);
        return commandQueue.getOpenclVersion() < 120 ? -1 : eventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_MARKER, commandQueue);
    }

    @Override
    public void reset(long executionPlanId) {
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        eventPool.reset();
        EnumMap<CUDAStreamType, CUDACommandQueue> queues = roleQueueTable.remove(executionPlanId);
        if (queues != null) {
            for (CUDACommandQueue roleQueue : queues.values()) {
                roleQueue.cleanup();
            }
        }
        List<CUDACommandQueue> pool = computePool.remove(executionPlanId);
        if (pool != null) {
            for (CUDACommandQueue computeQueue : pool) {
                computeQueue.cleanup();
            }
        }
        computeCursor.remove(executionPlanId);
        intraPlanConcurrencyPlans.remove(executionPlanId);
        oclEventPool.remove(executionPlanId);
        CUDACommandQueueTable table = commandQueueTable.get(executionPlanId);
        if (table != null) {
            table.cleanup(device);
            if (table.size() == 0) {
                commandQueueTable.remove(executionPlanId);
            }
            executionIDs.remove(executionPlanId);
        }
        getMemoryManager().releaseKernelStackFrame(executionPlanId);
        CUDACodeCache oclCodeCache = getCUDACodeCache(executionPlanId);
        oclCodeCache.reset();
        codeCache.remove(executionPlanId);
        wasReset = true;
    }

    public CUDATornadoDevice toDevice() {
        return new CUDATornadoDevice(context.getPlatformIndex(), device.getIndex());
    }

    public String getId() {
        return String.format("opencl-%d-%d", context.getPlatformIndex(), device.getIndex());
    }

    public void dumpEvents() {
        Set<Long> executionPlanIds = oclEventPool.keySet();
        for (Long id : executionPlanIds) {
            CUDAEventPool eventPool = getCUDAEventPool(id);
            List<CUDAEvent> events = eventPool.getEvents();
            final String deviceName = "Opencl-" + context.getPlatformIndex() + "-" + device.getIndex();
            System.out.printf("Found %d events on device %s:\n", events.size(), deviceName);
            if (events.isEmpty()) {
                return;
            }
            events.sort(Comparator.comparingLong(CUDAEvent::getCLSubmitTime).thenComparingLong(CUDAEvent::getCLStartTime));
            long base = events.getFirst().getCLSubmitTime();
            System.out.println("event: device,type,info,queued,submitted,start,end,status");
            events.forEach(event -> System.out.printf("event: %s,%s,%s,0x%x,%d,%d,%d,%s\n", deviceName, event.getName(), event.getOclEventID(), event.getCLQueuedTime() - base, event
                    .getCLSubmitTime() - base, event.getCLStartTime() - base, event.getCLEndTime() - base, event.getStatus()));
        }
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
        return this.getDevice().getDeviceType() == CUDADeviceType.CL_DEVICE_TYPE_ACCELERATOR && (getPlatformContext().getPlatform().getName().toLowerCase().contains("fpga") || isPlatformXilinxFPGA());
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

    @Override
    public Event resolveEvent(long executionPlanId, int event) {
        if (event == -1) {
            return EMPTY_EVENT;
        }
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId);
        CUDAEventPool eventPool = getCUDAEventPool(executionPlanId);
        return new CUDAEvent(eventPool.getDescriptor(event).getNameDescription(), commandQueue, event, eventPool.getCUDAEvent(event));
    }

    @Override
    public void flush(long executionPlanId) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId);
        commandQueue.flush();
    }

    @Override
    public void flushEvents(long executionPlanId) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId);
        commandQueue.flushEvents();
    }

    private CUDACodeCache getCUDACodeCache(long executionPlanId) {
        if (!codeCache.containsKey(executionPlanId)) {
            codeCache.put(executionPlanId, new CUDACodeCache(this));
        }
        return codeCache.get(executionPlanId);
    }

    @Override
    public boolean isKernelAvailable(long executionPlanId) {
        CUDACodeCache oclCodeCache = getCUDACodeCache(executionPlanId);
        return oclCodeCache.isKernelAvailable();
    }

    public CUDAInstalledCode installCode(long executionPlanId, CUDACompilationResult result) {
        return installCode(executionPlanId, result.getMeta(), result.getId(), result.getName(), result.getTargetCode());
    }

    @Override
    public CUDAInstalledCode installCode(long executionPlanId, TaskDataContext meta, String id, String entryPoint, byte[] code) {
        entryPoint = checkKernelName(entryPoint);
        CUDACodeCache oclCodeCache = getCUDACodeCache(executionPlanId);
        return oclCodeCache.installSource(meta, id, entryPoint, code);
    }

    @Override
    public CUDAInstalledCode installCode(long executionPlanId, String id, String entryPoint, byte[] code, boolean printKernel) {
        CUDACodeCache oclCodeCache = getCUDACodeCache(executionPlanId);
        return oclCodeCache.installFPGASource(id, entryPoint, code, printKernel);
    }

    @Override
    public boolean isCached(long executionPlanId, String id, String entryPoint) {
        entryPoint = checkKernelName(entryPoint);
        CUDACodeCache oclCodeCache = getCUDACodeCache(executionPlanId);
        return oclCodeCache.isCached(id + "-" + entryPoint);
    }

    @Override
    public boolean isCached(long executionPlanId, String methodName, SchedulableTask task) {
        methodName = checkKernelName(methodName);
        CUDACodeCache oclCodeCache = getCUDACodeCache(executionPlanId);
        return oclCodeCache.isCached(task.getId() + "-" + methodName);
    }

    @Override
    public CUDAInstalledCode getInstalledCode(long executionPlanId, String id, String entryPoint) {
        entryPoint = checkKernelName(entryPoint);
        CUDACodeCache oclCodeCache = getCUDACodeCache(executionPlanId);
        return oclCodeCache.getInstalledCode(id, entryPoint);
    }

    @Override
    public CUDACodeCache getCodeCache(long executionPlanId) {
        return getCUDACodeCache(executionPlanId);
    }

    public long mapOnDeviceMemoryRegion(long executionPlanId, long destDevicePtr, long srcDevicePtr, long offset, int sizeOfType, long sizeSource, long sizeDest) {
        CUDACommandQueue commandQueue = getCommandQueue(executionPlanId);
        return commandQueue.mapOnDeviceMemoryRegion(commandQueue.getCommandQueuePtr(), destDevicePtr, srcDevicePtr, offset, sizeOfType, sizeSource, sizeDest);
    }

    /* ---- CUDA Graph (stream capture) support ---- */

    /**
     * Begins capturing the device operations submitted to the execution-plan
     * stream into a CUDA graph. Subsequent host-to-device copies, kernel
     * launches and device-to-host copies are recorded as graph nodes.
     */
    public void beginExecutionGraphCapture(long executionPlanId) {
        getCommandQueue(executionPlanId).beginGraphCapture();
    }

    /**
     * Ends capture and instantiates the recorded graph into a replayable
     * CUgraphExec, returning its opaque handle. Any kernel stack-frame writes
     * deferred during capture are flushed here (synchronously, outside the
     * graph) so the device-side argument buffers are valid before the first
     * graph launch.
     */
    public long endExecutionGraphCaptureAndInstantiate(long executionPlanId) {
        long handle = getCommandQueue(executionPlanId).endGraphCaptureAndInstantiate();
        flushPendingKernelContextWrites(executionPlanId);
        return handle;
    }

    /**
     * Records a kernel stack-frame write to be performed once capture ends, or
     * returns false if the stream is not capturing (so the caller writes it
     * inline as usual).
     */
    public boolean deferKernelContextWriteIfCapturing(long executionPlanId, uk.ac.manchester.tornado.drivers.cuda.mm.CUDAKernelStackFrame kernelArgs) {
        if (!isStreamCapturing(executionPlanId)) {
            return false;
        }
        pendingKernelContextWrites.add(kernelArgs);
        return true;
    }

    private void flushPendingKernelContextWrites(long executionPlanId) {
        for (uk.ac.manchester.tornado.drivers.cuda.mm.CUDAKernelStackFrame kernelArgs : pendingKernelContextWrites) {
            kernelArgs.write(executionPlanId);
        }
        pendingKernelContextWrites.clear();
    }

    /**
     * Replays a previously instantiated CUDA graph on the execution-plan
     * stream. Host-to-device copy nodes re-read their (stable, off-heap) host
     * source pointers, so updated host data is picked up on every launch.
     */
    public int launchExecutionGraph(long executionPlanId, long executionGraphHandle) {
        getCommandQueue(executionPlanId).launchGraph(executionGraphHandle);
        return -1;
    }

    /**
     * @return whether the execution-plan stream is currently capturing.
     */
    public boolean isStreamCapturing(long executionPlanId) {
        return getCommandQueue(executionPlanId).isCapturing();
    }

    /**
     * Destroys an instantiated CUDA graph. Uses any live execution-plan queue
     * for the destroy call (cuGraphExecDestroy is independent of the stream).
     */
    public void destroyExecutionGraph(long executionGraphHandle) {
        Long anyPlanId = executionIDs.stream().findFirst().orElse(null);
        if (anyPlanId != null) {
            getCommandQueue(anyPlanId).destroyGraph(executionGraphHandle);
        }
    }

    /* ---- Native interop (external libraries, e.g. cuBLAS) ---- */

    /**
     * Raw CUstream handle of the execution-plan queue, for binding external
     * libraries (e.g., cublasSetStream) to TornadoVM's stream.
     */
    public long getNativeStream(long executionPlanId) {
        return getCommandQueue(executionPlanId).getNativeStream();
    }

    /**
     * Raw CUcontext handle of the execution-plan queue.
     */
    public long getNativeContext(long executionPlanId) {
        return getCommandQueue(executionPlanId).getNativeContext();
    }
}
