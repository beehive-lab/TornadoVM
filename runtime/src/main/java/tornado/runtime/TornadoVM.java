package tornado.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import tornado.api.Event;
import tornado.api.enums.TornadoExecutionStatus;
import tornado.common.CallStack;
import tornado.common.DeviceMapping;
import tornado.common.DeviceObjectState;
import tornado.common.SchedulableTask;
import tornado.common.Tornado;
import static tornado.common.Tornado.DEBUG;
import static tornado.common.Tornado.ENABLE_OOO_EXECUTION;
import static tornado.common.Tornado.VM_WAIT_EVENT;
import tornado.common.TornadoInstalledCode;
import tornado.common.TornadoLogger;
import tornado.common.enums.Access;
import tornado.common.exceptions.TornadoInternalError;
import static tornado.common.exceptions.TornadoInternalError.*;
import tornado.meta.Meta;
import tornado.runtime.api.GlobalObjectState;
import tornado.runtime.graph.ExecutionContext;
import static tornado.runtime.graph.GraphAssembler.*;

public class TornadoVM extends TornadoLogger {

    private final boolean useDependencies;

    private final ExecutionContext graphContext;
    private final List<Object> objects;
    private final GlobalObjectState[] globalStates;
    private final CallStack[] stacks;
    private final List<Event>[] events;
    private final List<DeviceMapping> contexts;
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
        events = new List[buffer.getInt()];

        installedCodes = new TornadoInstalledCode[stacks.length];

        for (int i = 0; i < events.length; i++) {
            events[i] = new ArrayList<>();
        }

        debug("found %d contexts", contexts.size());
        debug("created %d stacks", stacks.length);
        debug("created %d event lists", events.length);

        objects = graphContext.getObjects();
        globalStates = new GlobalObjectState[objects.size()];
        debug("fetching %d object states...", globalStates.length);
        for (int i = 0; i < objects.size(); i++) {
            final Object object = objects.get(i);
            globalStates[i] = TornadoRuntime.runtime.resolveObject(object);
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

        debug("vm ready to go");
        buffer.mark();
    }

    private DeviceObjectState resolveObjectState(int index, int device) {
        return globalStates[index].getDeviceState(contexts.get(device));
    }

    private static CallStack resolveStack(int index, int numArgs,
            CallStack[] stacks, DeviceMapping device) {
        if (stacks[index] == null) {
            stacks[index] = device.createStack(numArgs);
        }

        return stacks[index];
    }

    public void warmup() {
        execute(true);
    }

    public void execute() {
        execute(false);
    }

    private void execute(boolean isWarmup) {
        final long t0 = System.nanoTime();

        Event lastEvent = null;

        for (List<Event> waitList : events) {
            waitList.clear();
        }

        while (buffer.hasRemaining()) {
            final byte op = buffer.get();

            if (op == ALLOCATE) {
                final int objectIndex = buffer.getInt();
                final int contextIndex = buffer.getInt();

                if (isWarmup) {
                    continue;
                }

                final DeviceMapping device = contexts.get(contextIndex);
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

                final DeviceMapping device = contexts.get(contextIndex);
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

                final DeviceMapping device = contexts.get(contextIndex);
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

                final DeviceMapping device = contexts.get(contextIndex);
                final Object object = objects.get(objectIndex);
                if (DEBUG) {
                    debug("vm: STREAM_OUT [0x%x] %s on %s [event list=%d]",
                            object.hashCode(), object, device, eventList);
                }
                final DeviceObjectState objectState = resolveObjectState(
                        objectIndex, contextIndex);

                lastEvent = device.streamOut(object, objectState,
                        events[eventList]);
            } else if (op == LAUNCH) {
                final int gtid = buffer.getInt();
                final int contextIndex = buffer.getInt();
                final int taskIndex = buffer.getInt();
                final int numArgs = buffer.getInt();
                final int eventList = buffer.getInt();

                final DeviceMapping device = contexts.get(contextIndex);
                final CallStack stack = resolveStack(gtid, numArgs, stacks,
                        device);
                final List<Event> waitList = events[eventList];
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
                        TornadoInternalError.guarantee(objectState.isValid(),
                                "object is not valid: %s %s",
                                objects.get(argIndex), objectState);
                        stack.push(objects.get(argIndex), objectState);
                        if (accesses[i] == Access.WRITE
                                || accesses[i] == Access.READ_WRITE) {
                            objectState.setContents(true);
                        }
                    } else {
                        TornadoInternalError.shouldNotReachHere();
                    }
                }

                lastEvent = installedCode.launch(stack, task.meta(), waitList);

            } else if (op == ADD_DEP) {
                final int eventList = buffer.getInt();

                if (isWarmup) {
                    continue;
                }

                TornadoInternalError.guarantee(lastEvent != null,
                        "lastEvent is null");
                if (useDependencies && !(lastEvent instanceof EmptyEvent)) {
                    if (DEBUG) {
                        debug("vm: ADD_DEP %s to event list %d", lastEvent,
                                eventList);
                    }
                    events[eventList].add(lastEvent);
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
                TornadoInternalError.shouldNotReachHere();
            }

            if (lastEvent != null && !(lastEvent instanceof EmptyEvent)) {
//                lastEvent.waitOn();
                if (DEBUG) {
                    debug("vm: last event=%s", lastEvent);
                }
            }
        }

        if (!isWarmup) {
            if (useDependencies && contexts.size() == 1) {
                if (VM_WAIT_EVENT && lastEvent != null) {
                    lastEvent.waitOn();
                } else {
                    contexts.get(0).sync();
                }
            } else {
                for (DeviceMapping device : contexts) {
                    device.sync();
                }
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
    }

    public void dumpTimes() {
        System.out.printf("vm: complete %d iterations, %.9f s mean\n",
                invocations, (totalTime / invocations));
    }

    public void dumpProfiles() {
        for (final SchedulableTask task : tasks) {
            final Meta meta = task.meta();
            for (final Event profile : meta.getProfiles()) {
                if (profile.getStatus() == TornadoExecutionStatus.COMPLETE) {
                    System.out.printf("task: %s %.9f %9d %9d %9d\n", task.getName().substring(7), profile.getExecutionTime(), profile.getSubmitTime(), profile.getStartTime(), profile.getEndTime());
                }
            }
        }
    }

}
