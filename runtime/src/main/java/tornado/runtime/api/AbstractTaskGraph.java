/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
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
 *
 * Authors: James Clarkson
 *
 */
package tornado.runtime.api;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.phases.util.Providers;
import tornado.api.Event;
import tornado.api.meta.ScheduleMetaData;
import tornado.common.CallStack;
import tornado.common.DeviceObjectState;
import tornado.common.SchedulableTask;
import tornado.common.TornadoDevice;
import tornado.graal.compiler.TornadoSuitesProvider;
import tornado.runtime.TornadoVM;
import tornado.runtime.graph.*;
import tornado.runtime.sketcher.SketchRequest;

import static tornado.common.RuntimeUtilities.humanReadableByteCount;
import static tornado.common.RuntimeUtilities.isBoxedPrimitiveClass;
import static tornado.common.Tornado.VM_USE_DEPS;
import static tornado.common.Tornado.warn;
import static tornado.common.exceptions.TornadoInternalError.guarantee;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

public abstract class AbstractTaskGraph {

    private final ExecutionContext graphContext;

    public final static byte D2HCPY = 20; // D2HCPY(device, host, index)
    public final static byte H2DCPY = 21; // H2DCPY(host, device, index)
    public final static byte MODIFY = 30; // HMODIFY(index)
    public final static byte LOAD_REF = 8; // LOAD_REF(index)
    public final static byte LOAD_PRIM = 9; // LOAD_PRIM(index)
    public final static byte LAUNCH = 10; // LAUNCH() (args [, events])
    public final static byte DSYNC = 22; // DSYNC(device)
    public final static byte ARG_LIST = 11; // ARG_LIST(size)
    public final static byte CONTEXT = 12; // FRAME(tasktodevice_index, task_index)

    private byte[] hlcode = new byte[2048];
    private ByteBuffer hlBuffer;

    private GraphCompilationResult result;
    private TornadoVM vm;
    private Event event;

    public AbstractTaskGraph(String name) {
        graphContext = new ExecutionContext(name);

        hlBuffer = ByteBuffer.wrap(hlcode);
        hlBuffer.order(ByteOrder.LITTLE_ENDIAN);
        hlBuffer.rewind();
        result = null;
        event = null;
    }

    public SchedulableTask getTask(String id) {
        return graphContext.getTask(id);
    }

    public TornadoDevice getDevice() {
        return meta().getDevice();
    }

    public void setDevice(TornadoDevice device) {
        meta().setDevice(device);
    }

    public TornadoDevice getDeviceForTask(String id) {
        return graphContext.getDeviceForTask(id);
    }

    public long getReturnValue(String id) {
        CallStack stack = graphContext.getFrame(id);
        return stack.getReturnValue();
    }

    protected void addInner(SchedulableTask task) {
        Providers providers = getTornadoRuntime().getDriver(0).getProviders();
        TornadoSuitesProvider suites = getTornadoRuntime().getDriver(0).getSuitesProvider();

        int index = graphContext.addTask(task);

        if (task instanceof CompilableTask) {
            CompilableTask compilableTask = (CompilableTask) task;
            final ResolvedJavaMethod resolvedMethod = getTornadoRuntime()
                    .resolveMethod(compilableTask.getMethod());
            new SketchRequest(compilableTask.meta(), resolvedMethod, providers, suites.getGraphBuilderSuite(), suites.getSketchTier()).run();

        }

        hlBuffer.put(CONTEXT);
        int globalTaskId = graphContext.getTaskCount();
        hlBuffer.putInt(globalTaskId);
        graphContext.incrGlobalTaskCount();
        hlBuffer.putInt(index);
//		System.out.printf("inserting: 0x%x 0x%x 0x%x\n", CONTEXT, globalTaskId,
//				index);
        // insert parameters into variable tables

        // create parameter list
        final Object[] args = task.getArguments();
//		for(Object arg: args){
//			System.out.println("- arg: " + arg);
//		}
        hlBuffer.put(ARG_LIST);
        hlBuffer.putInt(args.length);

        for (int i = 0; i < args.length; i++) {
            final Object arg = args[i];
            index = graphContext.insertVariable(arg);
            if (arg.getClass().isPrimitive()
                    || isBoxedPrimitiveClass(arg.getClass())) {
                hlBuffer.put(LOAD_PRIM);
            } else {
                guarantee(arg != null, "null argument passed to task");
                hlBuffer.put(LOAD_REF);
            }
            hlBuffer.putInt(index);
        }

        // launch code
        hlBuffer.put(LAUNCH);
    }

    private void compile() {
//		dump();

        final ByteBuffer buffer = ByteBuffer.wrap(hlcode);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.limit(hlBuffer.position());

//		final long t0 = System.nanoTime();
        final Graph graph = GraphBuilder.buildGraph(graphContext, buffer);
//		final long t1 = System.nanoTime();
        result = GraphCompiler.compile(graph, graphContext);
//		final long t2 = System.nanoTime();
        vm = new TornadoVM(graphContext, result.getCode(), result.getCodeSize());
//		final long t3 = System.nanoTime();

//		System.out.printf("task graph: build graph %.9f s\n",(t1-t0)*1e-9);
//		System.out.printf("task graph: compile     %.9f s\n",(t2-t1)*1e-9);
//		System.out.printf("task graph: vm          %.9f s\n",(t3-t2)*1e-9);
        if (meta().shouldDumpSchedule()) {
            graphContext.print();
            graph.print();
//            result.dump();
        }
    }

    protected void scheduleInner() {

        if (result == null) {
            graphContext.assignToDevices();
            compile();
        }

        event = vm.execute();

    }

    public void apply(Consumer<SchedulableTask> consumer) {
        graphContext.apply(consumer);
    }

    protected void mapAllToInner(TornadoDevice device) {
        graphContext.mapAllTo(device);
    }

    public void dumpTimes() {
//		System.out.printf("Task Graph: %d tasks\n", events.size());
//		apply(task -> System.out
//				.printf("\t%s: status=%s, execute=%.8f s, total=%.8f s, queued=%.8f s\n",
//						task.getName(), task.getStatus(),
//						task.getExecutionTime(), task.getTotalTime(),
//						task.getQueuedTime()));
        vm.printTimes();
    }

    public void dumpProfiles() {
        vm.dumpProfiles();
    }

    public void dumpEvents() {
        vm.dumpEvents();
    }

    public void clearProfiles() {
        vm.clearProfiles();
    }

    public void waitOn() {
        if (VM_USE_DEPS && event != null) {
//        if (event != null) {
            event.waitOn();
        } else {
            // BUG waiting on an event seems unreliable, so we block on clFinish()
            graphContext.getDevices().forEach((TornadoDevice device) -> device.sync());
        }
    }

    protected void streamInInner(Object... objects) {
        for (Object object : objects) {
            if (object == null) {
                warn("null object passed into streamIn() in schedule %s", graphContext.getId());
                continue;
            }
            graphContext.getObjectState(object).setStreamIn(true);
        }
    }

    protected void streamOutInner(Object... objects) {
        for (Object object : objects) {
            if (object == null) {
                warn("null object passed into streamIn() in schedule %s", graphContext.getId());
                continue;
            }
            graphContext.getObjectState(object).setStreamOut(true);
        }
    }

    public void dump() {
        final int width = 16;
        System.out.printf("code  : capacity = %s, in use = %s \n",
                humanReadableByteCount(hlBuffer.capacity(), true),
                humanReadableByteCount(hlBuffer.position(), true));
        for (int i = 0; i < hlBuffer.position(); i += width) {
            System.out.printf("[0x%04x]: ", i);
            for (int j = 0; j < Math.min(hlBuffer.capacity() - i, width); j++) {
                if (j % 2 == 0) {
                    System.out.printf(" ");
                }
                if (j < hlBuffer.position() - i) {
                    System.out.printf("%02x", hlBuffer.get(i + j));
                } else {
                    System.out.printf("..");
                }
            }
            System.out.println();
        }
    }

    public void warmup() {
        if (result == null) {
            graphContext.assignToDevices();
            compile();
        }

        vm.warmup();
    }

    public void invalidateObjects() {
        if (vm != null) {
            vm.invalidateObjects();
        }
    }

    public void syncObject(Object object) {
        if (vm == null) {
            return;
        }
        graphContext.sync();
//        syncObjectInner(object).waitOn();
    }

    private Event syncObjectInner(Object object) {
        final LocalObjectState localState = graphContext.getObjectState(object);
        final GlobalObjectState globalState = localState.getGlobalState();
        final DeviceObjectState deviceState = globalState.getDeviceState();
        final TornadoDevice device = globalState.getOwner();
        final Event event = device.resolveEvent(device.streamOut(object, deviceState, null));
        return event;
    }

    public void syncObjects() {
        if (vm == null) {
            return;
        }

        graphContext.sync();
    }

    public void syncObjects(Object... objects) {
        if (vm == null) {
            return;
        }

        Event[] events = new Event[objects.length];
        for (int i = 0; i < objects.length; i++) {
            Object object = objects[i];
            events[i] = syncObjectInner(object);
        }

        for (Event event : events) {
            event.waitOn();
        }
    }

    public String getId() {
        return meta().getId();
    }

    public ScheduleMetaData meta() {
        return graphContext.meta();
    }

}
