/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.interpreter;

import static uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus.COMPLETE;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.VIRTUAL_DEVICE_ENABLED;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.VM_USE_DEPS;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TornadoEvents;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP64NotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoFailureException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.api.runtime.TaskContextInterface;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.BatchConfiguration;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoNativeInterpreterSupport;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.common.XPUDeviceBufferState;
import uk.ac.manchester.tornado.runtime.graph.TornadoExecutionContext;
import uk.ac.manchester.tornado.runtime.graph.TornadoVMBytecodeResult;
import uk.ac.manchester.tornado.runtime.graph.TornadoVMBytecodes;
import uk.ac.manchester.tornado.runtime.library.LibraryRegistry;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;
import uk.ac.manchester.tornado.runtime.profiler.TimeProfiler;
import uk.ac.manchester.tornado.runtime.tasks.DataObjectState;
import uk.ac.manchester.tornado.runtime.tasks.LibraryTask;
import uk.ac.manchester.tornado.runtime.tasks.PrebuiltTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;
import uk.ac.manchester.tornado.runtime.utils.TornadoUtils;

/**
 * TornadoVMInterpreter: serves as a bytecode interpreter for TornadoVM
 * bytecodes. Also, it functions as a memory manager for various devices,
 * including GPUs and multicore processors that adhere to any of the
 * supported programming models. Additionally, it features a Just-In-Time (JIT)
 * compiler that compiles Java bytecode to OpenCL and CUDA.
 */
public class TornadoVMInterpreter {
    private static final Event EMPTY_EVENT = new EmptyEvent();

    private static final int MAX_EVENTS = TornadoOptions.MAX_EVENTS;
    private boolean useDependencies;
    private boolean useNativeInterpreter;

    private final HashMap<Object, Access> objectAccesses;
    private final List<Object> objects;

    private final DataObjectState[] dataObjectStates;
    private final KernelStackFrame[] kernelStackFrame;
    private final int[][] events;
    private final int[] eventsIndexes;
    private final TornadoXPUDevice interpreterDevice;
    private final TornadoInstalledCode[] installedCodes;

    private final List<Object> constants;
    private final List<SchedulableTask> taskExecutionContexts;
    private final List<SchedulableTask> localTaskList;
    private final TornadoExecutionContext graphExecutionContext;
    private final TornadoVMBytecodeResult bytecodeResult;
    private TornadoProfiler timeProfiler;
    private double totalTime;
    private long invocations;
    private boolean finishedWarmup;

    private GridScheduler gridScheduler;

    private HashMap<Object, Integer> currentBatchNumberPerObject = new HashMap<>();
    private HashMap<Object, Integer> totalEvenBatchesPerObject = new HashMap<>();
    private final HashMap<Integer, Long> executionGraphHandles = new HashMap<>();
    private boolean insideCaptureRegion = false;
    private boolean executionGraphEnabled = true;

    /*
     * Tables passed into the native bytecode loop. Kernel/program handles stay
     * 0 until a backend-agnostic getter exists
     */
    private long[] nativeBufferHandles;
    private long[] nativeBufferOffsets;
    private long[] nativeBufferSizes;
    private long[] nativeHostPointers;
    private long[] nativeKernelHandles;
    private long[] nativeProgramHandles;
    private long[] nativeLaunchMetadata;
    private KernelStackFrame[] nativeLaunchFrames;
    private boolean[] nativeLaunchPrepared;
    private byte[] nativeConstants;
    private byte[] nativeObjectFlags;
    private byte[] nativeObjectAccesses;
    private Object[] nativeObjects;
    private byte[] nativeObjectKinds;
    private long[] nativeDataOffsets;
    private long[] nativePartialCopySizes;
    private boolean[] nativeAllocationPrepared;
    private boolean[] nativeAtomicCopyBack;
    // Compound objects (Matrix2D, Vector, Image) are flattened into a staging buffer
    // for C++. Only pack when this native run will actually H2D, and only unpack after D2H.
    private boolean[] nativeHostPackNeeded;
    private boolean[] nativeHostUnpackNeeded;

    private TornadoLogger logger = new TornadoLogger(this.getClass());

    /**
     * It constructs a new TornadoVMInterpreter object.
     *
     * @param graphExecutionContext
     *     The {@link TornadoExecutionContext}
     * @param bytecodeResult
     *     The {@link TornadoVMBytecodeResult}.
     * @param timeProfiler
     *     The {@link TornadoProfiler} for time measurements.
     * @param device
     *     The {@link TornadoXPUDevice} device.
     */
    public TornadoVMInterpreter(TornadoExecutionContext graphExecutionContext, TornadoVMBytecodeResult bytecodeResult, TornadoProfiler timeProfiler, TornadoXPUDevice device) {
        this.graphExecutionContext = graphExecutionContext;
        this.timeProfiler = timeProfiler;
        this.bytecodeResult = bytecodeResult;

        assert device != null;
        this.interpreterDevice = device;

        // NOTE: useDependencies is (re)computed at the start of execute() rather than latched here,
        // because plan-level withIntraPlanConcurrency() is applied after this interpreter is built.
        useDependencies = VM_USE_DEPS || isIntraPlanConcurrencyActive();
        useNativeInterpreter = resolveUseNativeInterpreter();
        totalTime = 0;
        invocations = 0;

        logger.debug("init an instance of a TornadoVM interpreter...");

        this.bytecodeResult.getLong(); // Skips bytes not needed

        kernelStackFrame = graphExecutionContext.getKernelStackFrame();
        // Rows are allocated on first use: MAX_EVENTS defaults to 32768, so an eager matrix costs
        // 131KB per dependency list even when a graph only ever fills a handful of entries.
        events = new int[this.bytecodeResult.getInt()][];
        eventsIndexes = new int[events.length];

        localTaskList = graphExecutionContext.getTasksForDevice(interpreterDevice.getDeviceContext());

        installedCodes = new TornadoInstalledCode[localTaskList.size()];

        // Wait-list rows are created on first use (already filled with -1) and eventsIndexes starts
        // zeroed, so there is nothing to initialise here.

        logger.debug("created %d kernelStackFrame", kernelStackFrame.length);
        logger.debug("created %d event lists", events.length);
        objectAccesses = graphExecutionContext.getObjectsAccesses();
        objects = graphExecutionContext.getObjects();
        initBatchDataStructures(graphExecutionContext);
        dataObjectStates = new DataObjectState[objects.size()];
        fetchGlobalStates();

        rewindBufferToBegin();

        constants = graphExecutionContext.getConstants();
        taskExecutionContexts = graphExecutionContext.getTasks();

        logger.debug("interpreter for device %s is ready to go", device.toString());

        this.bytecodeResult.mark();
    }

    private void initBatchDataStructures(TornadoExecutionContext context) {
        long batchSize = context.getBatchSize();
        if (batchSize != -1) {
            BatchConfiguration batchConfiguration = BatchConfiguration.computeChunkSizes(context, batchSize);
            int totalChunks = batchConfiguration.getTotalChunks();
            for (Object object : objects) {
                // Deliberately counts only the EVEN chunks: the DEALLOC after the last even chunk must
                // fire so the remainder chunk (if any) gets a fresh buffer sized to the remainder -
                // transfers use the buffer's allocated size, so reusing the even-chunk buffer would
                // overrun the host segment.
                totalEvenBatchesPerObject.put(object, totalChunks);
                currentBatchNumberPerObject.put(object, 0);
            }
        }
    }

    public void setTimeProfiler(TornadoProfiler tornadoProfiler) {
        this.timeProfiler = tornadoProfiler;
    }

    public void fetchGlobalStates() {
        for (int i = 0; i < objects.size(); i++) {
            final Object object = objects.get(i);
            final Access access = objectAccesses.get(object);
            TornadoInternalError.guarantee(object != null, "null object found in TornadoVM");
            dataObjectStates[i] = graphExecutionContext.getLocalStateObject(object, access).getDataObjectState();
        }
    }

    private void rewindBufferToBegin() {
        byte op = bytecodeResult.get();
        while (op != TornadoVMBytecodes.BEGIN.value()) {
            TornadoInternalError.guarantee(op == TornadoVMBytecodes.CONTEXT.value(), "invalid code: 0x%x", op);
            final int deviceIndex = bytecodeResult.getInt();
            assert deviceIndex == interpreterDevice.getDeviceContext().getDeviceIndex();
            logger.debug("loading context %s", interpreterDevice.toString());
            final long t0 = System.nanoTime();
            interpreterDevice.ensureLoaded(graphExecutionContext.getExecutionPlanId());
            final long t1 = System.nanoTime();
            logger.debug("loaded in %.9f s", (t1 - t0) * 1e-9);
            op = bytecodeResult.get();
        }
    }

    public void setGridScheduler(GridScheduler gridScheduler) {
        this.gridScheduler = gridScheduler;
    }

    public void printTimes() {
        System.out.printf("bc: complete %d iterations - %.9f s mean and %.9f s total%n", invocations, (totalTime / invocations), totalTime);
    }

    public void clearProfiles() {
        for (final SchedulableTask task : taskExecutionContexts) {
            task.meta().getProfiles(graphExecutionContext.getExecutionPlanId()).clear();
        }
    }

    public void dumpEvents() {
        if (!TornadoOptions.TORNADO_PROFILER) {
            logger.info("profiling and/or event dumping is not enabled");
            return;
        }

        interpreterDevice.dumpEvents(graphExecutionContext.getExecutionPlanId());
    }

    private void dumpEventProfiled(TornadoEvents eventSet, TaskDataContext meta) {
        final BitSet profiles = eventSet.getProfiles();
        for (int i = profiles.nextSetBit(0); i != -1; i = profiles.nextSetBit(i + 1)) {
            if (eventSet.getDevice() instanceof TornadoXPUDevice device) {
                final Event profile = device.resolveEvent(graphExecutionContext.getExecutionPlanId(), i);
                if (profile.getStatus() == COMPLETE) {
                    System.out.printf("task: %s %s %9d %9d %9d %9d %9d%n", device.getDeviceName(), meta.getId(), profile.getElapsedTime(), profile.getQueuedTime(), profile.getSubmitTime(), profile
                            .getStartTime(), profile.getEndTime());
                }
            } else {
                throw new TornadoRuntimeException("TornadoDevice not found");
            }
        }
    }

    public void dumpProfiles() {
        for (final SchedulableTask task : taskExecutionContexts) {
            final TaskDataContext meta = (TaskDataContext) task.meta();
            meta.getProfiles(graphExecutionContext.getExecutionPlanId()).forEach(eventSet -> dumpEventProfiled(eventSet, meta));
        }
    }

    public void withPreCompilation() {
        execute(true);
        finishedWarmup = true;
    }

    /**
     * Native is used whenever the flag is on and the library loaded. Profiling and
     * bytecode logging remain on the Java path until their Phase 7 native records exist;
     * otherwise enabling either feature would silently lose data.
     */
    private boolean resolveUseNativeInterpreter() {
        return TornadoOptions.INTERPRETER_NATIVE && interpreterDevice instanceof TornadoNativeInterpreterSupport && NativeBytecodeInterpreter.isAvailable() && !TornadoOptions.isProfilerEnabled()
                && !TornadoOptions.LOG_BYTECODES();
    }

    private boolean isMemoryLimitEnabled() {
        return graphExecutionContext.isMemoryLimited();
    }

    /**
     * Three conditions should be satisfied to allow intra-plan concurrency.
     * <ol>
     * <li> withIntraPlanConcurrency() API call</li>
     * <li>supported by backend</li>
     * <li>TaskGraph can indeed be parallelized</li>
     * </ol>
     */
    private boolean isIntraPlanConcurrencyActive() {
        return graphExecutionContext.isIntraPlanConcurrencyEnabled()
                && interpreterDevice.isIntraPlanConcurrencySupported()
                && !bytecodeResult.isSerialTaskGraph();
    }

    private Event execute(boolean isWarmup) {
        isWarmup = isWarmup || VIRTUAL_DEVICE_ENABLED;
        interpreterDevice.enableThreadSharing();

        // Push the per-plan intra-plan-concurrency setting to the backend before issuing any
        // bytecode, so transfer/launch routing (single- vs multi-stream) is decided per plan.
        interpreterDevice.setIntraPlanConcurrency(graphExecutionContext.getExecutionPlanId(), isIntraPlanConcurrencyActive());

        // Push the staged-transfer setting before the plan's ALLOCs: it also decides whether a
        // staged buffer skips the whole-segment host pin, which is settled at allocation time.
        interpreterDevice.setStagedTransfers(graphExecutionContext.isStagedTransfersEnabled());

        // Recompute here (not just in the constructor): plan-level withIntraPlanConcurrency() is
        // applied after this interpreter is built, so latching it at construction misses it and the
        // dependency DAG (waitList -> cross-stream events) would never engage for concurrent plans.
        useDependencies = VM_USE_DEPS || isIntraPlanConcurrencyActive();
        // Follows useDependencies for the same reason: it is derived from it, so latching it at
        // construction would leave the native loop enabled for a plan that turned dependencies on
        // afterwards.
        useNativeInterpreter = resolveUseNativeInterpreter();

        // Batched plans: reset the per-object chunk counters so every execution behaves like the
        // first (per-chunk DEALLOCs stay no-ops until the last even chunk). Without this reset the
        // counters keep growing across execute() calls, so on re-execution every per-chunk DEALLOC
        // frees for real and the buffers are deallocated and reallocated on every chunk.
        if (graphExecutionContext.getBatchSize() != -1 && !isWarmup) {
            currentBatchNumberPerObject.replaceAll((object, count) -> 0);
        }

        if (isMemoryLimitEnabled() && graphExecutionContext.doesExceedExecutionPlanLimit()) {
            throw new TornadoMemoryException("OutofMemoryException due to executionPlan.withMemoryLimit of " + graphExecutionContext.getExecutionPlanMemoryLimit());
        }

        final long t0 = System.nanoTime();

        // lastEvent: event ID produced by the most recently executed bytecode operation
        // (H2D, D2H, LAUNCH, ALLOC, etc.). The immediately following ADD_DEPENDENCY
        // bytecode stores it into events[slot], building the wait-list that is passed
        // as waitList to the next dependent operation.
        // In single-stream mode: a local CUDAEventPool index.
        // In multi-stream mode: a global event-registry ID resolved via
        // resolveAndWaitCrossStream into cuStreamWaitEvent calls on the target stream.
        // Initialised to -1; ADD_DEPENDENCY skips it when -1 (no-op or warmup).
        int lastEvent = -1;
        final int[] lastEventHolder = new int[1];
        initWaitEventList();

        StringBuilder logBuilder = null;
        if (TornadoOptions.LOG_BYTECODES() && !isWarmup) {
            logBuilder = new StringBuilder();
            logBuilder.append(InterpreterUtilities.debugHighLightHelper("Interpreter instance running bytecodes for: ")).append(interpreterDevice).append(InterpreterUtilities.debugHighLightHelper(
                    " Running in thread: ")).append(Thread.currentThread().getName()).append("\n");
        }

        boolean endedInNative = false;
        int nativeInterpreterCalls = 0;
        int javaFallbackOpcodes = 0;
        if (nativeAtomicCopyBack != null) {
            Arrays.fill(nativeAtomicCopyBack, false);
        }
        while (bytecodeResult.hasRemaining()) {
            if (useNativeInterpreter && !insideCaptureRegion && NativeBytecodeInterpreter.isPorted(bytecodeResult.peek()) && prepareNativeRun(isWarmup)) {
                lastEventHolder[0] = lastEvent;
                final int nativeStatus;
                try {
                    nativeInterpreterCalls++;
                    nativeStatus = advanceWithNativeInterpreter(isWarmup, lastEventHolder);
                } finally {
                    cancelPendingNativeAllocations();
                }
                lastEvent = lastEventHolder[0];
                if (nativeStatus == NativeBytecodeInterpreter.STATUS_END) {
                    endedInNative = true;
                    break;
                }
                if (nativeStatus == NativeBytecodeInterpreter.STATUS_EOF) {
                    break;
                }
            }
            if (useNativeInterpreter) {
                javaFallbackOpcodes++;
            }
            final byte op = bytecodeResult.get();
            if (op == TornadoVMBytecodes.ALLOC.value()) {
                final long sizeBatch = bytecodeResult.getLong();
                final int argSize = bytecodeResult.getInt();
                final int[] args = new int[argSize];
                for (int i = 0; i < argSize; i++) {
                    args[i] = bytecodeResult.getInt();
                }
                if (isWarmup || !executionGraphHandles.isEmpty()) {
                    continue;
                }
                lastEvent = executeAlloc(logBuilder, args, sizeBatch);
            } else if (op == TornadoVMBytecodes.DEALLOC.value()) {
                final int objectIndex = bytecodeResult.getInt();
                if (isWarmup) {
                    continue;
                }
                if (!executionGraphHandles.isEmpty()) {
                    if (TornadoOptions.LOG_BYTECODES()) {
                        Object object = objects.get(objectIndex);
                        logBuilder.append("bc: ").append(InterpreterUtilities.debugHighLightNonExecBC(
                                        "DEALLOC")).append(" [SKIPPED - execution graph active] ")
                                .append(object).append("\n");
                    }
                    continue;
                }
                lastEvent = executeDeAlloc(logBuilder, objectIndex);
            } else if (op == TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ONCE.value()) {
                final int objectIndex = bytecodeResult.getInt();
                final int eventId = bytecodeResult.getInt();
                final long offset = bytecodeResult.getLong();
                final long sizeBatch = bytecodeResult.getLong();
                final int[] waitList = (useDependencies && eventId != -1) ? waitListFor(eventId) : null;
                if (isWarmup) {
                    continue;
                }
                lastEvent = transferHostToDeviceOnce(logBuilder, objectIndex, offset, eventId, sizeBatch, waitList);
            } else if (op == TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ALWAYS.value()) {
                final int objectIndex = bytecodeResult.getInt();
                final int eventId = bytecodeResult.getInt();
                final long offset = bytecodeResult.getLong();
                final long sizeBatch = bytecodeResult.getLong();
                final int[] waitList = (useDependencies && eventId != -1) ? waitListFor(eventId) : null;
                if (isWarmup) {
                    continue;
                }
                lastEvent = transferHostToDeviceAlways(logBuilder, objectIndex, offset, eventId, sizeBatch, waitList);
            } else if (op == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS.value()) {
                final int objectIndex = bytecodeResult.getInt();
                final int eventId = bytecodeResult.getInt();
                final long offset = bytecodeResult.getLong();
                final long sizeBatch = bytecodeResult.getLong();
                final int[] waitList = (useDependencies) ? waitListFor(eventId) : null;
                if (isWarmup) {
                    continue;
                }
                lastEvent = transferDeviceToHost(logBuilder, objectIndex, offset, eventId, sizeBatch, waitList);
            } else if (op == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING.value()) {
                final int objectIndex = bytecodeResult.getInt();
                final int eventId = bytecodeResult.getInt();
                final long offset = bytecodeResult.getLong();
                final long sizeBatch = bytecodeResult.getLong();
                final int[] waitList = (useDependencies) ? waitListFor(eventId) : null;
                if (isWarmup) {
                    continue;
                }
                transferDeviceToHostBlocking(logBuilder, objectIndex, offset, eventId, sizeBatch, waitList);
            } else if (op == TornadoVMBytecodes.LAUNCH.value()) {
                final int callWrapperIndex = bytecodeResult.getInt();
                final int taskIndex = bytecodeResult.getInt();
                final int numArgs = bytecodeResult.getInt();
                final int eventId = bytecodeResult.getInt();
                final long offset = bytecodeResult.getLong();
                final long batchThreads = bytecodeResult.getLong();
                XPUExecutionFrame executionFrame = compileTaskFromBytecodeToBinary(callWrapperIndex, numArgs, eventId, taskIndex, batchThreads);
                if (isWarmup) {
                    popArgumentsFromCall(numArgs);
                    continue;
                }
                lastEvent = executeLaunch(logBuilder, numArgs, eventId, taskIndex, batchThreads, offset, executionFrame);
            } else if (op == TornadoVMBytecodes.ADD_DEPENDENCY.value()) {
                final int eventList = bytecodeResult.getInt();
                if (isWarmup) {
                    continue;
                }
                executeDependency(logBuilder, lastEvent, eventList);
            } else if (op == TornadoVMBytecodes.ON_DEVICE.value()) {
                final int objectIndex = bytecodeResult.getInt();
                final int eventId = bytecodeResult.getInt();
                if (isWarmup) {
                    continue;
                }
                lastEvent = executeOnDevice(logBuilder, objectIndex, eventId);
            } else if (op == TornadoVMBytecodes.PERSIST.value()) {
                final int objectIndex = bytecodeResult.getInt();
                final int eventId = bytecodeResult.getInt();
                if (isWarmup) {
                    continue;
                }
                lastEvent = executePersist(logBuilder, objectIndex, eventId);
            } else if (op == TornadoVMBytecodes.BARRIER.value()) {
                final int eventId = bytecodeResult.getInt();
                final int[] waitList = (useDependencies && eventId != -1) ? waitListFor(eventId) : null;
                if (isWarmup) {
                    continue;
                }
                lastEvent = executeBarrier(logBuilder, eventId, waitList);
            } else if (op == TornadoVMBytecodes.CUDA_GRAPH_LAUNCH.value()) {
                final int graphId = bytecodeResult.getInt();
                if (isWarmup) {
                    continue;
                }
                if (executionGraphHandles.containsKey(graphId)) {
                    lastEvent = executeGraphLaunch(logBuilder, graphId);
                }
            } else if (op == TornadoVMBytecodes.CUDA_GRAPH_BEGIN_CAPTURE.value()) {
                final int graphId = bytecodeResult.getInt();
                if (isWarmup) {
                    continue;
                }
                if (executionGraphHandles.containsKey(graphId)) {
                    // Graph already captured: skip entire capture region
                    skipToAfterEndCapture(graphId);
                } else {
                    // First execution: force all lazy allocations, then capture
                    preCompileLaunchesInCaptureRegion();
                    executeGraphBeginCapture(logBuilder, graphId);
                    insideCaptureRegion = true;
                }

            } else if (op == TornadoVMBytecodes.CUDA_GRAPH_END_CAPTURE.value()) {
                final int graphId = bytecodeResult.getInt();
                if (isWarmup) {
                    continue;
                }
                insideCaptureRegion = false;
                executeGraphEndCapture(logBuilder, graphId);
            } else if (op == TornadoVMBytecodes.CUDA_GRAPH_DESTROY.value()) {
                final int graphId = bytecodeResult.getInt();
                if (isWarmup) {
                    continue;
                }
                Long handle = executionGraphHandles.remove(graphId);
                if (handle != null) {
                    interpreterDevice.destroyExecutionGraph(handle);
                    if (TornadoOptions.LOG_BYTECODES()) {
                        logBuilder.append("bc: ").append(InterpreterUtilities.debugHighLightBC(
                                "EXECUTION_GRAPH_DESTROY")).append(" graphId=").append(graphId).append("\n");
                    }
                }
            } else if (op == TornadoVMBytecodes.END.value()) {
                if (!isWarmup && TornadoOptions.LOG_BYTECODES()) {
                    logBuilder.append("bc: ").append(InterpreterUtilities.debugHighLightBC("END\n")).append("\n");
                }
                break;
            } else {
                throwErrorInterpreter(op);
            }
        }

        Event barrier = EMPTY_EVENT;
        if (!isWarmup) {
            if (useDependencies) {
                final int event = interpreterDevice.enqueueMarker(graphExecutionContext.getExecutionPlanId());
                barrier = interpreterDevice.resolveEvent(graphExecutionContext.getExecutionPlanId(), event);
            }

            if (TornadoOptions.USE_VM_FLUSH && !endedInNative) {
                interpreterDevice.flush(graphExecutionContext.getExecutionPlanId());
            }
        }

        final long t1 = System.nanoTime();
        final double elapsed = (t1 - t0) * 1e-9;
        if (!isWarmup) {
            totalTime += elapsed;
            invocations++;
        }

        if (graphExecutionContext.meta().isDebug()) {
            logger.debug("bc: complete elapsed=%.9f s (%d iterations, %.9f s mean)", elapsed, invocations, (totalTime / invocations));
        }

        bytecodeResult.reset();

        if (TornadoOptions.PRINT_BYTECODES) {
            System.out.println(logBuilder);
        }

        if (!TornadoOptions.DUMP_BYTECODES.isBlank()) {
            RuntimeUtilities.writeBytecodeToFile(logBuilder);
        }

        if (useNativeInterpreter && (nativeInterpreterCalls != 1 || javaFallbackOpcodes != 0 || !endedInNative)) {
            System.out.printf("[native-interpreter-fallback] calls=%d javaOpcodes=%d endedInNative=%s warmup=%s%n", nativeInterpreterCalls, javaFallbackOpcodes, endedInNative, isWarmup);
        }

        return barrier;
    }

    /**
     * It runs the native bytecode loop from the current position, leaving the cursor on the
     * first bytecode that the native loop does not implement.
     *
     * <p>
     * The native loop only ever moves the cursor over bytecodes it fully handled, so the
     * position it reports back is always a bytecode boundary. Anything it does not implement
     * is left untouched for the Java interpreter below to decode and execute as usual.
     *
     * @param isWarmup
     *     whether this is a warm-up pass.
     * @param lastEventHolder
     *     length-1 in/out slot for the loop-carried event-pool id.
     * @return the native {@code STATUS_*} result.
     */
    private int advanceWithNativeInterpreter(boolean isWarmup, int[] lastEventHolder) {
        int flags = 0;
        if (isWarmup) {
            flags |= NativeBytecodeInterpreter.FLAG_WARMUP;
        }
        if (useDependencies) {
            flags |= NativeBytecodeInterpreter.FLAG_USE_DEPENDENCIES;
        }
        if (!executionGraphHandles.isEmpty()) {
            flags |= NativeBytecodeInterpreter.FLAG_GRAPH_INSTANTIATED;
        }
        if (!currentBatchNumberPerObject.isEmpty()) {
            flags |= NativeBytecodeInterpreter.FLAG_BATCHED_EXECUTION;
        }
        if (TornadoOptions.isDeallocateBufferEnabled()) {
            flags |= NativeBytecodeInterpreter.FLAG_FORCE_DEALLOCATION;
        }
        if (TornadoOptions.USE_VM_FLUSH) {
            flags |= NativeBytecodeInterpreter.FLAG_USE_VM_FLUSH;
        }
        final long executionPlanId = graphExecutionContext.getExecutionPlanId();
        long commandQueue = 0L;
        long deviceContextHandle = 0L;
        if (interpreterDevice instanceof TornadoNativeStreamSupport streams) {
            commandQueue = streams.getNativeStream(executionPlanId);
            deviceContextHandle = streams.getNativeContext(executionPlanId);
        }
        final int backend = interpreterDevice.getTornadoVMBackend().ordinal();
        final int deviceIndex = interpreterDevice.getDeviceContext().getDeviceIndex();
        final int platformIndex = interpreterDevice.getDeviceContext().getDevicePlatform();
        refreshNativeStateTables();
        final long result = NativeBytecodeInterpreter.execute(bytecodeResult.getBytecode(), bytecodeResult.position(), bytecodeResult.limit(), flags, nativeBufferHandles, nativeBufferOffsets,
                nativeBufferSizes, nativeHostPointers, nativeKernelHandles, nativeProgramHandles, nativeLaunchMetadata, nativeConstants, commandQueue, deviceContextHandle, backend, deviceIndex, platformIndex,
                executionPlanId, lastEventHolder, eventsIndexes, events, MAX_EVENTS, nativeObjectFlags, nativeObjectAccesses, nativeObjects, nativeObjectKinds, nativeDataOffsets, nativePartialCopySizes);
        bytecodeResult.position(NativeBytecodeInterpreter.positionOf(result));
        completeNativeInterpreterHostBuffers();
        completeNativeAtomics();
        syncNativeObjectFlags();

        final int status = NativeBytecodeInterpreter.statusOf(result);
        if (status == NativeBytecodeInterpreter.STATUS_ERROR) {
            throw new TornadoInternalError("native bytecode handler failed for opcode %d at bytecode position %d", Byte.toUnsignedInt(bytecodeResult.peek()), bytecodeResult.position());
        }
        return status;
    }

    // Prepares every ALLOC that C++ can reach before its next Java fallback.
    private boolean prepareNativeRun(boolean isWarmup) {
        final int savedPosition = bytecodeResult.position();
        boolean hasNativeWork = false;
        if (nativeHostPackNeeded != null) {
            Arrays.fill(nativeHostPackNeeded, false);
            Arrays.fill(nativeHostUnpackNeeded, false);
        }
        try {
            while (bytecodeResult.hasRemaining() && NativeBytecodeInterpreter.isPorted(bytecodeResult.peek())) {
                if (!prepareCurrentOpcodeForNativeExecution(isWarmup)) {
                    break;
                }
                hasNativeWork = true;
                final byte op = bytecodeResult.get();
                skipBytecodeOperands(op);
            }
            return hasNativeWork;
        } catch (RuntimeException | Error e) {
            cancelPendingNativeAllocations();
            throw e;
        } finally {
            bytecodeResult.position(savedPosition);
        }
    }

    // Creates the same wrapper metadata as Java ALLOC, but makes no backend JNI call.
    private void prepareCurrentNativeAllocation() {
        TornadoInternalError.guarantee(interpreterDevice instanceof TornadoNativeInterpreterSupport, "backend cannot prepare a native allocation");
        TornadoNativeInterpreterSupport nativeDevice = (TornadoNativeInterpreterSupport) interpreterDevice;
        ensureNativeStateTables();
        final int savedPosition = bytecodeResult.position();
        try {
            TornadoInternalError.guarantee(bytecodeResult.get() == TornadoVMBytecodes.ALLOC.value(), "native allocation preparation requested for a non-ALLOC bytecode");
            final long sizeBatch = bytecodeResult.getLong();
            final int argCount = bytecodeResult.getInt();
            for (int i = 0; i < argCount; i++) {
                final int objectIndex = bytecodeResult.getInt();
                Object object = objects.get(objectIndex);
                if (isObjectKernelContext(object)) {
                    continue;
                }
                if (object instanceof AtomicInteger) {
                    prepareNativeAtomicInteger(objectIndex, object, sizeBatch);
                    continue;
                }
                XPUDeviceBufferState state = resolveObjectState(objectIndex);
                if (state != null && !state.hasObjectBuffer()) {
                    nativeDevice.prepareNativeAllocation(object, sizeBatch, state, objectAccesses.get(object));
                    nativeAllocationPrepared[objectIndex] = true;
                }
            }
        } finally {
            bytecodeResult.position(savedPosition);
        }
    }

    // Cancels allocations prepared beyond the bytecode where the native loop stopped.
    private void cancelPendingNativeAllocations() {
        if (!(interpreterDevice instanceof TornadoNativeInterpreterSupport nativeDevice) || nativeAllocationPrepared == null) {
            return;
        }
        for (int i = 0; i < nativeAllocationPrepared.length; i++) {
            if (!nativeAllocationPrepared[i]) {
                continue;
            }
            XPUDeviceBufferState state = resolveObjectState(i);
            TornadoInternalError.guarantee(state != null && state.hasObjectBuffer(), "missing prepared native allocation");
            Object object = objects.get(i);
            long handle = Math.max(0L, state.getXPUBuffer().toBuffer());
            nativeDevice.detachNativeAllocation(state, objectAccesses.get(object), handle);
            nativeAllocationPrepared[i] = false;
        }
    }

    /* This mirrors the native bailout checks so ALLOC is only reserved for the next
     * uninterrupted native run. Unsupported lifecycles stay on the Java path. */
    private boolean prepareCurrentOpcodeForNativeExecution(boolean isWarmup) {
        final int savedPosition = bytecodeResult.position();
        try {
            final byte op = bytecodeResult.get();
            // LAUNCH must still compile and publish its native state during warm-up.
            if (isWarmup && op != TornadoVMBytecodes.LAUNCH.value()) {
                return true;
            }
            if (!currentBatchNumberPerObject.isEmpty() && isPhaseFiveMemoryOpcode(op)) {
                return false;
            }
            if (op == TornadoVMBytecodes.LAUNCH.value()) {
                return prepareCurrentNativeLaunch(isWarmup);
            } else if (op == TornadoVMBytecodes.ALLOC.value()) {
                // Java skips allocation after an execution graph has been captured.
                if (!executionGraphHandles.isEmpty()) {
                    return true;
                }
                bytecodeResult.getLong();
                final int argCount = bytecodeResult.getInt();
                for (int i = 0; i < argCount; i++) {
                    Object object = objects.get(bytecodeResult.getInt());
                    if (!isNativeNonBufferObject(object) && !isNativeBufferShape(object)) {
                        return false;
                    }
                }
                bytecodeResult.position(savedPosition);
                prepareCurrentNativeAllocation();
            } else if (op == TornadoVMBytecodes.DEALLOC.value()
                    || op == TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ONCE.value()
                    || op == TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ALWAYS.value()
                    || op == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS.value()
                    || op == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING.value()) {
                final int objectIndex = bytecodeResult.getInt();
                Object object = objects.get(objectIndex);
                // KernelContext and AtomicInteger have no per-object device buffer.
                if (isNativeNonBufferObject(object)) {
                    return true;
                }
                XPUDeviceBufferState state = resolveObjectState(objectIndex);
                if (op == TornadoVMBytecodes.DEALLOC.value() && !executionGraphHandles.isEmpty()) {
                    return true;
                }
                if (op == TornadoVMBytecodes.DEALLOC.value() && TornadoOptions.isDeallocateBufferEnabled() && state != null && !state.isLockedBuffer()) {
                    return false;
                }
                if (op != TornadoVMBytecodes.DEALLOC.value() && (useDependencies || !isNativeTransferSupported(object))) {
                    return false;
                }
                if (op != TornadoVMBytecodes.DEALLOC.value()) {
                    bytecodeResult.getInt(); // eventId
                    bytecodeResult.getLong(); // offset
                    markNativeTransferHostBuffer(op, objectIndex, bytecodeResult.getLong(), state);
                }
            }
            return true;
        } finally {
            bytecodeResult.position(savedPosition);
        }
    }

    // Matches C++ run_transfer: ONCE with content already on device is a no-op, ALWAYS copies,
    // and D2H always writes back. Staging flatten is only needed for those actual copies.
    private void markNativeTransferHostBuffer(byte op, int objectIndex, long sizeBatch, XPUDeviceBufferState state) {
        ensureNativeStateTables();
        if (op == TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ONCE.value()) {
            if (sizeBatch > 0 || state == null || !state.hasContent()) {
                nativeHostPackNeeded[objectIndex] = true;
            }
        } else if (op == TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ALWAYS.value()) {
            nativeHostPackNeeded[objectIndex] = true;
        } else if (op == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS.value()
                || op == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING.value()) {
            nativeHostUnpackNeeded[objectIndex] = true;
        }
    }

    /**
     * Compiles and schedules a LAUNCH without issuing it. The native loop will decode the
     * argument list and perform the first and all later kernel submissions.
     */
    private boolean prepareCurrentNativeLaunch(boolean isWarmup) {
        final int callWrapperIndex = bytecodeResult.getInt();
        final int taskIndex = bytecodeResult.getInt();
        final int numArgs = bytecodeResult.getInt();
        final int eventId = bytecodeResult.getInt();
        bytecodeResult.getLong(); // offset: only used by batched launches, which bail below
        final long batchThreads = bytecodeResult.getLong();

        if (useDependencies || batchThreads != 0 || !currentBatchNumberPerObject.isEmpty() || taskIndex < 0 || taskIndex >= taskExecutionContexts.size()) {
            return false;
        }
        final SchedulableTask task = taskExecutionContexts.get(taskIndex);
        if (task instanceof LibraryTask) {
            return false;
        }

        ensureNativeStateTables();
        nativeLaunchPrepared[taskIndex] = false;
        final XPUExecutionFrame executionFrame = compileTaskFromBytecodeToBinary(callWrapperIndex, numArgs, eventId, taskIndex, batchThreads);
        final int localTaskIndex = globalToLocalTaskIndex(taskIndex);
        TornadoInstalledCode installedCode = installedCodes[localTaskIndex];
        if (installedCode == null) {
            installedCode = interpreterDevice.getCodeFromCache(graphExecutionContext.getExecutionPlanId(), task);
            installedCodes[localTaskIndex] = installedCode;
        }

        final int[] atomics = (task instanceof PrebuiltTask prebuiltTask) ? prebuiltTask.getAtomics() : interpreterDevice.checkAtomicsForTask(task);
        if (installedCode == null || executionFrame.stackFrame == null || !(task.meta() instanceof TaskDataContext dataContext)
                || !installedCode.prepareForNativeLaunch(dataContext, batchThreads)) {
            return false;
        }
        if (!isWarmup && !publishNativeAtomics(task, numArgs, atomics)) {
            return false;
        }

        nativeLaunchFrames[taskIndex] = executionFrame.stackFrame;
        nativeLaunchPrepared[taskIndex] = true;
        return true;
    }

    /*
     * Same host-side atomics write as executeLaunch, done before the native loop so C++
     * can bind the shared region that is already kernel argument 3. AtomicInteger itself
     * is not a kernel argument.
     */
    private boolean publishNativeAtomics(SchedulableTask task, int numArgs, int[] atomicsArray) {
        if (atomicsArray == null) {
            return true;
        }
        if (nativeAtomicCopyBack == null) {
            nativeAtomicCopyBack = new boolean[objects.size()];
        }
        for (int i = 0; i < numArgs; i++) {
            final byte argType = bytecodeResult.get();
            final int argIndex = bytecodeResult.getInt();
            if (argType != TornadoVMBytecodes.PUSH_REFERENCE_ARGUMENT.value()) {
                continue;
            }
            Object object = objects.get(argIndex);
            if (!isObjectAtomic(object)) {
                continue;
            }
            final DataObjectState globalState = resolveGlobalObjectState(argIndex);
            final XPUDeviceBufferState objectState = globalState.getDeviceBufferState(interpreterDevice);
            if (!isObjectInAtomicRegion(objectState, interpreterDevice, task)) {
                continue;
            }
            atomicsArray = interpreterDevice.updateAtomicRegionAndObjectState(task, atomicsArray, i, object, objectState);
            nativeAtomicCopyBack[argIndex] = true;
        }
        XPUBuffer bufferAtomics = interpreterDevice.createOrReuseAtomicsBuffer(atomicsArray, Access.READ_WRITE);
        bufferAtomics.enqueueWrite(graphExecutionContext.getExecutionPlanId(), null, 0, 0, null, false);
        return true;
    }

    private static boolean isPhaseFiveMemoryOpcode(byte op) {
        return op == TornadoVMBytecodes.ALLOC.value() || op == TornadoVMBytecodes.DEALLOC.value() || op == TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ONCE.value()
                || op == TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ALWAYS.value() || op == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS.value()
                || op == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING.value();
    }

    private void refreshNativeLaunchTables() {
        Arrays.fill(nativeKernelHandles, 0L);
        Arrays.fill(nativeProgramHandles, 0L);
        Arrays.fill(nativeLaunchMetadata, 0L);

        for (int taskIndex = 0; taskIndex < taskExecutionContexts.size(); taskIndex++) {
            if (!nativeLaunchPrepared[taskIndex]) {
                continue;
            }
            final TornadoInstalledCode installedCode = installedCodes[globalToLocalTaskIndex(taskIndex)];
            final KernelStackFrame frame = nativeLaunchFrames[taskIndex];
            final SchedulableTask task = taskExecutionContexts.get(taskIndex);
            if (installedCode == null || !installedCode.isValid() || frame == null || !frame.isValid() || !(task.meta() instanceof TaskDataContext meta)) {
                nativeLaunchPrepared[taskIndex] = false;
                continue;
            }

            final long kernelHandle = installedCode.getNativeKernelHandle();
            final long frameBuffer = frame.getNativeFrameBuffer();
            final long constantBuffer = frame.getNativeConstantBuffer();
            final long atomicBuffer = frame.getNativeAtomicBuffer();
            if (kernelHandle == 0 || frameBuffer == 0 || constantBuffer == 0 || atomicBuffer == 0) {
                nativeLaunchPrepared[taskIndex] = false;
                continue;
            }

            final int base = taskIndex * NativeBytecodeInterpreter.LAUNCH_META_STRIDE;
            nativeKernelHandles[taskIndex] = kernelHandle;
            nativeLaunchMetadata[base + NativeBytecodeInterpreter.LAUNCH_META_FRAME_BUFFER] = frameBuffer;
            nativeLaunchMetadata[base + NativeBytecodeInterpreter.LAUNCH_META_CONSTANT_BUFFER] = constantBuffer;
            nativeLaunchMetadata[base + NativeBytecodeInterpreter.LAUNCH_META_ATOMIC_BUFFER] = atomicBuffer;
            nativeLaunchMetadata[base + NativeBytecodeInterpreter.LAUNCH_META_LOCAL_MEMORY] = meta.getLocalSize();

            final WorkerGrid workerGrid = gridScheduler == null ? null : gridScheduler.get(task.getId());
            final int dimensions;
            final long[] globalOffset;
            final long[] globalWork;
            final long[] localWork;
            if (workerGrid != null) {
                dimensions = workerGrid.dimension();
                globalOffset = workerGrid.getGlobalOffset();
                globalWork = workerGrid.getGlobalWork();
                localWork = workerGrid.getLocalWork();
                copyLaunchVector(workerGrid.getGlobalWork(), base + NativeBytecodeInterpreter.LAUNCH_META_CONTEXT);
            } else if (meta.isParallel()) {
                dimensions = meta.getDims();
                globalOffset = meta.getGlobalOffset();
                globalWork = meta.getGlobalWork();
                final boolean useDriverScheduling = interpreterDevice.getTornadoVMBackend() == TornadoVMBackendType.METAL
                        ? meta.shouldUseMetalDriverScheduling()
                        : meta.shouldUseOpenCLDriverScheduling();
                localWork = useDriverScheduling ? null : meta.getLocalWork();
            } else {
                dimensions = 1;
                globalOffset = null;
                final long[] sequentialGlobalWork = meta.getGlobalWork();
                final boolean singleThreadLaunch = sequentialGlobalWork == null || sequentialGlobalWork.length == 0;
                globalWork = singleThreadLaunch ? new long[] { 1 } : sequentialGlobalWork;
                localWork = singleThreadLaunch ? new long[] { 1 } : meta.getLocalWork();
            }

            if (dimensions < 1 || dimensions > 3 || globalWork == null) {
                nativeLaunchPrepared[taskIndex] = false;
                nativeKernelHandles[taskIndex] = 0L;
                continue;
            }
            nativeLaunchMetadata[base + NativeBytecodeInterpreter.LAUNCH_META_DIMENSIONS] = dimensions;
            copyLaunchVector(globalOffset, base + NativeBytecodeInterpreter.LAUNCH_META_GLOBAL_OFFSET);
            copyLaunchVector(globalWork, base + NativeBytecodeInterpreter.LAUNCH_META_GLOBAL_WORK);
            copyLaunchVector(localWork, base + NativeBytecodeInterpreter.LAUNCH_META_LOCAL_WORK);
            long launchFlags = NativeBytecodeInterpreter.LAUNCH_FLAG_SUPPORTED;
            if (localWork != null) {
                launchFlags |= NativeBytecodeInterpreter.LAUNCH_FLAG_HAS_LOCAL_WORK;
            }
            nativeLaunchMetadata[base + NativeBytecodeInterpreter.LAUNCH_META_FLAGS] = launchFlags;
        }
    }

    private void copyLaunchVector(long[] source, int destination) {
        if (source == null) {
            return;
        }
        System.arraycopy(source, 0, nativeLaunchMetadata, destination, Math.min(3, source.length));
    }

    /**
     * Rebuilds the primitive tables the native loop reads from the current Java interpreter
     * state. Called before every native crossing so a Java ALLOC/LAUNCH that ran since the
     * last crossing is visible. Launch slots stay zero until the Java prepass has compiled
     * and prepared that task for native submission.
     */
    private void refreshNativeStateTables() {
        ensureNativeStateTables();
        for (int i = 0; i < objects.size(); i++) {
            final Object object = objects.get(i);
            final Object transferObject = nativeTransferObject(object);
            final boolean backendNativeObject = interpreterDevice instanceof TornadoNativeInterpreterSupport nativeDevice && nativeDevice.supportsNativeInterpreterObject(object);
            nativeObjects[i] = transferObject;
            nativeHostPointers[i] = 0L;
            nativeObjectFlags[i] = 0;
            nativeObjectKinds[i] = isNativeBufferShape(object) ? nativeObjectKind(transferObject) : NativeBytecodeInterpreter.OBJECT_KIND_UNSUPPORTED;
            nativeDataOffsets[i] = 0;
            nativePartialCopySizes[i] = 0;
            if (object instanceof AtomicInteger) {
                nativeObjectKinds[i] = NativeBytecodeInterpreter.OBJECT_KIND_ATOMIC;
            }
            if (isObjectKernelContext(object)) {
                nativeObjectFlags[i] |= NativeBytecodeInterpreter.OBJ_KERNEL_CONTEXT;
            }
            if (backendNativeObject) {
                nativeObjectFlags[i] |= NativeBytecodeInterpreter.OBJ_STAGED_HOST_BUFFER;
                nativeObjectKinds[i] = NativeBytecodeInterpreter.OBJECT_KIND_SEGMENT;
            }
            if (isPersistentObject(object)) {
                nativeObjectFlags[i] |= NativeBytecodeInterpreter.OBJ_PERSISTENT;
            }
            if (nativeAllocationPrepared[i]) {
                nativeObjectFlags[i] |= NativeBytecodeInterpreter.OBJ_NATIVE_ALLOCATION_PREPARED;
            }
            nativeObjectAccesses[i] = objectAccesses.get(object).position;
            XPUDeviceBufferState state = resolveObjectState(i);
            if (state == null || !state.hasObjectBuffer()) {
                nativeBufferHandles[i] = 0L;
                nativeBufferOffsets[i] = 0L;
                nativeBufferSizes[i] = 0L;
                continue;
            }
            if (state.hasContent()) {
                nativeObjectFlags[i] |= NativeBytecodeInterpreter.OBJ_HAS_CONTENT;
            }
            if (state.isLockedBuffer()) {
                nativeObjectFlags[i] |= NativeBytecodeInterpreter.OBJ_LOCKED;
            }
            XPUBuffer buffer = state.getXPUBuffer();
            if (object instanceof AtomicInteger) {
                nativeBufferHandles[i] = 0L;
                nativeBufferOffsets[i] = 0L;
                nativeBufferSizes[i] = buffer.size();
                continue;
            }
            if (backendNativeObject) {
                if (nativeHostPackNeeded[i] || nativeHostUnpackNeeded[i]) {
                    nativeHostPointers[i] = ((TornadoNativeInterpreterSupport) interpreterDevice).prepareNativeInterpreterHostBuffer(object, state);
                    TornadoInternalError.guarantee(nativeHostPointers[i] != 0, "backend did not provide a native host buffer for %s", object.getClass().getName());
                    nativeDataOffsets[i] = 0;
                }
            } else {
                nativeHostPointers[i] = NativeBytecodeInterpreter.hostPointerOf(transferObject);
                nativeDataOffsets[i] = nativeDataOffset(object, transferObject, buffer);
            }
            nativeBufferHandles[i] = Math.max(0L, buffer.toBuffer());
            nativeBufferOffsets[i] = buffer.getBufferOffset();
            nativeBufferSizes[i] = buffer.size();
            nativePartialCopySizes[i] = state.getPartialCopySize();
        }
        refreshNativeLaunchTables();
    }

    private static Object nativeTransferObject(Object object) {
        if (object != null && object.getClass().getAnnotation(Vector.class) != null && TornadoUtils.hasAnnotatedField(object, Payload.class)) {
            return TornadoUtils.getAnnotatedObjectFromField(object, Payload.class);
        }
        return object;
    }

    private boolean isNativeTransferSupported(Object object) {
        if (interpreterDevice instanceof TornadoNativeInterpreterSupport nativeDevice && nativeDevice.supportsNativeInterpreterObject(object)) {
            return true;
        }
        if (!isNativeBufferShape(object)) {
            return false;
        }
        Object transferObject = nativeTransferObject(object);
        return NativeBytecodeInterpreter.hostPointerOf(transferObject) != 0 || nativeObjectKind(transferObject) != NativeBytecodeInterpreter.OBJECT_KIND_UNSUPPORTED;
    }

    /* These are the object shapes whose existing Java wrappers map to one flat buffer. */
    private boolean isNativeBufferShape(Object object) {
        if (object == null || object instanceof AtomicInteger) {
            return false;
        }
        Class<?> type = object.getClass();
        if (object instanceof TornadoNativeArray) {
            return true;
        }
        if (type.getAnnotation(Vector.class) != null) {
            return nativeObjectKind(nativeTransferObject(object)) != NativeBytecodeInterpreter.OBJECT_KIND_UNSUPPORTED;
        }
        if (type.isArray() && type.getComponentType().isPrimitive() && nativeObjectKind(object) != NativeBytecodeInterpreter.OBJECT_KIND_UNSUPPORTED) {
            return true;
        }
        return interpreterDevice instanceof TornadoNativeInterpreterSupport nativeDevice && nativeDevice.supportsNativeInterpreterObject(object);
    }

    private static byte nativeObjectKind(Object object) {
        if (object instanceof AtomicInteger) {
            return NativeBytecodeInterpreter.OBJECT_KIND_ATOMIC;
        }
        if (NativeBytecodeInterpreter.hostPointerOf(object) != 0) {
            return NativeBytecodeInterpreter.OBJECT_KIND_SEGMENT;
        }
        if (object == null || !object.getClass().isArray()) {
            return NativeBytecodeInterpreter.OBJECT_KIND_UNSUPPORTED;
        }
        Class<?> component = object.getClass().getComponentType();
        if (component == byte.class) {
            return NativeBytecodeInterpreter.OBJECT_KIND_BYTE_ARRAY;
        }
        if (component == char.class) {
            return NativeBytecodeInterpreter.OBJECT_KIND_CHAR_ARRAY;
        }
        if (component == short.class) {
            return NativeBytecodeInterpreter.OBJECT_KIND_SHORT_ARRAY;
        }
        if (component == int.class) {
            return NativeBytecodeInterpreter.OBJECT_KIND_INT_ARRAY;
        }
        if (component == long.class) {
            return NativeBytecodeInterpreter.OBJECT_KIND_LONG_ARRAY;
        }
        if (component == float.class) {
            return NativeBytecodeInterpreter.OBJECT_KIND_FLOAT_ARRAY;
        }
        if (component == double.class) {
            return NativeBytecodeInterpreter.OBJECT_KIND_DOUBLE_ARRAY;
        }
        return NativeBytecodeInterpreter.OBJECT_KIND_UNSUPPORTED;
    }

    private static long nativeDataOffset(Object object, Object transferObject, XPUBuffer buffer) {
        if (object != transferObject) {
            return 0;
        }
        if (NativeBytecodeInterpreter.hostPointerOf(transferObject) != 0) {
            return 0;
        }
        if (!transferObject.getClass().isArray()) {
            return 0;
        }
        final int elementBytes = switch (nativeObjectKind(transferObject)) {
            case NativeBytecodeInterpreter.OBJECT_KIND_BYTE_ARRAY -> Byte.BYTES;
            case NativeBytecodeInterpreter.OBJECT_KIND_CHAR_ARRAY -> Character.BYTES;
            case NativeBytecodeInterpreter.OBJECT_KIND_SHORT_ARRAY -> Short.BYTES;
            case NativeBytecodeInterpreter.OBJECT_KIND_INT_ARRAY -> Integer.BYTES;
            case NativeBytecodeInterpreter.OBJECT_KIND_LONG_ARRAY -> Long.BYTES;
            case NativeBytecodeInterpreter.OBJECT_KIND_FLOAT_ARRAY -> Float.BYTES;
            case NativeBytecodeInterpreter.OBJECT_KIND_DOUBLE_ARRAY -> Double.BYTES;
            default -> 0;
        };
        TornadoInternalError.guarantee(elementBytes > 0, "unsupported primitive array kind");
        long payloadBytes = (long) java.lang.reflect.Array.getLength(transferObject) * elementBytes;
        return buffer.size() - payloadBytes;
    }

    /**
     * Copies {@code HAS_CONTENT} bits the native loop set (after a ONCE transfer) back
     * onto the Java buffer state so the next {@code execute()} still skips the copy.
     */
    private void syncNativeObjectFlags() {
        if (nativeObjectFlags == null) {
            return;
        }
        boolean allocated = false;
        long allocationTotal = 0;
        for (int i = 0; i < objects.size(); i++) {
            XPUDeviceBufferState state = resolveObjectState(i);
            if (state == null) {
                continue;
            }
            final Access access = objectAccesses.get(objects.get(i));
            if ((nativeObjectFlags[i] & NativeBytecodeInterpreter.OBJ_NATIVE_ALLOCATED) != 0 && interpreterDevice instanceof TornadoNativeInterpreterSupport nativeDevice) {
                final long bytes = nativeBufferSizes[i];
                nativeDevice.attachNativeAllocation(state, access, nativeBufferHandles[i], bytes);
                nativeAllocationPrepared[i] = false;
                if (TornadoOptions.isReusedBuffersEnabled()) {
                    state.setLockBuffer(true);
                }
                allocated = true;
            }
            if ((nativeObjectFlags[i] & NativeBytecodeInterpreter.OBJ_NATIVE_DEALLOCATED) != 0 && interpreterDevice instanceof TornadoNativeInterpreterSupport nativeDevice) {
                final long handle = state.getXPUBuffer().toBuffer();
                nativeDevice.detachNativeAllocation(state, access, handle);
                nativeAllocationPrepared[i] = false;
                continue;
            }
            if ((nativeObjectFlags[i] & NativeBytecodeInterpreter.OBJ_HAS_CONTENT) != 0) {
                state.setContents(true);
            }
            if (state.hasObjectBuffer()) {
                allocationTotal += state.getXPUBuffer().size();
            }
        }
        if (allocated) {
            graphExecutionContext.setCurrentDeviceMemoryUsage(allocationTotal);
        }
    }

    private void completeNativeInterpreterHostBuffers() {
        if (!(interpreterDevice instanceof TornadoNativeInterpreterSupport nativeDevice) || nativeObjectFlags == null || nativeHostUnpackNeeded == null) {
            return;
        }
        for (int i = 0; i < objects.size(); i++) {
            if (!nativeHostUnpackNeeded[i] || (nativeObjectFlags[i] & NativeBytecodeInterpreter.OBJ_STAGED_HOST_BUFFER) == 0) {
                continue;
            }
            XPUDeviceBufferState state = resolveObjectState(i);
            if (state != null && state.hasObjectBuffer()) {
                nativeDevice.completeNativeInterpreterHostBuffer(objects.get(i), state);
            }
        }
    }

    /*
     * Native D2H of AtomicInteger is a no-op. Copy the shared atomics region back into
     * each AtomicInteger the same way Java streamOutBlocking does.
     */
    private void completeNativeAtomics() {
        if (nativeAtomicCopyBack == null) {
            return;
        }
        for (int i = 0; i < nativeAtomicCopyBack.length; i++) {
            if (!nativeAtomicCopyBack[i]) {
                continue;
            }
            XPUDeviceBufferState state = resolveObjectState(i);
            if (state != null && state.isAtomicRegionPresent()) {
                interpreterDevice.streamOutBlocking(graphExecutionContext.getExecutionPlanId(), objects.get(i), 0, state, null);
            }
            nativeAtomicCopyBack[i] = false;
        }
    }

    private void ensureNativeStateTables() {
        if (nativeBufferHandles != null) {
            return;
        }
        final int objectCount = objects.size();
        nativeBufferHandles = new long[objectCount];
        nativeBufferOffsets = new long[objectCount];
        nativeBufferSizes = new long[objectCount];
        nativeHostPointers = new long[objectCount];
        nativeObjectFlags = new byte[objectCount];
        nativeObjectAccesses = new byte[objectCount];
        nativeObjects = objects.toArray();
        nativeObjectKinds = new byte[objectCount];
        nativeDataOffsets = new long[objectCount];
        nativePartialCopySizes = new long[objectCount];
        nativeAllocationPrepared = new boolean[objectCount];
        nativeAtomicCopyBack = new boolean[objectCount];
        nativeHostPackNeeded = new boolean[objectCount];
        nativeHostUnpackNeeded = new boolean[objectCount];
        final int taskCount = taskExecutionContexts.size();
        nativeKernelHandles = new long[taskCount];
        nativeProgramHandles = new long[taskCount];
        nativeLaunchMetadata = new long[taskCount * NativeBytecodeInterpreter.LAUNCH_META_STRIDE];
        nativeLaunchFrames = new KernelStackFrame[taskCount];
        nativeLaunchPrepared = new boolean[taskCount];
        nativeConstants = NativeBytecodeInterpreter.packConstants(constants);
    }

    private void preCompileLaunchesInCaptureRegion() {
        int savedPosition = bytecodeResult.position();

        while (bytecodeResult.hasRemaining()) {
            final byte op = bytecodeResult.get();

            if (op == TornadoVMBytecodes.CUDA_GRAPH_END_CAPTURE.value()) {
                bytecodeResult.getInt();
                break;
            } else if (op == TornadoVMBytecodes.LAUNCH.value()) {
                final int callWrapperIndex = bytecodeResult.getInt();
                final int taskIndex = bytecodeResult.getInt();
                final int numArgs = bytecodeResult.getInt();
                final int eventId = bytecodeResult.getInt();
                final long offset = bytecodeResult.getLong();
                final long batchThreads = bytecodeResult.getLong();

                compileTaskFromBytecodeToBinary(callWrapperIndex, numArgs,
                        eventId, taskIndex, batchThreads);

                for (int i = 0; i < numArgs; i++) {
                    bytecodeResult.get();
                    bytecodeResult.getInt();
                }
            } else {
                skipBytecodeOperands(op);
            }
        }

        bytecodeResult.position(savedPosition);
    }

    private void skipToAfterEndCapture(int graphId) {
        while (bytecodeResult.hasRemaining()) {
            final byte op = bytecodeResult.get();
            if (op == TornadoVMBytecodes.CUDA_GRAPH_END_CAPTURE.value()) {
                int endGraphId = bytecodeResult.getInt();
                if (endGraphId == graphId) {
                    return;
                }
            } else {
                skipBytecodeOperands(op);
            }
        }
    }

    public void destroyExecutionGraphs() {
        for (Map.Entry<Integer, Long> entry : executionGraphHandles.entrySet()) {
            interpreterDevice.destroyExecutionGraph(entry.getValue());
        }
        executionGraphHandles.clear();
    }

    private void skipBytecodeOperands(byte op) {
        if (op == TornadoVMBytecodes.ALLOC.value()) {
            bytecodeResult.getLong();  // sizeBatch
            int argSize = bytecodeResult.getInt();
            for (int i = 0; i < argSize; i++) {
                bytecodeResult.getInt();
            }
        } else if (op == TornadoVMBytecodes.DEALLOC.value()) {
            bytecodeResult.getInt();  // objectIndex
        } else if (op == TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ONCE.value()
                || op == TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ALWAYS.value()
                || op == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS.value()
                || op == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING.value()) {
            bytecodeResult.getInt();   // objectIndex
            bytecodeResult.getInt();   // eventId
            bytecodeResult.getLong();  // offset
            bytecodeResult.getLong();  // sizeBatch
        } else if (op == TornadoVMBytecodes.LAUNCH.value()) {
            bytecodeResult.getInt();   // callWrapperIndex
            bytecodeResult.getInt();   // taskIndex
            int numArgs = bytecodeResult.getInt();
            bytecodeResult.getInt();   // eventId
            bytecodeResult.getLong();  // offset
            bytecodeResult.getLong();  // batchThreads
            for (int i = 0; i < numArgs; i++) {
                bytecodeResult.get();      // PUSH_CONSTANT or PUSH_REFERENCE opcode
                bytecodeResult.getInt();   // argIndex
            }
        } else if (op == TornadoVMBytecodes.ADD_DEPENDENCY.value()
                || op == TornadoVMBytecodes.BARRIER.value()
                || op == TornadoVMBytecodes.CONTEXT.value()) {
            bytecodeResult.getInt();
        } else if (op == TornadoVMBytecodes.ON_DEVICE.value()
                || op == TornadoVMBytecodes.PERSIST.value()) {
            bytecodeResult.getInt();
            bytecodeResult.getInt();
        } else if (op == TornadoVMBytecodes.CUDA_GRAPH_BEGIN_CAPTURE.value()
                || op == TornadoVMBytecodes.CUDA_GRAPH_LAUNCH.value()
                || op == TornadoVMBytecodes.CUDA_GRAPH_DESTROY.value()) {
            bytecodeResult.getInt();  // graphId
        } else if (op == TornadoVMBytecodes.INIT.value()) {
            bytecodeResult.getLong();
            bytecodeResult.getInt();
        } else if (op == TornadoVMBytecodes.BEGIN.value()
                || op == TornadoVMBytecodes.END.value()) {
            // no operands
        } else if (op == TornadoVMBytecodes.PUSH_CONSTANT_ARGUMENT.value()
                || op == TornadoVMBytecodes.PUSH_REFERENCE_ARGUMENT.value()) {
            bytecodeResult.getInt();
        }
    }

    private void executeGraphBeginCapture(StringBuilder logBuilder, int graphId) {
        if (!interpreterDevice.supportsExecutionGraphs()) {
            throw new TornadoBailoutRuntimeException(
                    "EXECUTION_GRAPH_BEGIN_CAPTURE bytecode reached a device that does not support " +
                            "execution graphs: " + interpreterDevice.getDeviceName());
        }
        if (TornadoOptions.LOG_BYTECODES()) {
            logBuilder.append("bc: ").append(InterpreterUtilities.debugHighLightBC(
                    "EXECUTION_GRAPH_BEGIN_CAPTURE")).append(" graphId=").append(graphId).append("\n");
        }
        interpreterDevice.beginExecutionGraphCapture(graphExecutionContext.getExecutionPlanId());
    }

    private void executeGraphEndCapture(StringBuilder logBuilder, int graphId) {
        if (TornadoOptions.LOG_BYTECODES()) {
            logBuilder.append("bc: ").append(InterpreterUtilities.debugHighLightBC(
                    "EXECUTION_GRAPH_END_CAPTURE")).append(" graphId=").append(graphId).append("\n");
        }
        long handle = interpreterDevice.endExecutionGraphCaptureAndInstantiate(
                graphExecutionContext.getExecutionPlanId());
        executionGraphHandles.put(graphId, handle);
    }

    private int executeGraphLaunch(StringBuilder logBuilder, int graphId) {
        if (TornadoOptions.LOG_BYTECODES()) {
            logBuilder.append("bc: ").append(InterpreterUtilities.debugHighLightBC(
                    "EXECUTION_GRAPH_LAUNCH")).append(" graphId=").append(graphId).append("\n");
        }
        int event = interpreterDevice.launchExecutionGraph(
                graphExecutionContext.getExecutionPlanId(),
                executionGraphHandles.get(graphId));

        return event;
    }

    private void initWaitEventList() {
        // Clear whole rows, not just the prefix up to eventsIndexes: resetEventIndexes() rewinds an
        // event list in the middle of an execution, so entries can live beyond the current index and a
        // partial clear would leave stale event ids behind. Rows never used stay null and cost nothing.
        for (int[] waitList : events) {
            if (waitList != null) {
                Arrays.fill(waitList, -1);
            }
        }
        Arrays.fill(eventsIndexes, 0);
    }

    /**
     * Wait-list for a dependency list. A row that was never written stays null, which the drivers treat
     * exactly like a list of -1 entries: no events to wait on.
     */
    private int[] waitListFor(int eventId) {
        return (eventId >= 0 && eventId < events.length) ? events[eventId] : null;
    }

    private int[] waitListForWrite(int eventId) {
        int[] waitList = events[eventId];
        if (waitList == null) {
            waitList = new int[MAX_EVENTS];
            Arrays.fill(waitList, -1);
            events[eventId] = waitList;
        }
        return waitList;
    }

    /** Whether the object already has a device buffer in this interpreter's object state. */
    private boolean hasDeviceBuffer(int arg) {
        XPUDeviceBufferState state = resolveObjectState(arg);
        return state != null && state.getXPUBuffer() != null;
    }

    /**
     * Checks if the given object exists in the persistent task objects map in
     * order to prevent excess allocations.
     *
     * @param object
     *     The object to search for in the persistent tasks
     * @return true if the object is found in any persistent task, otherwise false
     */
    private boolean isPersistentObject(Object object) {
        if (graphExecutionContext == null || object == null) {
            return false;
        }
        // Plain loops: this runs for every object of every ALLOC bytecode, and a stream pipeline per
        // object allocated hundreds of MiB over a run.
        for (List<Object> taskObjects : graphExecutionContext.getPersistedTaskToObjectsMap().values()) {
            if (taskObjects != null && taskObjects.contains(object)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Counts and classifies objects in the args array by determining which objects are persistent
     * and which need to be allocated.
     *
     * @param args
     *     Array of object indices to process from the object store
     * @return Information about objects to allocate including counts of persistent and non-persistent objects
     */
    private ObjectAllocationInfo countAndClassifyObjects(int[] args) {
        // Count only persistent objects that are actually in the current args array. An object that is
        // persistent but has no device buffer yet (its producing task-graph has not executed - e.g. the
        // first frames of a pipeline that raycasts only once a model exists) still has to be allocated
        // here, otherwise the pre-allocated size lookup dereferences a null buffer.
        int persistentObjectsInArgs = 0;
        for (int arg : args) {
            Object dataObject = this.objects.get(arg);
            if (isPersistentObject(dataObject) && hasDeviceBuffer(arg)) {
                persistentObjectsInArgs++;
            }
        }

        // Calculate allocation based on non-persistent objects in args
        int objectsToAlloc = args.length - persistentObjectsInArgs;

        return new ObjectAllocationInfo(persistentObjectsInArgs, objectsToAlloc);
    }

    private int executeAlloc(StringBuilder logBuilder, int[] args, long sizeBatch) {
        // Extract the counting and classification of objects into a separate method
        ObjectAllocationInfo allocationInfo = countAndClassifyObjects(args);

        Object[] objects = new Object[allocationInfo.objectsToAlloc];
        Access[] accesses = new Access[allocationInfo.objectsToAlloc];
        XPUDeviceBufferState[] objectStates = new XPUDeviceBufferState[allocationInfo.objectsToAlloc];

        int allocCounter = 0;
        long preAllocatedSizes = 0L;

        for (int arg : args) {
            Object dataObject = this.objects.get(arg);
            if (!isPersistentObject(dataObject) || !hasDeviceBuffer(arg)) {
                objects[allocCounter] = this.objects.get(arg);
                objectStates[allocCounter] = resolveObjectState(arg);
                accesses[allocCounter] = this.objectAccesses.get(objects[allocCounter]);
                allocCounter++;
            } else {
                XPUDeviceBufferState state = resolveObjectState(arg);
                preAllocatedSizes += state.getXPUBuffer().size();
            }
        }

        // total size of objects pre-allocated and current allocation
        long allocationSize = interpreterDevice.allocateObjects(objects, sizeBatch, objectStates, accesses);
        long allocationsTotalSize = allocationSize + preAllocatedSizes;
        increaseBatchNumber(sizeBatch);

        // Re-establish the buffer-reuse lock on (re)allocation. Locking otherwise happens only
        // once, when the task graph is built - so a SECOND execution plan created from the same
        // ImmutableTaskGraph (the first plan's close() unlocked the shared object states) would
        // run with unlocked buffers: every per-execution DEALLOC frees for real, resetting the
        // buffer contents, and every TRANSFER_HOST_TO_DEVICE_ONCE re-uploads on each execution.
        if (TornadoOptions.isReusedBuffersEnabled()) {
            for (XPUDeviceBufferState objectState : objectStates) {
                objectState.setLockBuffer(true);
            }
        }

        // Dump printing after object allocation, so the XPU-Buffer is created,
        // and we can query the size without having to use Java type analysis
        // to obtain the size at this point. 
        if (TornadoOptions.LOG_BYTECODES()) {
            int objIndex = 0;
            for (XPUDeviceBufferState state : objectStates) {
                long size = state.getXPUBuffer().size();
                if (!state.isBufferReused()) {
                    logBuilder.append(captureIndent());
                    DebugInterpreter.logAllocObject(objects[objIndex], interpreterDevice, size, sizeBatch, logBuilder);
                }
                objIndex++;
            }
        }

        graphExecutionContext.setCurrentDeviceMemoryUsage(allocationsTotalSize);

        // Register allocations values in the profiler only if the profiler is enabled
        if (TornadoOptions.isProfilerEnabled() && allocationSize > 0) {
            for (XPUDeviceBufferState objectState : objectStates) {
                timeProfiler.addValueToMetric(ProfilerType.ALLOCATION_BYTES, TimeProfiler.NO_TASK_NAME, objectState.getXPUBuffer().size());
            }
        }
        return -1;
    }

    private void increaseBatchNumber(long sizeBatch) {
        if (sizeBatch != 0) {
            for (Object object : objects) {
                int previousBatch = currentBatchNumberPerObject.get(object);
                currentBatchNumberPerObject.replace(object, previousBatch, ++previousBatch);
            }
        }
    }

    private int executeDeAlloc(StringBuilder tornadoVMBytecodeList, final int objectIndex) {
        Object object = objects.get(objectIndex);

        // Fast path for buffer reuse (the default): a locked buffer is never freed, so there is nothing
        // to do here. Taking it early skips the batch bookkeeping and the synchronized device call, both
        // of which are paid once per object per execution - dominant for plans made of many small graphs.
        if (resolveObjectState(objectIndex).isLockedBuffer()) {
            if (TornadoOptions.LOG_BYTECODES() && isNotObjectAtomic(object)) {
                DebugInterpreter.logDeallocObject(object, interpreterDevice, tornadoVMBytecodeList, false);
            }
            return -1;
        }

        if (!currentBatchNumberPerObject.isEmpty() && !currentBatchNumberPerObject.isEmpty()) {
            int currentBatchNumber = currentBatchNumberPerObject.get(object);
            int totalNumberOfBatches = totalEvenBatchesPerObject.get(object);

            if (currentBatchNumber < totalNumberOfBatches) {
                return -1;
            }
        }

        final XPUDeviceBufferState objectState = resolveObjectState(objectIndex);
        long spaceDeallocated = interpreterDevice.deallocate(objectState);
        // Update current device area use
        if (TornadoOptions.LOG_BYTECODES() && isNotObjectAtomic(object)) {
            boolean materializeDealloc = spaceDeallocated != 0;
            DebugInterpreter.logDeallocObject(object, interpreterDevice, tornadoVMBytecodeList, materializeDealloc);
        }
        graphExecutionContext.setCurrentDeviceMemoryUsage(graphExecutionContext.getCurrentDeviceMemoryUsage() - spaceDeallocated);
        return -1;
    }

    private int executeOnDevice(StringBuilder logBuilder, final int objectIndex, final int eventId) {
        Object object = objects.get(objectIndex);
        if (TornadoOptions.LOG_BYTECODES()) {
            DebugInterpreter.logOnDeviceObject(object, interpreterDevice, logBuilder);
        }
        resetEventIndexes(eventId);
        return -1;
    }

    private int executePersist(StringBuilder logBuilder, final int objectIndex, final int eventId) {
        Object object = objects.get(objectIndex);
        if (TornadoOptions.PRINT_BYTECODES) {
            DebugInterpreter.logPersistedObject(object, interpreterDevice, logBuilder);
        }
        resetEventIndexes(eventId);
        return -1;
    }

    private int transferHostToDeviceOnce(StringBuilder logBuilder, final int objectIndex, final long offset, final int eventId, final long sizeBatch, final int[] eventWaitList) {
        Object object = objects.get(objectIndex);

        if (isObjectKernelContext(object)) {
            return -1;
        }

        final XPUDeviceBufferState objectState = resolveObjectState(objectIndex);

        // We need to stream-in when using batches, because the whole data is not copied
        List<Integer> allEvents;
        if (sizeBatch > 0) {
            allEvents = interpreterDevice.streamIn(graphExecutionContext.getExecutionPlanId(), object, sizeBatch, offset, objectState, eventWaitList);
        } else {
            allEvents = interpreterDevice.ensurePresent(graphExecutionContext.getExecutionPlanId(), object, objectState, eventWaitList, sizeBatch, offset);
        }
        resetEventIndexes(eventId);

        if (TornadoOptions.LOG_BYTECODES() && isNotObjectAtomic(object)) {
            long sizeObject = objectState.getXPUBuffer().size();
            logBuilder.append(captureIndent());
            DebugInterpreter.logTransferToDeviceOnce(allEvents, object, interpreterDevice, sizeObject, sizeBatch, offset, eventId, logBuilder);
        }

        if (TornadoOptions.isProfilerEnabled() && !insideCaptureRegion && allEvents != null) {
            for (Integer e : allEvents) {
                Event event = interpreterDevice.resolveEvent(graphExecutionContext.getExecutionPlanId(), e);
                event.waitForEvents(graphExecutionContext.getExecutionPlanId());
                long copyInTimer = timeProfiler.getTimer(ProfilerType.COPY_IN_TIME);
                copyInTimer += event.getElapsedTime();
                timeProfiler.setTimer(ProfilerType.COPY_IN_TIME, copyInTimer);
                timeProfiler.addValueToMetric(ProfilerType.TOTAL_COPY_IN_SIZE_BYTES, TimeProfiler.NO_TASK_NAME, objectState.getXPUBuffer().size());
                long dispatchValue = timeProfiler.getTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME);
                dispatchValue += event.getDriverDispatchTime();
                timeProfiler.setTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME, dispatchValue);
            }
        }

        // return the eventId of the transfer event
        if (allEvents != null && !allEvents.isEmpty()) {
            return allEvents.getLast();
        }
        return -1;
    }

    private int transferHostToDeviceAlways(StringBuilder logBuilder, final int objectIndex, final long offset, final int eventId, final long sizeBatch, final int[] eventWaitList) {
        Object object = objects.get(objectIndex);

        if (isObjectKernelContext(object)) {
            return -1;
        }

        final XPUDeviceBufferState objectState = resolveObjectState(objectIndex);
        List<Integer> allEvents = interpreterDevice.streamIn(graphExecutionContext.getExecutionPlanId(), object, sizeBatch, offset, objectState, eventWaitList);

        resetEventIndexes(eventId);

        if (TornadoOptions.LOG_BYTECODES() && isNotObjectAtomic(object)) {
            long sizeObject = objectState.getXPUBuffer().size();
            logBuilder.append(captureIndent());
            DebugInterpreter.logTransferToDeviceAlways(object, interpreterDevice, sizeObject, sizeBatch, offset, eventId, logBuilder);
        }

        if (TornadoOptions.isProfilerEnabled() && !insideCaptureRegion && allEvents != null) {
            for (Integer e : allEvents) {
                Event event = interpreterDevice.resolveEvent(graphExecutionContext.getExecutionPlanId(), e);
                event.waitForEvents(graphExecutionContext.getExecutionPlanId());
                long copyInTimer = timeProfiler.getTimer(ProfilerType.COPY_IN_TIME);
                copyInTimer += event.getElapsedTime();
                timeProfiler.setTimer(ProfilerType.COPY_IN_TIME, copyInTimer);

                timeProfiler.addValueToMetric(ProfilerType.TOTAL_COPY_IN_SIZE_BYTES, TimeProfiler.NO_TASK_NAME, objectState.getXPUBuffer().size());

                long dispatchValue = timeProfiler.getTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME);
                dispatchValue += event.getDriverDispatchTime();
                timeProfiler.setTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME, dispatchValue);
            }
        }

        // return the eventId of the transfer event
        if (allEvents != null && !allEvents.isEmpty()) {
            return allEvents.getLast();
        }
        return -1;
    }

    private int transferDeviceToHost(StringBuilder logBuilder, final int objectIndex, final long offset, final int eventId, final long sizeBatch, final int[] eventWaitList) {
        Object object = objects.get(objectIndex);

        if (isObjectKernelContext(object)) {
            return 0;
        }

        final XPUDeviceBufferState objectState = resolveObjectState(objectIndex);
        if (TornadoOptions.LOG_BYTECODES()) {
            long sizeObject = objectState.getXPUBuffer().size();
            logBuilder.append(captureIndent());
            DebugInterpreter.logTransferToHostAlways(object, interpreterDevice, sizeObject, sizeBatch, offset, eventId, logBuilder);
        }

        int readEvent = interpreterDevice.streamOutBlocking(graphExecutionContext.getExecutionPlanId(), object, offset, objectState, eventWaitList);

        resetEventIndexes(eventId);

        if (TornadoOptions.isProfilerEnabled() && !insideCaptureRegion && readEvent != -1) {
            Event event = interpreterDevice.resolveEvent(graphExecutionContext.getExecutionPlanId(), readEvent);
            event.waitForEvents(graphExecutionContext.getExecutionPlanId());
            long value = timeProfiler.getTimer(ProfilerType.COPY_OUT_TIME);
            value += event.getElapsedTime();
            timeProfiler.setTimer(ProfilerType.COPY_OUT_TIME, value);

            timeProfiler.addValueToMetric(ProfilerType.TOTAL_COPY_OUT_SIZE_BYTES, TimeProfiler.NO_TASK_NAME, objectState.getXPUBuffer().size());

            long dispatchValue = timeProfiler.getTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME);
            dispatchValue += event.getDriverDispatchTime();
            timeProfiler.setTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME, dispatchValue);
        }
        return readEvent;
    }

    private void transferDeviceToHostBlocking(StringBuilder logBuilder, final int objectIndex, final long offset, final int eventId, final long sizeBatch, final int[] eventWaitList) {

        Object object = objects.get(objectIndex);

        if (isObjectKernelContext(object)) {
            return;
        }

        final XPUDeviceBufferState objectState = resolveObjectState(objectIndex);
        if (TornadoOptions.LOG_BYTECODES()) {
            long sizeOfObject = objectState.getXPUBuffer().size();
            logBuilder.append(captureIndent());
            DebugInterpreter.logTransferToHostAlwaysBlocking(object, interpreterDevice, logBuilder, sizeOfObject, sizeBatch, offset, eventId);
        }
        final int readEvent = interpreterDevice.streamOutBlocking(graphExecutionContext.getExecutionPlanId(), object, offset, objectState, eventWaitList);

        if (TornadoOptions.isProfilerEnabled() && !insideCaptureRegion && readEvent != -1) {
            Event event = interpreterDevice.resolveEvent(graphExecutionContext.getExecutionPlanId(), readEvent);
            event.waitForEvents(graphExecutionContext.getExecutionPlanId());
            long value = timeProfiler.getTimer(ProfilerType.COPY_OUT_TIME);
            value += event.getElapsedTime();
            timeProfiler.setTimer(ProfilerType.COPY_OUT_TIME, value);

            timeProfiler.addValueToMetric(ProfilerType.TOTAL_COPY_OUT_SIZE_BYTES, TimeProfiler.NO_TASK_NAME, objectState.getXPUBuffer().size());

            long dispatchValue = timeProfiler.getTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME);
            dispatchValue += event.getDriverDispatchTime();
            timeProfiler.setTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME, dispatchValue);
        }
        resetEventIndexes(eventId);
    }

    private boolean isRecompilationNeededForLastBatch(int taskIndex, SchedulableTask task, long batchThreads) {
        return (!shouldCompile(installedCodes[globalToLocalTaskIndex(taskIndex)]) && task.getBatchThreads() != 0 && task.getBatchThreads() != batchThreads);
    }

    private boolean currentBatchUsesThreadId(int currentBatch, boolean indexInWrite) {
        return (currentBatch > 0 && indexInWrite);
    }

    private void updateBatchThreads(SchedulableTask task, long batchThreads, boolean indexInWrite, int currentBatch) {
        // Set the batch size in the task information
        task.setBatchThreads(batchThreads);

        // The batch size is only set once. This is because, for the calculations of the
        // offset to be correct, we need to propagate the initial batch size, not the size
        // of the remaining chunk, if the batches are uneven.
        if (task.getBatchSize() == 0 && indexInWrite) {
            task.setBatchSize(batchThreads);
        }

        if (batchThreads != 0) {
            task.setBatchNumber(currentBatch);
        }
    }

    private void updateMeta(TaskContextInterface meta) {
        meta.setPrintKernelFlag(graphExecutionContext.meta().isPrintKernelEnabled());
        meta.setCompilerFlags(TornadoVMBackendType.OPENCL, graphExecutionContext.meta().getCompilerFlags(TornadoVMBackendType.OPENCL));
    }

    private XPUExecutionFrame compileTaskFromBytecodeToBinary(final int callWrapperIndex, final int numArgs, final int eventId, final int taskIndex, final long batchThreads) {

        if (interpreterDevice.getDeviceContext().wasReset() && finishedWarmup) {
            throw new TornadoFailureException("[ERROR] reset() was called after warmup() on device: " + interpreterDevice + "!");
        }

        boolean redeployOnDevice = graphExecutionContext.redeployOnDevice();

        final int[] waitList = (useDependencies && eventId != -1) ? waitListFor(eventId) : null;
        final SchedulableTask task = taskExecutionContexts.get(taskIndex);

        if (task instanceof LibraryTask libraryTask) {
            // Library tasks are dispatched to a native library at launch time;
            // there is nothing to compile and no kernel stack frame is needed.
            // The native context (e.g., cuBLAS handle + workspace) is created
            // eagerly here: this method also runs in the pre-compile pass before
            // CUDA graph capture starts, and native handle creation allocates
            // device memory, which is illegal inside a capture region.
            final LibraryTaskDescriptor descriptor = libraryTask.getDescriptor();
            final TornadoLibraryProvider provider = LibraryRegistry.findProvider(descriptor.getLibraryName(), interpreterDevice);
            LibraryContext preparedContext = LibraryRegistry.getOrCreateContext(provider, interpreterDevice, graphExecutionContext.getExecutionPlanId());
            // Let the provider create per-shape native plans (which may allocate
            // device memory) before any CUDA graph capture starts
            provider.prepare(descriptor, preparedContext);
            if (timeProfiler instanceof TimeProfiler) {
                timeProfiler.registerBackend(task.getId(), task.getDevice().getTornadoVMBackend().name());
                timeProfiler.registerDeviceID(task.getId(), task.meta().getBackendIndex() + ":" + task.meta().getDeviceIndex());
                timeProfiler.registerDeviceName(task.getId(), task.getDevice().getPhysicalDevice().getDeviceName());
            }
            return new XPUExecutionFrame(null, waitList);
        }

        final KernelStackFrame kernelStackFrame = resolveCallWrapper(callWrapperIndex, numArgs, this.kernelStackFrame, interpreterDevice, redeployOnDevice);

        int currentBatch = task.getBatchNumber();
        TaskContextInterface meta = task.meta();
        updateMeta(meta);

        boolean indexInWrite = interpreterDevice.loopIndexInWrite(task);
        // Check if a different batch size was used for the same kernel or
        // if the loop index is written in the output buffer, and we are not in the first batch.
        // If any is true, then the kernel needs to be recompiled.
        if (isRecompilationNeededForLastBatch(taskIndex, task, batchThreads) || currentBatchUsesThreadId(currentBatch, indexInWrite)) {
            task.forceCompilation();
            installedCodes[globalToLocalTaskIndex(taskIndex)].invalidate();
        }

        updateBatchThreads(task, batchThreads, indexInWrite, currentBatch);

        task.enableDefaultThreadScheduler(graphExecutionContext.useDefaultThreadScheduler());

        if (gridScheduler != null && gridScheduler.get(task.getId()) != null) {
            task.setUseGridScheduler(true);
            task.setGridScheduler(gridScheduler);
        }

        if (timeProfiler instanceof TimeProfiler) {
            // Register the backends only when the profiler is enabled
            timeProfiler.registerBackend(task.getId(), task.getDevice().getTornadoVMBackend().name());
            timeProfiler.registerDeviceID(task.getId(), task.meta().getBackendIndex() + ":" + task.meta().getDeviceIndex());
            timeProfiler.registerDeviceName(task.getId(), task.getDevice().getPhysicalDevice().getDeviceName());
        }

        if (shouldCompile(installedCodes[globalToLocalTaskIndex(taskIndex)])) {
            task.setDevice(interpreterDevice);
            try {
                task.attachProfiler(timeProfiler);
                if (taskIndex == (taskExecutionContexts.size() - 1)) {
                    // If it is the last task within the task-schedule or doUpdate is true -> we
                    // force compilation.
                    task.forceCompilation();
                }

                installedCodes[globalToLocalTaskIndex(taskIndex)] = interpreterDevice.installCode(graphExecutionContext.getExecutionPlanId(), task);
                profilerUpdateForPreCompiledTask(task);
                // After the compilation has been completed, increment
                // the batch number of the task and update it.
                if (indexInWrite && batchThreads != 0) {
                    task.setBatchNumber(++currentBatch);
                }
            } catch (TornadoBailoutRuntimeException e) {
                throw new TornadoBailoutRuntimeException("Unable to compile " + task.getFullName() + "\n" + "The internal error is: " + e.getMessage() + "\n" + "Stacktrace: " + Arrays.toString(e
                        .getStackTrace()), e);
            } catch (TornadoDeviceFP64NotSupported e) {
                throw e;
            } catch (InternalError e) {
                throw new TornadoBailoutRuntimeException("[Internal Error] Unable to compile " + task.getFullName() + "\n" + Arrays.toString(e.getStackTrace()));
            }
        }
        return new XPUExecutionFrame(kernelStackFrame, waitList);
    }

    private void popArgumentsFromCall(int numArgs) {
        for (int i = 0; i < numArgs; i++) {
            bytecodeResult.get();
            bytecodeResult.getInt();
        }
    }

    private int executeLaunch(StringBuilder logBuilder, final int numArgs, final int eventId, final int taskIndex, final long batchThreads, final long offset, XPUExecutionFrame executionFrame) {

        final SchedulableTask task = taskExecutionContexts.get(taskIndex);

        if (task instanceof LibraryTask libraryTask) {
            return executeLibraryLaunch(logBuilder, libraryTask, numArgs, eventId, batchThreads, executionFrame.waitList);
        }

        KernelStackFrame stackFrame = executionFrame.stackFrame;
        int[] waitList = executionFrame.waitList;

        if (installedCodes[globalToLocalTaskIndex(taskIndex)] == null) {
            // After warming-up, it is possible to get a null pointer in the task-cache due
            // to lazy compilation. In that case, we check again the code cache.
            installedCodes[globalToLocalTaskIndex(taskIndex)] = interpreterDevice.getCodeFromCache(graphExecutionContext.getExecutionPlanId(), task);
        }

        final TornadoInstalledCode installedCode = installedCodes[globalToLocalTaskIndex(taskIndex)];

        if (installedCode == null) {
            throw new TornadoBailoutRuntimeException("Code generator Failed");
        }

        int[] atomicsArray;

        atomicsArray = (task instanceof PrebuiltTask prebuiltTask) ? prebuiltTask.getAtomics() : interpreterDevice.checkAtomicsForTask(task);

        HashMap<Integer, Integer> threadDeploy = new HashMap<>();
        if (gridScheduler != null && gridScheduler.get(task.getId()) != null) {
            WorkerGrid workerGrid = gridScheduler.get(task.getId());
            long[] global = workerGrid.getGlobalWork();
            int i = 0;
            for (long maxThread : global) {
                threadDeploy.put(i++, (int) maxThread);
            }
        }
        stackFrame.reset();
        stackFrame.setKernelContext(threadDeploy);

        XPUBuffer bufferAtomics = null;

        for (int i = 0; i < numArgs; i++) {
            final byte argType = bytecodeResult.get();
            final int argIndex = bytecodeResult.getInt();

            if (argType == TornadoVMBytecodes.PUSH_CONSTANT_ARGUMENT.value()) {
                // Add a constant argument
                stackFrame.addCallArgument(constants.get(argIndex), false);
            } else if (argType == TornadoVMBytecodes.PUSH_REFERENCE_ARGUMENT.value()) {

                if (isObjectKernelContext(objects.get(argIndex))) {
                    // Mark a kernel context
                    stackFrame.addCallArgument(new KernelStackFrame.KernelContextArgument(), false);
                    continue;
                }

                final DataObjectState globalState = resolveGlobalObjectState(argIndex);
                final XPUDeviceBufferState objectState = globalState.getDeviceBufferState(interpreterDevice);
                if (!isObjectInAtomicRegion(objectState, interpreterDevice, task)) {
                    // Add a reference (arrays, vector types, panama regions)
                    stackFrame.addCallArgument(objectState.getXPUBuffer().toBuffer(), true);
                } else {
                    // Add the atomic buffer
                    atomicsArray = interpreterDevice.updateAtomicRegionAndObjectState(task, atomicsArray, i, objects.get(argIndex), objectState);
                }

            } else {
                TornadoInternalError.shouldNotReachHere();
            }
        }

        if (atomicsArray != null) {
            bufferAtomics = interpreterDevice.createOrReuseAtomicsBuffer(atomicsArray, Access.READ_WRITE);
            List<Integer> allEvents = bufferAtomics.enqueueWrite(graphExecutionContext.getExecutionPlanId(), null, 0, 0, null, false);
            if (TornadoOptions.isProfilerEnabled() && !insideCaptureRegion) {
                for (Integer e : allEvents) {
                    Event event = interpreterDevice.resolveEvent(graphExecutionContext.getExecutionPlanId(), e);
                    event.waitForEvents(graphExecutionContext.getExecutionPlanId());
                    long value = timeProfiler.getTimer(ProfilerType.COPY_IN_TIME);
                    value += event.getElapsedTime();
                    timeProfiler.setTimer(ProfilerType.COPY_IN_TIME, value);
                }
            }
            if (TornadoOptions.LOG_BYTECODES()) {
                logBuilder.append(captureIndent());
                DebugInterpreter.logStreamInAtomic(bufferAtomics, interpreterDevice, eventId, logBuilder);

            }
        }

        if (TornadoOptions.LOG_BYTECODES()) {
            logBuilder.append(captureIndent());
            DebugInterpreter.logLaunchTask(task, interpreterDevice, batchThreads, offset, eventId, logBuilder);
        }

        if (task.meta() instanceof TaskDataContext dataContext) {
            // We attach the profiler information, grid information and global threads
            dataContext.attachProfiler(timeProfiler);
            dataContext.setGridScheduler(gridScheduler);
            dataContext.setThreadInfoEnabled(graphExecutionContext.meta().isThreadInfoEnabled());

            try {
                int lastEvent = useDependencies
                        ? installedCode.launchWithDependencies(graphExecutionContext.getExecutionPlanId(), stackFrame, bufferAtomics, dataContext, batchThreads, waitList)
                        : installedCode.launchWithoutDependencies(graphExecutionContext.getExecutionPlanId(), stackFrame, bufferAtomics, dataContext, batchThreads);

                resetEventIndexes(eventId);
                return lastEvent;

            } catch (Exception e) {
                if (TornadoOptions.DEBUG) {
                    e.printStackTrace();
                }
                throw new TornadoBailoutRuntimeException("Bailout from LAUNCH Bytecode: \nReason: " + e, e);
            }
        } else {
            throw new TornadoRuntimeException("task.meta is not instanceof TaskDataContext");
        }
    }

    private int executeLibraryLaunch(StringBuilder logBuilder, LibraryTask task, final int numArgs, final int eventId, final long batchThreads, int[] waitList) {

        if (batchThreads != 0) {
            throw new TornadoRuntimeException("[ERROR] Batch processing is not supported for library tasks (task: " + task.getId() + ")");
        }

        final Object[] callArgs = new Object[numArgs];
        final long[] devicePointers = new long[numArgs];
        final boolean[] isReference = new boolean[numArgs];

        for (int i = 0; i < numArgs; i++) {
            final byte argType = bytecodeResult.get();
            final int argIndex = bytecodeResult.getInt();
            if (argType == TornadoVMBytecodes.PUSH_CONSTANT_ARGUMENT.value()) {
                callArgs[i] = constants.get(argIndex);
            } else if (argType == TornadoVMBytecodes.PUSH_REFERENCE_ARGUMENT.value()) {
                final DataObjectState globalState = resolveGlobalObjectState(argIndex);
                final XPUDeviceBufferState objectState = globalState.getDeviceBufferState(interpreterDevice);
                callArgs[i] = objects.get(argIndex);
                // Pointer to the first data element on the device, past the array header
                devicePointers[i] = objectState.getXPUBuffer().toBuffer() + TornadoNativeArray.ARRAY_HEADER;
                isReference[i] = true;
            } else {
                TornadoInternalError.shouldNotReachHere();
            }
        }

        if (useDependencies && waitList != null && !insideCaptureRegion) {
            // Ensure producer events have completed before the library call is
            // enqueued. Work on the same in-order stream is already ordered, so
            // during CUDA graph capture (single in-order stream) this is skipped
            // - waiting on pre-capture events would invalidate the capture.
            interpreterDevice.enqueueMarker(graphExecutionContext.getExecutionPlanId(), waitList);
        }

        final LibraryTaskDescriptor descriptor = task.getDescriptor();
        final TornadoLibraryProvider provider = LibraryRegistry.findProvider(descriptor.getLibraryName(), interpreterDevice);
        final LibraryContext libraryContext = LibraryRegistry.getOrCreateContext(provider, interpreterDevice, graphExecutionContext.getExecutionPlanId());

        if (TornadoOptions.LOG_BYTECODES()) {
            logBuilder.append(captureIndent());
            DebugInterpreter.logLaunchTask(task, interpreterDevice, batchThreads, 0, eventId, logBuilder);
        }

        // The CUDA-C backend does not expose device-event timestamps yet, so the
        // library call is timed on the host, bounded by stream markers so the
        // measurement covers exactly this call's device work. Disabled while
        // capturing into a CUDA graph: synchronising invalidates the capture.
        final boolean profileCall = TornadoOptions.isProfilerEnabled() && !insideCaptureRegion;
        long profilerStartTime = 0;
        if (profileCall) {
            int preEvent = interpreterDevice.enqueueMarker(graphExecutionContext.getExecutionPlanId());
            interpreterDevice.resolveEvent(graphExecutionContext.getExecutionPlanId(), preEvent).waitForEvents(graphExecutionContext.getExecutionPlanId());
            profilerStartTime = System.nanoTime();
        }

        // Wrap the native library call in an NVTX range so it shows up as a named
        // span (e.g. "cutlassHgemm") on the Nsight Systems timeline, alongside the
        // backend's own kernel/transfer ranges. No-op without a profiler attached.
        TornadoNativeStreamSupport nvtxDevice = (interpreterDevice instanceof TornadoNativeStreamSupport) ? (TornadoNativeStreamSupport) interpreterDevice : null;
        if (nvtxDevice != null) {
            nvtxDevice.nvtxRangePush(descriptor.getLibraryName() + "/" + descriptor.getFunctionName());
        }
        try {
            provider.dispatch(descriptor.getFunctionName(), new LibraryInvocation(callArgs, devicePointers, isReference, interpreterDevice, graphExecutionContext.getExecutionPlanId(), libraryContext,
                    descriptor.getTuning(), insideCaptureRegion));
        } finally {
            if (nvtxDevice != null) {
                nvtxDevice.nvtxRangePop();
            }
        }

        int lastEvent = ((useDependencies && !insideCaptureRegion) || profileCall) ? interpreterDevice.enqueueMarker(graphExecutionContext.getExecutionPlanId()) : -1;

        if (profileCall) {
            interpreterDevice.resolveEvent(graphExecutionContext.getExecutionPlanId(), lastEvent).waitForEvents(graphExecutionContext.getExecutionPlanId());
            long elapsed = System.nanoTime() - profilerStartTime;
            timeProfiler.setTaskTimer(ProfilerType.TASK_KERNEL_TIME, task.getId(), elapsed);
            timeProfiler.setTimer(ProfilerType.TOTAL_KERNEL_TIME, timeProfiler.getTimer(ProfilerType.TOTAL_KERNEL_TIME) + elapsed);
        }

        resetEventIndexes(eventId);
        return lastEvent;
    }


    /**
     * Records {@code lastEvent} as a dependency for the operation associated with
     * {@code eventId}.
     *
     * <p>Appends {@code lastEvent} to {@code events[eventId]}, which is the wait-list
     * later passed as {@code waitList} to the operation that holds dependency slot
     * {@code eventId}. In multi-stream mode the stored value is a global
     * event-registry ID; in single-stream mode it is a local
     * {@code CUDAEventPool} index. Skipped when {@code lastEvent == -1} (the preceding
     * operation produced no event) or when {@code useDependencies} is false.
     *
     * @param lastEvent event ID of the most recently executed bytecode operation
     * @param eventId   dependency slot index into the {@code events} array
     */
    private void executeDependency(StringBuilder logBuilder, int lastEvent, int eventId) {
        if (useDependencies && lastEvent != -1) {
            if (TornadoOptions.LOG_BYTECODES()) {
                DebugInterpreter.logAddDependency(lastEvent, eventId, logBuilder);
            }
            final int[] waitList = waitListForWrite(eventId);
            TornadoInternalError.guarantee(eventsIndexes[eventId] < waitList.length, "event list is too small");
            waitList[eventsIndexes[eventId]] = lastEvent;
            eventsIndexes[eventId]++;
        }
    }

    private int executeBarrier(StringBuilder logBuilder, int eventId, int[] waitList) {
        if (TornadoOptions.LOG_BYTECODES()) {
            DebugInterpreter.logBarrier(eventId, logBuilder);
        }

        int lastEvent;
        if (executionGraphEnabled) {
            // Events created during graph capture are invalid for host sync.
            // cuStreamSynchronize after cuGraphLaunch waits for graph completion.
            lastEvent = interpreterDevice.enqueueMarker(graphExecutionContext.getExecutionPlanId());
        } else {
            lastEvent = interpreterDevice.enqueueMarker(graphExecutionContext.getExecutionPlanId(), waitList);
        }

        resetEventIndexes(eventId);
        return lastEvent;
    }

    private void throwErrorInterpreter(byte op) {
        if (graphExecutionContext.meta().isDebug()) {
            logger.debug("bc: invalid op 0x%x(%d)", op, op);
        }
        throw new TornadoRuntimeException("[ERROR] TornadoVM Bytecode not recognized");
    }

    private XPUDeviceBufferState resolveObjectState(int index) {
        return dataObjectStates[index].getDeviceBufferState(interpreterDevice);
    }

    private boolean isObjectKernelContext(Object object) {
        return (object instanceof KernelContext);
    }

    private boolean isObjectAtomic(Object object) {
        return object instanceof AtomicInteger;
    }

    private boolean isNativeNonBufferObject(Object object) {
        return isObjectKernelContext(object) || isObjectAtomic(object);
    }

    private void prepareNativeAtomicInteger(int objectIndex, Object object, long sizeBatch) {
        XPUDeviceBufferState state = resolveObjectState(objectIndex);
        if (state == null || state.hasObjectBuffer()) {
            return;
        }
        interpreterDevice.allocate(object, sizeBatch, state, objectAccesses.get(object));
        if (TornadoOptions.isReusedBuffersEnabled()) {
            state.setLockBuffer(true);
        }
    }

    private boolean isNotObjectAtomic(Object object) {
        return !(object instanceof AtomicInteger);
    }

    private void resetEventIndexes(int eventList) {
        if (eventList != -1) {
            eventsIndexes[eventList] = 0;
        }
    }

    private KernelStackFrame resolveCallWrapper(int index, int numArgs, KernelStackFrame[] kernelStackFrame, TornadoXPUDevice device, boolean redeployOnDevice) {
        if (graphExecutionContext.meta().isDebug() && redeployOnDevice) {
            logger.debug("Recompiling task on device " + device);
        }
        if (kernelStackFrame[index] == null || !kernelStackFrame[index].isValid() || redeployOnDevice) {
            kernelStackFrame[index] = device.createKernelStackFrame(graphExecutionContext.getExecutionPlanId(), numArgs, Access.NONE);
        }
        return kernelStackFrame[index];
    }

    private boolean shouldCompile(TornadoInstalledCode installedCode) {
        return installedCode == null || !installedCode.isValid();
    }

    /**
     * Converts a global task index to a corresponding local task index within the local task list. This is inorder to preserve the original task list.
     *
     * @param taskIndex
     *     The global task index to convert.
     * @return The corresponding local task index, or 0 if the task is not found in the local task list.
     */
    private int globalToLocalTaskIndex(int taskIndex) {
        return localTaskList.indexOf(taskExecutionContexts.get(taskIndex)) == -1 ? 0 : localTaskList.indexOf(taskExecutionContexts.get(taskIndex));
    }

    private void profilerUpdateForPreCompiledTask(SchedulableTask task) {
        if (task instanceof PrebuiltTask prebuiltTask && timeProfiler instanceof TimeProfiler) {
            timeProfiler.registerDeviceID(task.getId(), prebuiltTask.meta().getXPUDevice().getBackendIndex() + ":" + prebuiltTask.meta().getDeviceIndex());
            timeProfiler.registerDeviceName(task.getId(), prebuiltTask.meta().getXPUDevice().getPhysicalDevice().getDeviceName());
        }
    }

    private DataObjectState resolveGlobalObjectState(int index) {
        return dataObjectStates[index];
    }

    private boolean isObjectInAtomicRegion(XPUDeviceBufferState objectState, TornadoXPUDevice device, SchedulableTask task) {
        return objectState.isAtomicRegionPresent() && device.checkAtomicsParametersForTask(task);
    }

    public void compile() {
        execute(true);
    }

    public Event execute() {
        return execute(false);
    }

    private String captureIndent() {
        return insideCaptureRegion ? "\t" : "";
    }

    public void clearInstalledCode() {
        Arrays.fill(installedCodes, null);
    }

    /**
     * Container class that holds information about object allocation counts.
     * Used to track the number of persistent objects and the number of objects
     * that need to be allocated.
     *
     * @param persistentObjectCount
     *     Number of persistent objects that don't need allocation
     * @param objectsToAlloc
     *     Number of objects that need to be allocated
     */
    public record ObjectAllocationInfo(int persistentObjectCount, int objectsToAlloc) {
    }

    private static class XPUExecutionFrame {
        private KernelStackFrame stackFrame;
        private int[] waitList;

        XPUExecutionFrame(KernelStackFrame callWrapper, int[] waitList) {
            this.stackFrame = callWrapper;
            this.waitList = waitList;
        }
    }
}
