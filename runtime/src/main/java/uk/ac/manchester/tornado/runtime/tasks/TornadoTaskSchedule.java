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
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.runtime.tasks;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getTornadoRuntime;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.isBoxedPrimitiveClass;
import static uk.ac.manchester.tornado.runtime.common.Tornado.PRINT_COMPILE_TIMES;
import static uk.ac.manchester.tornado.runtime.common.Tornado.VM_USE_DEPS;
import static uk.ac.manchester.tornado.runtime.common.Tornado.warn;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.function.Consumer;

import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.AbstractTaskGraph;
import uk.ac.manchester.tornado.api.DynamicPolicy;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task1;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task10;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task15;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task6;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task7;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task8;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task9;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.runtime.TornadoVM;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;
import uk.ac.manchester.tornado.runtime.graph.ExecutionContext;
import uk.ac.manchester.tornado.runtime.graph.GraphCompilationResult;
import uk.ac.manchester.tornado.runtime.graph.TornadoGraph;
import uk.ac.manchester.tornado.runtime.graph.TornadoGraphBuilder;
import uk.ac.manchester.tornado.runtime.graph.TornadoGraphCompiler;
import uk.ac.manchester.tornado.runtime.graph.nodes.ContextNode;
import uk.ac.manchester.tornado.runtime.sketcher.SketchRequest;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;

public class TornadoTaskSchedule implements AbstractTaskGraph {

    private final ExecutionContext graphContext;

    private byte[] hlcode = new byte[2048];
    private ByteBuffer hlBuffer;

    private GraphCompilationResult result;
    private TornadoVM vm;
    private Event event;
    private String taskName;

    private ArrayList<TaskPackage> taskPackages = new ArrayList<>();
    private ArrayList<Object> streamOutObjects = new ArrayList<>();

    public TornadoTaskSchedule(String name) {
        graphContext = new ExecutionContext(name);
        hlBuffer = ByteBuffer.wrap(hlcode);
        hlBuffer.order(ByteOrder.LITTLE_ENDIAN);
        hlBuffer.rewind();
        result = null;
        event = null;
        this.taskName = name;
    }

    public String getTaskScheduleName() {
        return taskName;
    }

    @Override
    public SchedulableTask getTask(String id) {
        return graphContext.getTask(id);
    }

    @Override
    public TornadoDevice getDevice() {
        return meta().getDevice();
    }

    @Override
    public void setDevice(TornadoDevice device) {
        meta().setDevice(device);
    }

    @Override
    public TornadoAcceleratorDevice getDeviceForTask(String id) {
        return graphContext.getDeviceForTask(id);
    }

    @Override
    public long getReturnValue(String id) {
        CallStack stack = graphContext.getFrame(id);
        return stack.getReturnValue();
    }

    @Override
    public void addInner(SchedulableTask task) {
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

    private boolean compareDevices(HashSet<TornadoAcceleratorDevice> lastDevices, TornadoAcceleratorDevice device2) {
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

    @Override
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

    @Override
    public void scheduleInner() {
        long t0 = System.nanoTime();
        compileTasks();
        long t1 = System.nanoTime();
        if (PRINT_COMPILE_TIMES) {
            System.out.printf("compile: compileTasks: " + (t1 - t0) + "ns" + "\n");
        }
        event = vm.execute();
    }

    @Override
    public void apply(Consumer<SchedulableTask> consumer) {
        graphContext.apply(consumer);
    }

    @Override
    public void mapAllToInner(TornadoDevice device) {
        graphContext.mapAllTo(device);
    }

    @Override
    public void dumpTimes() {
        vm.printTimes();
    }

    @Override
    public void dumpProfiles() {
        vm.dumpProfiles();
    }

    @Override
    public void dumpEvents() {
        vm.dumpEvents();
    }

    @Override
    public void clearProfiles() {
        vm.clearProfiles();
    }

    @Override
    public void waitOn() {
        if (VM_USE_DEPS && event != null) {
            event.waitOn();
        } else {
            graphContext.getDevices().forEach((TornadoAcceleratorDevice device) -> device.sync());
        }
    }

    @Override
    public void streamInInner(Object... objects) {
        for (Object object : objects) {
            if (object == null) {
                warn("null object passed into streamIn() in schedule %s", graphContext.getId());
                continue;
            }
            graphContext.getObjectState(object).setStreamIn(true);
        }
    }

    @Override
    public void streamOutInner(Object... objects) {
        for (Object object : objects) {
            if (object == null) {
                warn("null object passed into streamIn() in schedule %s", graphContext.getId());
                continue;
            }
            streamOutObjects.add(object);
            graphContext.getObjectState(object).setStreamOut(true);
        }
    }

    @Override
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

    @Override
    public void warmup() {
        compileTasks();
        vm.warmup();
    }

    @Override
    public void invalidateObjects() {
        if (vm != null) {
            vm.invalidateObjects();
        }
    }

    @Override
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
        final TornadoAcceleratorDevice device = globalState.getOwner();
        final Event event = device.resolveEvent(device.streamOut(object, deviceState, null));
        return event;
    }

    @Override
    public void syncObjects() {
        if (vm == null) {
            return;
        }
        graphContext.sync();
    }

    @Override
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

    @Override
    public String getId() {
        return meta().getId();
    }

    @Override
    public ScheduleMetaData meta() {
        return graphContext.meta();
    }

    @Override
    public AbstractTaskGraph schedule() {
        scheduleInner();
        return this;
    }

    private void runSequentialCodeInThread(TaskPackage taskPackage) {
        int type = taskPackage.getTaskType();
        switch (type) {
            case 2:
                Task2 task = (Task2) taskPackage.getTaskParameters()[0];
                task.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2]);
                break;
            default:
                System.out.println("Sequential Runner not supported yet");
                break;
        }
    }

    private ArrayList<Integer> synchronizeWithPolicy(DynamicPolicy policy, Thread[] threads) {
        // Set the Performance policy by default;
        if (policy == null) {
            policy = DynamicPolicy.PERFORMANCE;
        }

        ArrayList<Integer> positions = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();

        switch (policy) {
            case PERFORMANCE:
                int numThreads = threads.length;
                int threadsFinished = 0;
                boolean[] isFinished = new boolean[threads.length];
                boolean allFinished = false;
                while (!allFinished) {
                    for (int i = 0; i < threads.length; i++) {
                        boolean isAlive = threads[i].isAlive();
                        if (!isAlive && isFinished[i] == false) {
                            threadsFinished++;
                            isFinished[i] = true;
                            positions.add(i);
                            names.add(threads[i].getName());
                        }
                    }
                    if (threadsFinished == numThreads) {
                        allFinished = true;
                    }
                }
                break;
            default:
                throw new RuntimeException("Policy " + policy + " not defined yet");
        }

        for (String s : names) {
            System.out.println(s);
        }

        return positions;

    }

    @Override
    public AbstractTaskGraph scheduleWithProfile(DynamicPolicy policy) {

        final long startSearchProfiler = System.currentTimeMillis();

        System.out.println("FORK-JOIN Model");

        TornadoDriver driver = getTornadoRuntime().getDriver(0);
        int numDevices = driver.getDeviceCount();

        System.out.println("Number of devices: " + numDevices);

        // One additional threads is reserved for sequential CPU execution
        final int numThreads = numDevices + 1;
        final int indexSequential = numDevices;

        Thread[] threads = new Thread[numThreads];

        long[] totalTimers = new long[numThreads];

        // Last Thread runs the sequential code
        threads[indexSequential] = new Thread(() -> {
            for (int k = 0; k < taskPackages.size(); k++) {
                runSequentialCodeInThread(taskPackages.get(k));
            }
            final long endSequentialCode = System.currentTimeMillis();
            Thread.currentThread().setName("SEQUENTIAL");
            totalTimers[indexSequential] = (endSequentialCode - startSearchProfiler);
        });

        for (int i = 0; i < numDevices; i++) {
            final int taskScheduleNumber = i;
            threads[i] = new Thread(() -> {
                // Each thread compile a TaskSchedule and Run
                String taskScheduleName = "$$TS-Exploration$$" + taskScheduleNumber;
                TaskSchedule task = new TaskSchedule(taskScheduleName);

                // XXX: Fill stream IN
                // task.streamIn(objects);

                final long start = System.currentTimeMillis();

                for (int k = 0; k < taskPackages.size(); k++) {
                    String taskID = taskPackages.get(k).getId();
                    TornadoRuntime.setProperty(taskScheduleName + "." + taskID + ".device", "0:" + taskScheduleNumber);
                    System.out.println("SET DEVICE: " + taskScheduleName + "." + taskID + ".device=0:" + taskScheduleNumber);
                    task.addTask(taskPackages.get(k));
                }

                int numObjectsCopyOut = streamOutObjects.size();
                switch (numObjectsCopyOut) {
                    case 1:
                        task.streamOut(streamOutObjects.get(0));
                        break;
                    case 2:
                        task.streamOut(streamOutObjects.get(0), streamOutObjects.get(1));
                        break;
                    case 3:
                        task.streamOut(streamOutObjects.get(0), streamOutObjects.get(1), streamOutObjects.get(2));
                        break;
                    default:
                        System.out.println("Not supported");
                        break;
                }

                task.execute();

                final long end = System.currentTimeMillis();
                totalTimers[taskScheduleNumber] = end - start;
            });
        }

        // FORK
        for (Thread t : threads) {
            t.start();
        }

        ArrayList<Integer> positions = synchronizeWithPolicy(policy, threads);

        int k = 0;
        for (Integer i : positions) {
            System.out.println("#" + k++ + " -> " + totalTimers[i]);
        }

        // JOIN
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // scheduleInner();
        return this;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void addTask(TaskPackage taskPackage) {

        taskPackages.add(taskPackage);
        String id = taskPackage.getId();
        int type = taskPackage.getTaskType();
        Object[] parameters = taskPackage.getTaskParameters();

        switch (type) {
            case 1:
                addInner(TaskUtils.createTask(meta(), id, (Task1) parameters[0], parameters[1]));
                break;
            case 2:
                addInner(TaskUtils.createTask(meta(), id, (Task2) parameters[0], parameters[1], parameters[2]));
                break;
            case 3:
                addInner(TaskUtils.createTask(meta(), id, (Task3) parameters[0], parameters[1], parameters[2], parameters[3]));
                break;

            case 4:
                addInner(TaskUtils.createTask(meta(), id, (Task4) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4]));
                break;

            case 5:
                addInner(TaskUtils.createTask(meta(), id, (Task5) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5]));
                break;

            case 6:
                addInner(TaskUtils.createTask(meta(), id, (Task6) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6]));
                break;

            case 7:
                addInner(TaskUtils.createTask(meta(), id, (Task7) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7]));
                break;

            case 8:
                addInner(TaskUtils.createTask(meta(), id, (Task8) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                        parameters[8]));
                break;

            case 9:
                addInner(TaskUtils.createTask(meta(), id, (Task9) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7], parameters[8],
                        parameters[9]));
                break;

            case 10:
                addInner(TaskUtils.createTask(meta(), id, (Task10) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                        parameters[8], parameters[9], parameters[10]));
                break;

            case 15:
                addInner(TaskUtils.createTask(meta(), id, (Task15) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                        parameters[8], parameters[9], parameters[10], parameters[11], parameters[12], parameters[13], parameters[14], parameters[15]));
                break;

            default:
                throw new RuntimeException("Task not supported yet");
        }
    }

    @Override
    public void addPrebuiltTask(String id, String entryPoint, String filename, Object[] args, Access[] accesses, TornadoDevice device, int[] dimensions) {
        addInner(TaskUtils.createTask(meta(), id, entryPoint, filename, args, accesses, device, dimensions));
    }

    @Override
    public void addScalaTask(String id, Object function, Object[] args) {
        addInner(TaskUtils.scalaTask(id, function, args));
    }

}
