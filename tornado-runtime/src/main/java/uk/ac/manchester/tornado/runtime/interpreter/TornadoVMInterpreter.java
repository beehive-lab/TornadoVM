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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.interpreter;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TornadoEvents;
import uk.ac.manchester.tornado.api.exceptions.*;
import uk.ac.manchester.tornado.api.memory.ObjectBuffer;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.*;
import uk.ac.manchester.tornado.runtime.graph.TornadoExecutionContext;
import uk.ac.manchester.tornado.runtime.graph.TornadoVMBytecodes;
import uk.ac.manchester.tornado.runtime.graph.TornadoVMBytecodeBuilder;
import uk.ac.manchester.tornado.runtime.profiler.TimeProfiler;
import uk.ac.manchester.tornado.runtime.tasks.GlobalObjectState;
import uk.ac.manchester.tornado.runtime.tasks.PrebuiltTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus.COMPLETE;
import static uk.ac.manchester.tornado.runtime.common.Tornado.*;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.VIRTUAL_DEVICE_ENABLED;

/**
 *
 */
public class TornadoVMInterpreter extends TornadoLogger {
    private static final Event EMPTY_EVENT = new EmptyEvent();

    private static final int MAX_EVENTS = 32;
    private final boolean useDependencies;

    //    private final TornadoExecutionContext graphContext;
    private final List<Object> objects;

    private final GlobalObjectState[] globalStates;
    private final KernelArgs[] callWrappers;
    private final int[][] events;
    private final int[] eventsIndexes;
    //    private final List<TornadoAcceleratorDevice> contexts;
    private final TornadoAcceleratorDevice deviceForInterpreter;
    private final TornadoInstalledCode[] installedCodes;

    private final List<Object> constants;
    private final List<SchedulableTask> tasks;

    private final ByteBuffer buffer;

    private double totalTime;
    private long invocations;
    private final TornadoProfiler timeProfiler;
    private boolean finishedWarmup;
    private boolean doUpdate;
    private TornadoExecutionContext graphContext;
    private GridScheduler gridScheduler;

    public TornadoVMInterpreter(TornadoExecutionContext graphContext, TornadoVMBytecodeBuilder bytecode, TornadoProfiler timeProfiler, TornadoAcceleratorDevice deviceForContext, int idx) {
        this.graphContext = graphContext;
        this.timeProfiler = timeProfiler;

        this.deviceForInterpreter = graphContext.getDeviceForTask(idx);
        System.out.println("Get dev for task " + idx + "  : " + deviceForContext.toString());
        useDependencies = graphContext.meta().enableOooExecution() || VM_USE_DEPS;
        totalTime = 0;
        invocations = 0;

        buffer = setupBytecodeBuffer(bytecode);

        debug("loading tornado vm...");

        TornadoInternalError.guarantee(buffer.get() == TornadoVMBytecodes.INIT.value(), "invalid code");

        buffer.getInt();
        int taskCount = buffer.getInt();
        callWrappers = graphContext.getCallWrappers().clone();
        events = new int[buffer.getInt()][MAX_EVENTS];
        eventsIndexes = new int[events.length];
        System.out.println("tASK COUNT " + taskCount);
        installedCodes = new TornadoInstalledCode[taskCount];

        for (int i = 0; i < events.length; i++) {
            Arrays.fill(events[i], -1);
            eventsIndexes[i] = 0;
        }

//        debug("found %d contexts", contexts.size());
        debug("created %d callWrappers", callWrappers.length);
        debug("created %d event lists", events.length);

        objects = graphContext.getObjects();
        globalStates = new GlobalObjectState[objects.size()];
        fetchGlobalStates();

        byte op = buffer.get();
        while (op != TornadoVMBytecodes.BEGIN.value()) {
            TornadoInternalError.guarantee(op == TornadoVMBytecodes.CONTEXT.value(), "invalid code: 0x%x", op);
            final int deviceIndex = buffer.getInt();
            debug("loading context %s", deviceForContext.toString());
            final long t0 = System.nanoTime();
            deviceForContext.ensureLoaded();
            final long t1 = System.nanoTime();
            debug("loaded in %.9f s", (t1 - t0) * 1e-9);
            op = buffer.get();
        }

        constants = graphContext.getConstants();
        tasks = graphContext.getTasks();

        debug("%s - vm ready to go", graphContext.getId());
        buffer.mark();

    }

    private ByteBuffer setupBytecodeBuffer(TornadoVMBytecodeBuilder bytecode) {
        ByteBuffer interpreterBuffer = ByteBuffer.wrap(bytecode.getCode())
                .order(ByteOrder.LITTLE_ENDIAN)
                .limit(bytecode.getCodeSize());
        return interpreterBuffer;
    }

    public Event execute(boolean isWarmup) {
        isWarmup = isWarmup || VIRTUAL_DEVICE_ENABLED;
//        deviceForContext.enableThreadSharing();
        final long t0 = System.nanoTime();
        int lastEvent = -1;
        initWaitEventList();

        StringBuilder tornadoVMBytecodeList = null;
        if (TornadoOptions.PRINT_BYTECODES) {
            tornadoVMBytecodeList = new StringBuilder();
            tornadoVMBytecodeList.append("Bytecodes for : " + deviceForInterpreter.toString() + " Running in thread : " + Thread.currentThread().getId() + "\n");
        }

        while (buffer.hasRemaining()) {
            final byte op = buffer.get();
            if (op == TornadoVMBytecodes.ALLOC.value()) {
                final int contextIndex = buffer.getInt();
                final long sizeBatch = buffer.getLong();
                final int argSize = buffer.getInt();
                final int[] args = new int[argSize];
                for (int i = 0; i < argSize; i++) {
                    args[i] = buffer.getInt();
                }
                if (isWarmup) {
                    continue;
                }
                lastEvent = executeAlloc(tornadoVMBytecodeList, args, contextIndex, sizeBatch);
            } else if (op == TornadoVMBytecodes.DEALLOC.value()) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                if (isWarmup) {
                    continue;
                }
                lastEvent = executeDeAlloc(tornadoVMBytecodeList, objectIndex, contextIndex);
            } else if (op == TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ONCE.value()) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int eventList = buffer.getInt();
                final long offset = buffer.getLong();
                final long sizeBatch = buffer.getLong();
                final int[] waitList = (useDependencies && eventList != -1) ? events[eventList] : null;
                if (isWarmup) {
                    continue;
                }
                transferHostToDeviceOnce(tornadoVMBytecodeList, objectIndex, contextIndex, offset, eventList, sizeBatch, waitList);
            } else if (op == TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ALWAYS.value()) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int eventList = buffer.getInt();
                final long offset = buffer.getLong();
                final long sizeBatch = buffer.getLong();
                final int[] waitList = (useDependencies && eventList != -1) ? events[eventList] : null;
                if (isWarmup) {
                    continue;
                }
                transferHostToDeviceAlways(tornadoVMBytecodeList, objectIndex, contextIndex, offset, eventList, sizeBatch, waitList);
            } else if (op == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS.value()) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int eventList = buffer.getInt();
                final long offset = buffer.getLong();
                final long sizeBatch = buffer.getLong();
                final int[] waitList = (useDependencies) ? events[eventList] : null;
                if (isWarmup) {
                    continue;
                }
                lastEvent = transferDeviceToHost(tornadoVMBytecodeList, objectIndex, contextIndex, offset, eventList, sizeBatch, waitList);
            } else if (op == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING.value()) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int eventList = buffer.getInt();
                final long offset = buffer.getLong();
                final long sizeBatch = buffer.getLong();

                final int[] waitList = (useDependencies) ? events[eventList] : null;
                if (isWarmup) {
                    continue;
                }
                transferDeviceToHostBlocking(tornadoVMBytecodeList, objectIndex, contextIndex, offset, eventList, sizeBatch, waitList);
            } else if (op == TornadoVMBytecodes.LAUNCH.value()) {
                final int callWrapperIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int taskIndex = buffer.getInt();
                final int numArgs = buffer.getInt();
                final int eventList = buffer.getInt();
                final long offset = buffer.getLong();
                final long batchThreads = buffer.getLong();
                ExecutionInfo info = compileTaskFromBytecodeToBinary(callWrapperIndex, numArgs, eventList, taskIndex, batchThreads);
                if (isWarmup) {
                    popArgumentsFromCall(numArgs);
                    continue;
                }
                lastEvent = executeLaunch(tornadoVMBytecodeList, numArgs, eventList, taskIndex, batchThreads, offset, info);
            } else if (op == TornadoVMBytecodes.ADD_DEPENDENCY.value()) {
                final int eventList = buffer.getInt();
                if (isWarmup) {
                    continue;
                }
                executeDependency(tornadoVMBytecodeList, lastEvent, eventList);
            } else if (op == TornadoVMBytecodes.BARRIER.value()) {
                final int eventList = buffer.getInt();
                final int[] waitList = (useDependencies && eventList != -1) ? events[eventList] : null;
                if (isWarmup) {
                    continue;
                }
                lastEvent = executeBarrier(tornadoVMBytecodeList, eventList, waitList);
            } else if (op == TornadoVMBytecodes.END.value()) {
                if (!isWarmup && TornadoOptions.PRINT_BYTECODES) {
                    tornadoVMBytecodeList.append("bc: " + InterpreterUtilities.debugHighLightBC("END\n") + "\n");
                }
                break;
            } else {
                throwError(op);
            }
        }

        Event barrier = EMPTY_EVENT;
        if (!isWarmup) {
            if (deviceForInterpreter != null) {
                if (useDependencies) {
                    final int event = deviceForInterpreter.enqueueMarker();
                    barrier = deviceForInterpreter.resolveEvent(event);
                }

                if (USE_VM_FLUSH) {
                    deviceForInterpreter.flush();
                }
            }
        }

        final long t1 = System.nanoTime();
        final double elapsed = (t1 - t0) * 1e-9;
        if (!isWarmup) {
            totalTime += elapsed;
            invocations++;
        }

//        if (graphContext.meta().isDebug()) {
//            debug("bc: complete elapsed=%.9f s (%d iterations, %.9f s mean)", elapsed, invocations, (totalTime / invocations));
//        }

        buffer.reset();

        if (TornadoOptions.PRINT_BYTECODES) {
            System.out.println(tornadoVMBytecodeList);
        }

        return barrier;
    }

    private int executeAlloc(StringBuilder tornadoVMBytecodeList, int[] args, int contextIndex, long sizeBatch) {

        Object[] objects = new Object[args.length];
        DeviceObjectState[] objectStates = new DeviceObjectState[args.length];
        for (int i = 0; i < objects.length; i++) {
            objects[i] = this.objects.get(args[i]);
            objectStates[i] = resolveObjectState(args[i], contextIndex);

            if (TornadoOptions.PRINT_BYTECODES) {
                String verbose = String.format("bc: " + InterpreterUtilities.debugHighLightBC("ALLOC") + "%s on %s, size=%d", objects[i], InterpreterUtilities.debugDeviceBC(deviceForInterpreter), sizeBatch);
                tornadoVMBytecodeList.append(verbose).append("\n");

            }
        }

        return deviceForInterpreter.allocateObjects(objects, sizeBatch, objectStates);
    }

    private int executeDeAlloc(StringBuilder tornadoVMBytecodeList, final int objectIndex, final int contextIndex) {
        Object object = objects.get(objectIndex);

        if (isObjectKernelContext(object)) {
            return 0;
        }

        if (TornadoOptions.PRINT_BYTECODES && !isObjectAtomic(object)) {
            String verbose = String.format("bc: " + InterpreterUtilities.debugHighLightBC("DEALLOC") + "[0x%x] %s on %s", object.hashCode(), object, InterpreterUtilities.debugDeviceBC(deviceForInterpreter));
            tornadoVMBytecodeList.append(verbose).append("\n");

        }

        final DeviceObjectState objectState = resolveObjectState(objectIndex, contextIndex);
        return deviceForInterpreter.deallocate(objectState);
    }

    private boolean isObjectAtomic(Object object) {
        return object instanceof AtomicInteger;
    }

    private boolean isObjectKernelContext(Object object) {
        return (object instanceof KernelContext);
    }

    private int transferHostToDeviceOnce(StringBuilder tornadoVMBytecodeList, final int objectIndex, final int contextIndex, final long offset, final int eventList, final long sizeBatch,
                                         final int[] waitList) {
        Object object = objects.get(objectIndex);

        if (isObjectKernelContext(object)) {
            return 0;
        }

        final DeviceObjectState objectState = resolveObjectState(objectIndex, contextIndex);

        if (TornadoOptions.PRINT_BYTECODES & !isObjectAtomic(object)) {
            String verbose = String.format("bc: " + InterpreterUtilities.debugHighLightBC("TRANSFER_HOST_TO_DEVICE_ONCE") + " [Object Hash Code=0x%x] %s on %s, size=%d, offset=%d [event list=%d]", object.hashCode(),
                    object, InterpreterUtilities.debugDeviceBC(deviceForInterpreter), sizeBatch, offset, eventList);
            tornadoVMBytecodeList.append(verbose).append("\n");

        }

        List<Integer> allEvents;
        if (sizeBatch > 0) {
            // We need to stream-in when using batches, because the
            // whole data is not copied yet.
            allEvents = deviceForInterpreter.streamIn(object, sizeBatch, offset, objectState, waitList);
        } else {
            allEvents = deviceForInterpreter.ensurePresent(object, objectState, waitList, sizeBatch, offset);
        }

        resetEventIndexes(eventList);

        if (TornadoOptions.isProfilerEnabled() && allEvents != null) {
            for (Integer e : allEvents) {
                Event event = deviceForInterpreter.resolveEvent(e);
                event.waitForEvents();
                long copyInTimer = timeProfiler.getTimer(ProfilerType.COPY_IN_TIME);
                copyInTimer += event.getElapsedTime();
                timeProfiler.setTimer(ProfilerType.COPY_IN_TIME, copyInTimer);

                timeProfiler.addValueToMetric(ProfilerType.TOTAL_COPY_IN_SIZE_BYTES, TimeProfiler.NO_TASK_NAME, objectState.getObjectBuffer().size());

                long dispatchValue = timeProfiler.getTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME);
                dispatchValue += event.getDriverDispatchTime();
                timeProfiler.setTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME, dispatchValue);
            }
        }
        return 0;
    }

    private int transferHostToDeviceAlways(StringBuilder tornadoVMBytecodeList, final int objectIndex, final int contextIndex, final long offset, final int eventList, final long sizeBatch,
                                           final int[] waitList) {
        Object object = objects.get(objectIndex);

        if (isObjectKernelContext(object)) {
            return 0;
        }

        if (TornadoOptions.PRINT_BYTECODES && !isObjectAtomic(object)) {
            String verbose = String.format("bc: " + InterpreterUtilities.debugHighLightBC("TRANSFER_HOST_TO_DEVICE_ALWAYS") + " [0x%x] %s on %s, size=%d, offset=%d [event list=%d]", object.hashCode(), object,
                    InterpreterUtilities.debugDeviceBC(deviceForInterpreter), sizeBatch, offset, eventList);
            tornadoVMBytecodeList.append(verbose).append("\n");

        }

        final DeviceObjectState objectState = resolveObjectState(objectIndex, contextIndex);
        List<Integer> allEvents = deviceForInterpreter.streamIn(object, sizeBatch, offset, objectState, waitList);

        resetEventIndexes(eventList);

        if (TornadoOptions.isProfilerEnabled() && allEvents != null) {
            for (Integer e : allEvents) {
                Event event = deviceForInterpreter.resolveEvent(e);
                event.waitForEvents();
                long copyInTimer = timeProfiler.getTimer(ProfilerType.COPY_IN_TIME);
                copyInTimer += event.getElapsedTime();
                timeProfiler.setTimer(ProfilerType.COPY_IN_TIME, copyInTimer);

                timeProfiler.addValueToMetric(ProfilerType.TOTAL_COPY_IN_SIZE_BYTES, TimeProfiler.NO_TASK_NAME, objectState.getObjectBuffer().size());

                long dispatchValue = timeProfiler.getTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME);
                dispatchValue += event.getDriverDispatchTime();
                timeProfiler.setTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME, dispatchValue);
            }
        }
        return 0;
    }

    private int transferDeviceToHost(StringBuilder tornadoVMBytecodeList, final int objectIndex, final int contextIndex, final long offset, final int eventList, final long sizeBatch,
                                     final int[] waitList) {
        Object object = objects.get(objectIndex);

        if (isObjectKernelContext(object)) {
            return 0;
        }

        if (TornadoOptions.PRINT_BYTECODES) {
            String verbose = String.format("bc: " + InterpreterUtilities.debugHighLightBC("TRANSFER_DEVICE_TO_HOST_ALWAYS") + "[0x%x] %s on %s, size=%d, offset=%d [event list=%d]", object.hashCode(), object,
                    InterpreterUtilities.debugDeviceBC(deviceForInterpreter), sizeBatch, offset, eventList);
            tornadoVMBytecodeList.append(verbose).append("\n");

        }

        final DeviceObjectState objectState = resolveObjectState(objectIndex, contextIndex);
        int lastEvent = deviceForInterpreter.streamOutBlocking(object, offset, objectState, waitList);

        resetEventIndexes(eventList);

        if (TornadoOptions.isProfilerEnabled() && lastEvent != -1) {
            Event event = deviceForInterpreter.resolveEvent(lastEvent);
            event.waitForEvents();
            long value = timeProfiler.getTimer(ProfilerType.COPY_OUT_TIME);
            value += event.getElapsedTime();
            timeProfiler.setTimer(ProfilerType.COPY_OUT_TIME, value);

            timeProfiler.addValueToMetric(ProfilerType.TOTAL_COPY_OUT_SIZE_BYTES, TimeProfiler.NO_TASK_NAME, objectState.getObjectBuffer().size());

            long dispatchValue = timeProfiler.getTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME);
            dispatchValue += event.getDriverDispatchTime();
            timeProfiler.setTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME, dispatchValue);
        }
        return lastEvent;
    }

    private void transferDeviceToHostBlocking(StringBuilder tornadoVMBytecodeList, final int objectIndex, final int contextIndex, final long offset, final int eventList, final long sizeBatch,
                                              final int[] waitList) {

        Object object = objects.get(objectIndex);

        if (isObjectKernelContext(object)) {
            return;
        }

        if (TornadoOptions.PRINT_BYTECODES) {
            String verbose = String.format("bc: " + InterpreterUtilities.debugHighLightBC("STREAM_OUT_BLOCKING") + " [0x%x] %s on %s, size=%d, offset=%d [event list=%d]", object.hashCode(), object, InterpreterUtilities.debugDeviceBC(deviceForInterpreter),
                    sizeBatch, offset, eventList);
            tornadoVMBytecodeList.append(verbose).append("\n");

        }

        final DeviceObjectState objectState = resolveObjectState(objectIndex, contextIndex);

        final int tornadoEventID = deviceForInterpreter.streamOutBlocking(object, offset, objectState, waitList);

        if (TornadoOptions.isProfilerEnabled() && tornadoEventID != -1) {
            Event event = deviceForInterpreter.resolveEvent(tornadoEventID);
            event.waitForEvents();
            long value = timeProfiler.getTimer(ProfilerType.COPY_OUT_TIME);
            value += event.getElapsedTime();
            timeProfiler.setTimer(ProfilerType.COPY_OUT_TIME, value);

            timeProfiler.addValueToMetric(ProfilerType.TOTAL_COPY_OUT_SIZE_BYTES, TimeProfiler.NO_TASK_NAME, objectState.getObjectBuffer().size());

            long dispatchValue = timeProfiler.getTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME);
            dispatchValue += event.getDriverDispatchTime();
            timeProfiler.setTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME, dispatchValue);
        }
        resetEventIndexes(eventList);
    }

    private void profilerUpdateForPreCompiledTask(SchedulableTask task) {
        if (task instanceof PrebuiltTask && timeProfiler instanceof TimeProfiler) {
            PrebuiltTask prebuiltTask = (PrebuiltTask) task;
            timeProfiler.registerDeviceID(task.getId(), prebuiltTask.meta().getLogicDevice().getDriverIndex() + ":" + prebuiltTask.meta().getDeviceIndex());
            timeProfiler.registerDeviceName(task.getId(), prebuiltTask.meta().getLogicDevice().getPhysicalDevice().getDeviceName());
        }
    }

    private ExecutionInfo compileTaskFromBytecodeToBinary(final int callWrapperIndex, final int numArgs, final int eventList, final int taskIndex, final long batchThreads) {

        if (deviceForInterpreter.getDeviceContext().wasReset() && finishedWarmup) {
            throw new TornadoFailureException("[ERROR] reset() was called after warmup()");
        }

        boolean redeployOnDevice = graphContext.redeployOnDevice();

        final KernelArgs callWrapper = resolveCallWrapper(callWrapperIndex, numArgs, callWrappers, deviceForInterpreter, redeployOnDevice);

        final int[] waitList = (useDependencies && eventList != -1) ? events[eventList] : null;
        final SchedulableTask task = tasks.get(taskIndex);

        // Check if a different batch size was used for the same kernel. If true, then
        // the kernel needs to be recompiled.
        System.out.println("+++ Task index " + taskIndex);

        if (!shouldCompile(installedCodes[0]) && task.getBatchThreads() != 0 && task.getBatchThreads() != batchThreads) {
            installedCodes[0].invalidate();
        }
        // Set the batch size in the task information
        task.setBatchThreads(batchThreads);
//        task.enableDefaultThreadScheduler(graphContext.useDefaultThreadScheduler());

        if (gridScheduler != null && gridScheduler.get(task.getId()) != null) {
            task.setUseGridScheduler(true);
            task.setGridScheduler(gridScheduler);
        }

        if (shouldCompile(installedCodes[0])) {
            task.mapTo(deviceForInterpreter);
            try {
                task.attachProfiler(timeProfiler);
                if (taskIndex == (tasks.size() - 1)) {
                    // If it is the last task within the task-schedule -> we force compilation
                    // This is useful when compiling code for Xilinx/Altera FPGAs, that has to
                    // be a single source
                    task.forceCompilation();
                }
                if (doUpdate) {
                    task.forceCompilation();
                }
                installedCodes[0] = deviceForInterpreter.installCode(task);
                profilerUpdateForPreCompiledTask(task);
                doUpdate = false;
            } catch (TornadoBailoutRuntimeException e) {
                throw new TornadoBailoutRuntimeException("Unable to compile task " + task.getFullName() + "\n" + Arrays.toString(e.getStackTrace()), e);
            } catch (TornadoDeviceFP64NotSupported e) {
                throw e;
            } catch (InternalError e) {
                throw new TornadoBailoutRuntimeException("[Internal Error] Unable to compile task " + task.getFullName() + "\n" + Arrays.toString(e.getStackTrace()));
            }
        }
        return new ExecutionInfo(callWrapper, waitList);
    }

    private boolean shouldCompile(TornadoInstalledCode installedCode) {
        return installedCode == null || !installedCode.isValid();
    }

    private boolean isObjectInAtomicRegion(DeviceObjectState objectState, TornadoAcceleratorDevice device, SchedulableTask task) {
        return objectState.isAtomicRegionPresent() && device.checkAtomicsParametersForTask(task);
    }

    private int executeLaunch(StringBuilder tornadoVMBytecodeList, final int numArgs, final int eventList, final int taskIndex, final long batchThreads, final long offset,
                              ExecutionInfo info) {

        final SchedulableTask task = tasks.get(taskIndex);
        KernelArgs callWrapper = info.callWrapper;
        int[] waitList = info.waitList;

        if (installedCodes[0] == null) {
            // After warming-up, it is possible to get a null pointer in the task-cache due
            // to lazy compilation for FPGAs. In tha case, we check again the code cache.
            installedCodes[0] = deviceForInterpreter.getCodeFromCache(task);
        }

        final TornadoInstalledCode installedCode = installedCodes[0];
        if (installedCode == null) {
            // There was an error during compilation -> bailout
            throw new TornadoBailoutRuntimeException("Code generator Failed");
        }

        int[] atomicsArray;
        if (task instanceof PrebuiltTask) {
            atomicsArray = ((PrebuiltTask) task).getAtomics();
        } else {
            atomicsArray = deviceForInterpreter.checkAtomicsForTask(task);
        }

        HashMap<Integer, Integer> map = new HashMap<>();
        if (gridScheduler != null && gridScheduler.get(task.getId()) != null) {
            WorkerGrid workerGrid = gridScheduler.get(task.getId());
            long[] global = workerGrid.getGlobalWork();
            int i = 0;
            for (long maxThread : global) {
                map.put(i++, (int) maxThread);
            }
        }
        callWrapper.reset();
        callWrapper.setKernelContext(map);

        ObjectBuffer bufferAtomics = null;

        for (int i = 0; i < numArgs; i++) {
            final byte argType = buffer.get();
            final int argIndex = buffer.getInt();

            if (argType == TornadoVMBytecodes.PUSH_CONSTANT_ARGUMENT.value()) {
                callWrapper.addCallArgument(constants.get(argIndex), false);
            } else if (argType == TornadoVMBytecodes.PUSH_REFERENCE_ARGUMENT.value()) {
                if (isObjectKernelContext(objects.get(argIndex))) {
                    callWrapper.addCallArgument(new KernelArgs.KernelContextArgument(), false);
                    continue;
                }

                final GlobalObjectState globalState = resolveGlobalObjectState(argIndex);
                final DeviceObjectState objectState = globalState.getDeviceState(deviceForInterpreter);

                if (!isObjectInAtomicRegion(objectState, deviceForInterpreter, task)) {
                    callWrapper.addCallArgument(objectState.getObjectBuffer().toBuffer(), true);
                } else {
                    atomicsArray = deviceForInterpreter.updateAtomicRegionAndObjectState(task, atomicsArray, i, objects.get(argIndex), objectState);
                }
            } else {
                TornadoInternalError.shouldNotReachHere();
            }
        }

        if (atomicsArray != null) {
            bufferAtomics = deviceForInterpreter.createOrReuseAtomicsBuffer(atomicsArray);
            List<Integer> allEvents = bufferAtomics.enqueueWrite(null, 0, 0, null, false);
            if (TornadoOptions.isProfilerEnabled()) {
                for (Integer e : allEvents) {
                    Event event = deviceForInterpreter.resolveEvent(e);
                    event.waitForEvents();
                    long value = timeProfiler.getTimer(ProfilerType.COPY_IN_TIME);
                    value += event.getElapsedTime();
                    timeProfiler.setTimer(ProfilerType.COPY_IN_TIME, value);
                }
            }
            if (TornadoOptions.PRINT_BYTECODES) {
                String verbose = String.format("bc: " + InterpreterUtilities.debugHighLightBC("STREAM_IN") + "  ATOMIC [0x%x] %s on %s, size=%d, offset=%d [event list=%d]", bufferAtomics.hashCode(), bufferAtomics, deviceForInterpreter,
                        0, 0, eventList);
                tornadoVMBytecodeList.append(verbose).append("\n");

            }
        }

        if (TornadoOptions.PRINT_BYTECODES) {
            String verbose = String.format("bc: " + InterpreterUtilities.debugHighLightBC("LAUNCH") + " %s on %s, size=%d, offset=%d [event list=%d]", task.getFullName(), deviceForInterpreter, batchThreads, offset,
                    eventList);
            tornadoVMBytecodeList.append(verbose).append("\n");
        }

        TaskMetaData metadata;
        if (task.meta() instanceof TaskMetaData) {
            metadata = (TaskMetaData) task.meta();
        } else {
            throw new TornadoRuntimeException("task.meta is not instanceof TaskMetadata");
        }

        // We attach the profiler
        metadata.attachProfiler(timeProfiler);
        metadata.setGridScheduler(gridScheduler);

        int lastEvent;
        try {
            if (useDependencies) {
                lastEvent = installedCode.launchWithDependencies(callWrapper, bufferAtomics, metadata, batchThreads, waitList);
            } else {
                lastEvent = installedCode.launchWithoutDependencies(callWrapper, bufferAtomics, metadata, batchThreads);
            }

            resetEventIndexes(eventList);

        } catch (Exception e) {
            String re = e.toString();
            if (Tornado.DEBUG) {
                e.printStackTrace();
            }
            throw new TornadoBailoutRuntimeException("Bailout from LAUNCH Bytecode: \nReason: " + re, e);
        }
        return lastEvent;
    }

    private void executeDependency(StringBuilder tornadoVMBytecodeList, int lastEvent, int eventList) {
        if (useDependencies && lastEvent != -1) {
            if (TornadoOptions.PRINT_BYTECODES) {
                String verbose = String.format("bc: " + InterpreterUtilities.debugHighLightBC("ADD_DEPENDENCY") + " %s to event list %d", lastEvent, eventList);
                tornadoVMBytecodeList.append(verbose).append("\n");

            }
            TornadoInternalError.guarantee(eventsIndexes[eventList] < events[eventList].length, "event list is too small");
            events[eventList][eventsIndexes[eventList]] = lastEvent;
            eventsIndexes[eventList]++;
        }
    }

    private int executeBarrier(StringBuilder tornadoVMBytecodeList, int eventList, int[] waitList) {
        if (TornadoOptions.PRINT_BYTECODES) {
            tornadoVMBytecodeList.append(String.format("bc: " + InterpreterUtilities.debugHighLightBC("BARRIER") + " event-list %d%n", eventList));
        }

//        int id = contexts.size() - 1;
//        final TornadoAcceleratorDevice device = contexts.get(id);
        int lastEvent = deviceForInterpreter.enqueueMarker(waitList);

        resetEventIndexes(eventList);
        return lastEvent;
    }

    private void throwError(byte op) {
//        if (graphContext.meta().isDebug()) {
//            debug("bc: invalid op 0x%x(%d)", op, op);
//        }
        throw new TornadoRuntimeException("[ERROR] TornadoVM Bytecode not recognized");
    }


    private void resetEventIndexes(int eventList) {
        if (eventList != -1) {
            eventsIndexes[eventList] = 0;
        }
    }

    private void popArgumentsFromCall(int numArgs) {
        for (int i = 0; i < numArgs; i++) {
            buffer.get();
            buffer.getInt();
        }
    }

    public void setGridScheduler(GridScheduler gridScheduler) {
        this.gridScheduler = gridScheduler;
    }

    public void printTimes() {
        System.out.printf("bc: complete %d iterations - %.9f s mean and %.9f s total%n", invocations, (totalTime / invocations), totalTime);
    }

    public void clearProfiles() {
        for (final SchedulableTask task : tasks) {
            task.meta().getProfiles().clear();
        }
    }

    public void dumpEvents() {
//        if (!ENABLE_PROFILING || !graphContext.meta().shouldDumpEvents()) {
//            info("profiling and/or event dumping is not enabled");
//            return;
//        }

        deviceForInterpreter.dumpEvents();
    }

    private GlobalObjectState resolveGlobalObjectState(int index) {
        return globalStates[index];
    }

    private DeviceObjectState resolveObjectState(int index, int device) {
        return globalStates[index].getDeviceState(deviceForInterpreter);
    }

    private KernelArgs resolveCallWrapper(int index, int numArgs, KernelArgs[] callWrappers, TornadoAcceleratorDevice device, boolean setNewDevice) {
//        if (graphContext.meta().isDebug() && setNewDevice) {
//            debug("Recompiling task on device " + device);
//        }
        if (callWrappers[index] == null || setNewDevice) {
            callWrappers[index] = device.createCallWrapper(numArgs);
        }
        return callWrappers[index];
    }


    public void dumpProfiles() {
//        if (!graphContext.meta().shouldDumpProfiles()) {
//            info("profiling is not enabled");
//            return;
//        }

        for (final SchedulableTask task : tasks) {
            final TaskMetaData meta = (TaskMetaData) task.meta();
            for (final TornadoEvents eventSet : meta.getProfiles()) {
                final BitSet profiles = eventSet.getProfiles();
                for (int i = profiles.nextSetBit(0); i != -1; i = profiles.nextSetBit(i + 1)) {

                    if (!(eventSet.getDevice() instanceof TornadoAcceleratorDevice)) {
                        throw new RuntimeException("TornadoDevice not found");
                    }

                    TornadoAcceleratorDevice device = (TornadoAcceleratorDevice) eventSet.getDevice();
                    final Event profile = device.resolveEvent(i);
                    if (profile.getStatus() == COMPLETE) {
                        System.out.printf("task: %s %s %9d %9d %9d %9d %9d%n", device.getDeviceName(), meta.getId(), profile.getElapsedTime(), profile.getQueuedTime(), profile.getSubmitTime(),
                                profile.getStartTime(), profile.getEndTime());
                    }
                }
            }
        }
    }

    public void setCompileUpdate() {
        this.doUpdate = true;
    }

    public void fetchGlobalStates() {
        debug("fetching %d object states...", globalStates.length);
        for (int i = 0; i < objects.size(); i++) {
            final Object object = objects.get(i);
            TornadoInternalError.guarantee(object != null, "null object found in TornadoVM");
            globalStates[i] = TornadoCoreRuntime.getTornadoRuntime().resolveObject(object);
            debug("\tobject[%d]: [0x%x] %s %s", i, object.hashCode(), object.getClass().getTypeName(), globalStates[i]);
        }
    }


    public void warmup() {
        execute(true);
        finishedWarmup = true;
    }

    public void compile() {
        execute(true);
    }

    public Event execute() {
        return execute(false);
    }

    private void initWaitEventList() {
        for (int[] waitList : events) {
            Arrays.fill(waitList, -1);
        }
    }

    public void clearInstalledCode() {
        Arrays.fill(installedCodes, null);
    }


    private static class ExecutionInfo {
        KernelArgs callWrapper;
        int[] waitList;

        public ExecutionInfo(KernelArgs callWrapper, int[] waitList) {
            this.callWrapper = callWrapper;
            this.waitList = waitList;
        }
    }

}
