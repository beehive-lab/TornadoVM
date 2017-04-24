package tornado.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import tornado.api.Event;
import tornado.api.enums.TornadoExecutionStatus;
import tornado.common.*;
import tornado.common.enums.Access;
import tornado.meta.Meta;
import tornado.runtime.api.GlobalObjectState;
import tornado.runtime.graph.ExecutionContext;

import static tornado.common.Tornado.*;
import static tornado.common.exceptions.TornadoInternalError.*;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;
import static tornado.runtime.graph.GraphAssembler.*;

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

    private final byte[] code;
    private final ByteBuffer buffer;

    private double totalTime;
    private long invocations;

    @SuppressWarnings("unchecked")
    public TornadoVM(ExecutionContext graphContext, byte[] code, int limit) {
        this.graphContext = graphContext;
        this.code = code;

        useDependencies = ENABLE_OOO_EXECUTION | Boolean.parseBoolean(Tornado.getProperty("tornado.vm.deps", "False"));
        totalTime = 0;
        invocations = 0;

        buffer = ByteBuffer.wrap(code);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.limit(limit);

        debug("loading tornado vm...");

        guarantee(buffer.get() == SETUP, "invalid code");
        contexts = graphContext.getDevices();
        buffer.getInt();
        stacks = new CallStack[buffer.getInt()];
        events = new int[buffer.getInt()][MAX_EVENTS];
        eventsIndicies = new int[events.length];

        installedCodes = new TornadoInstalledCode[stacks.length];

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
            globalStates[i] = getTornadoRuntime().resolveObject(object);
            debug("\tobject[%d]: [0x%x] %s %s", i, object.hashCode(), object,
                    globalStates[i]);
        }

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

        constants = graphContext.getConstants();
        tasks = graphContext.getTasks();

        debug("%s - vm ready to go", graphContext.getId());
        buffer.mark();
    }

    private DeviceObjectState resolveObjectState(int index, int device) {
        return globalStates[index].getDeviceState(contexts.get(device));
    }

    private static CallStack resolveStack(int index, int numArgs,
            CallStack[] stacks, TornadoDevice device) {
        if (stacks[index] == null) {
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

//        if (!isWarmup) {
//            for (TornadoDevice device : contexts) {
//                device.markEvent();
//            }
//        }
        while (buffer.hasRemaining()) {
            final byte op = buffer.get();

            if (op == ALLOCATE) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();

                if (isWarmup) {
                    continue;
                }

                final TornadoDevice device = contexts.get(contextIndex);
                final Object object = objects.get(objectIndex);

                if (DEBUG) {
                    debug("vm: ALLOCATE [0x%x] %s on %s", object.hashCode(),
                            object, device);
                }
                final DeviceObjectState objectState = resolveObjectState(
                        objectIndex, contextIndex);

                lastEvent = device.ensureAllocated(object, objectState);

            } else if (op == COPY_IN) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int eventList = buffer.getInt();

                if (isWarmup) {
                    continue;
                }

                final TornadoDevice device = contexts.get(contextIndex);
                final Object object = objects.get(objectIndex);
                if (DEBUG) {
                    debug("vm: COPY_IN [0x%x] %s on %s [event list=%d]",
                            object.hashCode(), object, device, eventList);
                }
                final DeviceObjectState objectState = resolveObjectState(
                        objectIndex, contextIndex);
                if (DEBUG) {
                    debug("vm: state=%s", objectState);
                }

                lastEvent = device.ensurePresent(object, objectState);

            } else if (op == STREAM_IN) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int eventList = buffer.getInt();

                if (isWarmup) {
                    continue;
                }

                final TornadoDevice device = contexts.get(contextIndex);
                final Object object = objects.get(objectIndex);
                if (DEBUG) {
                    debug("vm: STREAM_IN [0x%x] %s on %s [event list=%d]",
                            object.hashCode(), object, device, eventList);
                }
                final DeviceObjectState objectState = resolveObjectState(
                        objectIndex, contextIndex);
                if (DEBUG) {
                    debug("vm: state=%s", objectState);
                }

                lastEvent = device.streamIn(object, objectState);

            } else if (op == STREAM_OUT) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int eventList = buffer.getInt();

                if (isWarmup) {
                    continue;
                }

                final TornadoDevice device = contexts.get(contextIndex);
                final Object object = objects.get(objectIndex);
                if (DEBUG) {
                    debug("vm: STREAM_OUT [0x%x] %s on %s [event list=%d]",
                            object.hashCode(), object, device, eventList);
                }
                final DeviceObjectState objectState = resolveObjectState(
                        objectIndex, contextIndex);

                lastEvent = device.streamOut(object, objectState,
                        events[eventList]);
                eventsIndicies[eventList] = 0;
            } else if (op == LAUNCH) {
                final int gtid = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int taskIndex = buffer.getInt();
                final int numArgs = buffer.getInt();
                final int eventList = buffer.getInt();

                final TornadoDevice device = contexts.get(contextIndex);
                final CallStack stack = resolveStack(gtid, numArgs, stacks,
                        device);
                final int[] waitList = (eventList == -1) ? null : events[eventList];
                final SchedulableTask task = tasks.get(taskIndex);

                if (DEBUG) {
                    debug("vm: LAUNCH %s on %s [event list=%d]",
                            tasks.get(taskIndex).getName(),
                            contexts.get(contextIndex), eventList);
                }

                if (installedCodes[taskIndex] == null) {
                    final long compileStart = System.nanoTime();
                    task.mapTo(device);
                    installedCodes[taskIndex] = device.installCode(task);
                    final long compileEnd = System.nanoTime();
                    if (PRINT_COMPILE_TIMES) {
                        System.out.printf("compile: task %s tornado %.9f\n", task.getName(), (compileEnd - compileStart) * 1e-9);
                    }

                    if (DEBUG) {
                        debug("vm: compiled in %.9f s",
                                (compileEnd - compileStart) * 1e-9);
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
                if (!stack.isOnDevice()) {
                    stack.reset();
                }
                for (int i = 0; i < numArgs; i++) {
                    final byte argType = buffer.get();
                    final int argIndex = buffer.getInt();

                    if (stack.isOnDevice()) {
                        continue;
                    }

                    if (argType == CONSTANT_ARG) {
                        stack.push(constants.get(argIndex));
                    } else if (argType == REFERENCE_ARG) {
                        final DeviceObjectState objectState = resolveObjectState(
                                argIndex, contextIndex);
                        guarantee(objectState.isValid(),
                                "object is not valid: %s %s",
                                objects.get(argIndex), objectState);
                        stack.push(objects.get(argIndex), objectState);
                        if (accesses[i] == Access.WRITE
                                || accesses[i] == Access.READ_WRITE) {
                            objectState.setContents(true);
                        }
                    } else {
                        shouldNotReachHere();
                    }
                }

                lastEvent = installedCode.launch(stack, task.meta(), waitList);
                if (eventList != -1) {
                    eventsIndicies[eventList] = 0;
                }

            } else if (op == ADD_DEP) {
                final int eventList = buffer.getInt();

                if (isWarmup) {
                    continue;
                }

                if (useDependencies && lastEvent != -1) {
                    if (DEBUG) {
                        debug("vm: ADD_DEP %s to event list %d", lastEvent,
                                eventList);
                    }

                    guarantee(eventsIndicies[eventList] < events[eventList].length, "event list is too small");
                    events[eventList][eventsIndicies[eventList]] = lastEvent;
                    eventsIndicies[eventList]++;

                }
            } else if (op == BARRIER) {
                final int eventList = buffer.getInt();

                if (isWarmup) {
                    continue;
                }

                if (DEBUG) {
                    debug("vm: BARRIER event list %d", eventList);
                }

            } else if (op == END) {
                if (DEBUG) {
                    debug("vm: END");
                }
                break;
            } else {
                if (DEBUG) {
                    debug("vm: invalid op 0x%x(%d)", op, op);
                }
                shouldNotReachHere();
            }
        }

        Event barrier = EMPTY_EVENT;
        if (!isWarmup) {
            if (contexts.size() == 1) {
                final TornadoDevice device = contexts.get(0);
                final int event = device.enqueueBarrier();
                barrier = device.resolveEvent(event);
//                device.flushEvents();
            } else if (contexts.size() > 1) {
                unimplemented("multi-context applications");
            }
        }

        final long t1 = System.nanoTime();
        final double elapsed = (t1 - t0) * 1e-9;
        if (!isWarmup) {
            totalTime += elapsed;
            invocations++;
        }

        if (DEBUG) {
            debug("vm: complete elapsed=%.9f s (%d iterations, %.9f s mean)",
                    elapsed, invocations, (totalTime / invocations));
        }

        buffer.reset();

        return barrier;
    }

    public void dumpTimes() {
        System.out.printf("vm: complete %d iterations, %.9f s mean\n",
                invocations, (totalTime / invocations));
    }

    public void clearProfiles() {
        for (final SchedulableTask task : tasks) {
            task.meta().getProfiles().clear();
        }
    }

    public void dumpEvents() {
        if (!ENABLE_PROFILING) {
            warn("profiling is not enabled");
            return;
        }

        for (final TornadoDevice device : contexts) {
            device.dumpEvents();
        }
    }

    public void dumpProfiles() {
        if (!ENABLE_PROFILING) {
            warn("profiling is not enabled");
            return;
        }

        for (final SchedulableTask task : tasks) {
            final Meta meta = task.meta();
            for (final Event profile : meta.getProfiles()) {
                if (profile.getStatus() == TornadoExecutionStatus.COMPLETE) {
                    System.out.printf("task: %s %s %.9f %9d %9d %9d\n", task.getDeviceMapping().getDeviceName(), task.getName().substring(7), profile.getExecutionTime(), profile.getSubmitTime(), profile.getStartTime(), profile.getEndTime());
                }
            }
        }
    }

}
