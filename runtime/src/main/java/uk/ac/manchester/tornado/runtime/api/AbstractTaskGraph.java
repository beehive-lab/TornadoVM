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
package uk.ac.manchester.tornado.runtime.api;

import static uk.ac.manchester.tornado.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.common.RuntimeUtilities.isBoxedPrimitiveClass;
import static uk.ac.manchester.tornado.common.Tornado.PRINT_COMPILE_TIMES;
import static uk.ac.manchester.tornado.common.Tornado.VM_USE_DEPS;
import static uk.ac.manchester.tornado.common.Tornado.warn;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.TornadoRuntime.getTornadoRuntime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;
import java.util.HashSet;
import java.util.function.Consumer;

import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.annotations.Event;
import uk.ac.manchester.tornado.api.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.common.CallStack;
import uk.ac.manchester.tornado.common.DeviceObjectState;
import uk.ac.manchester.tornado.common.SchedulableTask;
import uk.ac.manchester.tornado.common.TornadoDevice;
import uk.ac.manchester.tornado.graal.compiler.TornadoSuitesProvider;
import uk.ac.manchester.tornado.runtime.TornadoVM;
import uk.ac.manchester.tornado.runtime.graph.ExecutionContext;
import uk.ac.manchester.tornado.runtime.graph.GraphCompilationResult;
import uk.ac.manchester.tornado.runtime.graph.TornadoGraph;
import uk.ac.manchester.tornado.runtime.graph.TornadoGraphBuilder;
import uk.ac.manchester.tornado.runtime.graph.TornadoGraphCompiler;
import uk.ac.manchester.tornado.runtime.graph.nodes.ContextNode;
import uk.ac.manchester.tornado.runtime.sketcher.SketchRequest;

public abstract class AbstractTaskGraph {

    private final ExecutionContext graphContext;

    // @formatter:off
    public enum TornadoGraphBitcodes {
        
        LOAD_REF ((byte)1), 
        LOAD_PRIM((byte)2), 
        LAUNCH   ((byte)3), 
        ARG_LIST ((byte)4),
        CONTEXT  ((byte)5);
        
        private byte index;
        
        TornadoGraphBitcodes(byte index) {
            this.index = index;
        }
        
        public byte index() {
            return index;
        }
        
    }
    // @formatter:on

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
            final ResolvedJavaMethod resolvedMethod = getTornadoRuntime().resolveMethod(compilableTask.getMethod());
            new SketchRequest(compilableTask.meta(), resolvedMethod, providers, suites.getGraphBuilderSuite(), suites.getSketchTier()).run();

        }

        hlBuffer.put(TornadoGraphBitcodes.CONTEXT.index());
        int globalTaskId = graphContext.getTaskCount();
        hlBuffer.putInt(globalTaskId);
        graphContext.incrGlobalTaskCount();
        hlBuffer.putInt(index);

        // create parameter list
        final Object[] args = task.getArguments();
        hlBuffer.put(TornadoGraphBitcodes.ARG_LIST.index());
        hlBuffer.putInt(args.length);

        for (int i = 0; i < args.length; i++) {
            final Object arg = args[i];
            index = graphContext.insertVariable(arg);
            if (arg.getClass().isPrimitive() || isBoxedPrimitiveClass(arg.getClass())) {
                hlBuffer.put(TornadoGraphBitcodes.LOAD_PRIM.index());
            } else {
                guarantee(arg != null, "null argument passed to task");
                hlBuffer.put(TornadoGraphBitcodes.LOAD_REF.index());
            }
            hlBuffer.putInt(index);
        }

        // launch code
        hlBuffer.put(TornadoGraphBitcodes.LAUNCH.index());
    }

    private void updateDeviceContext(TornadoGraph graph) {
        BitSet deviceContexts = graph.filter(ContextNode.class);
        final ContextNode contextNode = (ContextNode) graph.getNode(deviceContexts.nextSetBit(0));
        contextNode.setDeviceIndex(meta().getDeviceIndex());
        graphContext.addDevice(meta().getDevice());
    }

    private void compile(boolean setNewDevice) {

        final ByteBuffer buffer = ByteBuffer.wrap(hlcode);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.limit(hlBuffer.position());

        // final long t0 = System.nanoTime();
        final TornadoGraph graph = TornadoGraphBuilder.buildGraph(graphContext, buffer);
        // final long t1 = System.nanoTime();

        if (setNewDevice) {
            updateDeviceContext(graph);
        }

        result = TornadoGraphCompiler.compile(graph, graphContext);
        // final long t2 = System.nanoTime();
        vm = new TornadoVM(graphContext, result.getCode(), result.getCodeSize());
        // final long t3 = System.nanoTime();

        if (meta().shouldDumpSchedule()) {
            graphContext.print();
            graph.print();
            result.dump();
        }
    }

    private boolean compareDevices(HashSet<TornadoDevice> lastDevices, TornadoDevice device2) {
        return lastDevices.contains(device2);
    }

    private static class CompileInfo {
        private boolean compile;
        private boolean updateDevice;

        public CompileInfo(boolean compile, boolean updateDevice) {
            super();
            this.compile = compile;
            this.updateDevice = updateDevice;
        }
    }

    public boolean isLastDeviceListEmpty() {
        return graphContext.getLastDevices().size() == 0;
    }

    /**
     * It queries if the task has to be recompiled. It returns two values:
     * <p>
     * <li>compile: This indicates if it has to be compiled</li>
     * <li>updateDevice:This indicates if there is a new device for the same
     * task</li>
     * </p>
     * 
     * @return
     */
    private CompileInfo extractCompileInfo() {
        if (result == null && isLastDeviceListEmpty()) {
            return new CompileInfo(true, false);
        } else if (result != null && isLastDeviceListEmpty() == false && !(compareDevices(graphContext.getLastDevices(), meta().getDevice()))) {
            return new CompileInfo(true, true);
        }
        return new CompileInfo(false, false);
    }

    private void compileTasks() {
        CompileInfo compileInfo = extractCompileInfo();
        if (compileInfo.compile) {
            graphContext.assignToDevices();
            compile(compileInfo.updateDevice);
        }
        graphContext.addLastDevice(meta().getDevice());
        graphContext.newStack(compileInfo.updateDevice);
    }

    protected void scheduleInner() {
        long t0 = System.nanoTime();
        compileTasks();
        long t1 = System.nanoTime();
        if (PRINT_COMPILE_TIMES) {
            System.out.printf("compile: compileTasks: " + (t1 - t0) + "ns" + "\n");
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
            event.waitOn();
        } else {
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
        System.out.printf("code  : capacity = %s, in use = %s \n", humanReadableByteCount(hlBuffer.capacity(), true), humanReadableByteCount(hlBuffer.position(), true));
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
        compileTasks();
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
