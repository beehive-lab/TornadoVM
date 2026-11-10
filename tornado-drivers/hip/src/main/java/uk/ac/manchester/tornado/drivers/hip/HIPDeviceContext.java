/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2021, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.drivers.hip;

import static uk.ac.manchester.tornado.drivers.hip.HIPCommandQueue.EMPTY_EVENT;
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
import uk.ac.manchester.tornado.drivers.hip.enums.HIPDeviceType;
import uk.ac.manchester.tornado.drivers.hip.mm.HIPMemoryManager;
import uk.ac.manchester.tornado.drivers.hip.power.HIPAMDPowerMetricHandler;
import uk.ac.manchester.tornado.drivers.hip.runtime.HIPBufferProvider;
import uk.ac.manchester.tornado.drivers.hip.runtime.HIPTornadoDevice;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPDriverAPI;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class HIPDeviceContext implements HIPDeviceContextInterface {

    private final HIPTargetDevice device;

    /**
     * Table to represent {@link uk.ac.manchester.tornado.api.TornadoExecutionPlan} -> {@link HIPCommandQueueTable}
     */
    private final Map<Long, HIPCommandQueueTable> commandQueueTable;
    private final HIPContext context;
    private final PowerMetric powerMetricHandler;
    private final HIPMemoryManager memoryManager;
    private final Map<Long, HIPEventPool> oclEventPool;
    private final TornadoBufferProvider bufferProvider;
    /** Execution plans whose device state has been torn down; see TornadoDeviceContext. */
    private final java.util.Set<Long> resetPlans = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<Long> executionIDs;

    /**
     * Ring of pinned host staging slots for staged transfers (one pinned block of
     * depth * chunk bytes, sliced into slots). 0 = unallocated (lazy). Shared across plans;
     * access is serialised by the synchronized staged-write method.
     */
    private long stagedRingPtr = 0L;
    private long stagedChunkSize;
    private int stagedRingDepth;
    /** Per-slot raw hipEvent_t handle of the slot's last enqueued DMA; 0 = slot never used. */
    private long[] stagedSlotEvents;

    /** Execution plans that requested intra-plan concurrency (multi-queue issue). */
    private final Set<Long> intraPlanConcurrencyPlans = Collections.synchronizedSet(new HashSet<>());

    /**
     * Whether large one-shot H2D uploads go through the pinned staging ring. Defaults to the
     * -Dtornado.staged.transfers property and is overridden by the plan-level withStagedTransfers()
     * API. Device-wide rather than per-plan: it also drives the host-pin decision in
     * {@code allocate()}, and pinning is a property of the buffer, not of the plan using it.
     */
    private volatile boolean stagedTransfersEnabled = TornadoOptions.ENABLE_STAGED_TRANSFERS;
    /** Guards the one-time warning emitted when concurrency is requested on hardware that cannot overlap. */
    private boolean warnedUnsupportedConcurrency;
    /** Per-plan single-instance role queues (H2D / D2H) used when intra-plan concurrency is enabled. */
    private final Map<Long, EnumMap<HIPStreamType, HIPCommandQueue>> roleQueueTable = new ConcurrentHashMap<>();

    /**
     * Number of HIP streams in the per-plan COMPUTE pool. DAG-independent kernels are round-robined
     * across these so they can execute concurrently (HIP serialises within a single stream). A single
     * COMPUTE queue would serialise every kernel, which is why kernel/kernel overlap needs a pool.
     * Configurable via {@code -Dtornado.hip.compute.streams}; defaults to 4.
     */
    private static final int COMPUTE_POOL_SIZE = Math.max(1, Integer.getInteger("tornado.hip.compute.streams", 4));
    /** Per-plan COMPUTE stream pool, grown lazily up to {@link #COMPUTE_POOL_SIZE}. */
    private final Map<Long, List<HIPCommandQueue>> computePool = new ConcurrentHashMap<>();
    /** Per-plan round-robin cursor for assigning kernels to COMPUTE-pool streams. */
    private final Map<Long, AtomicInteger> computeCursor = new ConcurrentHashMap<>();

    /**
     * Kernel stack-frame writes deferred during HIP graph capture. The stack
     * frame holds device buffer addresses and grid metadata that are constant
     * across graph replays, so it is written once outside the captured graph
     * rather than recorded as a graph node (which would pin a transient Java
     * heap array). Flushed when capture ends, before the first graph launch. Kept per execution plan,
     * so that plans capturing on the same device do not flush, or drop, each other's writes.
     */
    private final java.util.Map<Long, java.util.List<uk.ac.manchester.tornado.drivers.hip.mm.HIPKernelStackFrame>> pendingKernelContextWrites = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Map table to represent the compiled-code per execution plan. Each entry in the execution plan has its own
     * code cache. The code cache manages the compilation and the cache for each task within an execution plan.
     */
    private final Map<Long, HIPCodeCache> codeCache;

    public HIPDeviceContext(HIPTargetDevice device, HIPContext context) {
        this.device = device;
        this.context = context;
        this.memoryManager = new HIPMemoryManager(this);
        this.oclEventPool = new ConcurrentHashMap<>();
        this.bufferProvider = new HIPBufferProvider(this);
        this.commandQueueTable = new ConcurrentHashMap<>();
        this.device.setDeviceContext(this);
        this.executionIDs = Collections.synchronizedSet(new HashSet<>());
        this.powerMetricHandler = new HIPAMDPowerMetricHandler(this);
        codeCache = new ConcurrentHashMap<>();
    }

    public static String checkKernelName(String entryPoint) {
        if (entryPoint.contains("$")) {
            return entryPoint.replace("$", "_");
        }
        return entryPoint;
    }

    @Override
    public HIPTargetDevice getDevice() {
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
        return TornadoRuntimeProvider.getTornadoRuntime().getBackendIndex(HIPBackendImpl.class);
    }

    @Override
    public Set<Long> getRegisteredPlanIds() {
        return executionIDs;
    }

    /** HIP kernels cannot launch kernels, so there is no device-side launch queue to size. */
    @Override
    public void setDevicePendingLaunchCount(int count) {
    }

    @Override
    public HIPContext getPlatformContext() {
        return context;
    }

    @Override
    public HIPMemoryManager getMemoryManager() {
        return memoryManager;
    }

    @Override
    public TornadoBufferProvider getBufferProvider() {
        return bufferProvider;
    }

    @Override
    public void sync(long executionPlanId) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId);
        if (TornadoOptions.USE_SYNC_FLUSH) {
            commandQueue.flush();
        }
        // Join the plan's secondary queues (role + COMPUTE pool) into the default queue via
        // events, then host-sync ONCE: a hipStreamSynchronize per queue would serialise the
        // host against up to 7 queues per execution.
        joinRoleQueues(executionPlanId, commandQueue);
        commandQueue.finish();
    }

    @Override
    public long getDeviceId() {
        return device.getDevicePointer();
    }

    @Override
    public int enqueueBarrier(long executionPlanId) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId);
        joinRoleQueues(executionPlanId, commandQueue);
        long oclEvent = commandQueue.enqueueBarrier();
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return (commandQueue.getOpenclVersion() < 120) ? -1 : eventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_BARRIER, commandQueue);
    }

    @Override
    public int enqueueMarker(long executionPlanId) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId);
        joinRoleQueues(executionPlanId, commandQueue);
        long oclEvent = commandQueue.enqueueMarker();
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return commandQueue.getOpenclVersion() < 120 ? -1 : eventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_MARKER, commandQueue);
    }

    @Override
    public HIPProgram createProgramWithSource(byte[] source, long[] lengths) {
        return context.createProgramWithSource(source, lengths, this);
    }

    @Override
    public HIPProgram createProgramWithBinary(byte[] binary, long[] lengths) {
        return context.createProgramWithBinary(device.getDevicePointer(), binary, lengths, this);
    }

    @Override
    public HIPProgram createProgramWithIL(byte[] spirvBinary, long[] lengths) {
        return context.createProgramWithIL(spirvBinary, lengths, this);
    }

    public int enqueueNDRangeKernel(long executionPlanId, HIPKernel kernel, int dim, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize, int[] waitEvents) {
        HIPCommandQueue commandQueue = getComputeQueue(executionPlanId);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
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
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_INT, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_LONG, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_SHORT, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_FLOAT, commandQueue);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_DOUBLE, commandQueue);
    }

    private HIPCommandQueue getCommandQueue(long executionPlanId) {
        executionIDs.add(executionPlanId);
        if (!commandQueueTable.containsKey(executionPlanId)) {
            HIPTargetDevice device = context.devices().get(getDeviceIndex());
            HIPCommandQueueTable oclCommandQueueTable = new HIPCommandQueueTable();
            oclCommandQueueTable.get(device, context).nameStream(HIPStreamType.DEFAULT.name());
            commandQueueTable.put(executionPlanId, oclCommandQueueTable);
        }
        return commandQueueTable.get(executionPlanId).get(context.devices().get(getDeviceIndex()), context);
    }

    private HIPEventPool getHIPEventPool(long executionPlanId) {
        if (!oclEventPool.containsKey(executionPlanId)) {
            HIPEventPool eventPool = new HIPEventPool(EVENT_WINDOW);
            oclEventPool.put(executionPlanId, eventPool);
        }
        return oclEventPool.get(executionPlanId);
    }

    /**
     * Records, per execution plan, whether intra-plan concurrency (multi-queue issue) is enabled.
     * Pushed by the runtime before issuing a plan's bytecodes.
     */
    public void setIntraPlanConcurrency(long executionPlanId, boolean enabled) {
        // Piggyback on this per-execution hook (the interpreter calls it before issuing any
        // bytecode) to push the CURRENT profiler state to the native layer: the profiler can be
        // enabled dynamically per plan (withProfiler), and per-op timing events are only worth
        // creating while it is active.
        HIPCommandQueue.nativeEnableTiming(TornadoOptions.isProfilerEnabled());
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

    @Override
    public void setStagedTransfers(boolean enabled) {
        stagedTransfersEnabled = enabled;
    }

    @Override
    public boolean isStagedTransfersEnabled() {
        return stagedTransfersEnabled;
    }

    /**
     * Intra-plan concurrency (multi-queue issue) is only beneficial when the device can actually overlap
     * work: either run kernels concurrently or overlap copies with compute (async copy engines). On hardware
     * that supports neither, role queues add cross-queue event overhead for no gain, so we fall back to
     * single-queue execution.
     */
    private boolean deviceSupportsIntraPlanConcurrency() {
        return device.supportsConcurrentKernels() || device.getAsyncEngineCount() >= 1;
    }

    /**
     * Multi-queue issue is disabled while the plan's stream is capturing into a HIP graph:
     * capture records a single stream, and touching other streams would invalidate it.
     */
    public boolean isMultiStreamEnabled(long executionPlanId) {
        return intraPlanConcurrencyPlans.contains(executionPlanId) && !isStreamCapturing(executionPlanId);
    }

    /**
     * Returns the queue that should execute an operation of the given role: a dedicated per-plan
     * role queue under intra-plan concurrency, the plan's default queue otherwise. Cross-queue
     * ordering is preserved by serialising the dependency wait lists into hipEvent_t waits (see
     * {@link HIPEventPool#serialiseEvents(int[], HIPCommandQueue, boolean)}).
     */
    private HIPCommandQueue getCommandQueue(long executionPlanId, HIPStreamType streamType) {
        if (streamType == HIPStreamType.DEFAULT || !isMultiStreamEnabled(executionPlanId)) {
            return getCommandQueue(executionPlanId);
        }
        HIPCommandQueue queue = roleQueueTable.computeIfAbsent(executionPlanId, id -> new EnumMap<>(HIPStreamType.class)).computeIfAbsent(streamType, type -> createQueue(type.name()));
        queue.markDirty();
        return queue;
    }

    /**
     * Returns the COMPUTE queue a kernel should run on: round-robined across the per-plan COMPUTE pool
     * under intra-plan concurrency so DAG-independent kernels overlap, or the plan's default queue
     * otherwise. During HIP-graph capture {@link #isMultiStreamEnabled} is already false, so capture
     * records on the single default queue.
     */
    private HIPCommandQueue getComputeQueue(long executionPlanId) {
        if (!isMultiStreamEnabled(executionPlanId)) {
            return getCommandQueue(executionPlanId);
        }
        int index = nextComputeIndex(executionPlanId);
        List<HIPCommandQueue> pool = computePool.computeIfAbsent(executionPlanId, id -> new ArrayList<>());
        synchronized (pool) {
            while (pool.size() <= index) {
                pool.add(createQueue(pool.isEmpty() ? "COMPUTE" : "COMPUTE_" + pool.size()));
            }
            HIPCommandQueue queue = pool.get(index);
            queue.markDirty();
            return queue;
        }
    }

    /** Round-robin index of the next COMPUTE-pool stream for this plan (0..COMPUTE_POOL_SIZE-1). */
    private int nextComputeIndex(long executionPlanId) {
        AtomicInteger cursor = computeCursor.computeIfAbsent(executionPlanId, id -> new AtomicInteger());
        return Math.floorMod(cursor.getAndIncrement(), COMPUTE_POOL_SIZE);
    }

    /**
     * The plan's secondary queues (role + COMPUTE pool) that received work since the last join,
     * clearing their dirty flags. Untouched queues are excluded: their last marker is already
     * ordered into the default queue by the previous join, so re-joining them is pure overhead.
     */
    private List<HIPCommandQueue> drainDirtySecondaryQueues(long executionPlanId) {
        List<HIPCommandQueue> dirty = new ArrayList<>();
        EnumMap<HIPStreamType, HIPCommandQueue> roles = roleQueueTable.get(executionPlanId);
        if (roles != null) {
            for (HIPCommandQueue queue : roles.values()) {
                if (queue.pollDirty()) {
                    dirty.add(queue);
                }
            }
        }
        List<HIPCommandQueue> pool = computePool.get(executionPlanId);
        if (pool != null) {
            synchronized (pool) {
                for (HIPCommandQueue queue : pool) {
                    if (queue.pollDirty()) {
                        dirty.add(queue);
                    }
                }
            }
        }
        return dirty;
    }

    private HIPCommandQueue createQueue(String nvtxName) {
        HIPTargetDevice targetDevice = context.devices().get(getDeviceIndex());
        try {
            long queuePtr = context.clCreateCommandQueue(context.getContextId(getDeviceIndex()), targetDevice.getDevicePointer(), context.getProperties());
            HIPCommandQueue queue = new HIPCommandQueue(queuePtr, context.getProperties(), targetDevice.deviceVersion());
            queue.nameStream(nvtxName);
            return queue;
        } catch (uk.ac.manchester.tornado.drivers.hip.exceptions.HIPException e) {
            throw new uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException(e);
        }
    }

    /**
     * Staged H2D transfer through a ring of pinned host slots. The transfer is split into
     * chunks; each chunk is memcpy'd (multi-threaded) into a pinned slot and uploaded with an
     * async copy, so the staging of chunk i+1 overlaps the DMA of chunk i. Slot reuse is gated
     * on the slot's previous DMA event. Unlike the direct path, the source segment does not
     * need to be page-locked - the pinned slots are what makes the DMA asynchronous. Returns
     * the pool id of the LAST chunk's event (ordering-equivalent to the single-copy event of
     * the direct path, since all chunks are issued in-order on the same queue).
     */
    public synchronized int enqueueStagedWriteBuffer(long executionPlanId, long bufferId, long deviceOffset, long bytes, long hostPointer, long hostOffset, int[] waitEvents) {
        if (isStreamCapturing(executionPlanId)) {
            // Host-side slot waits are illegal during HIP graph capture; use the direct path.
            return enqueueWriteBuffer(executionPlanId, bufferId, deviceOffset, bytes, hostPointer, hostOffset, waitEvents);
        }
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        ensureStagedRing();
        long[] firstChunkWaits = eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId)) ? eventPool.waitEventsBuffer : null;
        long offset = 0;
        int chunkIndex = 0;
        while (offset < bytes) {
            int slot = chunkIndex % stagedRingDepth;
            long chunkBytes = Math.min(stagedChunkSize, bytes - offset);
            if (stagedSlotEvents[slot] != 0) {
                // Wait (host-side) until the slot's previous upload has drained before refilling
                // it, then destroy the guard: slot-guard events are RING-owned. They must NOT be
                // registered in the per-plan event pool - the pool destroys its events on slot
                // recycling and on plan reset, while this ring is device-wide and outlives plans,
                // so a pooled handle kept here would dangle (use-after-free in the driver).
                try {
                    HIPEvent.clWaitForEvents(new long[] { 1, stagedSlotEvents[slot] });
                    HIPEvent.clReleaseEvent(stagedSlotEvents[slot]);
                } catch (uk.ac.manchester.tornado.drivers.hip.exceptions.HIPException e) {
                    throw new uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException(e.getMessage());
                }
                stagedSlotEvents[slot] = 0;
            }
            long slotPtr = stagedRingPtr + (long) slot * stagedChunkSize;
            fillSlot(slotPtr, hostPointer + hostOffset + offset, chunkBytes);
            stagedSlotEvents[slot] = commandQueue.enqueueWrite(bufferId, HIPBlocking.FALSE, deviceOffset + offset, chunkBytes, slotPtr, 0, chunkIndex == 0 ? firstChunkWaits : null);
            offset += chunkBytes;
            chunkIndex++;
        }
        // The dependency event handed back to the caller is a pool-registered marker enqueued
        // AFTER all chunks (the queue is in-order, so it is ordering-equivalent to the last
        // chunk's event). The pool owns this marker; the ring keeps owning the chunk events.
        return eventPool.registerEvent(commandQueue.enqueueMarker(), EventDescriptor.DESC_WRITE_SEGMENT, commandQueue);
    }

    private void ensureStagedRing() {
        if (stagedRingPtr == 0L) {
            stagedChunkSize = TornadoOptions.STAGED_TRANSFER_CHUNK_SIZE;
            stagedRingDepth = Math.max(2, TornadoOptions.STAGED_TRANSFER_RING_DEPTH);
            // Pinned host memory belongs to the context current at allocation time.
            context.makeCurrent(getDeviceIndex());
            stagedRingPtr = HIPCommandQueue.hipHostMalloc(stagedChunkSize * stagedRingDepth);
            if (stagedRingPtr == 0L) {
                throw new uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException("[HIP] hipHostMalloc failed: could not allocate " + (stagedChunkSize * stagedRingDepth) + " bytes of pinned host memory for the staging ring");
            }
            stagedSlotEvents = new long[stagedRingDepth];
        }
    }

    /**
     * Frees the pinned staging ring. Called at device teardown, matching the ring's scope: the ring is
     * device-wide and shared across plans, so it must NOT be released from the per-plan reset - that
     * would pull it out from under a concurrent plan.
     * <p>
     * Each in-flight slot DMA reads FROM ring memory, so every outstanding slot event is drained
     * before the pinned block is released; freeing it under a live DMA is undefined behaviour. The
     * ring is left unallocated, so a subsequent staged transfer lazily rebuilds it.
     */
    @Override
    public synchronized void releaseStagedRing() {
        if (stagedRingPtr == 0L) {
            return;
        }
        for (int slot = 0; slot < stagedSlotEvents.length; slot++) {
            if (stagedSlotEvents[slot] != 0) {
                try {
                    HIPEvent.clWaitForEvents(new long[] { 1, stagedSlotEvents[slot] });
                    HIPEvent.clReleaseEvent(stagedSlotEvents[slot]);
                } catch (uk.ac.manchester.tornado.drivers.hip.exceptions.HIPException e) {
                    throw new uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException(e.getMessage());
                }
                stagedSlotEvents[slot] = 0;
            }
        }
        makeCurrent();
        HIPCommandQueue.hipHostFree(stagedRingPtr);
        stagedRingPtr = 0L;
        stagedSlotEvents = null;
    }

    /**
     * Fills a pinned staging slot from the source. A single thread's memcpy is slower than the
     * PCIe DMA it feeds, which would make the staged path fill-bound; splitting the copy across
     * threads lifts the fill above the DMA rate so the transfer stays DMA-bound.
     */
    private void fillSlot(long dstPtr, long srcPtr, long bytes) {
        int threads = TornadoOptions.STAGED_TRANSFER_FILL_THREADS;
        long minPerThread = 1 << 21;
        if (threads <= 1 || bytes < threads * minPerThread) {
            HIPCommandQueue.memcpyHostToHost(dstPtr, srcPtr, bytes);
            return;
        }
        long subSize = bytes / threads;
        java.util.stream.IntStream.range(0, threads).parallel().forEach(t -> {
            long subOffset = t * subSize;
            long subBytes = (t == threads - 1) ? bytes - subOffset : subSize;
            HIPCommandQueue.memcpyHostToHost(dstPtr + subOffset, srcPtr + subOffset, subBytes);
        });
    }

    /**
     * Joins the plan's dirty (touched since the last join) secondary queues into its default
     * queue: records a marker event on each and makes the default queue wait on all of them,
     * so a subsequent marker/barrier on the default queue covers the whole plan.
     */
    private void joinRoleQueues(long executionPlanId, HIPCommandQueue defaultQueue) {
        List<HIPCommandQueue> queues = drainDirtySecondaryQueues(executionPlanId);
        if (queues.isEmpty()) {
            return;
        }
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        long[] buffer = new long[queues.size() + 1];
        int index = 0;
        for (HIPCommandQueue queue : queues) {
            long event = queue.enqueueMarker();
            eventPool.registerEvent(event, EventDescriptor.DESC_SYNC_MARKER, queue);
            buffer[++index] = event;
        }
        buffer[0] = index;
        defaultQueue.enqueueBarrier(buffer);
    }

    public int enqueueWriteBuffer(long executionPlanId, long bufferId, long deviceOffset, long bytes, long hostPointer, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        long eventId = commandQueue.enqueueWrite(bufferId, HIPBlocking.FALSE, deviceOffset, bytes, hostPointer, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null);
        return eventPool.registerEvent(eventId, EventDescriptor.DESC_WRITE_SEGMENT, commandQueue);
    }

    /*
     * ASync reads from device
     *
     */
    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_INT, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_LONG, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_FLOAT, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_DOUBLE, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.FALSE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_SHORT, commandQueue);
    }

    public int enqueueReadBuffer(long executionPlanId, long bufferId, long offset, long bytes, long hostPointer, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.FALSE, offset, bytes, hostPointer, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_SEGMENT, commandQueue);
    }

    /*
     * Synchronous writes to device
     */
    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_BYTE, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_INT, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_LONG, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_SHORT, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_FLOAT, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_DOUBLE, commandQueue);
    }

    public void writeBuffer(long executionPlanId, long bufferId, long offset, long bytes, long hostPointer, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_H2D);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        eventPool.registerEvent(commandQueue.enqueueWrite(bufferId, HIPBlocking.TRUE, offset, bytes, hostPointer, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_WRITE_SEGMENT, commandQueue);
    }

    /*
     * Synchronous reads from device
     */
    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_BYTE, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_INT, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_LONG, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_FLOAT, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_DOUBLE, commandQueue);

    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, HIPBlocking.TRUE, offset, bytes, array, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_SHORT, commandQueue);
    }

    public int readBuffer(long executionPlanId, long bufferId, long offset, long bytes, long hostPointer, long hostOffset, int[] waitEvents) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId, HIPStreamType.DATA_TRANSFER_D2H);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        // non-blocking/async copy-out for off-heap (and therefore pinned) buffers when we go multi-stream
        boolean blocking = (isMultiStreamEnabled(executionPlanId) && waitEvents != null) ? HIPBlocking.FALSE : HIPBlocking.TRUE;
        return eventPool.registerEvent(commandQueue.enqueueRead(bufferId, blocking, offset, bytes, hostPointer, hostOffset, eventPool.serialiseEvents(waitEvents, commandQueue, isMultiStreamEnabled(executionPlanId))
                ? eventPool.waitEventsBuffer
                : null), EventDescriptor.DESC_READ_SEGMENT, commandQueue);
    }

    @Override
    public int enqueueBarrier(long executionPlanId, int[] events) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId);
        joinRoleQueues(executionPlanId, commandQueue);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        long oclEvent = commandQueue.enqueueBarrier(eventPool.serialiseEvents(events, commandQueue, isMultiStreamEnabled(executionPlanId)) ? eventPool.waitEventsBuffer : null);
        return commandQueue.getOpenclVersion() < 120 ? -1 : eventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_BARRIER, commandQueue);
    }

    @Override
    public int enqueueMarker(long executionPlanId, int[] events) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId);
        joinRoleQueues(executionPlanId, commandQueue);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        long oclEvent = commandQueue.enqueueMarker(eventPool.serialiseEvents(events, commandQueue, isMultiStreamEnabled(executionPlanId)) ? eventPool.waitEventsBuffer : null);
        return commandQueue.getOpenclVersion() < 120 ? -1 : eventPool.registerEvent(oclEvent, EventDescriptor.DESC_SYNC_MARKER, commandQueue);
    }

    @Override
    public void reset(long executionPlanId) {
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        eventPool.reset();
        EnumMap<HIPStreamType, HIPCommandQueue> queues = roleQueueTable.remove(executionPlanId);
        if (queues != null) {
            for (HIPCommandQueue roleQueue : queues.values()) {
                roleQueue.cleanup();
            }
        }
        List<HIPCommandQueue> pool = computePool.remove(executionPlanId);
        if (pool != null) {
            for (HIPCommandQueue computeQueue : pool) {
                computeQueue.cleanup();
            }
        }
        computeCursor.remove(executionPlanId);
        intraPlanConcurrencyPlans.remove(executionPlanId);
        oclEventPool.remove(executionPlanId);
        HIPCommandQueueTable table = commandQueueTable.get(executionPlanId);
        if (table != null) {
            table.cleanup(device);
            if (table.size() == 0) {
                commandQueueTable.remove(executionPlanId);
            }
            executionIDs.remove(executionPlanId);
        }
        getMemoryManager().releaseKernelStackFrame(executionPlanId);
        HIPCodeCache oclCodeCache = getHIPCodeCache(executionPlanId);
        oclCodeCache.reset();
        codeCache.remove(executionPlanId);
        // Plan teardown may bulk-release device buffers without the per-buffer free hook
        // running, which would leave host pins behind - the stale-pin hazard. Drain all
        // owned pins here once no execution plan is left. While other plans are alive, drop
        // only the pins no live buffer holds: a live plan's captured HIP graphs copy from the
        // pinned host buffers it holds, and unregistering them under it crashes its next launch.
        if (executionIDs.isEmpty()) {
            context.getPinnedMemoryRegistry().unpinAll();
        } else {
            context.getPinnedMemoryRegistry().unpinUnheld();
        }
        resetPlans.add(executionPlanId);
    }

    public HIPTornadoDevice toDevice() {
        return new HIPTornadoDevice(context.getPlatformIndex(), device.getIndex());
    }

    public String getId() {
        return String.format("opencl-%d-%d", context.getPlatformIndex(), device.getIndex());
    }

    public void dumpEvents() {
        Set<Long> executionPlanIds = oclEventPool.keySet();
        for (Long id : executionPlanIds) {
            HIPEventPool eventPool = getHIPEventPool(id);
            List<HIPEvent> events = eventPool.getEvents();
            final String deviceName = "Opencl-" + context.getPlatformIndex() + "-" + device.getIndex();
            System.out.printf("Found %d events on device %s:\n", events.size(), deviceName);
            if (events.isEmpty()) {
                return;
            }
            events.sort(Comparator.comparingLong(HIPEvent::getCLSubmitTime).thenComparingLong(HIPEvent::getCLStartTime));
            long base = events.getFirst().getCLSubmitTime();
            System.out.println("event: device,type,info,queued,submitted,start,end,status");
            events.forEach(event -> System.out.printf("event: %s,%s,%s,0x%x,%d,%d,%d,%s\n", deviceName, event.getName(), event.getOclEventID(), event.getCLQueuedTime() - base, event
                    .getCLSubmitTime() - base, event.getCLStartTime() - base, event.getCLEndTime() - base, event.getStatus()));
        }
    }

    @Override
    public boolean wasReset(long executionPlanId) {
        return resetPlans.contains(executionPlanId);
    }

    @Override
    public void setResetToFalse(long executionPlanId) {
        resetPlans.remove(executionPlanId);
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
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId);
        HIPEventPool eventPool = getHIPEventPool(executionPlanId);
        return new HIPEvent(eventPool.getDescriptor(event).getNameDescription(), commandQueue, event, eventPool.getHIPEvent(event));
    }

    @Override
    public void flush(long executionPlanId) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId);
        commandQueue.flush();
    }

    @Override
    public void flushEvents(long executionPlanId) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId);
        commandQueue.flushEvents();
    }

    private HIPCodeCache getHIPCodeCache(long executionPlanId) {
        if (!codeCache.containsKey(executionPlanId)) {
            codeCache.put(executionPlanId, new HIPCodeCache(this));
        }
        return codeCache.get(executionPlanId);
    }

    @Override
    public boolean isKernelAvailable(long executionPlanId) {
        HIPCodeCache oclCodeCache = getHIPCodeCache(executionPlanId);
        return oclCodeCache.isKernelAvailable();
    }

    @Override
    public HIPInstalledCode installCode(long executionPlanId, TaskDataContext meta, String id, String entryPoint, byte[] code) {
        entryPoint = checkKernelName(entryPoint);
        HIPCodeCache oclCodeCache = getHIPCodeCache(executionPlanId);
        return oclCodeCache.installSource(meta, id, entryPoint, code);
    }

    @Override
    public boolean isCached(long executionPlanId, String id, String entryPoint) {
        entryPoint = checkKernelName(entryPoint);
        HIPCodeCache oclCodeCache = getHIPCodeCache(executionPlanId);
        return oclCodeCache.isCached(id + "-" + entryPoint);
    }

    @Override
    public boolean isCached(long executionPlanId, String methodName, SchedulableTask task) {
        methodName = checkKernelName(methodName);
        HIPCodeCache oclCodeCache = getHIPCodeCache(executionPlanId);
        return oclCodeCache.isCached(task.getId() + "-" + methodName);
    }

    @Override
    public HIPInstalledCode getInstalledCode(long executionPlanId, String id, String entryPoint) {
        entryPoint = checkKernelName(entryPoint);
        HIPCodeCache oclCodeCache = getHIPCodeCache(executionPlanId);
        return oclCodeCache.getInstalledCode(id, entryPoint);
    }

    @Override
    public HIPCodeCache getCodeCache(long executionPlanId) {
        return getHIPCodeCache(executionPlanId);
    }

    public long mapOnDeviceMemoryRegion(long executionPlanId, long destDevicePtr, long srcDevicePtr, long offset, int sizeOfType, long sizeSource, long sizeDest) {
        HIPCommandQueue commandQueue = getCommandQueue(executionPlanId);
        return commandQueue.mapOnDeviceMemoryRegion(commandQueue.getCommandQueuePtr(), destDevicePtr, srcDevicePtr, offset, sizeOfType, sizeSource, sizeDest);
    }

    /* ---- HIP Graph (stream capture) support ---- */

    /**
     * Begins capturing the device operations submitted to the execution-plan
     * stream into a HIP graph. Subsequent host-to-device copies, kernel
     * launches and device-to-host copies are recorded as graph nodes.
     */
    public void beginExecutionGraphCapture(long executionPlanId) {
        getCommandQueue(executionPlanId).beginGraphCapture();
    }

    /**
     * Ends capture and instantiates the recorded graph into a replayable
     * hipGraphExec_t, returning its opaque handle. Any kernel stack-frame writes
     * deferred during capture are flushed here (synchronously, outside the
     * graph) so the device-side argument buffers are valid before the first
     * graph launch.
     */
    public long endExecutionGraphCaptureAndInstantiate(long executionPlanId) {
        long handle = getCommandQueue(executionPlanId).endGraphCaptureAndInstantiate();
        try {
            flushPendingKernelContextWrites(executionPlanId);
        } catch (RuntimeException | Error e) {
            // The graph is not handed back to the caller, so destroy it here or it leaks.
            getCommandQueue(executionPlanId).destroyGraph(handle);
            throw e;
        }
        return handle;
    }

    /**
     * Records a kernel stack-frame write to be performed once capture ends, or
     * returns false if the stream is not capturing (so the caller writes it
     * inline as usual).
     */
    public boolean deferKernelContextWriteIfCapturing(long executionPlanId, uk.ac.manchester.tornado.drivers.hip.mm.HIPKernelStackFrame kernelArgs) {
        if (!isStreamCapturing(executionPlanId)) {
            return false;
        }
        pendingKernelContextWrites.computeIfAbsent(executionPlanId, id -> new java.util.ArrayList<>()).add(kernelArgs);
        return true;
    }

    private void flushPendingKernelContextWrites(long executionPlanId) {
        java.util.List<uk.ac.manchester.tornado.drivers.hip.mm.HIPKernelStackFrame> pending = pendingKernelContextWrites.remove(executionPlanId);
        if (pending == null) {
            return;
        }
        for (uk.ac.manchester.tornado.drivers.hip.mm.HIPKernelStackFrame kernelArgs : pending) {
            kernelArgs.write(executionPlanId);
        }
    }

    /**
     * Abandons the capture of a plan whose execution failed inside its capture region: the stream
     * stops capturing and the writes deferred for that capture are dropped, so neither leaks into
     * the next capture on this device.
     */
    @Override
    public void abortExecutionGraphCapture(long executionPlanId) {
        pendingKernelContextWrites.remove(executionPlanId);
        getCommandQueue(executionPlanId).abortGraphCapture();
    }

    /**
     * Replays a previously instantiated HIP graph on the execution-plan
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
     * Destroys an instantiated HIP graph. hipGraphExecDestroy needs no stream,
     * so this does not go through an execution-plan queue: when a plan of several
     * graphs is freed, the first graph's free resets the plan's queues, and the
     * later graphs would otherwise find no queue and leak their HIP graphs (and
     * anything they hold, such as an NCCL communicator that then cannot be
     * destroyed).
     */
    public void destroyExecutionGraph(long executionGraphHandle) {
        if (executionGraphHandle != 0) {
            HIPDriverAPI.hipGraphExecDestroy(executionGraphHandle);
        }
    }

    /* ---- Native interop (external libraries, e.g. hipBLAS) ---- */

    /**
     * Raw hipStream_t handle of the execution-plan queue, for binding external
     * libraries (e.g., cublasSetStream) to TornadoVM's stream.
     */
    public long getNativeStream(long executionPlanId) {
        return getCommandQueue(executionPlanId).getNativeStream();
    }

    @Override
    public void warmUp() {
        context.warmUp(getDeviceIndex());
    }

    /** Makes this device's context current on the calling thread. */
    @Override
    public void makeCurrent() {
        context.makeCurrent(getDeviceIndex());
    }

    /**
     * Raw context handle of the execution-plan queue.
     */
    public long getNativeContext(long executionPlanId) {
        return getCommandQueue(executionPlanId).getNativeContext();
    }
}
