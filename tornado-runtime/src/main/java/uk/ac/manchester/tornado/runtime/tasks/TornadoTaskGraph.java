/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.tasks;

import static uk.ac.manchester.tornado.api.profiler.ProfilerType.ALLOCATION_BYTES;
import static uk.ac.manchester.tornado.api.profiler.ProfilerType.TOTAL_COPY_IN_SIZE_BYTES;
import static uk.ac.manchester.tornado.api.profiler.ProfilerType.TOTAL_COPY_OUT_SIZE_BYTES;
import static uk.ac.manchester.tornado.api.profiler.ProfilerType.TOTAL_KERNEL_TIME;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEBUG;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoRuntime;
import uk.ac.manchester.tornado.api.TornadoTaskGraphInterface;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.PrebuiltTaskPackage;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task1;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task10;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task11;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task12;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task13;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task14;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task15;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task6;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task7;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task8;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task9;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDynamicReconfigurationException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoTaskRuntimeException;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.api.runtime.ExecutorFrame;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.TornadoVM;
import uk.ac.manchester.tornado.runtime.analyzer.MetaReduceCodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.ReduceCodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.TaskUtils;
import uk.ac.manchester.tornado.runtime.common.BatchConfiguration;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoVMClient;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.common.XPUDeviceBufferState;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;
import uk.ac.manchester.tornado.runtime.graph.TornadoExecutionContext;
import uk.ac.manchester.tornado.runtime.graph.TornadoGraph;
import uk.ac.manchester.tornado.runtime.graph.TornadoGraphBuilder;
import uk.ac.manchester.tornado.runtime.graph.TornadoVMBytecodeBuilder;
import uk.ac.manchester.tornado.runtime.profiler.EmptyProfiler;
import uk.ac.manchester.tornado.runtime.profiler.TimeProfiler;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.SketchRequest;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

/**
 * Implementation of the Tornado API for running on heterogeneous devices.
 */
public class TornadoTaskGraph implements TornadoTaskGraphInterface {

    /**
     * Options for Dynamic Reconfiguration.
     */
    private static final boolean EXPERIMENTAL_MULTI_HOST_HEAP = false;
    private static final int DEFAULT_DRIVER_INDEX = 0;
    private static final boolean TIME_IN_NANOSECONDS = TornadoOptions.TIME_IN_NANOSECONDS;
    public static final String GENERATED_TASK_GRAPH_PREFIX = "__GENERATED_TASK_GRAPH__";
    private static final ConcurrentHashMap<Policy, ConcurrentHashMap<String, HistoryTable>> executionHistoryPolicy = new ConcurrentHashMap<>();

    private static final boolean USE_GLOBAL_TASK_CACHE = false;

    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String WARNING_DEOPT_MESSAGE = RED + "WARNING: Code Bailout to Java sequential. Use --debug to see the reason" + RESET;

    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+)(MB|mg|gb|GB)");

    private static final int ITERATIONS_WARMUP_DYNAMIC_RECONFIGURATION_LOOP = 100;
    private static final int ITERATIONS_WARMUP_DYNAMIC_RECONFIGURATION_PERF = 3;

    private static ConcurrentHashMap<Integer, TornadoExecutionPlan> globalExecutionPlanIndex = new ConcurrentHashMap<>();
    private static int baseGlobalIndex = 0;
    private static AtomicInteger offsetGlobalIndex = new AtomicInteger(0);
    private MetaReduceCodeAnalysis analysisTaskGraph;
    private TornadoExecutionContext executionContext;
    private byte[] highLevelCode = new byte[8192];
    private ByteBuffer hlBuffer;
    private TornadoVMBytecodeBuilder bytecodeBuilder;
    private long batchSizeBytes = -1;
    private long memoryLimitSizeBytes = -1;

    /**
     * One TornadoVM instance per TornadoExecutionPlan
     */
    private TornadoVM vm;

    // HashMap to keep an instance of the TornadoVM per Device
    private Map<TornadoXPUDevice, TornadoVM> vmTable;
    private Event event;
    private String taskGraphName;
    private List<TaskPackage> taskPackages;
    private List<Object> streamOutObjects;
    private List<Object> streamInObjects;

    private Map<TornadoTaskGraph, List<Object>> taskToPersistentObjectMap;
    private TornadoTaskGraphInterface lastExecutedTaskGraph;

    private Set<Object> argumentsLookUp;

    private List<StreamingObject> inputModesObjects; // List of objects with its data transfer mode (IN)

    private List<StreamingObject> outputModeObjects; // List of objects with its data transfer mode (OUT)
    private ConcurrentHashMap<Policy, Integer> policyTimeTable = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, ArrayList<Object>> multiHeapManagerOutputs = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, TornadoExecutionPlan> executionPlansIndexes = new ConcurrentHashMap<>();
    private StringBuilder bufferLogProfiler = new StringBuilder();
    private Graph compilationGraph;
    /**
     * Options for new reductions - experimental.
     */
    private boolean reduceExpressionRewritten = false;
    private ReduceTaskGraph reduceTaskGraph;
    private boolean reduceAnalysis = false;
    private TornadoProfiler timeProfiler;
    private boolean updateData;
    private boolean isFinished;
    private GridScheduler gridScheduler;

    private ProfilerMode profilerMode;

    private boolean isConcurrentDevicesEnabled;
    private long executionPlanId;
    private boolean bailout;
    private Access[] accesses;

    /**
     * Task Schedule implementation that uses GPU/FPGA and multicore backends. This constructor must be public. It is invoked using the reflection API.
     *
     * @param taskScheduleName
     *     Task-Schedule name
     */
    public TornadoTaskGraph(String taskScheduleName) {
        executionContext = new TornadoExecutionContext(taskScheduleName);
        hlBuffer = ByteBuffer.wrap(highLevelCode);
        hlBuffer.order(ByteOrder.LITTLE_ENDIAN);
        hlBuffer.rewind();
        bytecodeBuilder = null;
        event = null;
        this.taskGraphName = taskScheduleName;
        vmTable = new HashMap<>();
        argumentsLookUp = new HashSet<>();
        taskPackages = new ArrayList<>();
        streamOutObjects = new ArrayList<>();
        streamInObjects = new ArrayList<>();
        inputModesObjects = new ArrayList<>();
        outputModeObjects = new ArrayList<>();
        taskToPersistentObjectMap = new HashMap<>();
    }

    static void performStreamInObject(TaskGraph task, Object inputObject, final int dataTransferMode) {
        task.transferToDevice(dataTransferMode, inputObject);
    }

    static void performStreamInObject(TaskGraph task, List<Object> inputObjects, final int dataTransferMode) {
        int numObjectsCopyIn = inputObjects.size();
        switch (numObjectsCopyIn) {
            case 0 -> {
            }
            case 1 -> task.transferToDevice(dataTransferMode, inputObjects.getFirst());
            case 2 -> task.transferToDevice(dataTransferMode, inputObjects.get(0), inputObjects.get(1));
            case 3 -> task.transferToDevice(dataTransferMode, inputObjects.get(0), inputObjects.get(1), inputObjects.get(2));
            case 4 -> task.transferToDevice(dataTransferMode, inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3));
            case 5 -> task.transferToDevice(dataTransferMode, inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4));
            case 6 -> task.transferToDevice(dataTransferMode, inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5));
            case 7 -> task.transferToDevice(dataTransferMode, inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5), inputObjects
                    .get(6));
            case 8 -> task.transferToDevice(dataTransferMode, inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5), inputObjects
                    .get(6), inputObjects.get(7));
            case 9 -> task.transferToDevice(dataTransferMode, inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5), inputObjects
                    .get(6), inputObjects.get(7), inputObjects.get(8));
            case 10 -> task.transferToDevice(dataTransferMode, inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5),
                    inputObjects.get(6), inputObjects.get(7), inputObjects.get(8), inputObjects.get(9));
            case 11 -> task.transferToDevice(dataTransferMode, inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5),
                    inputObjects.get(6), inputObjects.get(7), inputObjects.get(8), inputObjects.get(9), inputObjects.get(10));
            case 12 -> task.transferToDevice(dataTransferMode, inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5),
                    inputObjects.get(6), inputObjects.get(7), inputObjects.get(8), inputObjects.get(9), inputObjects.get(10), inputObjects.get(11));
            case 13 -> task.transferToDevice(dataTransferMode, inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5),
                    inputObjects.get(6), inputObjects.get(7), inputObjects.get(8), inputObjects.get(9), inputObjects.get(10), inputObjects.get(11), inputObjects.get(12));
            case 14 -> task.transferToDevice(dataTransferMode, inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5),
                    inputObjects.get(6), inputObjects.get(7), inputObjects.get(8), inputObjects.get(9), inputObjects.get(10), inputObjects.get(11), inputObjects.get(12), inputObjects.get(13));
            case 15 -> task.transferToDevice(dataTransferMode, inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5),
                    inputObjects.get(6), inputObjects.get(7), inputObjects.get(8), inputObjects.get(9), inputObjects.get(10), inputObjects.get(11), inputObjects.get(12), inputObjects.get(13),
                    inputObjects.get(14));
            default -> System.out.println("COPY-IN Not supported yet: " + numObjectsCopyIn);
        }
    }

    static void performStreamOutThreads(final int mode, TaskGraph task, Object outputObject) {
        task.transferToHost(mode, outputObject);
    }

    static void performStreamOutThreads(final int mode, TaskGraph task, List<Object> outputArrays) {
        int numObjectsCopyOut = outputArrays.size();
        switch (numObjectsCopyOut) {
            case 0 -> {
            }
            case 1 -> task.transferToHost(mode, outputArrays.getFirst());
            case 2 -> task.transferToHost(mode, outputArrays.get(0), outputArrays.get(1));
            case 3 -> task.transferToHost(mode, outputArrays.get(0), outputArrays.get(1), outputArrays.get(2));
            case 4 -> task.transferToHost(mode, outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3));
            case 5 -> task.transferToHost(mode, outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4));
            case 6 -> task.transferToHost(mode, outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5));
            case 7 -> task.transferToHost(mode, outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5), outputArrays.get(6));
            case 8 -> task.transferToHost(mode, outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5), outputArrays.get(6),
                    outputArrays.get(7));
            case 9 -> task.transferToHost(mode, outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5), outputArrays.get(6),
                    outputArrays.get(7), outputArrays.get(8));
            case 10 -> task.transferToHost(mode, outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5), outputArrays.get(6),
                    outputArrays.get(7), outputArrays.get(8), outputArrays.get(9));
            case 11 -> task.transferToHost(mode, outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5), outputArrays.get(6),
                    outputArrays.get(7), outputArrays.get(8), outputArrays.get(9), outputArrays.get(10));
            case 12 -> task.transferToHost(mode, outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5), outputArrays.get(6),
                    outputArrays.get(7), outputArrays.get(8), outputArrays.get(9), outputArrays.get(10), outputArrays.get(11));
            case 13 -> task.transferToHost(mode, outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5), outputArrays.get(6),
                    outputArrays.get(7), outputArrays.get(8), outputArrays.get(9), outputArrays.get(10), outputArrays.get(11), outputArrays.get(12));
            case 14 -> task.transferToHost(mode, outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5), outputArrays.get(6),
                    outputArrays.get(7), outputArrays.get(8), outputArrays.get(9), outputArrays.get(10), outputArrays.get(11), outputArrays.get(12), outputArrays.get(13));
            case 15 -> task.transferToHost(mode, outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5), outputArrays.get(6),
                    outputArrays.get(7), outputArrays.get(8), outputArrays.get(9), outputArrays.get(10), outputArrays.get(11), outputArrays.get(12), outputArrays.get(13), outputArrays.get(14));
            default -> System.out.println("COPY-OUT Not supported yet: " + numObjectsCopyOut);
        }
    }

    @Override
    public String getTaskGraphName() {
        return taskGraphName;
    }

    @Override
    public void useDefaultThreadScheduler(boolean use) {
        executionContext.setDefaultThreadScheduler(use);
    }

    @Override
    public boolean isFinished() {
        return this.isFinished;
    }

    @Override
    public Set<Object> getArgumentsLookup() {
        return argumentsLookUp;
    }

    public TornadoTaskGraph createImmutableTaskGraph() {

        TornadoTaskGraph newTaskGraph = new TornadoTaskGraph(this.taskGraphName);

        newTaskGraph.accesses = this.accesses;
        newTaskGraph.inputModesObjects = Collections.unmodifiableList(this.inputModesObjects);
        newTaskGraph.streamInObjects = Collections.unmodifiableList(this.streamInObjects);
        newTaskGraph.outputModeObjects = Collections.unmodifiableList(this.outputModeObjects);
        newTaskGraph.taskToPersistentObjectMap = Collections.unmodifiableMap(this.taskToPersistentObjectMap);

        newTaskGraph.lastExecutedTaskGraph = this.lastExecutedTaskGraph;

        newTaskGraph.streamOutObjects = Collections.unmodifiableList(this.streamOutObjects);
        newTaskGraph.hlBuffer = this.hlBuffer;

        newTaskGraph.executionContext = this.executionContext.clone();

        newTaskGraph.taskPackages = Collections.unmodifiableList(this.taskPackages);
        newTaskGraph.argumentsLookUp = Collections.unmodifiableSet(this.argumentsLookUp);

        newTaskGraph.reduceTaskGraph = this.reduceTaskGraph;
        newTaskGraph.analysisTaskGraph = this.analysisTaskGraph;
        newTaskGraph.highLevelCode = this.highLevelCode;

        newTaskGraph.timeProfiler = this.timeProfiler;
        newTaskGraph.gridScheduler = this.gridScheduler;

        // Pass the profiler to the execution context
        newTaskGraph.executionContext.withProfiler(timeProfiler);

        // The graph object is used when rewriting task-graphs (e.g., reductions)
        newTaskGraph.compilationGraph = this.compilationGraph;

        return newTaskGraph;
    }

    @Override
    public void enableProfiler(ProfilerMode profilerMode) {
        this.profilerMode = profilerMode;
        TornadoOptions.TORNADO_PROFILER = true;
        if (profilerMode == ProfilerMode.SILENT) {
            TornadoOptions.TORNADO_PROFILER_LOG = true;
        }
    }

    @Override
    public Collection<?> getOutputs() {
        return streamOutObjects;
    }

    @Override
    public void disableProfiler() {
        TornadoOptions.TORNADO_PROFILER = false;
        TornadoOptions.TORNADO_PROFILER_LOG = false;
        this.timeProfiler = null;
        this.profilerMode = null;
    }

    @Override
    public void withConcurrentDevices() {
        this.isConcurrentDevicesEnabled = true;
    }

    @Override
    public void withoutConcurrentDevices() {
        this.isConcurrentDevicesEnabled = false;
    }

    @Override
    public void withThreadInfo() {
        meta().enableThreadInfo();
    }

    @Override
    public void withoutThreadInfo() {
        meta().disableThreadInfo();
    }

    @Override
    public void withPrintKernel() {
        meta().enablePrintKernel();
    }

    @Override
    public void withoutPrintKernel() {
        meta().disablePrintKernel();
    }

    @Override
    public void withGridScheduler(GridScheduler gridScheduler) {
        this.gridScheduler = gridScheduler;
        checkGridSchedulerNames();
    }

    @Override
    public long getCurrentDeviceMemoryUsage() {
        return executionContext.getCurrentDeviceMemoryUsage();
    }

    @Override
    public Map<String, List<Object>> getPersistedTaskToObjectsMap() {
        return executionContext.getPersistedTaskToObjectsMap();
    }

    @Override
    public void withCompilerFlags(TornadoVMBackendType backendType, String compilerFlags) {
        executionContext.meta().setCompilerFlags(backendType, compilerFlags);
    }

    @Override
    public void mapOnDeviceMemoryRegion(Object destArray, Object srcArray, long offset, TornadoTaskGraphInterface taskGraphSrc) {

        TornadoTaskGraph graphSrc = (TornadoTaskGraph) taskGraphSrc;
        Access objectAccessSrc = graphSrc.getObjectAccess(srcArray);
        final LocalObjectState localStateSrc = graphSrc.executionContext.getLocalStateObject(srcArray, objectAccessSrc);
        final DataObjectState dataObjectStateSrc = localStateSrc.getDataObjectState();

        // The device is the same for both task-graphs
        final TornadoXPUDevice device = graphSrc.meta().getXPUDevice();

        final XPUDeviceBufferState deviceStateSrc = dataObjectStateSrc.getDeviceBufferState(device);

        Access objectAccessDest = getObjectAccess(destArray);
        final LocalObjectState localStateDest = executionContext.getLocalStateObject(destArray, objectAccessDest);
        final DataObjectState dataObjectStateDest = localStateDest.getDataObjectState();
        final XPUDeviceBufferState deviceStateDest = dataObjectStateDest.getDeviceBufferState(device);

        // We need to alloc if needed
        if (!deviceStateDest.hasObjectBuffer()) {
            device.allocate(destArray, 0, deviceStateDest, objectAccessDest);
        }

        final TornadoXPUDevice deviceDest = meta().getXPUDevice();
        // destDevice and device must be the same

        // Then we need to copy the pointers
        deviceDest.mapDeviceRegion(executionPlanId, destArray, srcArray, deviceStateSrc, deviceStateDest, offset);
    }

    @Override
    public void updateObjectAccess() {
        // iterate over stream-in and stream-out objects and, if the mode is UNDER_DEMAND, update their Access to READ-WRITE
        HashMap<Object, Access> objectAccesses = executionContext.getObjectsAccesses();

        for (StreamingObject inputStreamObject : inputModesObjects) {
            if (inputStreamObject.getMode() == DataTransferMode.UNDER_DEMAND) {
                Object streamInObject = inputStreamObject.getObject();
                Access currentAccess = objectAccesses.get(streamInObject);
                if (currentAccess != Access.READ_WRITE) {
                    objectAccesses.replace(streamInObject, currentAccess, Access.READ_WRITE);
                }
            }
        }

        for (StreamingObject outputStreamObject : outputModeObjects) {
            if (outputStreamObject.getMode() == DataTransferMode.UNDER_DEMAND) {
                Object streamOutObject = outputStreamObject.getObject();
                Access currentAccess = objectAccesses.get(streamOutObject);
                if (currentAccess != Access.READ_WRITE) {
                    objectAccesses.replace(streamOutObject, currentAccess, Access.READ_WRITE);
                }
            }
        }

        for (StreamingObject outputStreamObject : outputModeObjects) {
            if (outputStreamObject.getMode() == DataTransferMode.UNDER_DEMAND) {
                Object streamOutObject = outputStreamObject.getObject();
                Access currentAccess = objectAccesses.get(streamOutObject);
                if (currentAccess != Access.READ_WRITE) {
                    objectAccesses.replace(streamOutObject, currentAccess, Access.READ_WRITE);
                }
            }
        }
    }

    @Override
    public boolean isGridRegistered() {
        return checkGridSchedulerNames();
    }

    @Override
    public void setLastExecutedTaskGraph(TornadoTaskGraphInterface lastExecutedTaskGraph) {
        this.lastExecutedTaskGraph = lastExecutedTaskGraph;
    }

    @Override
    public long getTotalBytesTransferred() {
        return getProfilerValue(ProfilerType.TOTAL_COPY_IN_SIZE_BYTES) + getProfilerValue(TOTAL_COPY_OUT_SIZE_BYTES);
    }

    @Override
    public long getTotalDeviceMemoryUsage() {
        return getProfilerValue(ALLOCATION_BYTES);
    }

    @Override
    public SchedulableTask getTask(String id) {
        return executionContext.getTask(id);
    }

    /**
     * Returns the device attached to any of the tasks. Currently, TornadoVM executes all tasks that belong to the same task-schedule on the same device. Therefore, this call returns the device
     * attached to the first task or the first task is planning to be executed.
     *
     * @return {@link TornadoDevice}
     */
    @Override
    public TornadoDevice getDevice() {
        return executionContext.getDeviceOfFirstTask();
    }

    @Override
    public void setDevice(TornadoDevice device) {

        TornadoDevice oldDevice = meta().getXPUDevice();

        // prevent to set again the same device as it invalidates its state
        if (oldDevice.equals(device)) {
            reuseDeviceBuffersForSameDevice(device);
            return;
        }

        meta().setDevice(device);

        // Make sure that a sketch is available for the device.
        for (int i = 0; i < executionContext.getTaskCount(); i++) {
            SchedulableTask task = executionContext.getTask(i);
            task.meta().setDevice(device);
            if (task instanceof CompilableTask compilableTask) {
                ResolvedJavaMethod method = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(compilableTask.getMethod());
                if (!meta().getXPUDevice().getDeviceContext().isCached(executionPlanId, method.getName(), compilableTask)) {
                    updateInner(i, executionContext.getTask(i));
                }
            }
        }

        //Release locked buffers from the old device and lock them on the new one.
        for (LocalObjectState localState : executionContext.getObjectStates()) {
            final DataObjectState dataObjectState = localState.getDataObjectState();
            final XPUDeviceBufferState deviceState = dataObjectState.getDeviceBufferState(oldDevice);
            if (deviceState.isLockedBuffer()) {
                releaseObjectFromDeviceMemory(localState, oldDevice);
                reuseDeviceBufferObject(localState, device);
            }
        }

        // Set Thread-Schedulers to Default Value
        for (int i = 0; i < executionContext.getTaskCount(); i++) {
            SchedulableTask task = executionContext.getTask(i);
            task.meta().resetThreadBlocks();
        }

    }

    private void reuseDeviceBuffersForSameDevice(TornadoDevice device) {
        for (LocalObjectState localState : executionContext.getObjectStates()) {
            reuseDeviceBufferObject(localState, device);
        }
    }

    @Override
    public void updatePersistedObjectState() {
        if (this.lastExecutedTaskGraph == null) {
            //this indicates that this is the first task-graph executed
            return;
        }

        TornadoTaskGraph graphSrc = (TornadoTaskGraph) this.lastExecutedTaskGraph;
        List<Object> objectsToSync = executionContext.getPersistedTaskToObjectsMap().get(graphSrc.taskGraphName);

        if (objectsToSync == null) {
            objectsToSync = executionContext.getPersistedObjects();
            executionContext.addPersistedObject(this.taskGraphName, objectsToSync);
        }

        for (Object objectToSync : objectsToSync) {
            Access objectAccessSrc = graphSrc.getObjectAccess(objectToSync);
            LocalObjectState localStateSrc = graphSrc.executionContext.getLocalStateObject(objectToSync, objectAccessSrc);
            DataObjectState dataObjectStateSrc = localStateSrc.getDataObjectState();

            // The device is the same for both task-graphs
            TornadoXPUDevice device = graphSrc.meta().getXPUDevice();
            XPUDeviceBufferState deviceStateSrc = dataObjectStateSrc.getDeviceBufferState(device);

            Access objectAccessDest = Access.READ_WRITE;
            LocalObjectState localStateDest = executionContext.getLocalStateObject(objectToSync, objectAccessDest);
            if (localStateDest == null) {
                continue;
            }
            if (!graphSrc.meta().getXPUDevice().equals(executionContext.meta().getXPUDevice())) {
                throw new TornadoRuntimeException("[ERROR] Object " + objectsToSync + " is not on the same device pesisted and consumed: " + graphSrc.meta()
                        .getXPUDevice() + " " + " vs " + executionContext.meta().getXPUDevice());
            }

            DataObjectState dataObjectStateDest = localStateDest.getDataObjectState();
            XPUDeviceBufferState deviceStateDest = dataObjectStateDest.getDeviceBufferState(device);

            deviceStateDest.setXPUBuffer(deviceStateSrc.getXPUBuffer());
        }
    }

    @Override
    public void setDevice(String taskName, TornadoDevice device) {

        TornadoDevice oldDevice = meta().getXPUDevice();

        // Make sure that a sketch is available for the device.
        for (int i = 0; i < executionContext.getTaskCount(); i++) {
            SchedulableTask task = executionContext.getTask(i);
            String name = task.getId();
            if (name.equals(taskName)) {
                task.meta().setDevice(device);
                if (task instanceof CompilableTask) {
                    ResolvedJavaMethod method = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(((CompilableTask) task).getMethod());
                    if (!task.getDevice().getDeviceContext().isCached(executionPlanId, method.getName(), task)) {
                        updateInner(i, task);
                    }
                }
            }
        }

        // Release locked buffers from the old device and lock them on the new one.
        for (LocalObjectState localState : executionContext.getObjectStates()) {
            final DataObjectState dataObjectState = localState.getDataObjectState();
            final XPUDeviceBufferState deviceState = dataObjectState.getDeviceBufferState(oldDevice);
            if (deviceState.isLockedBuffer()) {
                releaseObjectFromDeviceMemory(localState, oldDevice);
                reuseDeviceBufferObject(localState, device);
            }
        }

        // Set Thread-Schedulers to Default Value
        for (int i = 0; i < executionContext.getTaskCount(); i++) {
            SchedulableTask task = executionContext.getTask(i);
            task.meta().resetThreadBlocks();
        }

    }

    @Override
    public TornadoXPUDevice getDeviceForTask(String id) {
        return executionContext.getDeviceForTask(id);
    }

    private void updateInner(int index, SchedulableTask task) {
        int driverIndex = task.meta().getBackendIndex();
        Providers providers = TornadoCoreRuntime.getTornadoRuntime().getBackend(driverIndex).getProviders();
        TornadoSuitesProvider suites = TornadoCoreRuntime.getTornadoRuntime().getBackend(driverIndex).getSuitesProvider();

        executionContext.setTask(index, task);

        if (task instanceof CompilableTask compilableTask) {
            final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(compilableTask.getMethod());
            final TaskDataContext taskMetaData = compilableTask.meta();
            new SketchRequest(resolvedMethod, providers, suites.getGraphBuilderSuite(), suites.getSketchTier(), taskMetaData.getBackendIndex(), taskMetaData.getDeviceIndex()).run();

            Sketch sketchGraph = TornadoSketcher.lookup(resolvedMethod, taskMetaData.getBackendIndex(), taskMetaData.getDeviceIndex());
            this.compilationGraph = sketchGraph.getGraph();
        }
    }

    @Override
    public void addInner(SchedulableTask task) {
        int driverIndex = task.meta().getBackendIndex();
        Providers providers = TornadoCoreRuntime.getTornadoRuntime().getBackend(driverIndex).getProviders();
        TornadoSuitesProvider suites = TornadoCoreRuntime.getTornadoRuntime().getBackend(driverIndex).getSuitesProvider();

        int index = executionContext.addTask(task);

        if (task instanceof CompilableTask compilableTask) {
            checkForMemorySegmentAsTaskParameter(compilableTask);

            final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(compilableTask.getMethod());
            final TaskDataContext taskMetaData = compilableTask.meta();
            new SketchRequest(resolvedMethod, providers, suites.getGraphBuilderSuite(), suites.getSketchTier(), taskMetaData.getBackendIndex(), taskMetaData.getDeviceIndex()).run();

            Sketch lookup = TornadoSketcher.lookup(resolvedMethod, compilableTask.meta().getBackendIndex(), compilableTask.meta().getDeviceIndex());
            this.compilationGraph = lookup.getGraph();
            this.accesses = lookup.getArgumentsAccess();
        } else {
            PrebuiltTask prebuiltTask = (PrebuiltTask) task;
            this.accesses = prebuiltTask.getArgumentsAccess();
        }

        // Prepare Initial Graph before the TornadoVM bytecode generation
        hlBuffer.put(TornadoGraphBitcodes.CONTEXT.index());
        int globalTaskId = executionContext.getTaskCountAndIncrement();
        hlBuffer.putInt(globalTaskId);
        hlBuffer.putInt(index);

        // create parameter list
        final Object[] args = task.getArguments();
        hlBuffer.put(TornadoGraphBitcodes.ARG_LIST.index());
        hlBuffer.putInt(args.length);
        int i = 0;

        for (final Object arg : args) {
            index = executionContext.insertVariable(arg, accesses[i]);
            if (arg.getClass().isPrimitive() || RuntimeUtilities.isBoxedPrimitiveClass(arg.getClass())) {
                hlBuffer.put(TornadoGraphBitcodes.LOAD_PRIM.index());
            } else {
                hlBuffer.put(TornadoGraphBitcodes.LOAD_REF.index());
            }
            hlBuffer.putInt(index);
            i++;
        }

        // launch code
        hlBuffer.put(TornadoGraphBitcodes.LAUNCH.index());
    }

    private void logTaskMethodHandle(SchedulableTask task) {
        if ((task.getTaskName() != null) && (task.getId() != null)) {
            String methodName = (task instanceof PrebuiltTask prebuiltTask)
                    ? prebuiltTask.getFilename()
                    : ((CompilableTask) task).getMethod().getDeclaringClass().getSimpleName() + "." + task.getTaskName();
            timeProfiler.registerMethodHandle(ProfilerType.METHOD, task.getId(), methodName);
        }

    }

    private void updateDeviceContext() {
        executionContext.setDevice(meta().getXPUDevice());
    }

    /**
     * Compile a {@link TaskGraph} into TornadoVM byte-code.
     *
     * @param setNewDevice:
     *     boolean that specifies if set a new device or not.
     */
    private TornadoVM compileGraphAndBuildVM(boolean setNewDevice) {
        final ByteBuffer buffer = ByteBuffer.wrap(highLevelCode);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.limit(hlBuffer.position());

        final TornadoGraph tornadoGraph = TornadoGraphBuilder.buildGraph(executionContext, buffer);

        if (setNewDevice) {
            // setNewDevice does not need to propagate any further as executionContext is
            // updated. So, all the required state is set properly in the executionContext
            updateDeviceContext();
        }

        // TornadoVM byte-code generation
        TornadoVM tornadoVM = new TornadoVM(executionContext, tornadoGraph, timeProfiler);

        if (meta().isDebug()) {
            executionContext.dumpExecutionContextMeta();
            tornadoGraph.dumpTornadoGraph();
        }

        return tornadoVM;
    }

    private boolean compareDevices(Set<TornadoXPUDevice> lastDevices, TornadoXPUDevice device2) {
        return lastDevices.contains(device2);
    }

    @Override
    public boolean isLastDeviceListEmpty() {
        return executionContext.getLastDevices().isEmpty();
    }

    /**
     * It queries if the task has to be recompiled. It returns two values:
     * <p>
     * <li>compile: This indicates if it has to be re-compiled</li>
     * <li>updateDevice:This indicates if there is a new device for the same
     * task</li>
     * </p>
     *
     * @return {@link CompileInfo}
     */
    private CompileInfo extractCompileInfo() {
        if (bytecodeBuilder == null && isLastDeviceListEmpty()) {
            return COMPILE_ONLY;
        }

        if (bytecodeBuilder != null && !isLastDeviceListEmpty() && !(compareDevices(executionContext.getLastDevices(), meta().getXPUDevice()))) {
            return COMPILE_AND_UPDATE;
        }

        if (updateData && (gridScheduler == null || !hasWorkerGridForAllTasks())) {
            return COMPILE_ONLY;
        }

        if (!compareDevices(executionContext.getLastDevices(), meta().getXPUDevice())) {
            return COMPILE_AND_UPDATE;
        }

        return NOT_COMPILE_UPDATE;
    }

    /*
     * TornadoVM should not recompile if there is a worker grid for each task.
     * Otherwise, there is a combination of the
     *
     * @Parallel API and the Grid Task. The @Parallel task might need the loop bound
     * updated. TODO This check will no longer be needed once we pass the loop
     * bounds via the call wrapper instead of constant folding.
     */
    private boolean hasWorkerGridForAllTasks() {
        for (TaskPackage taskPackage : taskPackages) {
            if (!gridScheduler.contains(taskGraphName, taskPackage.getId())) {
                return false;
            }
        }
        return true;
    }

    private boolean compileComputeGraphToTornadoVMBytecode() {
        CompileInfo compileInfo = extractCompileInfo();
        if (compileInfo.compile) {
            timeProfiler.start(ProfilerType.TOTAL_BYTE_CODE_GENERATION);
            executionContext.scheduleTaskToDevices();
            TornadoVM tornadoVM = compileGraphAndBuildVM(compileInfo.updateDevice);
            vmTable.put(meta().getXPUDevice(), tornadoVM);
            timeProfiler.stop(ProfilerType.TOTAL_BYTE_CODE_GENERATION);
        }
        executionContext.addLastDevice(meta().getXPUDevice());

        vm = vmTable.get(meta().getXPUDevice());

        /*
         * Set the grid scheduler outside the constructor of the {@link
         * uk.ac.manchester.tornado.runtime.TornadoVM} object. The same TornadoVM object
         * will be used for different grid scheduler objects, if the
         * TornadoTaskSchedule::compile method is not called in different runs of the
         * same TaskSchedule.
         */
        vm.setGridScheduler(gridScheduler);

        if (updateData) {
            executionContext.newCallWrapper(true);
        } else {
            executionContext.newCallWrapper(compileInfo.updateDevice);
        }
        return compileInfo.compile;
    }

    private void compileTaskToOpenCL() {
        vm.withPreCompilation();
    }

    /**
     * If current FPGA execution and JIT mode, then run warm-up.
     */
    private void preCompileForFPGAs() {
        boolean compile = false;
        if (TornadoOptions.FPGA_EMULATION) {
            compile = true;
        } else if (executionContext.getDeviceOfFirstTask() instanceof TornadoXPUDevice tornadoAcceleratorDevice) {
            if (tornadoAcceleratorDevice.isFullJITMode(executionPlanId, executionContext.getTask(0))) {
                compile = true;
            }
        }

        if (compile) {
            if (DEBUG) {
                System.out.println("[DEBUG] JIT compilation for the FPGA");
            }
            compileTaskToOpenCL();
        }
    }

    private void updateProfiler() {
        if (!TornadoOptions.isProfilerEnabled()) {
            return;
        }

        if (!TornadoOptions.PROFILER_LOGS_ACCUMULATE()) {
            timeProfiler.dumpJson(new StringBuilder(), this.getId());
        } else {
            bufferLogProfiler.append(timeProfiler.createJson(new StringBuilder(), this.getId()));
        }

        if (!TornadoOptions.SOCKET_PORT.isEmpty()) {
            TornadoVMClient tornadoVMClient = new TornadoVMClient();
            try {
                tornadoVMClient.sentLogOverSocket(timeProfiler.createJson(new StringBuilder(), this.getId()));
            } catch (IOException e) {
                System.out.println(e);
            }
        }

        if (!TornadoOptions.PROFILER_DIRECTORY.isEmpty()) {
            String jsonFile = timeProfiler.createJson(new StringBuilder(), this.getId());
            RuntimeUtilities.profilerFileWriter(jsonFile);
        }
    }

    private void dumpDeoptimisationReason(TornadoBailoutRuntimeException e) {
        if (!DEBUG) {
            System.err.println(RED + "[Bailout] Running the sequential implementation. Enable --debug to see the reason." + RESET);
        } else {
            System.err.println(e.getMessage());
            for (StackTraceElement s : e.getStackTrace()) {
                System.err.println("\t" + s);
            }
        }
    }

    private void deoptimiseToSequentialJava(TornadoBailoutRuntimeException e) {
        dumpDeoptimisationReason(e);
        runAllTasksJavaSequential();
    }

    @Override
    public void scheduleInner() {
        boolean compile = compileComputeGraphToTornadoVMBytecode();
        TornadoXPUDevice deviceForTask = executionContext.getDeviceForTask(0);
        if (compile && deviceForTask.getDeviceContext().isPlatformFPGA()) {
            preCompileForFPGAs();
        }

        try {
            event = vm.execute(isConcurrentDevicesEnabled, timeProfiler);
            timeProfiler.stop(ProfilerType.TOTAL_TASK_GRAPH_TIME);
            updateProfiler();
        } catch (TornadoBailoutRuntimeException e) {
            if (TornadoOptions.RECOVER_BAILOUT) {
                deoptimiseToSequentialJava(e);
            } else {
                if (DEBUG) {
                    e.printStackTrace();
                }
                throw new TornadoBailoutRuntimeException("Bailout is disabled. \nReason: " + e.getMessage());
            }
        }

    }

    @Override
    public void apply(Consumer<SchedulableTask> consumer) {
        executionContext.apply(consumer);
    }

    @Override
    public void mapAllToInner(TornadoDevice device) {
        executionContext.mapAllTasksToSingleDevice(device);
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
        if (TornadoOptions.VM_USE_DEPS && event != null) {
            event.waitOn();
        } else {
            for (TornadoXPUDevice tornadoXPUDevice : executionContext.getDevices()) {
                if (tornadoXPUDevice != null) {
                    tornadoXPUDevice.sync(executionPlanId);
                }
            }
        }
    }

    @Override
    public void transferToDevice(final int mode, Object... objects) {
        for (Object parameter : objects) {
            if (parameter == null) {
                throw new TornadoRuntimeException("[ERROR] null object passed into streamIn() in schedule " + executionContext.getId());
            }

            // If the parameter is a number, then we do not need to copy
            // since the TornadoVM JIT compiler inlines all values
            if (parameter instanceof Number) {
                continue;
            }

            // Only add the object is the streamIn list if the data transfer mode is set to
            // EVERY_EXECUTION
            boolean isObjectForStreaming = false;
            if (mode == DataTransferMode.EVERY_EXECUTION) {
                streamInObjects.add(parameter);
                isObjectForStreaming = true;
            }

            // The access will be updated later on by the TornadoDataflowAnalysis if necessary
            executionContext.getLocalStateObject(parameter, Access.READ_ONLY).setStreamIn(isObjectForStreaming);

            if (mode == DataTransferMode.UNDER_DEMAND) {
                executionContext.getLocalStateObject(parameter, Access.READ_ONLY).enableUnderDemand();
            }

            // List of input objects in the processing list for the dynamic reconfiguration
            inputModesObjects.add(new StreamingObject(mode, parameter));

            if (TornadoOptions.isReusedBuffersEnabled()) {
                if (!argumentsLookUp.contains(parameter)) {
                    lockObjectsInMemory(parameter);
                }
            }

            // Add in the list for fast arguments lookup
            argumentsLookUp.add(parameter);
        }
    }

    @Override
    public void consumeFromDevice(String sourceTaskGraphName, Object... objects) {
        for (Object parameter : objects) {
            if (parameter == null) {
                throw new TornadoRuntimeException("[ERROR] null object passed into streamIn() in task-graph " + executionContext.getId());
            } else if (parameter instanceof Number) {
                throw new TornadoRuntimeException("[ERROR] Invalid object type (Number) passed into streamIn() in task-graph " + executionContext.getId());
            }

            // the accessor is set to READ_WRITE because the object is UNDER_DEMAND
            executionContext.getLocalStateObject(parameter, Access.READ_WRITE).setOnDevice(true);
            executionContext.addPersistedObject(sourceTaskGraphName, parameter);
            executionContext.addPersistedObject(parameter);

            if (TornadoOptions.isReusedBuffersEnabled()) {
                if (!argumentsLookUp.contains(parameter)) {
                    lockObjectsInMemory(parameter);
                }
            }

            argumentsLookUp.add(parameter);
        }
    }

    @Override
    public void consumeFromDevice(Object... objects) {
        for (Object parameter : objects) {
            if (parameter == null) {
                throw new TornadoRuntimeException("[ERROR] null object passed into streamIn() in task-graph " + executionContext.getId());
            } else if (parameter instanceof Number) {
                throw new TornadoRuntimeException("[ERROR] Invalid object type (Number) passed into streamIn() in task-graph " + executionContext.getId());
            }

            // the accessor is set to READ_WRITE because the object is UNDER_DEMAND
            executionContext.getLocalStateObject(parameter, Access.READ_WRITE).setOnDevice(true);
            executionContext.addPersistedObject(parameter);

            if (TornadoOptions.isReusedBuffersEnabled()) {
                if (!argumentsLookUp.contains(parameter)) {
                    lockObjectsInMemory(parameter);
                }
            }

            argumentsLookUp.add(parameter);
        }
    }

    private boolean isANumber(Object parameter) {
        return parameter instanceof Number;
    }

    private boolean isAtomic(Object parameter) {
        return parameter instanceof AtomicInteger;
    }

    @Override
    public void transferToHost(final int mode, Object... objects) {
        for (Object functionParameter : objects) {
            if (functionParameter == null) {
                new TornadoLogger().warn("null object passed into streamIn() in schedule %s", executionContext.getId());
                continue;
            }

            if (isANumber(functionParameter) && !isAtomic(functionParameter)) {
                throw new TornadoRuntimeException("[ERROR] Scalar value used as output. Use an array or a vector-type instead");
            }

            if (mode != DataTransferMode.UNDER_DEMAND) {
                streamOutObjects.add(functionParameter);
                // the access will be updated later on by the TornadoDataflowAnalysis if necessary
                executionContext.getLocalStateObject(functionParameter, Access.WRITE_ONLY).setStreamOut(true);
            }

            if (mode == DataTransferMode.UNDER_DEMAND) {
                executionContext.addPersistedObject(functionParameter);
            }

            // List of output objects for the dynamic reconfiguration
            outputModeObjects.add(new StreamingObject(mode, functionParameter));

            if (TornadoOptions.isReusedBuffersEnabled() || mode == DataTransferMode.UNDER_DEMAND) {
                if (!argumentsLookUp.contains(functionParameter)) {
                    // We already set function parameter in transferToDevice
                    lockObjectsInMemory(functionParameter);
                }
            }

            argumentsLookUp.add(functionParameter);
        }
    }

    @Override
    public void dump() {
        final int width = 16;
        System.out.printf("code  : capacity = %s, in use = %s %n", RuntimeUtilities.humanReadableByteCount(hlBuffer.capacity(), true), RuntimeUtilities.humanReadableByteCount(hlBuffer.position(),
                true));
        for (int i = 0; i < hlBuffer.position(); i += width) {
            System.out.printf("[0x%04x]: ", i);
            for (int j = 0; j < Math.min(hlBuffer.capacity() - i, width); j++) {
                if (j % 2 == 0) {
                    System.out.print(" ");
                }
                if (j < hlBuffer.position() - i) {
                    System.out.printf("%02x", hlBuffer.get(i + j));
                } else {
                    System.out.print("..");
                }
            }
            System.out.println();
        }
    }

    @Override
    public void withPreCompilation(ExecutorFrame executionPackage) {
        setupProfiler();
        getDevice().getDeviceContext().setResetToFalse();
        timeProfiler.clean();

        compileComputeGraphToTornadoVMBytecode();
        executionPlanId = executionPackage.getExecutionPlanId();
        executionContext.setExecutionPlanId(executionPlanId);
        vm.withPreCompilation();

        if (TornadoOptions.isProfilerEnabled() && !TornadoOptions.PROFILER_LOGS_ACCUMULATE()) {
            timeProfiler.dumpJson(new StringBuilder(), this.getId());
        }
    }

    private Access getObjectAccess(Object object) {
        boolean isRead = false;
        boolean isWrite = false;
        for (StreamingObject inputStreamObject : inputModesObjects) {
            if (inputStreamObject.object.equals(object)) {
                isRead = true;
            }
        }

        for (StreamingObject outputStreamObject : outputModeObjects) {
            if (outputStreamObject.object.equals(object)) {
                isWrite = true;
            }
        }

        if (isRead && isWrite) {
            return Access.READ_WRITE;
        } else if (isRead) {
            return Access.READ_ONLY;
        } else if (isWrite) {
            return Access.WRITE_ONLY;
        } else {
            return Access.NONE;
        }

    }

    private void reuseDeviceBufferObject(Object object) {
        Access objectAccess = getObjectAccess(object);
        final LocalObjectState localState = executionContext.getLocalStateObject(object, objectAccess);
        // Since we do not know upfront the device to use, we need to set the lock buffer
        // for all available devices. This does not allocate any buffer, just annotates
        // a Java object as pinned, so the TornadoVM runtime is free to reuse the space
        final TornadoRuntime runtime = TornadoRuntimeProvider.getTornadoRuntime();
        int numBackends = runtime.getNumBackends();
        for (int i = 0; i < numBackends; i++) {
            TornadoBackend backend = runtime.getBackend(i);
            // For every device from all backends, set the local state to reuse the buffer
            backend.getAllDevices().forEach(device -> reuseDeviceBufferObject(localState, device));
        }
    }

    private void reuseDeviceBufferObject(final LocalObjectState localState, final TornadoDevice device) {
        final DataObjectState dataObjectState = localState.getDataObjectState();
        final XPUDeviceBufferState deviceState = dataObjectState.getDeviceBufferState(device);
        deviceState.setLockBuffer(true);
    }

    void lockObjectsInMemory(Object... objects) {
        Arrays.stream(objects).forEach(this::reuseDeviceBufferObject);
    }

    @Override
    public void freeDeviceMemory() {
        free();
    }

    private void freePlansFromDynamicReconfiguration() {
        if (!executionPlansIndexes.isEmpty()) {
            if (DEBUG) {
                System.out.println("Free " + executionPlansIndexes.size() + " execution plans");
            }
            for (Integer deviceNumber : executionPlansIndexes.keySet()) {
                TornadoExecutionPlan executionPlan = executionPlansIndexes.get(deviceNumber);
                if (executionPlan != null) {
                    executionPlan.freeDeviceMemory();
                }
            }
            globalExecutionPlanIndex.clear();
            policyTimeTable.clear();
            executionPlansIndexes.clear();
        }
    }

    private void freeIOObjects() {
        for (StreamingObject inputStreamObject : inputModesObjects) {
            if (streamOutObjects.contains(inputStreamObject.object)) {
                // READ-WRITE
                freeDeviceMemoryObject(inputStreamObject.object, Access.READ_WRITE);
            } else {
                freeDeviceMemoryObject(inputStreamObject.object, Access.READ_ONLY);
            }
        }

        for (StreamingObject outputStreamObject : outputModeObjects) {
            if (streamInObjects.contains(outputStreamObject.object)) {
                // READ-WRITE
                freeDeviceMemoryObject(outputStreamObject.object, Access.READ_WRITE);
            } else {
                freeDeviceMemoryObject(outputStreamObject.object, Access.WRITE_ONLY);
            }
        }
    }

    private void free() {
        freePlansFromDynamicReconfiguration();

        if (vm == null) {
            return;
        }

        freeIOObjects();
        meta().getXPUDevice().getDeviceContext().reset(executionPlanId);
    }

    private void freeDeviceMemoryObject(Object object, Access access) {
        final LocalObjectState localState = executionContext.getLocalStateObject(object, access);
        releaseObjectFromDeviceMemory(localState, meta().getXPUDevice());
    }

    private void releaseObjectFromDeviceMemory(final LocalObjectState localState, final TornadoDevice device) {
        final DataObjectState dataObjectState = localState.getDataObjectState();
        final XPUDeviceBufferState deviceBufferState = dataObjectState.getDeviceBufferState(device);
        deviceBufferState.setLockBuffer(false);
        if (deviceBufferState.hasObjectBuffer()) {
            device.deallocate(deviceBufferState);
        }
    }

    private void syncField(Object object) {
        // Clean the profiler: it avoids the possibility of reporting
        // the `execute` profiling information twice.
        timeProfiler.clean();

        // sync all objects from the context
        executionContext.sync();

        // update the profiler if needed
        updateProfiler();
    }

    private Event syncObjectInner(Object object) {
        Access objectAccess = getObjectAccess(object);
        final LocalObjectState localState = executionContext.getLocalStateObject(object, objectAccess);
        final DataObjectState dataObjectState = localState.getDataObjectState();
        final TornadoXPUDevice device = meta().getXPUDevice();
        final XPUDeviceBufferState deviceState = dataObjectState.getDeviceBufferState(device);
        if (deviceState.isLockedBuffer()) {
            return device.resolveEvent(executionPlanId, device.streamOutBlocking(executionPlanId, object, 0, deviceState, null));
        }
        return null;
    }

    private Event syncObjectInner(Object object, long offset, long partialCopySize) {
        Access objectAccess = getObjectAccess(object);
        final LocalObjectState localState = executionContext.getLocalStateObject(object, objectAccess);
        final DataObjectState dataObjectState = localState.getDataObjectState();
        final TornadoXPUDevice device = meta().getXPUDevice();
        final XPUDeviceBufferState deviceState = dataObjectState.getDeviceBufferState(device);
        deviceState.setPartialCopySize(partialCopySize);
        if (deviceState.isLockedBuffer()) {
            return device.resolveEvent(executionPlanId, device.streamOutBlocking(executionPlanId, object, offset, deviceState, null));
        }
        return null;
    }

    private Event syncObjectInnerLazy(Object object, long hostOffset, long bufferSize) {
        Access objectAccess = getObjectAccess(object);
        final LocalObjectState localState = executionContext.getLocalStateObject(object, objectAccess);
        final DataObjectState dataObjectState = localState.getDataObjectState();
        final TornadoXPUDevice device = meta().getXPUDevice();
        final XPUDeviceBufferState deviceBufferState = dataObjectState.getDeviceBufferState(device);
        if (deviceBufferState.isLockedBuffer()) {
            deviceBufferState.getXPUBuffer().setSizeSubRegion(bufferSize);
            return device.resolveEvent(executionPlanId, device.streamOutBlocking(executionPlanId, object, hostOffset, deviceBufferState, null));
        }
        return null;
    }

    private Event syncParameter(Object object) {
        Event eventParameter = null;
        if (batchSizeBytes != TornadoExecutionContext.INIT_VALUE) {
            BatchConfiguration batchConfiguration = BatchConfiguration.computeChunkSizes(executionContext, batchSizeBytes);
            long hostOffset = 0;
            for (int i = 0; i < batchConfiguration.getTotalChunks(); i++) {
                hostOffset = (batchSizeBytes * i);
                eventParameter = syncObjectInnerLazy(object, hostOffset, batchSizeBytes);
            }
            // Last chunk
            if (batchConfiguration.getRemainingChunkSize() != 0) {
                hostOffset += batchSizeBytes;
                eventParameter = syncObjectInnerLazy(object, hostOffset, batchConfiguration.getRemainingChunkSize());
            }
        } else {
            eventParameter = syncObjectInner(object);
        }
        if (eventParameter != null) {
            eventParameter.waitOn();
        }
        return eventParameter;
    }

    private Event syncParameter(Object object, long offset, long partialCopySize) {
        Event eventParameter = syncObjectInner(object, offset, partialCopySize);
        if (eventParameter != null) {
            eventParameter.waitOn();
        }
        return eventParameter;
    }

    private boolean copyUnderDemand(Object object) {
        for (StreamingObject outputObject : outputModeObjects) {
            if (outputObject.getObject().equals(object) && outputObject.getMode() == DataTransferMode.UNDER_DEMAND) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void syncRuntimeTransferToHost(Object... objects) {
        if (vm == null) {
            return;
        }

        List<Event> events = new ArrayList<>();
        for (Object object : objects) {
            if (DEBUG) {
                if (copyUnderDemand(object)) {
                    new TornadoLogger().debug("Object " + object + " to be copied UNDER_DEMAND");
                }
            }
            // Check if it is an argument captured by the scope (not in the parameter list).
            if (!argumentsLookUp.contains(object)) {
                syncField(object);
            } else {
                Event eventParameter = syncParameter(object);
                events.add(eventParameter);
            }
        }

        if (TornadoOptions.isProfilerEnabled()) {

            /*
             * Clean the profiler. It avoids the possibility of reporting the `execute`
             * profiling information twice.
             */
            timeProfiler.clean();
            for (int i = 0; i < events.size(); i++) {
                Event eventParameter = events.get(i);
                if (eventParameter == null) {
                    continue;
                }
                long value = timeProfiler.getTimer(ProfilerType.COPY_OUT_TIME_SYNC);
                eventParameter.waitForEvents(executionPlanId);
                value += eventParameter.getElapsedTime();
                timeProfiler.setTimer(ProfilerType.COPY_OUT_TIME_SYNC, value);
                Access objectAccess = getObjectAccess(objects[i]);
                LocalObjectState localState = executionContext.getLocalStateObject(objects[i], objectAccess);
                XPUDeviceBufferState deviceObjectState = localState.getDataObjectState().getDeviceBufferState(meta().getXPUDevice());
                timeProfiler.addValueToMetric(ProfilerType.COPY_OUT_SIZE_BYTES_SYNC, TimeProfiler.NO_TASK_NAME, deviceObjectState.getXPUBuffer().size());
            }
            updateProfiler();
        }
    }

    @Override
    public void syncRuntimeTransferToHost(Object object, long offset, long partialCopySize) {

        if (vm == null) {
            return;
        }

        Event event = null;
        if (DEBUG) {
            if (copyUnderDemand(object)) {
                new TornadoLogger().debug(partialCopySize + " bytes of the object " + object + " to be copied UNDER_DEMAND from offset " + offset);
            }
        }

        // Check if it is an argument captured by the scope (not in the parameter list).
        if (!argumentsLookUp.contains(object)) {
            syncField(object);
        } else {
            event = syncParameter(object, offset, partialCopySize);
        }

        if (TornadoOptions.isProfilerEnabled()) {
            timeProfiler.clean();
            if (event != null) {
                long value = timeProfiler.getTimer(ProfilerType.COPY_OUT_TIME_SYNC);
                event.waitForEvents(executionPlanId);
                value += event.getElapsedTime();
                timeProfiler.setTimer(ProfilerType.COPY_OUT_TIME_SYNC, value);
                Access objectAccess = getObjectAccess(object);
                LocalObjectState localState = executionContext.getLocalStateObject(object, objectAccess);
                XPUDeviceBufferState deviceObjectState = localState.getDataObjectState().getDeviceBufferState(meta().getXPUDevice());
                timeProfiler.addValueToMetric(ProfilerType.COPY_OUT_SIZE_BYTES_SYNC, TimeProfiler.NO_TASK_NAME, deviceObjectState.getXPUBuffer().size());
                updateProfiler();
            }
        }
    }

    @Override
    public String getId() {
        return meta().getId();
    }

    @Override
    public ScheduleContext meta() {
        return executionContext.meta();
    }

    private void runReduceTaskGraph() {
        this.reduceTaskGraph.executeExpression();
    }

    private void rewriteTaskForReduceSkeleton(MetaReduceCodeAnalysis analysisTaskSchedule) {
        reduceTaskGraph = new ReduceTaskGraph(this.getId(), taskPackages, streamInObjects, inputModesObjects, streamOutObjects, outputModeObjects, compilationGraph, this);
        reduceTaskGraph.scheduleWithReduction(analysisTaskSchedule);
        reduceExpressionRewritten = true;
    }

    private TornadoTaskGraphInterface reduceAnalysis() {
        TornadoTaskGraphInterface abstractTaskGraph = null;
        if (analysisTaskGraph == null && !reduceAnalysis) {
            analysisTaskGraph = ReduceCodeAnalysis.analyzeTaskGraph(taskPackages);
            reduceAnalysis = true;
            if (analysisTaskGraph != null && analysisTaskGraph.isValid()) {
                rewriteTaskForReduceSkeleton(analysisTaskGraph);
                abstractTaskGraph = this;
            }
        }
        return abstractTaskGraph;
    }

    private TornadoTaskGraphInterface analyzeSkeletonAndRun() {
        TornadoTaskGraphInterface abstractTaskGraph;
        if (!reduceExpressionRewritten) {
            abstractTaskGraph = reduceAnalysis();
        } else {
            runReduceTaskGraph();
            abstractTaskGraph = this;
        }
        return abstractTaskGraph;
    }

    private void cleanUp() {
        updateData = false;
        isFinished = true;
    }

    private boolean isArgumentIgnorable(Object parameter) {
        return parameter instanceof Number || parameter instanceof KernelContext || //
                parameter instanceof IntArray || parameter instanceof FloatArray || //
                parameter instanceof DoubleArray || parameter instanceof LongArray || //
                parameter instanceof ShortArray;
    }

    private boolean checkAllArgumentsPerTask() {

        for (TaskPackage task : taskPackages) {
            Object[] taskParameters = task.getTaskParameters();
            // Note: the first element in the object list is a lambda expression (computation)
            for (int i = 1; i < (taskParameters.length - 1); i++) {
                Object parameter = taskParameters[i];
                if (isArgumentIgnorable(parameter)) {
                    continue;
                }
                if (!argumentsLookUp.contains(parameter)) {
                    throw new TornadoTaskRuntimeException("Parameter #" + i + " <" + parameter + "> from task <" + task
                            .getId() + "> not specified either in transferToDevice or transferToHost functions");
                }
            }
        }
        return true;
    }

    private boolean checkForMemorySegmentAsTaskParameter(CompilableTask task) {
        for (Object parameter : task.getArguments()) {
            if (parameter instanceof MemorySegment) {
                String parameterClassName = parameter.getClass().getSimpleName();
                throw new TornadoRuntimeException("Parameter " + parameterClassName + " is not a valid task argument because it is an instance of a TornadoNativeArray.");
            }
        }
        return true;
    }

    private void lockInPendingFieldsObjects() {
        // All Fields are set to reuse buffers by default
        final int taskCount = executionContext.getTaskCount();
        for (int i = 0; i < taskCount; i++) {
            SchedulableTask task = executionContext.getTask(i);
            Object[] arguments = task.getArguments();
            for (Object arg : arguments) {
                if (isArgumentIgnorable(arg)) {
                    continue;
                }
                if (!argumentsLookUp.contains(arg)) {
                    lockObjectsInMemory(arg);
                }
            }
        }
    }

    private void setupProfiler() {
        if (isProfilerEnabled()) {
            this.timeProfiler = new TimeProfiler();
        } else {
            this.timeProfiler = new EmptyProfiler();
        }
        executionContext.withProfiler(timeProfiler);
        for (SchedulableTask task : executionContext.getTasks()) {
            logTaskMethodHandle(task);
        }
    }

    private void bailout() {
        if (!TornadoOptions.RECOVER_BAILOUT) {
            throw new TornadoBailoutRuntimeException("[TornadoVM] Error - Recover option disabled");
        } else {
            runAllTasksJavaSequential();
        }
    }

    private TornadoTaskGraphInterface execute() {

        // check if bailout due to task-rewriting
        if (bailout) {
            bailout();
        }

        isFinished = false;
        setupProfiler();
        timeProfiler.clean();
        timeProfiler.start(ProfilerType.TOTAL_TASK_GRAPH_TIME);

        // Single context ID per execution plan.
        // This is used to create/obtain low-level command queues from the driver
        // and other resources (e.g., Level Zero Command Lists).
        executionContext.setExecutionPlanId(executionPlanId);

        updatePersistedObjectState();

        TornadoTaskGraphInterface reduceTaskGraph = null;
        if (TornadoOptions.EXPERIMENTAL_REDUCE && !(getId().startsWith(GENERATED_TASK_GRAPH_PREFIX))) {
            reduceTaskGraph = analyzeSkeletonAndRun();
        }

        if (reduceTaskGraph != null) {
            return reduceTaskGraph;
        }

        // check parameter list
        if (TornadoOptions.FORCE_CHECK_PARAMETERS) {
            checkAllArgumentsPerTask();
        }

        lockInPendingFieldsObjects();
        analysisTaskGraph = null;

        try {
            scheduleInner();
            cleanUp();
        } catch (TornadoRuntimeException e) {
            bailout();
        }
        return this;
    }

    private void checkProfilerOn(ExecutorFrame executorFrame) {
        if (executorFrame.getProfilerMode() != null) {
            enableProfiler(executorFrame.getProfilerMode());
        } else {
            disableProfiler();
        }
    }

    private TornadoTaskGraphInterface executeWithDynamicReconfiguration(ExecutorFrame executorFrame) throws InterruptedException {
        return switch (executorFrame.getDRMode()) {
            case SERIAL -> runDynamicReconfigurationInSequentialMode(executorFrame.getDynamicReconfigurationPolicy());
            case PARALLEL -> runDynamicReconfigurationInParallelMode(executorFrame.getDynamicReconfigurationPolicy());
            case null -> throw new TornadoRuntimeException("[Error] Dynamic Reconfiguration Mode not Implemented: " + executorFrame.getDRMode());
        };
    }

    @Override
    public TornadoTaskGraphInterface execute(ExecutorFrame executorFrame) {
        executionPlanId = executorFrame.getExecutionPlanId();
        checkProfilerOn(executorFrame);
        if (executorFrame.getDynamicReconfigurationPolicy() == null) {
            return execute();
        } else {
            try {
                return executeWithDynamicReconfiguration(executorFrame);
            } catch (InterruptedException e) {
                throw new TornadoRuntimeException(e);
            }
        }
    }

    private boolean isTaskNamePresent(String taskName) {
        for (TaskPackage taskPackage : taskPackages) {
            if (taskName.equals(taskGraphName + "." + taskPackage.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean checkGridSchedulerNames() {
        Set<String> gridTaskNames = gridScheduler.keySet();
        return gridTaskNames.stream().anyMatch(this::isTaskNamePresent);
    }

    @SuppressWarnings("unchecked")
    private void runSequentialCodeInThread(TaskPackage taskPackage) {
        int type = taskPackage.getTaskType();
        switch (type) {
            case 0 -> {
                @SuppressWarnings("rawtypes") Task task = (Task) taskPackage.getTaskParameters()[0];
                task.apply();
            }
            case 1 -> {
                @SuppressWarnings("rawtypes") Task1 task1 = (Task1) taskPackage.getTaskParameters()[0];
                task1.apply(taskPackage.getTaskParameters()[1]);
            }
            case 2 -> {
                @SuppressWarnings("rawtypes") Task2 task2 = (Task2) taskPackage.getTaskParameters()[0];
                task2.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2]);
            }
            case 3 -> {
                @SuppressWarnings("rawtypes") Task3 task3 = (Task3) taskPackage.getTaskParameters()[0];
                task3.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3]);
            }
            case 4 -> {
                @SuppressWarnings("rawtypes") Task4 task4 = (Task4) taskPackage.getTaskParameters()[0];
                task4.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4]);
            }
            case 5 -> {
                @SuppressWarnings("rawtypes") Task5 task5 = (Task5) taskPackage.getTaskParameters()[0];
                task5.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4], taskPackage
                        .getTaskParameters()[5]);
            }
            case 6 -> {
                @SuppressWarnings("rawtypes") Task6 task6 = (Task6) taskPackage.getTaskParameters()[0];
                task6.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4], taskPackage
                        .getTaskParameters()[5], taskPackage.getTaskParameters()[6]);
            }
            case 7 -> {
                @SuppressWarnings("rawtypes") Task7 task7 = (Task7) taskPackage.getTaskParameters()[0];
                task7.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4], taskPackage
                        .getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7]);
            }
            case 8 -> {
                @SuppressWarnings("rawtypes") Task8 task8 = (Task8) taskPackage.getTaskParameters()[0];
                task8.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4], taskPackage
                        .getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7], taskPackage.getTaskParameters()[8]);
            }
            case 9 -> {
                @SuppressWarnings("rawtypes") Task9 task9 = (Task9) taskPackage.getTaskParameters()[0];
                task9.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4], taskPackage
                        .getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7], taskPackage.getTaskParameters()[8], taskPackage.getTaskParameters()[9]);
            }
            case 10 -> {
                @SuppressWarnings("rawtypes") Task10 task10 = (Task10) taskPackage.getTaskParameters()[0];
                task10.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4], taskPackage
                        .getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7], taskPackage.getTaskParameters()[8], taskPackage.getTaskParameters()[9],
                        taskPackage.getTaskParameters()[10]);
            }
            case 11 -> {
                @SuppressWarnings("rawtypes") Task11 task11 = (Task11) taskPackage.getTaskParameters()[0];
                task11.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4], taskPackage
                        .getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7], taskPackage.getTaskParameters()[8], taskPackage.getTaskParameters()[9],
                        taskPackage.getTaskParameters()[10], taskPackage.getTaskParameters()[11]);
            }
            case 12 -> {
                @SuppressWarnings("rawtypes") Task12 task12 = (Task12) taskPackage.getTaskParameters()[0];
                task12.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4], taskPackage
                        .getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7], taskPackage.getTaskParameters()[8], taskPackage.getTaskParameters()[9],
                        taskPackage.getTaskParameters()[10], taskPackage.getTaskParameters()[11], taskPackage.getTaskParameters()[12]);
            }
            case 13 -> {
                @SuppressWarnings("rawtypes") Task13 task13 = (Task13) taskPackage.getTaskParameters()[0];
                task13.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4], taskPackage
                        .getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7], taskPackage.getTaskParameters()[8], taskPackage.getTaskParameters()[9],
                        taskPackage.getTaskParameters()[10], taskPackage.getTaskParameters()[11], taskPackage.getTaskParameters()[12], taskPackage.getTaskParameters()[13]);
            }
            case 14 -> {
                @SuppressWarnings("rawtypes") Task14 task14 = (Task14) taskPackage.getTaskParameters()[0];
                task14.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4], taskPackage
                        .getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7], taskPackage.getTaskParameters()[8], taskPackage.getTaskParameters()[9],
                        taskPackage.getTaskParameters()[10], taskPackage.getTaskParameters()[11], taskPackage.getTaskParameters()[12], taskPackage.getTaskParameters()[13], taskPackage
                                .getTaskParameters()[14]);
            }
            case 15 -> {
                @SuppressWarnings("rawtypes") Task15 task15 = (Task15) taskPackage.getTaskParameters()[0];
                task15.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4], taskPackage
                        .getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7], taskPackage.getTaskParameters()[8], taskPackage.getTaskParameters()[9],
                        taskPackage.getTaskParameters()[10], taskPackage.getTaskParameters()[11], taskPackage.getTaskParameters()[12], taskPackage.getTaskParameters()[13], taskPackage
                                .getTaskParameters()[14], taskPackage.getTaskParameters()[15]);
            }
            default -> throw new TornadoRuntimeException("Sequential Runner not supported yet. Number of parameters: " + type);
        }
    }

    private int getWinnerDeviceIndex(Policy policy, long[] totalTimers) {
        // Set to the `PERFORMANCE` policy by default;
        if (policy == null) {
            policy = Policy.PERFORMANCE;
        }

        int deviceWinnerIndex;

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
                throw new TornadoDynamicReconfigurationException("Policy " + policy + " not defined yet");
        }

        return deviceWinnerIndex;
    }

    private PairWinnerThread syncThreadWinnerConfiguration(Thread[] threads) {
        int winner = 0;
        boolean isAlive = true;
        Thread thread = null;
        while (isAlive) {
            for (int threadIndex = 0; threadIndex < threads.length; threadIndex++) {
                isAlive = threads[threadIndex].isAlive();
                if (!isAlive) {
                    if (DEBUG) {
                        System.out.println("SELECTED Thread-Device: " + threads[threadIndex].getName() + " ");
                    }
                    winner = threadIndex;
                    thread = new Thread(() -> {
                        // kill the others
                        for (Thread t : threads) {
                            if (t.isAlive()) {
                                t.interrupt();
                            }
                        }
                    });
                }
            }
        }
        return new PairWinnerThread(winner, thread);
    }

    private void runAllTasksJavaSequential() {
        for (TaskPackage taskPackage : taskPackages) {
            runSequentialCodeInThread(taskPackage);
        }
    }

    private void setUpThreadSequentialVersion(Policy policy, Thread[] threads, int indexSequential, Timer timer, long[] totalTimers) {
        // Last Thread runs the sequential code
        threads[indexSequential] = new Thread(() -> {
            Thread.currentThread().setName("Thread-sequential");

            if (policy == Policy.PERFORMANCE) {
                // Warm up for the PERFORMANCE policy
                for (int i = 0; i < ITERATIONS_WARMUP_DYNAMIC_RECONFIGURATION_LOOP; i++) {
                    runAllTasksJavaSequential();
                }
            }

            final long start = timer.time();
            runAllTasksJavaSequential();
            final long endSequentialCode = timer.time();

            if (DEBUG) {
                System.out.println("Seq finished: " + Thread.currentThread().getName());
            }

            totalTimers[indexSequential] = (endSequentialCode - start);
        });
    }

    private void setUpParallelExecutionPlans(int numDevices, Thread[] threads, Timer timer, Policy policy, long[] totalTimers) {
        for (int i = 0; i < numDevices; i++) {
            final int taskScheduleNumber = i;
            threads[i] = new Thread(() -> {
                String newTaskScheduleName = GENERATED_TASK_GRAPH_PREFIX + taskScheduleNumber;
                TaskGraph task = new TaskGraph(newTaskScheduleName);

                Thread.currentThread().setName("Thread-DEV: " + TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getDevice(taskScheduleNumber).getPhysicalDevice().getDeviceName());

                for (StreamingObject streamingObject : inputModesObjects) {
                    performStreamInObject(task, streamingObject.object, streamingObject.mode);
                }

                taskPackages.forEach(task::addTask);

                for (StreamingObject streamingObject : outputModeObjects) {
                    performStreamOutThreads(streamingObject.mode, task, streamingObject.object);
                }

                TornadoExecutionPlan executor = new TornadoExecutionPlan(task.snapshot());
                TornadoDevice device = TornadoExecutionPlan.getDevice(DEFAULT_DRIVER_INDEX, taskScheduleNumber);
                executor.withDevice(device);

                if (policy == Policy.PERFORMANCE) {
                    // first warm up
                    for (int k = 0; k < ITERATIONS_WARMUP_DYNAMIC_RECONFIGURATION_PERF; k++) {
                        executor.execute();
                    }
                }

                long start = timer.time();
                executor.execute();
                final long end = timer.time();
                executionPlansIndexes.put(taskScheduleNumber, executor);

                if (USE_GLOBAL_TASK_CACHE) {
                    globalExecutionPlanIndex.put(offsetGlobalIndex.get(), executor);
                    offsetGlobalIndex.incrementAndGet();
                } else {
                    globalExecutionPlanIndex.put(taskScheduleNumber, executor);
                }

                totalTimers[taskScheduleNumber] = (end - start);
            });
        }

    }

    private void join(Thread[] threads) {
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new TornadoDynamicReconfigurationException(e);
            }
        }
    }

    private void runScheduleWithParallelEvaluationMode(Policy policy) throws InterruptedException {

        final Timer timer = (TIME_IN_NANOSECONDS) ? new NanoSecTimer() : new MilliSecTimer();

        // The default reconfiguration starts with the default driver.
        // In the near future it will be an API call to modify this value. 
        TornadoBackend tornadoDriver = TornadoCoreRuntime.getTornadoRuntime().getBackend(DEFAULT_DRIVER_INDEX);
        int numAccelerators = tornadoDriver.getNumDevices();
        long masterThreadID = Thread.currentThread().threadId();

        // One additional threads is reserved for sequential CPU execution
        final int numThreads = numAccelerators + 1;

        Thread[] threads = new Thread[numThreads];
        long[] totalTimers = new long[numThreads];

        // Last Thread runs the sequential code
        setUpThreadSequentialVersion(policy, threads, numAccelerators, timer, totalTimers);

        // Run all task schedules in parallel
        setUpParallelExecutionPlans(numAccelerators, threads, timer, policy, totalTimers);

        // run all threads
        IntStream.range(0, numThreads).forEach(i -> threads[i].start());

        // Define the winner, based on the first thread to finish
        if (policy == Policy.LATENCY) {
            PairWinnerThread winnerThread = syncThreadWinnerConfiguration(threads);
            // wait for the winner configuration
            winnerThread.thread.join();

            // add the winner configuration into the table
            policyTimeTable.put(policy, winnerThread.threadWinnerIndex);
            return;
        }

        // JOIN operation for the `PERFORMANCE` and `END_TO_END` policies.
        join(threads);
        if ((policy == Policy.PERFORMANCE || policy == Policy.END_2_END) //
                && (masterThreadID == Thread.currentThread().threadId())) {

            int deviceWinnerIndex = getWinnerDeviceIndex(policy, totalTimers);
            policyTimeTable.put(policy, deviceWinnerIndex);

            if (DEBUG) {
                System.out.println(getListDevices());
                System.out.println("BEST Position: #" + deviceWinnerIndex + " " + Arrays.toString(totalTimers));
            }
        }
    }

    private void runSequential() {
        taskPackages.forEach(this::runSequentialCodeInThread);
    }

    private TornadoExecutionPlan recompileExecutionPlan(int deviceWinnerIndex) {
        // Force re-compilation for the device <deviceWinnerIndex>
        String newTaskScheduleName = GENERATED_TASK_GRAPH_PREFIX + deviceWinnerIndex;
        TaskGraph taskToCompile = new TaskGraph(newTaskScheduleName);
        performStreamInObject(taskToCompile, streamInObjects, DataTransferMode.EVERY_EXECUTION);
        taskPackages.forEach(taskToCompile::addTask);
        performStreamOutThreads(DataTransferMode.EVERY_EXECUTION, taskToCompile, streamOutObjects);
        return new TornadoExecutionPlan(taskToCompile.snapshot());
    }

    private void runTaskGraphParallelSelected(int deviceWinnerIndex) {
        if (DEBUG) {
            System.out.println("Running in parallel device: " + deviceWinnerIndex);
        }
        TornadoExecutionPlan executionPlan = executionPlansIndexes.get(deviceWinnerIndex);
        if (executionPlan == null) {
            if (USE_GLOBAL_TASK_CACHE) {
                // This is only if compilation is not using Partial Evaluation
                executionPlan = globalExecutionPlanIndex.get(deviceWinnerIndex);
            } else {
                executionPlan = recompileExecutionPlan(deviceWinnerIndex);
                // Save the TaskSchedule in cache
                executionPlansIndexes.put(deviceWinnerIndex, executionPlan);
            }
        }
        TornadoDevice device = TornadoExecutionPlan.getDevice(DEFAULT_DRIVER_INDEX, deviceWinnerIndex);
        executionPlan.withDevice(device) //
                .execute();
    }

    private TornadoTaskGraphInterface runDynamicReconfigurationInParallelMode(Policy policy) throws InterruptedException {
        if (!policyTimeTable.containsKey(policy) || policyTimeTable.get(policy) == null) {
            runScheduleWithParallelEvaluationMode(policy);
        } else {
            // Run with the winner device
            int deviceWinnerIndex = policyTimeTable.get(policy);
            if (deviceWinnerIndex >= TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getNumDevices()) {
                runSequential();
            } else {
                runTaskGraphParallelSelected(deviceWinnerIndex);
            }
        }
        return this;
    }

    private void runSequentialTaskGraph(Policy policy, Timer timer, long[] totalTimers, int indexSequential) {
        if (policy == Policy.PERFORMANCE) {
            for (int k = 0; k < ITERATIONS_WARMUP_DYNAMIC_RECONFIGURATION_LOOP; k++) {
                runAllTasksJavaSequential();
            }
        }
        long startSequential = timer.time();
        runAllTasksJavaSequential();
        final long endSequentialCode = timer.time();
        totalTimers[indexSequential] = (endSequentialCode - startSequential);
    }

    private void runAllTaskGraphsInAcceleratorsSequentially(int numDevices, Timer timer, Policy policy, long[] totalTimers) {
        String[] ignoreTaskNames = System.getProperties().getProperty("tornado.ignore.tasks", "").split(",");

        // Running sequentially for all the devices
        for (int taskNumber = 0; taskNumber < numDevices; taskNumber++) {
            String newTaskScheduleName = GENERATED_TASK_GRAPH_PREFIX + taskNumber;
            TaskGraph task = new TaskGraph(newTaskScheduleName);

            for (StreamingObject streamingObject : inputModesObjects) {
                performStreamInObject(task, streamingObject.object, streamingObject.mode);
            }

            boolean ignoreTask = false;
            for (TaskPackage taskPackage : taskPackages) {
                String taskID = taskPackage.getId();

                String name = newTaskScheduleName + "." + taskID;
                for (String s : ignoreTaskNames) {
                    if (s.equals(name)) {
                        totalTimers[taskNumber] = Long.MAX_VALUE;
                        ignoreTask = true;
                        break;
                    }
                }

                task.addTask(taskPackage);
            }

            if (ignoreTask) {
                continue;
            }
            for (StreamingObject modeObject : outputModeObjects) {
                performStreamOutThreads(modeObject.mode, task, modeObject.object);
            }

            TornadoExecutionPlan executor = new TornadoExecutionPlan(task.snapshot());
            TornadoDevice device = TornadoExecutionPlan.getDevice(DEFAULT_DRIVER_INDEX, taskNumber);
            executor.withDevice(device);

            if (policy == Policy.PERFORMANCE) {
                for (int k = 0; k < ITERATIONS_WARMUP_DYNAMIC_RECONFIGURATION_PERF; k++) {
                    executor.execute();
                }
            }

            final long start = timer.time();
            executor.execute();
            final long end = timer.time();
            executionPlansIndexes.put(taskNumber, executor);

            // TaskSchedules Global
            if (USE_GLOBAL_TASK_CACHE) {
                globalExecutionPlanIndex.put(offsetGlobalIndex.get(), executor);
                offsetGlobalIndex.incrementAndGet();
            } else {
                globalExecutionPlanIndex.put(taskNumber, executor);
            }

            totalTimers[taskNumber] = end - start;
        }
    }

    private void updateHistoryTables(Policy policy, int deviceWinnerIndex) {
        // Matching the name
        for (TaskPackage taskPackage : taskPackages) {
            Object code = taskPackage.getTaskParameters()[0];
            Method methodHandle = TaskUtils.resolveMethodHandle(code);
            ConcurrentHashMap<String, HistoryTable> tableSizes = null;

            int dev = baseGlobalIndex + deviceWinnerIndex;

            if (!executionHistoryPolicy.containsKey(policy)) {
                tableSizes = new ConcurrentHashMap<>();
                HistoryTable table = new HistoryTable();
                int size = getMaxInputSize();
                table.getTree().put(size, dev);

                tableSizes.put(methodHandle.toGenericString(), table);

            } else {
                tableSizes = executionHistoryPolicy.get(policy);
                if (!tableSizes.containsKey(methodHandle.toGenericString())) {
                    HistoryTable table = new HistoryTable();
                    int size = getMaxInputSize();
                    table.getTree().put(size, dev);
                    tableSizes.put(methodHandle.toGenericString(), table);
                } else {
                    // update the size
                    HistoryTable table = tableSizes.get(methodHandle.toGenericString());
                    int size = getMaxInputSize();
                    table.getTree().put(size, dev);
                    tableSizes.put(methodHandle.toGenericString(), table);
                }
            }
            executionHistoryPolicy.put(policy, tableSizes);
            baseGlobalIndex = offsetGlobalIndex.get();
        }
    }

    private String getListDevices() {
        StringBuilder str = new StringBuilder();
        str.append("                  : [");
        int num = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getNumDevices();
        for (int i = 0; i < num; i++) {
            TornadoDeviceType deviceType = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getDevice(i).getDeviceType();
            String type = switch (deviceType) {
                case CPU -> "CPU";
                case GPU -> "GPU";
                case FPGA -> "FPGA";
                case ACCELERATOR -> "ACCELERATOR";
                default -> "JAVA";
            };
            str.append(type).append(",").append("\t ");
        }
        str.append("JVM]");
        return str.toString();
    }

    private void evaluateDynamicReconfigurationInSequentialMode(Policy policy) {
        final Timer timer = (TIME_IN_NANOSECONDS) ? new NanoSecTimer() : new MilliSecTimer();
        int numDevices = TornadoCoreRuntime.getTornadoRuntime().getBackend(DEFAULT_DRIVER_INDEX).getNumDevices();
        final int totalTornadoDevices = numDevices + 1;
        long[] totalTimers = new long[totalTornadoDevices];

        // Run Sequential
        runSequentialTaskGraph(policy, timer, totalTimers, numDevices);

        // Run Task Schedules on the accelerator
        runAllTaskGraphsInAcceleratorsSequentially(numDevices, timer, policy, totalTimers);

        if (policy == Policy.PERFORMANCE || policy == Policy.END_2_END) {
            int deviceWinnerIndex = getWinnerDeviceIndex(policy, totalTimers);
            policyTimeTable.put(policy, deviceWinnerIndex);

            updateHistoryTables(policy, deviceWinnerIndex);

            if (DEBUG) {
                System.out.println(getListDevices());
                System.out.println("BEST Position: #" + deviceWinnerIndex + " " + Arrays.toString(totalTimers));
            }
        }
    }

    /**
     * Experimental method to sync all objects when making a clone copy for all output objects per device.
     *
     * @param policy
     *     input policy
     * @param numDevices
     *     number of devices
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
     * @return max size of all input arrays.
     */
    private int getMaxInputSize() {
        Object[] parameters = taskPackages.getFirst().getTaskParameters();
        int size = 0;
        for (int i = 1; i < parameters.length; i++) {
            Object o = parameters[i];
            if (o.getClass().isArray()) {
                int currentSize = Array.getLength(o);
                size = Math.max(currentSize, size);
            } else {
                size = Math.max(1, size);
            }
        }
        return size;
    }

    private TornadoTaskGraphInterface runDynamicReconfigurationInSequentialMode(Policy policy) {

        if (policy == Policy.LATENCY) {
            if (DEBUG) {
                System.out.println("[WARNING]: LATENCY policy using the DRMode.SERIAL is not allowed. Changing to PERFORMANCE mode");
            }
            policy = Policy.PERFORMANCE;
        }

        int numAccelerators = TornadoRuntimeProvider.getTornadoRuntime().getBackend(DEFAULT_DRIVER_INDEX).getNumDevices();

        if (policyTimeTable.get(policy) == null) {
            // The table for this policy is empty, which means that this policy has not been executed yet.
            evaluateDynamicReconfigurationInSequentialMode(policy);

            if (EXPERIMENTAL_MULTI_HOST_HEAP) {
                restoreVarsIntoJavaHeap(policy, numAccelerators);
            }
        } else {
            // Run with the winner device
            int deviceWinnerIndex = policyTimeTable.get(policy);
            if (deviceWinnerIndex == numAccelerators) {
                // if the winner is the last index => it is the sequential (Hotspot)
                runSequential();
            } else {
                // Otherwise, it runs the parallel in the corresponding device
                runTaskGraphParallelSelected(deviceWinnerIndex);
            }
        }
        return this;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void addInner(int type, Method method, ScheduleContext meta, String id, Object[] parameters) {
        switch (type) {
            case 0 -> addInner(TaskUtils.createTask(method, meta, id, (Task) parameters[0]));
            case 1 -> addInner(TaskUtils.createTask(method, meta, id, (Task1) parameters[0], parameters[1]));
            case 2 -> addInner(TaskUtils.createTask(method, meta, id, (Task2) parameters[0], parameters[1], parameters[2]));
            case 3 -> addInner(TaskUtils.createTask(method, meta, id, (Task3) parameters[0], parameters[1], parameters[2], parameters[3]));
            case 4 -> addInner(TaskUtils.createTask(method, meta, id, (Task4) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4]));
            case 5 -> addInner(TaskUtils.createTask(method, meta, id, (Task5) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5]));
            case 6 -> addInner(TaskUtils.createTask(method, meta, id, (Task6) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6]));
            case 7 -> addInner(TaskUtils.createTask(method, meta, id, (Task7) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7]));
            case 8 -> addInner(TaskUtils.createTask(method, meta, id, (Task8) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                    parameters[8]));
            case 9 -> addInner(TaskUtils.createTask(method, meta, id, (Task9) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                    parameters[8], parameters[9]));
            case 10 -> addInner(TaskUtils.createTask(method, meta, id, (Task10) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                    parameters[8], parameters[9], parameters[10]));
            case 11 -> addInner(TaskUtils.createTask(method, meta, id, (Task11) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                    parameters[8], parameters[9], parameters[10], parameters[11]));
            case 12 -> addInner(TaskUtils.createTask(method, meta, id, (Task12) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                    parameters[8], parameters[9], parameters[10], parameters[11], parameters[12]));
            case 13 -> addInner(TaskUtils.createTask(method, meta, id, (Task13) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                    parameters[8], parameters[9], parameters[10], parameters[11], parameters[12], parameters[13]));
            case 14 -> addInner(TaskUtils.createTask(method, meta, id, (Task14) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                    parameters[8], parameters[9], parameters[10], parameters[11], parameters[12], parameters[13], parameters[14]));
            case 15 -> addInner(TaskUtils.createTask(method, meta, id, (Task15) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                    parameters[8], parameters[9], parameters[10], parameters[11], parameters[12], parameters[13], parameters[14], parameters[15]));
            default -> throw new TornadoRuntimeException("Task not supported yet. Type: " + type);
        }
    }

    @Override
    public void addTask(TaskPackage taskPackage) {
        taskPackages.add(taskPackage);
        String id = taskPackage.getId();
        int type = taskPackage.getTaskType();
        Object[] parameters = taskPackage.getTaskParameters();

        Method method = TaskUtils.resolveMethodHandle(parameters[0]);
        ScheduleContext meta = meta();

        // Set the number of threads to run. If 0, it will execute as many
        // threads as input size (after Tornado analyses the right block-size).
        // Otherwise, we force the number of threads to run. This is the case,
        // for example, when executing reductions in which the input size is not
        // power of two.
        meta.setNumThreads(taskPackage.getNumThreadsToRun());

        try {
            addInner(type, method, meta, id, parameters);
        } catch (TornadoBailoutRuntimeException e) {
            this.bailout = true;
            if (!DEBUG) {
                System.out.println(WARNING_DEOPT_MESSAGE);
            }
            throw e; // Rethrow the same exception
        }
    }

    @Override
    public void addPrebuiltTask(TaskPackage taskPackage) {
        taskPackages.add(taskPackage);
        PrebuiltTaskPackage prebuiltTaskPackage = (PrebuiltTaskPackage) taskPackage;
        addInner(TaskUtils.createTask(meta(), prebuiltTaskPackage));
    }

    @Override
    public void withBatch(String batchSize) {
        this.batchSizeBytes = parseSizeToBytes(batchSize);
        executionContext.setBatchSize(this.batchSizeBytes);
    }

    @Override
    public void withMemoryLimit(String memoryLimit) {
        this.memoryLimitSizeBytes = parseSizeToBytes(memoryLimit);
        executionContext.setExecutionPlanMemoryLimit(this.memoryLimitSizeBytes);
    }

    @Override
    public void withoutMemoryLimit() {
        executionContext.setExecutionPlanMemoryLimit(TornadoExecutionContext.INIT_VALUE);
    }

    private long parseSizeToBytes(String sizeStr) {
        Matcher matcher = SIZE_PATTERN.matcher(sizeStr);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid size format");
        }

        long value = Long.parseLong(matcher.group(1));
        String units = matcher.group(2).toUpperCase();

        return switch (Objects.requireNonNull(units)) {
            case "MB" -> value * 1_000_000;
            case "GB" -> value * 1_000_000_000;
            default -> throw new TornadoRuntimeException("Units not supported: " + units);
        };
    }

    @Override
    public long getTotalTime() {
        return getProfilerValue(ProfilerType.TOTAL_TASK_GRAPH_TIME);
    }

    @Override
    public long getCompileTime() {
        return getProfilerValue(ProfilerType.TOTAL_GRAAL_COMPILE_TIME) + getProfilerValue(ProfilerType.TOTAL_DRIVER_COMPILE_TIME);
    }

    @Override
    public long getTornadoCompilerTime() {
        return getProfilerValue(ProfilerType.TOTAL_GRAAL_COMPILE_TIME);
    }

    @Override
    public long getDriverInstallTime() {
        return getProfilerValue(ProfilerType.TOTAL_DRIVER_COMPILE_TIME);
    }

    @Override
    public long getDataTransfersTime() {
        return getProfilerValue(ProfilerType.COPY_IN_TIME) + getProfilerValue(ProfilerType.COPY_OUT_TIME);
    }

    @Override
    public long getDeviceWriteTime() {
        return getProfilerValue(ProfilerType.COPY_IN_TIME);
    }

    @Override
    public long getDeviceReadTime() {
        return getProfilerValue(ProfilerType.COPY_OUT_TIME);
    }

    @Override
    public long getDataTransferDispatchTime() {
        return getProfilerValue(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME);
    }

    @Override
    public long getKernelDispatchTime() {
        return getProfilerValue(ProfilerType.TOTAL_DISPATCH_KERNEL_TIME);
    }

    @Override
    public long getDeviceKernelTime() {
        return getProfilerValue(TOTAL_KERNEL_TIME);
    }

    private long getProfilerValueFromReduceTaskGraph(ProfilerType profilerType) {
        return switch (profilerType) {
            case TOTAL_KERNEL_TIME -> reduceTaskGraph.getExecutionResult().getProfilerResult().getDeviceKernelTime();
            case TOTAL_DISPATCH_KERNEL_TIME -> reduceTaskGraph.getExecutionResult().getProfilerResult().getKernelDispatchTime();
            case TOTAL_DISPATCH_DATA_TRANSFERS_TIME -> reduceTaskGraph.getExecutionResult().getProfilerResult().getDataTransferDispatchTime();
            case COPY_OUT_TIME -> reduceTaskGraph.getExecutionResult().getProfilerResult().getDeviceReadTime();
            case COPY_IN_TIME -> reduceTaskGraph.getExecutionResult().getProfilerResult().getDeviceWriteTime();
            case TOTAL_DRIVER_COMPILE_TIME -> reduceTaskGraph.getExecutionResult().getProfilerResult().getDriverInstallTime();
            case TOTAL_GRAAL_COMPILE_TIME -> reduceTaskGraph.getExecutionResult().getProfilerResult().getTornadoCompilerTime();
            case TOTAL_TASK_GRAPH_TIME -> reduceTaskGraph.getExecutionResult().getProfilerResult().getTotalTime();
            case TOTAL_COPY_IN_SIZE_BYTES -> reduceTaskGraph.getExecutionResult().getProfilerResult().getTotalBytesCopyIn();
            case TOTAL_COPY_OUT_SIZE_BYTES -> reduceTaskGraph.getExecutionResult().getProfilerResult().getTotalBytesCopyOut();
            case ALLOCATION_BYTES -> reduceTaskGraph.getExecutionResult().getProfilerResult().getTotalDeviceMemoryUsage();
            default -> 0L;
        };
    }

    private long __getProfilerValue(ProfilerType profilerType) {
        return switch (profilerType) {
            case TOTAL_KERNEL_TIME -> timeProfiler.getTimer(TOTAL_KERNEL_TIME);
            case TOTAL_DISPATCH_KERNEL_TIME -> timeProfiler.getTimer(ProfilerType.TOTAL_DISPATCH_KERNEL_TIME);
            case TOTAL_DISPATCH_DATA_TRANSFERS_TIME -> timeProfiler.getTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME);
            case COPY_OUT_TIME -> timeProfiler.getTimer(ProfilerType.COPY_OUT_TIME);
            case COPY_IN_TIME -> timeProfiler.getTimer(ProfilerType.COPY_IN_TIME);
            case TOTAL_DRIVER_COMPILE_TIME -> timeProfiler.getTimer(ProfilerType.TOTAL_DRIVER_COMPILE_TIME);
            case TOTAL_GRAAL_COMPILE_TIME -> timeProfiler.getTimer(ProfilerType.TOTAL_GRAAL_COMPILE_TIME);
            case TOTAL_TASK_GRAPH_TIME -> timeProfiler.getTimer(ProfilerType.TOTAL_TASK_GRAPH_TIME);
            case TOTAL_COPY_IN_SIZE_BYTES -> timeProfiler.getSize(ProfilerType.TOTAL_COPY_IN_SIZE_BYTES);
            case TOTAL_COPY_OUT_SIZE_BYTES -> timeProfiler.getSize(ProfilerType.TOTAL_COPY_OUT_SIZE_BYTES);
            case ALLOCATION_BYTES -> timeProfiler.getSize(ProfilerType.ALLOCATION_BYTES);
            default -> 0L;
        };
    }

    private long getProfilerValue(ProfilerType profilerType) {
        if (reduceTaskGraph != null) {
            return getProfilerValueFromReduceTaskGraph(profilerType);
        } else {
            return __getProfilerValue(profilerType);
        }
    }

    @Override
    public String getProfileLog() {
        return bufferLogProfiler.toString();
    }

    @Override
    public long getTotalBytesCopyIn() {
        return getProfilerValue(TOTAL_COPY_IN_SIZE_BYTES);
    }

    @Override
    public long getTotalBytesCopyOut() {
        return getProfilerValue(TOTAL_COPY_OUT_SIZE_BYTES);
    }

    boolean isProfilerEnabled() {
        return (getProfilerMode() != null || TornadoOptions.isProfilerEnabled());
    }

    ProfilerMode getProfilerMode() {
        return profilerMode;
    }

    public record PairWinnerThread(int threadWinnerIndex, Thread thread) {
    }

    // Timer implementation within the Task Schedule
    private interface Timer {
        long time();
    }

    private static class MilliSecTimer implements Timer {
        @Override
        public long time() {
            return System.currentTimeMillis();
        }
    }

    private static class NanoSecTimer implements Timer {
        @Override
        public long time() {
            return System.nanoTime();
        }
    }

    private record CompileInfo(boolean compile, boolean updateDevice) {
    }

    private static final CompileInfo COMPILE_ONLY = new CompileInfo(true, false);
    private static final CompileInfo COMPILE_AND_UPDATE = new CompileInfo(true, true);
    private static final CompileInfo NOT_COMPILE_UPDATE = new CompileInfo(false, false);

    /**
     * Class that keeps the history of executions based on their data sizes. It has a sorted map
     * (TreeMap) that keeps the relationship between the input size and the actual Tornado device
     * in which the task was executed based on the profiler for the dynamic reconfiguration.
     */
    private static class HistoryTable {

        /**
         * TreeMap between input size -> device index.
         */
        private final TreeMap<Integer, Integer> table = new TreeMap<>();

        private TreeMap<Integer, Integer> getTree() {
            return table;
        }
    }
}
