/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.runtime;

import static uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus.COMPLETE;
import static uk.ac.manchester.tornado.common.Tornado.ENABLE_PROFILING;
import static uk.ac.manchester.tornado.common.Tornado.USE_VM_FLUSH;
import static uk.ac.manchester.tornado.common.Tornado.VM_USE_DEPS;
import static uk.ac.manchester.tornado.common.enums.Access.READ_WRITE;
import static uk.ac.manchester.tornado.common.enums.Access.WRITE;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import uk.ac.manchester.tornado.api.Event;
import uk.ac.manchester.tornado.api.meta.TaskMetaData;
import uk.ac.manchester.tornado.common.CallStack;
import uk.ac.manchester.tornado.common.DeviceObjectState;
import uk.ac.manchester.tornado.common.SchedulableTask;
import uk.ac.manchester.tornado.common.TornadoDevice;
import uk.ac.manchester.tornado.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.common.TornadoLogger;
import uk.ac.manchester.tornado.common.TornadoOptions;
import uk.ac.manchester.tornado.common.enums.Access;
import uk.ac.manchester.tornado.common.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.runtime.api.GlobalObjectState;
import uk.ac.manchester.tornado.runtime.graph.ExecutionContext;
import uk.ac.manchester.tornado.runtime.graph.GraphAssembler.TornadoVMBytecodes;

public class TornadoVM extends TornadoLogger {

    private static final Event EMPTY_EVENT = new EmptyEvent();

    private static final int MAX_EVENTS = 32;
    private final boolean useDependencies;

    private final ExecutionContext graphContext;
    private final List<Object> objects;
    private final GlobalObjectState[] globalStates;
    private final CallStack[] stacks;
    private final int[][] events;
    private final int[] eventsIndicies;
    private final List<TornadoDevice> contexts;
    private final TornadoInstalledCode[] installedCodes;

    private final List<Object> constants;
    private final List<SchedulableTask> tasks;

    private final ByteBuffer buffer;

    private double totalTime;
    private long invocations;

    public TornadoVM(ExecutionContext graphContext, byte[] code, int limit) {

        this.graphContext = graphContext;

        useDependencies = graphContext.meta().enableOooExecution() | VM_USE_DEPS;
        totalTime = 0;
        invocations = 0;

        buffer = ByteBuffer.wrap(code);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.limit(limit);

        debug("loading tornado vm...");

        TornadoInternalError.guarantee(buffer.get() == TornadoVMBytecodes.SETUP.index(), "invalid code");
        contexts = graphContext.getDevices();
        buffer.getInt();
        int taskCount = buffer.getInt();
        stacks = graphContext.getFrames();
        events = new int[buffer.getInt()][MAX_EVENTS];
        eventsIndicies = new int[events.length];

        installedCodes = new TornadoInstalledCode[taskCount];

        for (int i = 0; i < events.length; i++) {
            Arrays.fill(events[i], -1);
            eventsIndicies[i] = 0;
        }

        debug("found %d contexts", contexts.size());
        debug("created %d stacks", stacks.length);
        debug("created %d event lists", events.length);

        objects = graphContext.getObjects();
        globalStates = new GlobalObjectState[objects.size()];
        debug("fetching %d object states...", globalStates.length);
        for (int i = 0; i < objects.size(); i++) {
            final Object object = objects.get(i);
            TornadoInternalError.guarantee(object != null, "null object found in TornadoVM");
            globalStates[i] = TornadoRuntime.getTornadoRuntime().resolveObject(object);
            debug("\tobject[%d]: [0x%x] %s %s", i, object.hashCode(), object.getClass().getTypeName(), globalStates[i]);
        }

        byte op = buffer.get();
        while (op != TornadoVMBytecodes.BEGIN.index()) {
            TornadoInternalError.guarantee(op == TornadoVMBytecodes.CONTEXT.index(), "invalid code: 0x%x", op);
            final int deviceIndex = buffer.getInt();
            debug("loading context %s", contexts.get(deviceIndex));
            final long t0 = System.nanoTime();
            contexts.get(deviceIndex).ensureLoaded();
            final long t1 = System.nanoTime();
            debug("loaded in %.9f s", (t1 - t0) * 1e-9);
            op = buffer.get();
        }

        constants = graphContext.getConstants();
        tasks = graphContext.getTasks();

        debug("%s - vm ready to go", graphContext.getId());
        buffer.mark();
    }

    private GlobalObjectState resolveGlobalObjectState(int index) {
        return globalStates[index];
    }

    private DeviceObjectState resolveObjectState(int index, int device) {
        return globalStates[index].getDeviceState(contexts.get(device));
    }

    private CallStack resolveStack(int index, int numArgs, CallStack[] stacks, TornadoDevice device, boolean setNewDevice) {
        if (graphContext.meta().isDebug() && setNewDevice) {
            debug("Recompiling task on device " + device);
        }
        if (stacks[index] == null || setNewDevice) {
            stacks[index] = device.createStack(numArgs);
        }

        return stacks[index];
    }

    public void invalidateObjects() {
        for (GlobalObjectState globalState : globalStates) {
            globalState.invalidate();
        }
    }

    public void warmup() {
        execute(true);
    }

    public Event execute() {
        return execute(false);
    }

    private Event execute(boolean isWarmup) {

        final long t0 = System.nanoTime();
        int lastEvent = -1;
        for (int[] waitList : events) {
            Arrays.fill(waitList, -1);
        }

        StringBuilder bytecodesList = new StringBuilder();

        while (buffer.hasRemaining()) {
            final byte op = buffer.get();

            if (op == TornadoVMBytecodes.ALLOCATE.index()) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();

                if (isWarmup) {
                    continue;
                }

                final TornadoDevice device = contexts.get(contextIndex);
                final Object object = objects.get(objectIndex);

                if (graphContext.meta().isDebug()) {
                    debug("vm: ALLOCATE [0x%x] %s on %s", object.hashCode(), object, device);
                }
                bytecodesList.append(String.format("ALLOCATE [0x%x] %s on %s\n", object.hashCode(), object, device));

                final DeviceObjectState objectState = resolveObjectState(objectIndex, contextIndex);
                lastEvent = device.ensureAllocated(object, objectState);
            } else if (op == TornadoVMBytecodes.COPY_IN.index()) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int eventList = buffer.getInt();
                final int[] waitList = (useDependencies && eventList != -1) ? events[eventList] : null;

                if (isWarmup) {
                    continue;
                }

                final TornadoDevice device = contexts.get(contextIndex);
                final Object object = objects.get(objectIndex);

                if (graphContext.meta().isDebug()) {
                    debug("vm: COPY_IN [0x%x] %s on %s [event list=%d]", object.hashCode(), object, device, eventList);
                }
                bytecodesList.append(String.format("COPY_IN [0x%x] %s on %s [event list=%d]\n", object.hashCode(), object, device, eventList));

                final DeviceObjectState objectState = resolveObjectState(objectIndex, contextIndex);
                if (graphContext.meta().isDebug()) {
                    debug("vm: state=%s", objectState);
                }

                if (useDependencies) {
                    lastEvent = device.ensurePresent(object, objectState, waitList);
                } else {
                    lastEvent = device.ensurePresent(object, objectState);
                }
                if (eventList != -1) {
                    eventsIndicies[eventList] = 0;
                }

            } else if (op == TornadoVMBytecodes.STREAM_IN.index()) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int eventList = buffer.getInt();
                final int[] waitList = (useDependencies && eventList != -1) ? events[eventList] : null;

                if (isWarmup) {
                    continue;
                }

                final TornadoDevice device = contexts.get(contextIndex);
                final Object object = objects.get(objectIndex);
                if (graphContext.meta().isDebug()) {
                    debug("vm: STREAM_IN [0x%x] %s on %s [event list=%d]", object.hashCode(), object, device, eventList);
                }
                bytecodesList.append(String.format("STREAM_IN [0x%x] %s on %s [event list=%d]\n", object.hashCode(), object, device, eventList));

                final DeviceObjectState objectState = resolveObjectState(objectIndex, contextIndex);

                if (graphContext.meta().isDebug()) {
                    debug("vm: state=%s", objectState);
                }

                if (useDependencies) {
                    lastEvent = device.streamIn(object, objectState, waitList);
                } else {
                    lastEvent = device.streamIn(object, objectState);
                }
                if (eventList != -1) {
                    eventsIndicies[eventList] = 0;
                }

            } else if (op == TornadoVMBytecodes.STREAM_OUT.index()) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int eventList = buffer.getInt();
                final int[] waitList = (useDependencies) ? events[eventList] : null;

                if (isWarmup) {
                    continue;
                }

                final TornadoDevice device = contexts.get(contextIndex);
                final Object object = objects.get(objectIndex);

                if (graphContext.meta().isDebug()) {
                    debug("vm: STREAM_OUT [0x%x] %s on %s [event list=%d]", object.hashCode(), object, device, eventList);
                }
                bytecodesList.append(String.format("STREAM_OUT [0x%x] %s on %s [event list=%d]\n", object.hashCode(), object, device, eventList));

                final DeviceObjectState objectState = resolveObjectState(objectIndex, contextIndex);

                if (useDependencies) {
                    lastEvent = device.streamOut(object, objectState, waitList);
                } else {
                    lastEvent = device.streamOut(object, objectState);
                }
                if (eventList != -1) {
                    eventsIndicies[eventList] = 0;
                }
            } else if (op == TornadoVMBytecodes.STREAM_OUT_BLOCKING.index()) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int eventList = buffer.getInt();
                final int[] waitList = (useDependencies) ? events[eventList] : null;

                if (isWarmup) {
                    continue;
                }

                final TornadoDevice device = contexts.get(contextIndex);
                final Object object = objects.get(objectIndex);

                if (graphContext.meta().isDebug()) {
                    debug("vm: STREAM_OUT_BLOCKING [0x%x] %s on %s [event list=%d]", object.hashCode(), object, device, eventList);
                }
                bytecodesList.append(String.format("STREAM_OUT_BLOCKING [0x%x] %s on %s [event list=%d]\n", object.hashCode(), object, device, eventList));

                final DeviceObjectState objectState = resolveObjectState(objectIndex, contextIndex);

                if (useDependencies) {
                    lastEvent = device.streamOut(object, objectState, waitList);
                } else {
                    lastEvent = device.streamOut(object, objectState);
                }

                if (eventList != -1) {
                    eventsIndicies[eventList] = 0;
                }
            } else if (op == TornadoVMBytecodes.LAUNCH.index()) {
                final int gtid = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int taskIndex = buffer.getInt();
                final int numArgs = buffer.getInt();
                final int eventList = buffer.getInt();

                final TornadoDevice device = contexts.get(contextIndex);
                boolean redeployOnDevice = graphContext.redeployOnDevice();

                final CallStack stack = resolveStack(gtid, numArgs, stacks, device, redeployOnDevice);

                final int[] waitList = (useDependencies && eventList != -1) ? events[eventList] : null;
                final SchedulableTask task = tasks.get(taskIndex);

                if (graphContext.meta().isDebug()) {
                    debug("vm: LAUNCH %s on %s [event list=%d]", task.getName(), contexts.get(contextIndex), eventList);
                }
                bytecodesList.append(String.format("LAUNCH %s on %s [event list=%d]\n", task.getName(), contexts.get(contextIndex), eventList));

                if (installedCodes[taskIndex] == null) {
                    final long compileStart = System.nanoTime();
                    task.mapTo(device);
                    try {
                        installedCodes[taskIndex] = device.installCode(task);
                    } catch (Error | Exception e) {
                        fatal("unable to compile task %s", task.getName());
                        debug(e.getMessage());
                    }
                    final long compileEnd = System.nanoTime();
                    if (graphContext.meta().shouldPrintCompileTimes()) {
                        System.out.printf("compile: task %s tornado %.9f\n", task.getName(), (compileEnd - compileStart) * 1e-9);
                    }

                    if (graphContext.meta().isDebug()) {
                        debug("vm: compiled in %.9f s", (compileEnd - compileStart) * 1e-9);
                    }

                }

                if (isWarmup) {
                    for (int i = 0; i < numArgs; i++) {
                        buffer.get();
                        buffer.getInt();
                    }
                    continue;
                }

                final TornadoInstalledCode installedCode = installedCodes[taskIndex];

                final Access[] accesses = task.getArgumentsAccess();

                if (redeployOnDevice || !stack.isOnDevice()) {
                    stack.reset();
                }
                for (int i = 0; i < numArgs; i++) {
                    final byte argType = buffer.get();
                    final int argIndex = buffer.getInt();

                    if (stack.isOnDevice()) {
                        continue;
                    }

                    if (argType == TornadoVMBytecodes.CONSTANT_ARG.index()) {
                        stack.push(constants.get(argIndex));
                    } else if (argType == TornadoVMBytecodes.REFERENCE_ARG.index()) {
                        final GlobalObjectState globalState = resolveGlobalObjectState(argIndex);
                        final DeviceObjectState objectState = globalState.getDeviceState(contexts.get(contextIndex));

                        TornadoInternalError.guarantee(objectState.isValid(), "object is not valid: %s %s", objects.get(argIndex), objectState);

                        stack.push(objects.get(argIndex), objectState);
                        if (accesses[i] == WRITE || accesses[i] == READ_WRITE) {
                            globalState.setOwner(device);
                            objectState.setContents(true);
                            objectState.setModified(true);
                        }
                    } else {
                        TornadoInternalError.shouldNotReachHere();
                    }
                }

                if (useDependencies) {
                    lastEvent = installedCode.launchWithDeps(stack, task.meta(), waitList);
                } else {
                    lastEvent = installedCode.launchWithoutDeps(stack, task.meta());
                }
                if (eventList != -1) {
                    eventsIndicies[eventList] = 0;
                }
            } else if (op == TornadoVMBytecodes.ADD_DEP.index()) {
                final int eventList = buffer.getInt();
                if (isWarmup) {
                    continue;
                }
                if (useDependencies && lastEvent != -1) {
                    if (graphContext.meta().isDebug()) {
                        debug("vm: ADD_DEP %s to event list %d", lastEvent, eventList);
                    }
                    bytecodesList.append(String.format("ADD_DEP %s to event list %d\n", lastEvent, eventList));

                    TornadoInternalError.guarantee(eventsIndicies[eventList] < events[eventList].length, "event list is too small");
                    events[eventList][eventsIndicies[eventList]] = lastEvent;
                    eventsIndicies[eventList]++;
                }

            } else if (op == TornadoVMBytecodes.BARRIER.index()) {
                final int eventList = buffer.getInt();
                final int[] waitList = (useDependencies && eventList != -1) ? events[eventList] : null;

                if (isWarmup) {
                    continue;
                }

                if (graphContext.meta().isDebug()) {
                    debug("vm: BARRIER event list %d", eventList);
                }
                bytecodesList.append(String.format("BARRIER event list %d\n", eventList));

                if (contexts.size() == 1) {
                    final TornadoDevice device = contexts.get(0);
                    lastEvent = device.enqueueMarker(waitList);
                } else if (contexts.size() > 1) {
                    TornadoInternalError.shouldNotReachHere("unimplemented multi-context barrier");
                }

                if (eventList != -1) {
                    eventsIndicies[eventList] = 0;
                }
            } else if (op == TornadoVMBytecodes.END.index()) {
                if (graphContext.meta().isDebug()) {
                    debug("vm: END");
                }
                bytecodesList.append(String.format("END\n"));

                break;
            } else {
                if (graphContext.meta().isDebug()) {
                    debug("vm: invalid op 0x%x(%d)", op, op);
                }
                TornadoInternalError.shouldNotReachHere();
            }
        }

        Event barrier = EMPTY_EVENT;
        if (!isWarmup) {
            for (TornadoDevice dev : contexts) {
                if (useDependencies) {
                    final int event = dev.enqueueMarker();
                    barrier = dev.resolveEvent(event);
                }

                if (USE_VM_FLUSH) {
                    dev.flush();
                }
            }
        }

        final long t1 = System.nanoTime();
        final double elapsed = (t1 - t0) * 1e-9;
        if (!isWarmup) {
            totalTime += elapsed;
            invocations++;
        }

        if (graphContext.meta().isDebug()) {
            debug("vm: complete elapsed=%.9f s (%d iterations, %.9f s mean)", elapsed, invocations, (totalTime / invocations));
        }

        buffer.reset();

        if (TornadoOptions.printBytecodes) {
            System.out.println(bytecodesList.toString());
        }

        return barrier;
    }

    public void printTimes() {
        System.out.printf("vm: complete %d iterations - %.9f s mean and %.9f s total\n", invocations, (totalTime / invocations), totalTime);
    }

    public void clearProfiles() {
        for (final SchedulableTask task : tasks) {
            task.meta().getProfiles().clear();
        }
    }

    public void dumpEvents() {
        if (!ENABLE_PROFILING || !graphContext.meta().shouldDumpEvents()) {
            info("profiling and/or event dumping is not enabled");
            return;
        }

        for (final TornadoDevice device : contexts) {
            device.dumpEvents();
        }
    }

    public void dumpProfiles() {
        if (!graphContext.meta().shouldDumpProfiles()) {
            info("profiling is not enabled");
            return;
        }

        for (final SchedulableTask task : tasks) {
            final TaskMetaData meta = task.meta();
            for (final EventSet eventset : meta.getProfiles()) {
                final BitSet profiles = eventset.getProfiles();
                for (int i = profiles.nextSetBit(0); i != -1; i = profiles.nextSetBit(i + 1)) {
                    final Event profile = eventset.getDevice().resolveEvent(i);

                    if (profile.getStatus() == COMPLETE) {
                        System.out.printf("task: %s %s %.9f %9d %9d %9d\n", eventset.getDevice().getDeviceName(), meta.getId(), profile.getExecutionTime(), profile.getSubmitTime(),
                                profile.getStartTime(), profile.getEndTime());
                    }
                }

            }

        }
    }

}
