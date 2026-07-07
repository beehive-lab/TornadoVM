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
import java.util.ArrayList;
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
    private final PTXStreamPool streamPool;
    /** Execution plans (by id) for which intra-plan concurrency / multi-stream is enabled. Per-plan, set by the runtime. */
    private final Set<Long> intraPlanConcurrencyPlans = ConcurrentHashMap.newKeySet();
    /** Guards the one-time warning when concurrency is requested on hardware that cannot overlap work. */
    private boolean warnedUnsupportedConcurrency;
    private boolean wasReset;
    private final Set<Long> executionIDs;
    private PTXKernelStackFrame pendingKernelContextWrite;

    /**
     * Map table to represent the compiled-code per execution plan. Each entry in the execution plan has its own
     * code cache. The code cache manages the compilation and the cache for each task within an execution plan.
     */
    private final Map<Long, PTXCodeCache> codeCache;

    public PTXDeviceContext(PTXDevice device) {
        this.device = device;
        streamPool = new PTXStreamPool();
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

    /**
     * Records, per execution plan, whether intra-plan concurrency (multi-stream) is enabled.
     * Pushed by the runtime ({@code TornadoVMInterpreter}) before issuing a plan's bytecodes.
     */
    public void setIntraPlanConcurrency(long executionPlanId, boolean enabled) {
        if (enabled && deviceSupportsIntraPlanConcurrency()) {
            intraPlanConcurrencyPlans.add(executionPlanId);
        } else {
            if (enabled && !warnedUnsupportedConcurrency) {
                System.err.printf("Warning: intra-plan concurrency requested but device '%s' cannot overlap work "
                        + "(asyncEngineCount=%d, concurrentKernels=%b); falling back to single-stream execution.%n",
                        device.getDeviceName(), device.getAsyncEngineCount(), device.supportsConcurrentKernels());
                warnedUnsupportedConcurrency = true;
            }
            intraPlanConcurrencyPlans.remove(executionPlanId);
        }
    }

    /**
     * Intra-plan concurrency (multi-stream) is only beneficial when the device can actually overlap
     * work: either run kernels concurrently (COMPUTE pool) or overlap copies with compute (async copy
     * engines). On hardware that supports neither, role streams add event overhead for no gain, so we
     * fall back to single-stream execution.
     */
    private boolean deviceSupportsIntraPlanConcurrency() {
        return device.supportsConcurrentKernels() || device.getAsyncEngineCount() >= 1;
    }

    /**
     * The hardware ceiling on the number of concurrent copy operations, and therefore on the number of
     * useful role-based transfer streams: {@code asyncEngineCount} copies can run at once (typically 2:
     * one H2D + one D2H). Same-direction copy streams beyond this cannot run in parallel - they serialise
     * on the single copy engine for that direction - so the transfer layer must not pool them.
     */
    public int maxConcurrentCopyStreams() {
        return Math.max(1, device.getAsyncEngineCount());
    }

    private boolean isMultiStreamEnabled(long executionPlanId) {
        return intraPlanConcurrencyPlans.contains(executionPlanId);
    }

    public PTXTornadoDevice toDevice() {
        return new PTXTornadoDevice(device.getDeviceIndex());
    }

    public TornadoInstalledCode installCode(TaskDataContext taskMeta, long executionPlanId, PTXCompilationResult result, String resolvedMethodName) {
        PTXCodeCache ptxCodeCache = getPTXCodeCache(executionPlanId);
        return ptxCodeCache.installSource(taskMeta, result.getName(), result.getTargetCode(), resolvedMethodName, result.metaData().isPrintKernelEnabled());
    }

    public TornadoInstalledCode installCode(TaskDataContext taskMeta, long executionPlanId, String name, byte[] code, String resolvedMethodName, boolean printKernel) {
        PTXCodeCache ptxCodeCache = getPTXCodeCache(executionPlanId);
        return ptxCodeCache.installSource(taskMeta, name, code, resolvedMethodName, printKernel);
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

    private PTXEventRegistry getEventRegistry(long executionPlanId) {
        return streamPool.eventRegistry(executionPlanId);
    }

    private PTXStream getStreamIfExists(long executionPlanId, PTXStreamType type) {
        return streamPool.getIfExists(executionPlanId, type);
    }

    public Event resolveEvent(long executionPlanId, int event) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXEventRegistry registry = getEventRegistry(executionPlanId);
            PTXEventRegistry.PTXEventEntry entry = registry.resolve(event);
            if (entry != null) {
                PTXStream stream = getStream(executionPlanId, entry.streamType(), entry.streamIndex());
                return stream.resolveEvent(entry.localEventId());
            }
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.resolveEvent(event);
    }

    /**
     * Fine-grained cross-stream synchronization.
     *
     * <p>For each global event ID in {@code waitEvents}, resolves it through the
     * {@link PTXEventRegistry} to find the source stream and local event, then calls
     * {@code cuStreamWaitEvent} to make work submitted on {@code targetStream} to
     * wait upon event's completion before starting execution WITHOUT blocking the
     * host, in our case the TornadoVM Interpreter thread.
     *
     * <p>This provides event-level granularity: COMPUTE waits only on the specific
     * H2D transfers it depends on, D2H waits only on the specific COMPUTE operations
     * it depends on. Same-stream events are handled by CUDA's in-order guarantee.
     *
     * @param executionPlanId the execution plan context
     * @param waitEvents array of global event IDs (from the interpreter's dependency tracking)
     * @param targetStream the stream that should wait for the source events
     */
    private void resolveAndWaitCrossStream(long executionPlanId, int[] waitEvents, PTXStream targetStream) {
        if (waitEvents == null || !isMultiStreamEnabled(executionPlanId)) return;
        PTXEventRegistry registry = getEventRegistry(executionPlanId);
        for (int globalEventId : waitEvents) {
            if (globalEventId == -1) continue;
            PTXEventRegistry.PTXEventEntry entry = registry.resolve(globalEventId);
            if (entry == null) continue;
            // Skip same-stream events - CUDA's in-order execution already guarantees ordering
            // within a stream. During graph capture, cuStreamWaitEvent(S, capturedEventOnS)
            // returns CUDA_ERROR_STREAM_CAPTURE_ISOLATION (905) and invalidates the capture.
            // "Same stream" must compare the pool index too: two distinct COMPUTE streams share
            // the COMPUTE role but are different streams and DO need a cross-stream wait.
            if (entry.streamType() == targetStream.getStreamType() && entry.streamIndex() == targetStream.getStreamIndex()) continue;
            PTXStream sourceStream = getStream(executionPlanId, entry.streamType(), entry.streamIndex());
            PTXEvent event = sourceStream.getEventPool().getEvent(entry.localEventId());
            if (event != null) {
                event.waitOnStream(targetStream.getStreamHandle());
            }
        }
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

    /**
     * Enqueues a full barrier that waits for all in-flight GPU work to complete.
     *
     * <p>In single-stream mode, issues {@code cuStreamSynchronize} on the default stream
     * (CPU-blocking). In multi-stream mode, issues {@code cuStreamSynchronize} on every
     * active stream (H2D, COMPUTE, D2H).
     *
     * <p>Not called by the main interpreter path - the {@code BARRIER} bytecode routes
     * through {@link #enqueueMarker(long, int[])} instead. Reachable from legacy
     * {@code PTXMultiDimArrayWrapper} and as a null-events fallback from
     * {@link #enqueueBarrier(long, int[])}.
     *
     * @param executionPlanId the execution plan context
     * @return a local event ID in single-stream mode, or {@code -1} in multi-stream mode
     */
    public int enqueueBarrier(long executionPlanId) {
        if (isMultiStreamEnabled(executionPlanId)) {
            // Sync all active streams (every role plus the whole COMPUTE pool)
            for (PTXStream stream : streamPool.activeStreams(executionPlanId)) {
                if (stream.getStreamType() == PTXStreamType.DEFAULT) continue;
                stream.enqueueBarrier(executionPlanId);
            }
            return -1;
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueBarrier(executionPlanId);
    }

    /**
     * Enqueues a barrier with fine-grained dependency resolution.
     *
     * <p>In multi-stream mode, resolves each global event ID in {@code events} through
     * the {@link PTXEventRegistry} and inserts a {@code cuStreamWaitEvent} on every active
     * stream for each dependency, so each stream only waits on the specific operations
     * it depends on rather than all prior work. If {@code events} is null, delegates to
     * {@link #enqueueBarrier(long)}.
     *
     * <p>In single-stream mode, forwards {@code events} (local pool IDs) directly to
     * {@link PTXStream#enqueueBarrier(long, int[])} for CPU-side synchronisation.
     *
     * @param executionPlanId the execution plan context
     * @param events array of global event IDs to wait on, or {@code null} for a full barrier
     * @return a local event ID in single-stream mode, or {@code -1} in multi-stream mode
     */
    public int enqueueBarrier(long executionPlanId, int[] events) {
        if (isMultiStreamEnabled(executionPlanId)) {
            if (events == null) {
                return enqueueBarrier(executionPlanId);
            }
            // Use fine-grained sync: each stream waits only on cross-stream events
            for (PTXStream stream : streamPool.activeStreams(executionPlanId)) {
                if (stream.getStreamType() == PTXStreamType.DEFAULT) continue;
                resolveAndWaitCrossStream(executionPlanId, events, stream);
            }
            return -1;
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueBarrier(executionPlanId, events);
    }

    /**
     * End-of-iteration full sync: waits for all in-flight GPU work and resets the
     * {@link PTXEventRegistry}.
     *
     * <p>Called by the interpreter after all bytecodes have been dispatched
     * (when {@code VM_USE_DEPS=true}) to ensure the iteration is fully complete before
     * returning the execution result. Also used as a null-events fallback from
     * {@link #enqueueMarker(long, int[])}.
     *
     * <p>In single-stream mode, issues {@code cuStreamSynchronize} on the default stream.
     * In multi-stream mode, issues {@code cuStreamSynchronize} on every active stream
     * (H2D, COMPUTE, D2H) and then resets the {@link PTXEventRegistry}, allowing global
     * event IDs to be reused in the next iteration.
     *
     * @param executionPlanId the execution plan context
     * @return a local event ID in single-stream mode, or {@code -1} in multi-stream mode
     */
    public int enqueueMarker(long executionPlanId) {
        // Since streams are always in-order in CUDA there is no difference
        // between marker and barrier
        if (isMultiStreamEnabled(executionPlanId)) {
            for (PTXStream stream : streamPool.activeStreams(executionPlanId)) {
                if (stream.getStreamType() == PTXStreamType.DEFAULT) continue;
                stream.enqueueBarrier(executionPlanId);
            }
            // Reset registry after full sync - all events are complete,
            // prevents unbounded growth across iterations
            getEventRegistry(executionPlanId).reset();
            return -1;
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueBarrier(executionPlanId);
    }

    /**
     * Enqueues a dependency-aware sync point, then resets the {@link PTXEventRegistry}.
     *
     * <p>The primary caller is the interpreter's {@code BARRIER} bytecode handler, which
     * passes global event IDs from the interpreter dependency lists. In multi-stream mode,
     * each ID is resolved through the {@link PTXEventRegistry} to its source stream and a
     * {@code cuStreamWaitEvent} is inserted on every active stream - providing fine-grained,
     * GPU-side synchronisation without blocking the CPU. The registry is reset afterwards
     * so global IDs can be reused in the next iteration.
     *
     * <p>If {@code events} is null, delegates to {@link #enqueueMarker(long)} for a full
     * barrier. In single-stream mode, forwards {@code events} (local pool IDs) directly to
     * {@link PTXStream#enqueueBarrier(long, int[])}.
     *
     * <p>Note: {@code CUDAFieldBuffer} also calls this method with local (non-global) event
     * IDs; in multi-stream mode those IDs will not be found in the registry and are silently
     * skipped by {@link #resolveAndWaitCrossStream}.
     *
     * @param executionPlanId the execution plan context
     * @param events array of global event IDs to wait on, or {@code null} for a full barrier
     * @return a local event ID in single-stream mode, or {@code -1} in multi-stream mode
     */
    public int enqueueMarker(long executionPlanId, int[] events) {
        // Since streams are always in-order in CUDA there is no difference
        // between marker and barrier
        if (isMultiStreamEnabled(executionPlanId)) {
            if (events == null) {
                return enqueueMarker(executionPlanId);
            }
            for (PTXStream targetStream : streamPool.activeStreams(executionPlanId)) {
                if (targetStream.getStreamType() == PTXStreamType.DEFAULT) continue;
                resolveAndWaitCrossStream(executionPlanId, events, targetStream);
            }
            // Only reset the PTXEventRegistry when NOT inside a graph capture region.
            // During capture the registry is still needed for resolving cross-stream deps.
            if (!isStreamCapturing(executionPlanId)) {
                getEventRegistry(executionPlanId).reset();
            }
            return -1;
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueBarrier(executionPlanId, events);
    }

    public void sync(long executionPlanId) {
        if (isMultiStreamEnabled(executionPlanId)) {
            for (PTXStream stream : streamPool.activeStreams(executionPlanId)) {
                if (stream.getStreamType() == PTXStreamType.DEFAULT) continue;
                stream.sync();
            }
        } else {
            PTXStream stream = getStream(executionPlanId);
            stream.sync();
        }
    }

    /**
     * Sync the CUDA Stream only if the Stream Exists
     *
     * @param executionPlanId
     */
    public void syncIfNeeded(long executionPlanId) {
        if (isMultiStreamEnabled(executionPlanId)) {
            for (PTXStream stream : streamPool.activeStreams(executionPlanId)) {
                if (stream.getStreamType() == PTXStreamType.DEFAULT) continue;
                stream.sync();
            }
        } else {
            PTXStream stream = getStreamIfNeeded(executionPlanId);
            if (stream != null) {
                stream.sync();
            }
        }
    }

    public void flush(long executionPlanId) {
        // I don't think there is anything like this in CUDA, so I am calling sync
        sync(executionPlanId);
    }

    @Override
    public synchronized void reset(long executionPlanId) {
        if (streamPool.contains(executionPlanId)) {
            // Destroys the plan's role streams and clears its plan-private event registry
            streamPool.remove(executionPlanId);
            executionIDs.remove(executionPlanId);
        }
        intraPlanConcurrencyPlans.remove(executionPlanId);
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
        KernelContextWriteResult ctxResult = writePTXKernelContextOnDevice(executionPlanId, (PTXKernelStackFrame) kernelArgs, taskMeta);
        int kernelLaunchEvent = stream.enqueueKernelLaunch(executionPlanId, module, taskMeta, ctxResult.params(), gridDimension,
                blockDimension);
        updateProfiler(executionPlanId, kernelLaunchEvent, taskMeta);
        return kernelLaunchEvent;
    }

    /**
     * for async execution
     */
    public int enqueueKernelLaunchWithDependencies(long executionPlanId, PTXModule module, KernelStackFrame kernelArgs,
                                                   TaskDataContext taskMeta, long batchThreads, int[] waitEvents) {
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

        PTXStreamType streamType = isMultiStreamEnabled(executionPlanId) ? PTXStreamType.COMPUTE : PTXStreamType.DEFAULT;
        // Round-robin DAG-independent kernels across the COMPUTE pool so they can overlap.
        // During graph capture we pin to compute index 0: the fork/join capture protocol is
        // single-compute-stream, and a freshly-created pool stream would not have joined the
        // capture (CUDA_ERROR_STREAM_CAPTURE_ISOLATION).
        int computeIndex = (streamType == PTXStreamType.COMPUTE && !isStreamCapturing(executionPlanId))
                ? streamPool.nextComputeIndex(executionPlanId)
                : 0;
        PTXStream stream = getStream(executionPlanId, streamType, computeIndex);

        // Write kernel context first (goes to H2D stream)
        KernelContextWriteResult ctxResult = writePTXKernelContextOnDevice(executionPlanId, (PTXKernelStackFrame) kernelArgs, taskMeta);

        if (isMultiStreamEnabled(executionPlanId)) {
            // Fine-grained synchronization of dependencies:
            // make COMPUTE stream wait on specific H2D events this kernel depends on, plus the kernel context write
            int[] allDeps = includeInDeps(waitEvents, ctxResult.writeEventId());
            resolveAndWaitCrossStream(executionPlanId, allDeps, stream);
        }

        int kernelLaunchEvent = stream.enqueueKernelLaunch(executionPlanId, module, taskMeta,
                ctxResult.params(), gridDimension, blockDimension);

        if (isMultiStreamEnabled(executionPlanId)) {
            int globalEventId = getEventRegistry(executionPlanId).register(streamType, computeIndex, kernelLaunchEvent);
            updateProfiler(executionPlanId, globalEventId, taskMeta);
            return globalEventId;
        }
        updateProfiler(executionPlanId, kernelLaunchEvent, taskMeta);
        return kernelLaunchEvent;
    }

    /**
     * Represents a KernelContext write operation on the device.
     * @param params the parameters of the kernel context.
     * @param writeEventId the event ID of the write operation.
     */
    private record KernelContextWriteResult(byte[] params, int writeEventId) {}

    private KernelContextWriteResult writePTXKernelContextOnDevice(long executionPlanId, PTXKernelStackFrame ptxKernelArgs, TaskDataContext meta) {
        int capacity = Long.BYTES + ptxKernelArgs.getCallArguments().size() * Long.BYTES;
        ByteBuffer args = ByteBuffer.allocate(capacity);
        args.order(getByteOrder());

        // Kernel context pointer
        int kernelContextWriteEventId = -1;
        if (!isStreamCapturing(executionPlanId)) {
            kernelContextWriteEventId = ptxKernelArgs.enqueueWrite(executionPlanId);
            updateProfilerKernelContextWrite(executionPlanId, kernelContextWriteEventId, meta, ptxKernelArgs);
        } else {
            pendingKernelContextWrite = ptxKernelArgs;
        }
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

        return new KernelContextWriteResult(args.array(), kernelContextWriteEventId);
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
        if (isMultiStreamEnabled(executionPlanId)) {
            for (PTXStream stream : streamPool.activeStreams(executionPlanId)) {
                if (stream.getStreamType() == PTXStreamType.DEFAULT) continue;
                if (!stream.isDestroy()) {
                    stream.cuDestroyStream();
                }
            }
        } else {
            PTXStream stream = getStream(executionPlanId);
            if (stream != null && !stream.isDestroy()) {
                stream.cuDestroyStream();
            }
        }
    }

    /**
     * Adds the additionalEvent into waitEvents to include it in the dependencies of the kernel launch.
     */
    private int[] includeInDeps(int[] waitEvents, int additionalEvent) {
        if (waitEvents == null) {
            return new int[] { additionalEvent };
        }
        int[] combinedDeps = Arrays.copyOf(waitEvents, waitEvents.length + 1);
        combinedDeps[waitEvents.length] = additionalEvent;
        return combinedDeps;
    }

    /*
     * SYNC READS
     */

    public int readBuffer(long executionPlanId, long address, long length, long hostPointer, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, hostPointer, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, hostPointer, hostOffset, waitEvents);
    }

    public int readBuffer(long executionPlanId, long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long executionPlanId, long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long executionPlanId, long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long executionPlanId, long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long executionPlanId, long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long executionPlanId, long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long executionPlanId, long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    /*
     * ASYNC READS
     */

    public int enqueueReadBuffer(long executionPlanId, long address, long length, long hostPointer, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, hostPointer, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, hostPointer, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long executionPlanId, long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long executionPlanId, long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long executionPlanId, long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long executionPlanId, long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long executionPlanId, long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long executionPlanId, long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long executionPlanId, long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncRead(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    /*
     * SYNC WRITES
     */
    public void writeBuffer(long executionPlanId, long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, null);
        } else {
            PTXStream stream = getStream(executionPlanId);
            stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
        }
    }

    public void writeBuffer(long executionPlanId, long address, long length, long hostPointer, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            stream.enqueueWrite(executionPlanId, address, length, hostPointer, hostOffset, null);
        } else {
            PTXStream stream = getStream(executionPlanId);
            stream.enqueueWrite(executionPlanId, address, length, hostPointer, hostOffset, waitEvents);
        }
    }

    public void writeBuffer(long executionPlanId, long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, null);
        } else {
            PTXStream stream = getStream(executionPlanId);
            stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
        }
    }

    public void writeBuffer(long executionPlanId, long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, null);
        } else {
            PTXStream stream = getStream(executionPlanId);
            stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
        }
    }

    public void writeBuffer(long executionPlanId, long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, null);
        } else {
            PTXStream stream = getStream(executionPlanId);
            stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
        }
    }

    public void writeBuffer(long executionPlanId, long address, long length, long[] array, int hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, null);
        } else {
            PTXStream stream = getStream(executionPlanId);
            stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
        }
    }

    public void writeBuffer(long executionPlanId, long address, long length, float[] array, int hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, null);
        } else {
            PTXStream stream = getStream(executionPlanId);
            stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
        }
    }

    public void writeBuffer(long executionPlanId, long address, long length, double[] array, int hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, null);
        } else {
            PTXStream stream = getStream(executionPlanId);
            stream.enqueueWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
        }
    }

    /*
     * ASYNC WRITES
     */

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, long hostPointer, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncWrite(executionPlanId, address, length, hostPointer, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_H2D, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, hostPointer, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_H2D, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_H2D, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_H2D, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_H2D, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_H2D, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_H2D, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long executionPlanId, long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        if (isMultiStreamEnabled(executionPlanId)) {
            PTXStream stream = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
            resolveAndWaitCrossStream(executionPlanId, waitEvents, stream);
            int localEventId = stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, null);
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_H2D, localEventId);
        }
        PTXStream stream = getStream(executionPlanId);
        return stream.enqueueAsyncWrite(executionPlanId, address, length, array, hostOffset, waitEvents);
    }

    public void dumpEvents(long executionPlanId) {
        final String deviceName = "PTX-" + device.getDeviceName();
        if (isMultiStreamEnabled(executionPlanId)) {
            for (PTXStreamType type : PTXStreamType.values()) {
                if (type == PTXStreamType.DEFAULT) continue;
                PTXStream stream = getStreamIfExists(executionPlanId, type);
                if (stream == null) continue;
                List<PTXEvent> events = stream.getEventPool().getEvents();
                System.out.printf("Found %d events on device %s [%s]:%n", events.size(), deviceName, type);
                if (!events.isEmpty()) {
                    events.forEach((e) -> System.out.printf("event: %s, %s, %s%n", deviceName, e.getName(), e.getStatus()));
                }
            }
        } else {
            PTXStream stream = getStream(executionPlanId);
            List<PTXEvent> events = stream.getEventPool().getEvents();
            System.out.printf("Found %d events on device %s:%n", events.size(), deviceName);
            if (events.isEmpty()) return;
            System.out.println("event: device, type, info, status");
            events.forEach((e) -> System.out.printf("event: %s, %s, %s%n", deviceName, e.getName(), e.getStatus()));
        }
    }

    /**
     * For backwards compatibility
     */
    private PTXStream getStream(long executionPlanId) {
        return getStream(executionPlanId, PTXStreamType.DEFAULT);
    }

    private PTXStream getStream(long executionPlanId, PTXStreamType type) {
        executionIDs.add(executionPlanId);
        return streamPool.acquire(executionPlanId, type);
    }

    /** Resolves a specific stream instance by role and pool index (index used only for COMPUTE). */
    private PTXStream getStream(long executionPlanId, PTXStreamType type, int index) {
        executionIDs.add(executionPlanId);
        if (type == PTXStreamType.COMPUTE) {
            return streamPool.acquireCompute(executionPlanId, index);
        }
        return streamPool.acquire(executionPlanId, type);
    }

    private PTXCodeCache getPTXCodeCache(long executionPlanId) {
        if (!codeCache.containsKey(executionPlanId)) {
            codeCache.put(executionPlanId, new PTXCodeCache(this));
        }
        return codeCache.get(executionPlanId);
    }

    private PTXStream getStreamIfNeeded(long executionPlanId) {
        return streamPool.getIfExists(executionPlanId, PTXStreamType.DEFAULT);
    }

    public long mapOnDeviceMemoryRegion(long executionPlanId, long destDevicePtr, long srcDevicePtr, long offset, int sizeOfType) {
        PTXStream ptxStream = getStream(executionPlanId);
        return ptxStream.mapOnDeviceMemoryRegion(destDevicePtr, srcDevicePtr, offset, sizeOfType);
    }

    /**
     * Begins CUDA graph capture across all three streams using the fork protocol.
     *
     * <p>D2H is the primary capture stream because it is the terminal stream in the
     * H2D->COMPUTE->D2H pipeline. Using D2H as primary means the graph-completion event
     * (recorded when cuGraphLaunch completes) is on D2H, naturally signalling that all
     * work - including the final device-to-host copy - is done.
     *
     * <p>Fork protocol: while D2H is in capture mode, record an event on it and make
     * H2D and COMPUTE wait on that event. A stream that waits on an event recorded in a
     * captured stream automatically joins the capture. All subsequent work on H2D and
     * COMPUTE becomes part of the same graph.
     */
    private void beginMultiStreamGraphCapture(long executionPlanId) {
        PTXStream d2hStream     = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
        PTXStream h2dStream     = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
        PTXStream computeStream = getStream(executionPlanId, PTXStreamType.COMPUTE);

        // Start capture on the primary (terminal) stream
        d2hStream.beginGraphCapture();

        // Fork: record an event on D2H and make H2D + COMPUTE wait on it -> they join the capture
        byte[] forkEvent = d2hStream.recordCaptureEvent();
        h2dStream.waitOnCapturedEvent(forkEvent);
        computeStream.waitOnCapturedEvent(forkEvent);
    }

    /**
     * Joins the multi-stream capture back into the primary D2H stream, ends capture,
     * instantiates the graph, and flushes the deferred kernel context write.
     *
     * <p>Join protocol: record end-events on H2D and COMPUTE streams, then make D2H wait
     * on them. This ensures every captured node from all three streams is included before
     * cuStreamEndCapture is called on D2H.
     *
     * <p>Deferred context write: written after graph instantiation, then H2D stream is
     * synchronised so the write completes before the first graph replay.
     */
    private long endMultiStreamGraphCaptureAndInstantiate(long executionPlanId) {
        PTXStream d2hStream     = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
        PTXStream h2dStream     = getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_H2D);
        PTXStream computeStream = getStream(executionPlanId, PTXStreamType.COMPUTE);

        // Join: record end-events on secondary streams and make D2H (primary) wait on them
        byte[] h2dJoinEvent     = h2dStream.recordCaptureEvent();
        byte[] computeJoinEvent = computeStream.recordCaptureEvent();
        d2hStream.waitOnCapturedEvent(h2dJoinEvent);
        d2hStream.waitOnCapturedEvent(computeJoinEvent);

        // End capture on D2H (primary) - collects all nodes from all joined streams
        long handle = d2hStream.endGraphCaptureAndInstantiate();

        // Reset event pools for all three streams: captured events are absorbed into
        // graph nodes and their original CUevent handles become invalid for host-side
        // synchronization (cuEventSynchronize -> CUDA_ERROR_INVALID_VALUE). Resetting
        // discards them without syncing (PTXEventPool.reset() calls destroy(), not wait).
        h2dStream.reset();
        computeStream.reset();
        d2hStream.reset();

        // Flush deferred kernel context write (host->device, outside graph),
        // then synchronize H2D stream so data is ready before the first graph replay
        if (pendingKernelContextWrite != null) {
            pendingKernelContextWrite.enqueueWrite(executionPlanId);
            pendingKernelContextWrite = null;
            h2dStream.sync();
        }

        return handle;
    }

    public void beginExecutionGraphCapture(long executionPlanId) {
        if (isMultiStreamEnabled(executionPlanId)) {
            // Reset registry before capture: clears pre-capture event IDs (from ALLOC
            // bytecodes etc.) so only events recorded after all streams join the capture
            // are tracked globally. Without this, cuStreamWaitEvent(capturedStream,
            // preCapturEvent) -> CUDA_ERROR_STREAM_CAPTURE_ISOLATION (905).
            getEventRegistry(executionPlanId).reset();
            beginMultiStreamGraphCapture(executionPlanId);
        } else {
            getStream(executionPlanId).beginGraphCapture();
        }
    }

    public long endExecutionGraphCaptureAndInstantiate(long executionPlanId) {
        if (isMultiStreamEnabled(executionPlanId)) {
            return endMultiStreamGraphCaptureAndInstantiate(executionPlanId);
        }
        PTXStream stream = getStream(executionPlanId);
        long handle = stream.endGraphCaptureAndInstantiate();

        // Reset event pool: captured events are absorbed into graph nodes and become
        // invalid for host-side sync; discard without waiting.
        stream.reset();

        // Write the kernel context that was deferred during capture
        if (pendingKernelContextWrite != null) {
            pendingKernelContextWrite.enqueueWrite(executionPlanId);
            pendingKernelContextWrite = null;
        }

        return handle;
    }

    public int launchExecutionGraph(long executionPlanId, long executionGraphHandle) {
        // In multi-stream mode, launch on D2H stream - the primary capture stream.
        // cuGraphLaunch replays all captured nodes on their original streams internally;
        // the stream argument only controls where the graph-completion event is recorded.
        // D2H is the terminal stream, so the completion event correctly signals all work done.
        PTXStream stream = isMultiStreamEnabled(executionPlanId)
                ? getStream(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H)
                : getStream(executionPlanId);
        int localEventId = stream.launchGraph(executionGraphHandle);
        if (isMultiStreamEnabled(executionPlanId)) {
            return getEventRegistry(executionPlanId).register(PTXStreamType.DATA_TRANSFER_D2H, localEventId);
        }
        return localEventId;
    }

    public boolean isStreamCapturing(long executionPlanId) {
        if (isMultiStreamEnabled(executionPlanId)) {
            // In multi-stream mode, D2H is the primary capture stream
            PTXStream d2hStream = getStreamIfExists(executionPlanId, PTXStreamType.DATA_TRANSFER_D2H);
            return d2hStream != null && d2hStream.isCapturing();
        }
        return getStream(executionPlanId).isCapturing();
    }

    public void destroyExecutionGraph(long executionGraphHandle) {
        PTXStream.destroyGraph(executionGraphHandle);
    }
}
