/*
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import tornado.api.Event;
import tornado.api.meta.TaskMetaData;
import tornado.common.TornadoDevice.BlockingMode;
import tornado.common.TornadoDevice.CacheMode;
import tornado.common.TornadoDevice.SharingMode;
import tornado.common.*;
import tornado.common.enums.Access;
import tornado.runtime.graph.ExecutionContext;

import static tornado.api.enums.TornadoExecutionStatus.COMPLETE;
import static tornado.common.RuntimeUtilities.*;
import static tornado.common.Tornado.*;
import static tornado.common.exceptions.TornadoInternalError.*;
import static tornado.runtime.cache.TornadoObjectCache.invalidate;
import static tornado.runtime.cache.TornadoObjectCache.postfetch;
import static tornado.runtime.graph.GraphAssembler.*;

public class TornadoVM extends TornadoLogger {

    private static final Event EMPTY_EVENT = new EmptyEvent();

    private static final int MAX_EVENTS = 32;
    private final boolean useDependencies;

    private final ExecutionContext graphContext;

    private DeviceFrame[] frames;
    private int[][] events;
    private int[] eventsIndicies;
    private List<TornadoDevice> contexts;
    private TornadoInstalledCode[] installedCodes;
    private Set<Object> streamIn;
    private Set<Object> streamOut;
    private final List<Object> parameters;

    private final List<SchedulableTask> tasks;

    private final byte[] code;
    private final ByteBuffer buffer;

    private double totalTime;
    private double totalIssueTime;
    private long invocations;
    private long totalBytecodes;

    @SuppressWarnings("unchecked")
    public TornadoVM(ExecutionContext graphContext, byte[] code, int limit) {
        this.graphContext = graphContext;
        this.code = code;

        totalTime = 0;
        totalIssueTime = 0;
        invocations = 0;
        totalBytecodes = 0;

        buffer = ByteBuffer.wrap(code);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.limit(limit);

//        constants = graphContext.getConstants();
        tasks = graphContext.getTasks();
        parameters = graphContext.getParameters();
//        objects = graphContext.getObjects();
//        cacheManagers = new GlobalCacheEntry[objects.size()];
        debug("vm frame has %d objects...", parameters.size());
        for (int i = 0; i < parameters.size(); i++) {
            final Object object = parameters.get(i);
            guarantee(object != null, "null object found in TornadoVM");
            debug("\tobject[%2d]: [0x%08x] %s", i,
                    object.hashCode(),
                    object.getClass().getName()
            );
        }

        useDependencies = graphContext.meta().enableOooExecution() | VM_USE_DEPS;

    }

    public void reset() {
        debug("setting up %s on tornado vm", graphContext.getId());
        buffer.rewind();

        guarantee(buffer.get() == SETUP, "invalid code");
        contexts = graphContext.getDevices();
        streamIn = graphContext.getStreamInSet();
        streamOut = graphContext.getStreamOutSet();
        frames = graphContext.getStacks();

        buffer.getInt();

        final int numStacks = buffer.getInt();
        if (frames == null) {
            frames = new DeviceFrame[numStacks];
            graphContext.setStacks(frames);
            installedCodes = new TornadoInstalledCode[frames.length];
        } else {
            for (DeviceFrame stack : frames) {
                stack.reset();
            }

            Arrays.fill(frames, null);
            Arrays.fill(installedCodes, null);
        }

        final int numEventLists = buffer.getInt();
        if (events == null) {
            events = new int[numEventLists][MAX_EVENTS];
            eventsIndicies = new int[events.length];
        }

        // reset all event lists
        for (int i = 0; i < events.length; i++) {
            Arrays.fill(events[i], -1);
            eventsIndicies[i] = 0;
        }

        debug("found %d contexts", contexts.size());
        debug("created %d stacks", frames.length);
        debug("created %d event lists", events.length);

        byte op = buffer.get();
        while (op != BEGIN) {
            guarantee(op == CONTEXT, "invalid code: 0x%x", op);
            final int deviceIndex = buffer.getInt();
            debug("loading context %s", contexts.get(deviceIndex));
            final long t0 = System.nanoTime();
            contexts.get(deviceIndex).ensureLoaded();
            final long t1 = System.nanoTime();
            debug("loaded in %.9f s", (t1 - t0) * 1e-9);
            op = buffer.get();
        }

        debug("%s - vm ready to go", graphContext.getId());
        buffer.mark();
    }

    private Object resolveObject(int index) {
        return parameters.get(index);
    }

    private static DeviceFrame resolveStack(int index, int numArgs,
            DeviceFrame[] stacks, TornadoDevice device) {
        if (stacks[index] == null) {
            stacks[index] = device.createStack(numArgs);
        }

        return stacks[index];
    }

    public void invalidateObjects() {
        for (TornadoDevice device : contexts) {
            device.flushCache();
        }
    }

    public void warmup() {
        execute(true);
    }

    public Event execute() {
        return execute(false);
    }

    @Deprecated
    private int allocate(final boolean shouldCopy, final int objectIndex, final int contextIndex) {
        unimplemented();
        return -1;
    }

    private int readHost(BlockingMode blocking, CacheMode cacheable, SharingMode exclusive, final int objectIndex, final int contextIndex, final int eventList, final int[] waitList) {
        final TornadoDevice device = contexts.get(contextIndex);
        final Object object = parameters.get(objectIndex);
        if (graphContext.meta().isDebug()) {
            debug("vm: READ_HOST %s %s %s [0x%x] %s on %s [event list=%d]",
                    blocking, cacheable, exclusive,
                    object.hashCode(), object, device, eventList
            );
        }

        final int lastEvent = device.read(blocking,
                exclusive,
                cacheable,
                object,
                useDependencies ? waitList : null);

        if (eventList != -1) {
            eventsIndicies[eventList] = 0;
        }

        return lastEvent;
    }

    private int writeHost(BlockingMode blocking, CacheMode cacheable, final int objectIndex, final int contextIndex, final int eventList, final int[] waitList) {
        final TornadoDevice device = contexts.get(contextIndex);
        final Object object = parameters.get(objectIndex);
        if (graphContext.meta().isDebug()) {
            debug("vm: WRITE_HOST %s %s [0x%x] %s on %s [event list=%d]",
                    blocking, cacheable, object.hashCode(), object, device, eventList
            );
        }

        final int lastEvent = device.write(
                blocking,
                cacheable,
                object,
                useDependencies ? waitList : null);

        if (eventList != -1) {
            eventsIndicies[eventList] = 0;
        }

        return lastEvent;
    }

    private int postFetch(int index) {
        final Object object = parameters.get(index);
        if (!streamOut.contains(object)) {
            return -1;
        }

        if (graphContext.meta().isDebug()) {
            debug("vm: POST_FETCH [0x%x]",
                    object.hashCode()
            );
        }

        return postfetch(object);

    }

    private int launch(final byte mode, final int contextIndex, final TornadoInstalledCode installedCode, final int numArgs, final int eventList, final DeviceFrame stack, final int[] waitList, final SchedulableTask task) {

        final TornadoDevice device = contexts.get(contextIndex);

        if (graphContext.meta().isDebug()) {
            debug("vm: LAUNCH %s on %s [event list=%d]",
                    task.getName(),
                    device, eventList);
        }

        final Access[] accesses = task.getArgumentsAccess();
        if (!stack.isOnDevice()) {
            stack.reset();
        }
        for (int i = 0; i < numArgs; i++) {
            final byte argType = buffer.get();
            guarantee(argType == PUSH_ARG, "invalid bytecode (%d)", argType);
            final int argIndex = buffer.getInt();
            final Object arg = parameters.get(argIndex);
            if (arg == null || isPrimitive(arg.getClass()) || isBoxedPrimitive(arg)) {
                if (!stack.isOnDevice()) {
                    stack.push(arg);
                }
                if (graphContext.meta().isDebug()) {
                    debug("\t[%2d]: %s", i, arg);
                }
            } else {
                if (!stack.isOnDevice()) {
                    final long address = (OPENCL_USE_RELATIVE_ADDRESSES) ? device.toRelativeDeviceAddress(arg) : device.toAbsoluteDeviceAddress(arg);
                    if (graphContext.meta().isDebug()) {
                        debug("\t[%2d]: 0x%x", i, address);
                    }
                    stack.push(arg, address);
                }
            }

        }

        final int lastEvent;
        if (useDependencies) {
            lastEvent = installedCode.launchWithDeps(stack, task.meta(), waitList);
        } else {
            lastEvent = installedCode.launchWithoutDeps(stack, task.meta());
        }
        if (eventList != -1) {
            eventsIndicies[eventList] = 0;
        }
        return lastEvent;
    }

    private TornadoInstalledCode resolveCode(final int taskIndex, SchedulableTask task, TornadoDevice device) {
        if (installedCodes[taskIndex] == null) {
            final long compileStart = System.nanoTime();
            installedCodes[taskIndex] = device.installCode(task);
            final long compileEnd = System.nanoTime();
            if (graphContext.meta().shouldPrintCompileTimes()) {
                System.out.printf("vm: compiled task %s in %.9f s\n", task.getName(), (compileEnd - compileStart) * 1e-9);
            }

            if (graphContext.meta().isDebug()) {
                debug("vm: compiled in %.9f s",
                        (compileEnd - compileStart) * 1e-9);
            }

        }
        return installedCodes[taskIndex];
    }

    private Event execute(boolean isWarmup) {
        final long t0 = System.nanoTime();

        int lastEvent = -1;
        long bytecodes = 0;

        for (int[] waitList : events) {
            Arrays.fill(waitList, -1);
        }

        for (Object volatileObject : streamIn) {
            invalidate(volatileObject);
        }

        while (buffer.hasRemaining()) {
            final byte op = buffer.get();

            if (op == ALLOCATE) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();

                if (isWarmup) {
                    continue;
                }

                lastEvent = allocate(false, objectIndex, contextIndex);

            } else if (op == ALLOCATE_OR_COPY) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();

                if (isWarmup) {
                    continue;
                }

                lastEvent = allocate(true, objectIndex, contextIndex);

            } else if (op == READ_HOST) {
                final byte mode = buffer.get();
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int eventList = buffer.getInt();
                final int[] waitList = (useDependencies && eventList != -1) ? events[eventList] : null;

                if (isWarmup) {
                    continue;
                }

                lastEvent = readHost(decodeBlockingMode(mode), decodeCacheMode(mode), decodeSharingMode(mode), objectIndex, contextIndex, eventList, waitList);
            } else if (op == WRITE_HOST) {
                final byte mode = buffer.get();
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int eventList = buffer.getInt();
                final int[] waitList = (useDependencies) ? events[eventList] : null;

                if (isWarmup) {
                    continue;
                }

                lastEvent = writeHost(decodeBlockingMode(mode), decodeCacheMode(mode), objectIndex, contextIndex, eventList, waitList);
            } else if (op == LAUNCH) {
                final byte mode = buffer.get();
                final int gtid = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int taskIndex = buffer.getInt();
                final int numArgs = buffer.getInt();
                final int eventList = buffer.getInt();

                final TornadoDevice device = contexts.get(contextIndex);
                final DeviceFrame stack = resolveStack(gtid, numArgs, frames,
                        device);
                final int[] waitList = (useDependencies && eventList != -1) ? events[eventList] : null;
                final SchedulableTask task = tasks.get(taskIndex);

                final TornadoInstalledCode installedCode = resolveCode(taskIndex, task, device);

                if (isWarmup) {
                    for (int i = 0; i < numArgs; i++) {
                        buffer.get();
                        buffer.getInt();
                    }
                    continue;
                }

                lastEvent = launch(mode, contextIndex, installedCode, numArgs, eventList, stack, waitList, task);
            } else if (op == PUSH_EVENT_Q) {
                final int eventList = buffer.getInt();

                if (isWarmup) {
                    continue;
                }

                if (useDependencies && lastEvent != -1) {
                    if (graphContext.meta().isDebug()) {
                        debug("vm: PUSH_EVENT_Q %s to event list %d", lastEvent,
                                eventList);
                    }

                    guarantee(eventsIndicies[eventList] < events[eventList].length, "event list is too small");
                    events[eventList][eventsIndicies[eventList]] = lastEvent;
                    eventsIndicies[eventList]++;

                }
            } else if (op == POST_FETCH) {
                final int index = buffer.getInt();
                lastEvent = postFetch(index);
            } else if (op == BARRIER) {
                final int eventList = buffer.getInt();
                final int[] waitList = (useDependencies && eventList != -1) ? events[eventList] : null;

                if (isWarmup) {
                    continue;
                }

                if (graphContext.meta().isDebug()) {
                    debug("vm: BARRIER event list %d", eventList);
                }

                if (contexts.size() == 1) {
                    final TornadoDevice device = contexts.get(0);
                    lastEvent = device.enqueueBarrier(waitList);
                } else if (contexts.size() > 1) {
                    shouldNotReachHere("unimplemented multi-context barrier");
                }

                if (eventList != -1) {
                    eventsIndicies[eventList] = 0;
                }
            } else if (op == END) {
                if (graphContext.meta().isDebug()) {
                    debug("vm: END");
                }
                break;
            } else {
                if (graphContext.meta().isDebug()) {
                    debug("vm: invalid op 0x%x(%d)", op, op);
                }
                shouldNotReachHere();
            }
            bytecodes++;
        }
        final long t1 = System.nanoTime();

        Event barrier = EMPTY_EVENT;
        if (!isWarmup) {
            if (contexts.size() == 1) {
                final TornadoDevice device = contexts.get(0);

                if (useDependencies) {
                    final int event = device.enqueueBarrier();
                    barrier = device.resolveEvent(event);
                }

                if (USE_VM_FLUSH) {
                    device.flush();
                }
            } else if (contexts.size() > 1) {
                if (USE_VM_FLUSH) {
                    contexts.forEach((context) -> context.flush());
                }
            }
        }

        final long t2 = System.nanoTime();
        final double issue = (t1 - t0) * 1e-9;
        final double elapsed = (t2 - t0) * 1e-9;
        if (!isWarmup) {
            totalTime += elapsed;
            totalIssueTime += issue;
            totalBytecodes += bytecodes;
            invocations++;
        }

        if (graphContext.meta().isDebug()) {
            debug("vm: complete issue=%.9f s, elapsed=%.9f s (%s)\n",
                    issue, elapsed, humanReadableBPS((double) bytecodes / issue));
        }

        buffer.reset();

        return barrier;
    }

    public void printTimes() {
        System.out.printf("vm: complete %d iterations:\n\tissue =%.9f s mean and %.9f s total\n\telapsed=%.9f s mean and %.9f s total\n\tspeed=%s average\n",
                invocations,
                totalIssueTime / invocations, totalIssueTime,
                totalTime / invocations, totalTime,
                humanReadableBPS(totalBytecodes / totalIssueTime));
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
                        System.out.printf("task: %s %s %.9f %9d %9d %9d\n", task.getDevice().getDeviceName(), meta.getId(), profile.getExecutionTime(), profile.getSubmitTime(), profile.getStartTime(), profile.getEndTime());
                    }
                }

            }

        }
    }

}
