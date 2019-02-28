/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.AbstractTaskGraph;
import uk.ac.manchester.tornado.api.Policy;
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
import uk.ac.manchester.tornado.runtime.common.Tornado;
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

/**
 * Implementation of the Tornado API for running on heterogeneous devices.
 * 
 */
public class TornadoTaskSchedule implements AbstractTaskGraph {

    private ExecutionContext graphContext;

    private byte[] hlcode = new byte[2048];
    private ByteBuffer hlBuffer;

    private GraphCompilationResult result;

    // One TornadoVM instance per TaskSchedule
    private TornadoVM vm;
    private Event event;
    private String taskScheduleName;

    private ArrayList<TaskPackage> taskPackages = new ArrayList<>();
    private ArrayList<Object> streamOutObjects = new ArrayList<>();
    private ArrayList<Object> streamInObjects = new ArrayList<>();
    private ConcurrentHashMap<Policy, Integer> policyTimeTable = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, ArrayList<Object>> multiHeapManagerOutputs = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, ArrayList<Object>> multiHeapManagerInputs = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, TaskSchedule> taskScheduleIndex = new ConcurrentHashMap<>();

    private static ConcurrentHashMap<Integer, TaskSchedule> globalTaskScheduleIndex = new ConcurrentHashMap<>();
    private static int baseGlobalIndex = 0;
    private static AtomicInteger offsetGlobalIndex = new AtomicInteger(0);

    /**
     * Options for Dynamic Reconfiguration
     */
    public static final boolean DEBUG_POLICY = Boolean.parseBoolean(System.getProperty("tornado.dynamic.verbose", "False"));
    public static final boolean EXEPERIMENTAL_MULTI_HOST_HEAP = false;
    private static final int DEFAULT_DRIVER_INDEX = 0;
    private static final int PERFORMANCE_WARMUP = 3;
    private final static boolean TIME_IN_NANOSECONDS = Tornado.TIME_IN_NANOSECONDS;
    public static final String TASK_SCHEDULE_PREFIX = "XXX";

    private static final ConcurrentHashMap<Policy, ConcurrentHashMap<String, HistoryTable>> executionHistoryPolicy = new ConcurrentHashMap<>();

    public static final int HISTORY_POINTS_PREDICTION = 5;

    public static final boolean USE_GLOBAL_TASK_CACHE = false;

    /**
     * Task Schedule implementation that uses GPU/FPGA and multi-core backends.
     * 
     * @param taskScheduleName
     */
    public TornadoTaskSchedule(String taskScheduleName) {
        graphContext = new ExecutionContext(taskScheduleName);
        hlBuffer = ByteBuffer.wrap(hlcode);
        hlBuffer.order(ByteOrder.LITTLE_ENDIAN);
        hlBuffer.rewind();
        result = null;
        event = null;
        this.taskScheduleName = taskScheduleName;
    }

    @Override
    public String getTaskScheduleName() {
        return taskScheduleName;
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

    // Timer implementation within the Task Schedule
    private static abstract class Timer {
        abstract long time();
    }

    private static class MillesecTimer extends Timer {
        @Override
        long time() {
            return System.currentTimeMillis();
        }
    }

    private static class NanoSecTimer extends Timer {
        @Override
        long time() {
            return System.nanoTime();
        }
    }

    private void updateDeviceContext(TornadoGraph graph) {
        BitSet deviceContexts = graph.filter(ContextNode.class);
        final ContextNode contextNode = (ContextNode) graph.getNode(deviceContexts.nextSetBit(0));
        contextNode.setDeviceIndex(meta().getDeviceIndex());
        graphContext.addDevice(meta().getDevice());
    }

    /**
     * Compile a task-schedule into TornadoVM bytecode
     * 
     * @param setNewDevice
     */
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

    private boolean compileToTornadoVMBytecodes() {
        CompileInfo compileInfo = extractCompileInfo();
        if (compileInfo.compile) {
            graphContext.assignToDevices();
            compile(compileInfo.updateDevice);
        }
        graphContext.addLastDevice(meta().getDevice());
        graphContext.newStack(compileInfo.updateDevice);
        return compileInfo.compile;
    }

    private void compileTaskToOpenCL() {
        vm.compile();
    }

    private void precompilationForFPGA() {
        // If current FPGA execution and JIT mode => run warmup
        if (Tornado.FPGA_EMULATION && Tornado.ACCELERATOR_IS_FPGA) {
            System.out.println("Compilation for Emulation");
            compileTaskToOpenCL();
        } else if (graphContext.getDeviceFirtTask() instanceof TornadoAcceleratorDevice && Tornado.ACCELERATOR_IS_FPGA) {
            TornadoAcceleratorDevice device = (TornadoAcceleratorDevice) graphContext.getDeviceFirtTask();
            if (device.isFullJITMode(graphContext.getTask(0))) {
                System.out.println("Compilation for full JIT");
                compileTaskToOpenCL();
            }
        }
    }

    @Override
    public void scheduleInner() {
        long t0 = System.nanoTime();
        boolean compile = compileToTornadoVMBytecodes();
        long t1 = System.nanoTime();
        if (PRINT_COMPILE_TIMES) {
            System.out.printf("compile: compileTasks: " + (t1 - t0) + "ns" + "\n");
        }

        if (compile) {
            precompilationForFPGA();
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
            streamInObjects.add(object);
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
        compileToTornadoVMBytecodes();
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

    public ExecutionContext getGraphContext() {
        return this.graphContext;
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

    @SuppressWarnings("unchecked")
    private void runSequentialCodeInThread(TaskPackage taskPackage) {
        int type = taskPackage.getTaskType();
        switch (type) {
            case 2:
                @SuppressWarnings("rawtypes") Task2 task2 = (Task2) taskPackage.getTaskParameters()[0];
                task2.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2]);
                break;
            case 3:
                @SuppressWarnings("rawtypes") Task3 task3 = (Task3) taskPackage.getTaskParameters()[0];
                task3.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3]);
                break;
            case 4:
                @SuppressWarnings("rawtypes") Task4 task4 = (Task4) taskPackage.getTaskParameters()[0];
                task4.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4]);
                break;
            case 5:
                @SuppressWarnings("rawtypes") Task5 task5 = (Task5) taskPackage.getTaskParameters()[0];
                task5.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4],
                        taskPackage.getTaskParameters()[5]);
                break;
            case 6:
                @SuppressWarnings("rawtypes") Task6 task6 = (Task6) taskPackage.getTaskParameters()[0];
                task6.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4],
                        taskPackage.getTaskParameters()[5], taskPackage.getTaskParameters()[6]);
                break;
            case 7:
                @SuppressWarnings("rawtypes") Task7 task7 = (Task7) taskPackage.getTaskParameters()[0];
                task7.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4],
                        taskPackage.getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7]);
                break;
            case 8:
                @SuppressWarnings("rawtypes") Task8 task8 = (Task8) taskPackage.getTaskParameters()[0];
                task8.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4],
                        taskPackage.getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7], taskPackage.getTaskParameters()[8]);
                break;
            case 9:
                @SuppressWarnings("rawtypes") Task9 task9 = (Task9) taskPackage.getTaskParameters()[0];
                task9.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4],
                        taskPackage.getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7], taskPackage.getTaskParameters()[8],
                        taskPackage.getTaskParameters()[9]);
                break;
            case 10:
                @SuppressWarnings("rawtypes") Task10 task10 = (Task10) taskPackage.getTaskParameters()[0];
                task10.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4],
                        taskPackage.getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7], taskPackage.getTaskParameters()[8],
                        taskPackage.getTaskParameters()[9], taskPackage.getTaskParameters()[10]);
                break;
            default:
                System.out.println("Sequential Runner not supported yet. Number of parameters: " + type);
                break;
        }
    }

    private int synchronizeWithPolicy(Policy policy, long[] totalTimers) {
        // Set the Performance policy by default;
        if (policy == null) {
            policy = Policy.PERFORMANCE;
        }

        int deviceWinnerIndex = -1;

        switch (policy) {
            case END_2_END:
            case PERFORMANCE:
                int position = 0;
                long min = Long.MAX_VALUE;
                for (int i = 0; i < totalTimers.length; i++) {
                    if (min > totalTimers[i]) {
                        min = totalTimers[i];
                        position = i;
                    }
                }
                deviceWinnerIndex = position;
                break;
            default:
                throw new RuntimeException("Policy " + policy + " not defined yet");
        }

        return deviceWinnerIndex;
    }

    private int syncWinner(Thread[] threads) {
        int winner = 0;
        boolean isAlive = true;
        while (isAlive) {

            for (int i = 0; i < threads.length; i++) {
                isAlive = threads[i].isAlive();
                if (!isAlive) {
                    if (DEBUG_POLICY) {
                        System.out.println("Thread " + threads[i].getName() + " finished");
                    }
                    winner = i;
                    // kill the others
                    for (int j = 0; j < threads.length; j++) {
                        if (i != j) {
                            threads[j].interrupt();
                        }
                    }
                    break;
                }
            }
        }
        return winner;
    }

    private void performStreamInThread(TaskSchedule task, ArrayList<Object> inputObjects) {
        int numObjectsCopyIn = inputObjects.size();
        switch (numObjectsCopyIn) {
            case 0:
                break;
            case 1:
                task.streamIn(inputObjects.get(0));
                break;
            case 2:
                task.streamIn(inputObjects.get(0), inputObjects.get(1));
                break;
            case 3:
                task.streamIn(inputObjects.get(0), inputObjects.get(1), inputObjects.get(2));
                break;
            case 4:
                task.streamIn(inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3));
                break;
            case 5:
                task.streamIn(inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4));
                break;
            case 6:
                task.streamIn(inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5));
                break;
            default:
                System.out.println("COPY-IN Not supported yet: " + numObjectsCopyIn);
                break;
        }
    }

    private void performStreamOutThreads(TaskSchedule task, ArrayList<Object> outputArrays) {
        int numObjectsCopyOut = outputArrays.size();
        switch (numObjectsCopyOut) {
            case 0:
                break;
            case 1:
                task.streamOut(outputArrays.get(0));
                break;
            case 2:
                task.streamOut(outputArrays.get(0), outputArrays.get(1));
                break;
            case 3:
                task.streamOut(outputArrays.get(0), outputArrays.get(1), outputArrays.get(2));
                break;
            case 4:
                task.streamOut(outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3));
                break;
            case 5:
                task.streamOut(outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4));
                break;
            case 6:
                task.streamOut(outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5));
                break;
            default:
                System.out.println("COPY-OUT Not supported yet: " + numObjectsCopyOut);
                break;
        }
    }

    private void runAllTasksSequentially() {
        for (int k = 0; k < taskPackages.size(); k++) {
            runSequentialCodeInThread(taskPackages.get(k));
        }
    }

    private void runParallelSequential(Policy policy, Thread[] threads, int indexSequential, Timer timer, long[] totalTimers) {
        // Last Thread runs the sequential code
        threads[indexSequential] = new Thread(() -> {
            long start = System.currentTimeMillis();
            if (policy == Policy.PERFORMANCE) {
                for (int k = 0; k < PERFORMANCE_WARMUP; k++) {
                    runAllTasksSequentially();
                }
                start = timer.time();
            }
            final long endSequentialCode = timer.time();
            Thread.currentThread().setName("Thread-sequential");
            if (DEBUG_POLICY) {
                System.out.println("Seq finished: " + Thread.currentThread().getName());
            }

            totalTimers[indexSequential] = (endSequentialCode - start);
        });
    }

    private void runParallelTaskSchedules(int numDevices, Thread[] threads, Timer timer, Policy policy, long[] totalTimers) {
        for (int i = 0; i < numDevices; i++) {
            final int taskScheduleNumber = i;
            threads[i] = new Thread(() -> {
                String taskScheduleName = TASK_SCHEDULE_PREFIX + taskScheduleNumber;
                TaskSchedule task = new TaskSchedule(taskScheduleName);

                Thread.currentThread().setName("Thread-DEV: " + TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(taskScheduleNumber).getDevice().getName());

                long start = timer.time();
                performStreamInThread(task, streamInObjects);
                for (int k = 0; k < taskPackages.size(); k++) {
                    String taskID = taskPackages.get(k).getId();
                    TornadoRuntime.setProperty(taskScheduleName + "." + taskID + ".device", "0:" + taskScheduleNumber);
                    if (Tornado.DEBUG) {
                        System.out.println("SET DEVICE: " + taskScheduleName + "." + taskID + ".device=0:" + taskScheduleNumber);
                    }
                    task.addTask(taskPackages.get(k));
                }
                performStreamOutThreads(task, streamOutObjects);

                if (policy == Policy.PERFORMANCE) {
                    // first warm up
                    for (int k = 0; k < PERFORMANCE_WARMUP; k++) {
                        task.execute();
                    }
                    start = timer.time();
                }
                task.execute();
                final long end = timer.time();
                taskScheduleIndex.put(taskScheduleNumber, task);

                if (USE_GLOBAL_TASK_CACHE) {
                    globalTaskScheduleIndex.put(offsetGlobalIndex.get(), task);
                    offsetGlobalIndex.incrementAndGet();
                } else {
                    globalTaskScheduleIndex.put(taskScheduleNumber, task);
                }

                totalTimers[taskScheduleNumber] = end - start;
            });
        }

    }

    private void runScheduleWithParallelProfiler(Policy policy) {

        final Timer timer = (TIME_IN_NANOSECONDS) ? new NanoSecTimer() : new MillesecTimer();
        TornadoDriver tornadoDriver = getTornadoRuntime().getDriver(DEFAULT_DRIVER_INDEX);
        int numDevices = tornadoDriver.getDeviceCount();
        long masterThreadID = Thread.currentThread().getId();

        // One additional threads is reserved for sequential CPU execution
        final int numThreads = numDevices + 1;
        final int indexSequential = numDevices;
        Thread[] threads = new Thread[numThreads];
        long[] totalTimers = new long[numThreads];

        // Last Thread runs the sequential code
        runParallelSequential(policy, threads, indexSequential, timer, totalTimers);

        // Run all task schedules in parallel
        runParallelTaskSchedules(numDevices, threads, timer, policy, totalTimers);

        // FORK
        for (int i = 0; i < numThreads; i++) {
            threads[i].start();
        }

        // Define the winner, based on the first thread to finish
        if (policy == Policy.LATENCY) {
            int deviceWinnerIndex = syncWinner(threads);
            policyTimeTable.put(policy, deviceWinnerIndex);
        }

        // JOIN
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        if ((policy == Policy.PERFORMANCE || policy == Policy.END_2_END) && (masterThreadID == Thread.currentThread().getId())) {
            int deviceWinnerIndex = synchronizeWithPolicy(policy, totalTimers);
            policyTimeTable.put(policy, deviceWinnerIndex);
            if (DEBUG_POLICY) {
                System.out.println("BEST Position: #" + deviceWinnerIndex + " " + Arrays.toString(totalTimers));
            }
        }
    }

    private void runSequential() {
        for (int k = 0; k < taskPackages.size(); k++) {
            runSequentialCodeInThread(taskPackages.get(k));
        }
    }

    private TaskSchedule taskRecompilation(int deviceWinnerIndex) {
        // Force re-compilation in device <deviceWinnerIndex>
        String taskScheduleName = TASK_SCHEDULE_PREFIX + deviceWinnerIndex;
        TaskSchedule taskToCompile = new TaskSchedule(taskScheduleName);
        performStreamInThread(taskToCompile, streamInObjects);
        for (int k = 0; k < taskPackages.size(); k++) {
            String taskID = taskPackages.get(k).getId();
            TornadoRuntime.setProperty(taskScheduleName + "." + taskID + ".device", "0:" + deviceWinnerIndex);
            taskToCompile.addTask(taskPackages.get(k));
        }
        performStreamOutThreads(taskToCompile, streamOutObjects);
        return taskToCompile;
    }

    private void runTaskScheduleParallelSelected(int deviceWinnerIndex) {
        for (int k = 0; k < taskPackages.size(); k++) {
            TaskPackage taskPackage = taskPackages.get(k);
            TornadoRuntime.setProperty(this.getTaskScheduleName() + "." + taskPackage.getId() + ".device", "0:" + deviceWinnerIndex);
        }
        if (DEBUG_POLICY) {
            System.out.println("Running in parallel device: " + deviceWinnerIndex);
        }
        TaskSchedule task = taskScheduleIndex.get(deviceWinnerIndex);
        if (task == null) {
            if (USE_GLOBAL_TASK_CACHE) {
                // This is only if compilation is not using Partial Evaluation
                task = globalTaskScheduleIndex.get(deviceWinnerIndex);
            } else {
                task = taskRecompilation(deviceWinnerIndex);
                // Save the TaskSchedule in cache
                taskScheduleIndex.put(deviceWinnerIndex, task);
            }
        }
        task.execute();
    }

    @Override
    public AbstractTaskGraph scheduleWithProfile(Policy policy) {
        if (policyTimeTable.get(policy) == null) {
            runScheduleWithParallelProfiler(policy);
        } else {
            // Run with the winner device
            int deviceWinnerIndex = policyTimeTable.get(policy);
            if (deviceWinnerIndex >= TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount()) {
                runSequential();
            } else {
                runTaskScheduleParallelSelected(deviceWinnerIndex);
            }
        }
        return this;
    }

    private Object cloneObject(Object o) {
        if (o instanceof float[]) {
            float[] clone = ((float[]) o).clone();
            return clone;
        } else if (o instanceof int[]) {
            int[] clone = ((int[]) o).clone();
            return clone;
        } else {
            throw new RuntimeException("Data type cloning not supported");
        }
    }

    @SuppressWarnings("unused")
    private void cloneInputOutputObjects() {
        final long startSearchProfiler = (TIME_IN_NANOSECONDS) ? System.nanoTime() : System.currentTimeMillis();
        TornadoDriver tornadoDriver = getTornadoRuntime().getDriver(DEFAULT_DRIVER_INDEX);
        int numDevices = tornadoDriver.getDeviceCount();
        // Clone objects (only outputs) for each device
        for (int deviceNumber = 0; deviceNumber < numDevices; deviceNumber++) {
            ArrayList<Object> newInObjects = new ArrayList<>();
            ArrayList<Object> newOutObjects = new ArrayList<>();

            for (int i = 0; i < streamInObjects.size(); i++) {
                Object in = streamInObjects.get(i);
                boolean outputObjectFound = false;
                for (int j = 0; j < streamOutObjects.size(); j++) {
                    Object out = streamOutObjects.get(j);
                    if (in == out) {
                        outputObjectFound = true;
                        break;
                    }
                }
                if (outputObjectFound) {
                    Object clonedObject = cloneObject(in);
                    newInObjects.add(clonedObject);
                    newOutObjects.add(clonedObject);
                } else {
                    newInObjects.add(in);
                }
            }
            multiHeapManagerInputs.put(deviceNumber, newInObjects);
            multiHeapManagerOutputs.put(deviceNumber, newOutObjects);
        }
    }

    private void runSequentialTaskSchedule(Policy policy, Timer timer, long[] totalTimers, int indexSequential) {
        long startSequential = timer.time();
        if (policy == Policy.PERFORMANCE) {
            for (int k = 0; k < PERFORMANCE_WARMUP; k++) {
                runAllTasksSequentially();
            }
            startSequential = timer.time();
        }
        runAllTasksSequentially();
        final long endSequentialCode = timer.time();
        totalTimers[indexSequential] = (endSequentialCode - startSequential);
    }

    private void runAllTaskSchedulesInAcceleratorsSequentually(int numDevices, Timer timer, Policy policy, long[] totalTimers) {
        String[] ignoreTaskNames = System.getProperties().getProperty("tornado.ignore.tasks", "").split(",");

        // Running sequentially for all the devices
        for (int taskNumber = 0; taskNumber < numDevices; taskNumber++) {
            String taskScheduleName = TASK_SCHEDULE_PREFIX + taskNumber;
            TaskSchedule task = new TaskSchedule(taskScheduleName);

            long start = timer.time();
            performStreamInThread(task, streamInObjects);

            boolean ignoreTask = false;
            for (int k = 0; k < taskPackages.size(); k++) {
                String taskID = taskPackages.get(k).getId();

                String name = taskScheduleName + "." + taskID;
                for (String s : ignoreTaskNames) {
                    if (s.equals(name)) {
                        totalTimers[taskNumber] = Integer.MAX_VALUE;
                        ignoreTask = true;
                        break;
                    }
                }

                TornadoRuntime.setProperty(taskScheduleName + "." + taskID + ".device", "0:" + taskNumber);
                if (Tornado.DEBUG) {
                    System.out.println("SET DEVICE: " + taskScheduleName + "." + taskID + ".device=0:" + taskNumber);
                }
                task.addTask(taskPackages.get(k));
            }

            if (ignoreTask) {
                continue;
            }
            performStreamOutThreads(task, streamOutObjects);

            if (policy == Policy.PERFORMANCE) {
                for (int k = 0; k < PERFORMANCE_WARMUP; k++) {
                    task.execute();
                }
                start = timer.time();
            }

            task.execute();
            taskScheduleIndex.put(taskNumber, task);

            // TaskSchedules Global
            if (USE_GLOBAL_TASK_CACHE) {
                globalTaskScheduleIndex.put(offsetGlobalIndex.get(), task);
                offsetGlobalIndex.incrementAndGet();
            } else {
                globalTaskScheduleIndex.put(taskNumber, task);
            }

            final long end = timer.time();
            totalTimers[taskNumber] = end - start;
        }
    }

    private void updateHistoryTables(Policy policy, int deviceWinnerIndex) {
        // Matching the name
        for (TaskPackage taskPackage : taskPackages) {
            Object code = taskPackage.getTaskParameters()[0];
            Method m = TaskUtils.resolveMethodHandle(code);
            ConcurrentHashMap<String, HistoryTable> tableSizes = null;

            int dev = baseGlobalIndex + deviceWinnerIndex;

            if (!executionHistoryPolicy.containsKey(policy)) {
                tableSizes = new ConcurrentHashMap<>();
                HistoryTable table = new HistoryTable();
                int size = getMaxInputSize();
                table.getTree().put(size, dev);

                tableSizes.put(m.toGenericString(), table);

            } else {
                tableSizes = executionHistoryPolicy.get(policy);
                if (!tableSizes.containsKey(m.toGenericString())) {
                    HistoryTable table = new HistoryTable();
                    int size = getMaxInputSize();
                    table.getTree().put(size, dev);
                    tableSizes.put(m.toGenericString(), table);
                } else {
                    // update the size
                    HistoryTable table = tableSizes.get(m.toGenericString());
                    int size = getMaxInputSize();
                    table.getTree().put(size, dev);
                    tableSizes.put(m.toGenericString(), table);
                }
            }
            executionHistoryPolicy.put(policy, tableSizes);
            baseGlobalIndex = offsetGlobalIndex.get();
        }
    }

    private void runWithSequentialProfiler(Policy policy) {
        final Timer timer = (TIME_IN_NANOSECONDS) ? new NanoSecTimer() : new MillesecTimer();
        int numDevices = getTornadoRuntime().getDriver(DEFAULT_DRIVER_INDEX).getDeviceCount();
        final int totalTornadoDevices = numDevices + 1;
        final int indexSequential = numDevices;
        long[] totalTimers = new long[totalTornadoDevices];

        // Run Sequential
        runSequentialTaskSchedule(policy, timer, totalTimers, indexSequential);

        // Run Task Schedules on the accelerator
        runAllTaskSchedulesInAcceleratorsSequentually(numDevices, timer, policy, totalTimers);

        if (policy == Policy.PERFORMANCE || policy == Policy.END_2_END) {
            int deviceWinnerIndex = synchronizeWithPolicy(policy, totalTimers);
            policyTimeTable.put(policy, deviceWinnerIndex);

            updateHistoryTables(policy, deviceWinnerIndex);

            if (DEBUG_POLICY) {
                System.out.println("BEST Position: #" + deviceWinnerIndex + " " + Arrays.toString(totalTimers));
            }
        }
    }

    /**
     * Experimental method to sync all objects when making a clone copy for all
     * output objects per device.
     * 
     * @param policy
     * @param numDevices
     */
    private void restoreVarsIntoJavaHeap(Policy policy, int numDevices) {
        if (policyTimeTable.get(policy) < numDevices) {
            // link output
            int deviceWinnerIndex = policyTimeTable.get(policy);
            ArrayList<Object> deviceOutputObjects = multiHeapManagerOutputs.get(deviceWinnerIndex);
            for (int i = 0; i < streamOutObjects.size(); i++) {
                @SuppressWarnings("unused") Object output = streamOutObjects.get(i);
                output = deviceOutputObjects.get(i);
            }

            for (int i = 0; i < numDevices; i++) {
                deviceOutputObjects = multiHeapManagerOutputs.get(i);
            }
        }
    }

    /**
     * It obtains the maximum input size for an input task.
     * 
     * @return int
     */
    private int getMaxInputSize() {
        Object[] parameters = taskPackages.get(0).getTaskParameters();
        int size = 0;
        for (int i = 1; i < parameters.length; i++) {
            Object o = parameters[i];
            if (o instanceof float[]) {
                float[] a = (float[]) o;
                size = Math.max(a.length, size);
            } else if (o instanceof int[]) {
                int[] a = (int[]) o;
                size = Math.max(a.length, size);
            } else if (o instanceof double[]) {
                double[] a = (double[]) o;
                size = Math.max(a.length, size);
            } else if (o instanceof long[]) {
                long[] a = (long[]) o;
                size = Math.max(a.length, size);
            } else if (o instanceof short[]) {
                short[] a = (short[]) o;
                size = Math.max(a.length, size);
            } else if (o instanceof byte[]) {
                byte[] a = (byte[]) o;
                size = Math.max(a.length, size);
            } else if (o instanceof char[]) {
                char[] a = (char[]) o;
                size = Math.max(a.length, size);
            } else
                size = Math.max(1, size);
        }
        return size;
    }

    public void runInParallel(int deviceWinnerIndex, int numDevices) {
        // Run with the winner device
        if (deviceWinnerIndex >= numDevices) {
            // Last index corresponds to the sequential in HostVM
            runSequential();
        } else {
            // It runs the parallel in the corresponding device
            runTaskScheduleParallelSelected(deviceWinnerIndex);
        }
    }

    /**
     * Class that keeps the history of executions based on their data sizes. It
     * has a sorted map (TreeMap) that keeps the relationship between the input
     * size and the actual Tornado device in which the task was executed based
     * on the profiler for the dynamic reconfiguration.
     */
    private static class HistoryTable {
        /**
         * TreeMap between input size -> device index
         */
        private TreeMap<Integer, Integer> table = new TreeMap<>();

        public int getClosestKey(int goal) {
            Set<Integer> keySet = table.keySet();
            return keySet.stream().reduce((prev, current) -> Math.abs(current - goal) < Math.abs(prev - goal) ? current : prev).get();
        }

        public TreeMap<Integer, Integer> getTree() {
            return table;
        }

        public int getNumKeys() {
            return table.keySet().size();
        }

        public int getDeviceNumber(int key) {
            return table.get(key);
        }

        public boolean isKeyInTable(int key) {
            return table.containsKey(key);
        }
    }

    @Override
    public AbstractTaskGraph scheduleWithProfileSequentialGlobal(Policy policy) {
        int numDevices = TornadoRuntime.getTornadoRuntime().getDriver(DEFAULT_DRIVER_INDEX).getDeviceCount();

        if (!executionHistoryPolicy.containsKey(policy)) {
            runWithSequentialProfiler(policy);

            if (EXEPERIMENTAL_MULTI_HOST_HEAP) {
                restoreVarsIntoJavaHeap(policy, numDevices);
            }

        } else {
            Object codeTask0 = taskPackages.get(0).getTaskParameters()[0];
            String fullMethodName = TaskUtils.resolveMethodHandle(codeTask0).toGenericString();
            // if policy registered but method not explored yet
            ConcurrentHashMap<String, HistoryTable> methodHistory = executionHistoryPolicy.get(policy);
            if (!methodHistory.containsKey(fullMethodName)) {
                // current methods to be compiled are not registered with the
                // current policy.
                runWithSequentialProfiler(policy);
            } else {

                // If current methods are found with the current policy -> match
                // the device, a) exact size is found, b) closest size
                HistoryTable table = methodHistory.get(fullMethodName);

                // 1. Infer sizes
                // We get the first set of parameters for the first task as a
                // reference
                int inputSize = getMaxInputSize();

                // 2. Make decision
                if (table.isKeyInTable(inputSize)) {
                    int deviceWinnerIndex = table.getDeviceNumber(inputSize);
                    runInParallel(deviceWinnerIndex, numDevices);
                } else {
                    // Input size not found
                    if (table.getNumKeys() < HISTORY_POINTS_PREDICTION) {
                        // not enough to make a decision -> run with the whole
                        // profiler
                        runWithSequentialProfiler(policy);
                    } else {
                        // get the closet one to the input history data
                        int closestKey = table.getClosestKey(inputSize);
                        int deviceWinnerIndex = table.getTree().get(closestKey);
                        runInParallel(deviceWinnerIndex, numDevices);
                    }
                }
            }
        }
        return this;
    }

    @Override
    public AbstractTaskGraph scheduleWithProfileSequential(Policy policy) {
        int numDevices = TornadoRuntime.getTornadoRuntime().getDriver(DEFAULT_DRIVER_INDEX).getDeviceCount();

        if (policyTimeTable.get(policy) == null) {
            runWithSequentialProfiler(policy);

            if (EXEPERIMENTAL_MULTI_HOST_HEAP) {
                restoreVarsIntoJavaHeap(policy, numDevices);
            }

        } else {
            // Run with the winner device
            int deviceWinnerIndex = policyTimeTable.get(policy);
            if (deviceWinnerIndex >= numDevices) {
                // if the winner is the last index => it is the sequential
                // (HotSpot)
                runSequential();
            } else {
                // Otherwise, it runs the parallel in the corresponding device
                runTaskScheduleParallelSelected(deviceWinnerIndex);
            }
        }
        return this;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void addTask(TaskPackage taskPackage) {

        taskPackages.add(taskPackage);
        String id = taskPackage.getId();
        int type = taskPackage.getTaskType();
        Object[] parameters = taskPackage.getTaskParameters();

        Method method = TaskUtils.resolveMethodHandle(parameters[0]);
        ScheduleMetaData meta = meta();

        switch (type) {
            case 1:
                addInner(TaskUtils.createTask(method, meta, id, (Task1) parameters[0], parameters[1]));
                break;
            case 2:
                addInner(TaskUtils.createTask(method, meta, id, (Task2) parameters[0], parameters[1], parameters[2]));
                break;
            case 3:
                addInner(TaskUtils.createTask(method, meta, id, (Task3) parameters[0], parameters[1], parameters[2], parameters[3]));
                break;
            case 4:
                addInner(TaskUtils.createTask(method, meta, id, (Task4) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4]));
                break;
            case 5:
                addInner(TaskUtils.createTask(method, meta, id, (Task5) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5]));
                break;
            case 6:
                addInner(TaskUtils.createTask(method, meta, id, (Task6) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6]));
                break;
            case 7:
                addInner(TaskUtils.createTask(method, meta, id, (Task7) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7]));
                break;
            case 8:
                addInner(TaskUtils.createTask(method, meta, id, (Task8) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                        parameters[8]));
                break;
            case 9:
                addInner(TaskUtils.createTask(method, meta, id, (Task9) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                        parameters[8], parameters[9]));
                break;
            case 10:
                addInner(TaskUtils.createTask(method, meta, id, (Task10) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                        parameters[8], parameters[9], parameters[10]));
                break;
            case 15:
                addInner(TaskUtils.createTask(method, meta, id, (Task15) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                        parameters[8], parameters[9], parameters[10], parameters[11], parameters[12], parameters[13], parameters[14], parameters[15]));
                break;
            default:
                throw new RuntimeException("Task not supported yet. Type: " + type);
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
