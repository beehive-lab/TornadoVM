/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.nodes.StructuredGraph;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoTaskRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.analyzer.CodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.MetaReduceCodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.MetaReduceTasks;
import uk.ac.manchester.tornado.runtime.analyzer.ReduceCodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.ReduceCodeAnalysis.REDUCE_OPERATION;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.MetaDataUtils;
import uk.ac.manchester.tornado.runtime.tasks.meta.MetaDataUtils.BackendSelectionContainer;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

class ReduceTaskGraph {

    private static final String EXCEPTION_MESSAGE_ERROR = "[ERROR] reduce type not supported yet: ";
    private static final String OPERATION_NOT_SUPPORTED_MESSAGE = "Operation not supported";
    private static final String SEQUENTIAL_TASK_REDUCE_NAME = "reduce_seq";

    private static final int DEFAULT_GPU_WORK_GROUP = 256;
    private static final int DEFAULT_BACKEND_INDEX = 0;
    private static final int DEFAULT_DEVICE_INDEX = 0;
    private static AtomicInteger counterName = new AtomicInteger(0);
    private static AtomicInteger counterSeqName = new AtomicInteger(0);
    private final List<StreamingObject> inputModeObjects;
    private final List<StreamingObject> outputModeObjects;
    private final TornadoTaskGraph originalTaskGraph;
    private final Graph sketchGraph;
    private String idTaskGraph;
    private List<TaskPackage> taskPackages;
    private List<Object> streamOutObjects;
    private List<Object> streamInObjects;
    private Map<Object, Object> originalReduceVariables;
    private Map<Object, Object> hostHybridVariables;
    private List<Thread> threadSequentialExecution;
    private List<HybridThreadMeta> hybridThreadMetas;
    private Map<Object, Object> neutralElementsNew = new HashMap<>();
    private Map<Object, Object> neutralElementsOriginal = new HashMap<>();
    private TaskGraph rewrittenTaskGraph;
    private Map<Object, List<Integer>> reduceOperandTable;
    private boolean hybridMode;
    private Map<Object, REDUCE_OPERATION> hybridMergeTable;
    private boolean hybridInitialized;
    private TornadoExecutionPlan executionPlan;

    private TornadoExecutionResult executionResult;

    ReduceTaskGraph(String taskScheduleID, List<TaskPackage> taskPackages, List<Object> streamInObjects, List<StreamingObject> streamingObjects, List<Object> streamOutObjects,
            List<StreamingObject> outputModeObjects, Graph graph, TornadoTaskGraph originalTaskGraph) {
        this.idTaskGraph = taskScheduleID;
        this.sketchGraph = graph;
        this.originalTaskGraph = originalTaskGraph;

        // We need to make all lists mutable again in order to re-write the expressions
        // and the data IN/OUT the tasks. Task-Graph rewriting is the mechanism of
        // TornadoVM for performing full parallel reductions from Java sequential code.
        this.taskPackages = new ArrayList<>(taskPackages);
        this.streamInObjects = new ArrayList<>(streamInObjects);
        this.inputModeObjects = new ArrayList<>(streamingObjects);
        this.streamOutObjects = new ArrayList<>(streamOutObjects);
        this.outputModeObjects = new ArrayList<>(outputModeObjects);
    }

    /**
     * @param driverIndex
     *     Index within the Tornado drivers' index
     * @param device
     *     Index of the device within the Tornado's device list.
     * @param inputSize
     *     Input size
     * @return Output array size
     */
    private static int obtainSizeArrayResult(int driverIndex, int device, int inputSize) {
        TornadoDeviceType deviceType = TornadoCoreRuntime.getTornadoRuntime().getBackend(driverIndex).getDevice(device).getDeviceType();
        TornadoDevice deviceToRun = TornadoCoreRuntime.getTornadoRuntime().getBackend(driverIndex).getDevice(device);
        return switch (deviceType) {
            case CPU -> deviceToRun.getAvailableProcessors() + 1;
            case GPU, ACCELERATOR -> inputSize > calculateAcceleratorGroupSize(deviceToRun, inputSize) ? (inputSize / calculateAcceleratorGroupSize(deviceToRun, inputSize)) + 1 : 2;
            default -> 0;
        };
    }

    /**
     * It computes the right local work group size for GPUs/FPGAs.
     *
     * @param device
     *     Input device.
     * @param globalWorkSize
     *     Number of global threads to run.
     * @return Local Work Threads.
     */
    private static int calculateAcceleratorGroupSize(TornadoDevice device, long globalWorkSize) {

        if (device.getPlatformName().contains("AMD")) {
            return DEFAULT_GPU_WORK_GROUP;
        }

        int maxBlockSize = (int) device.getDeviceMaxWorkgroupDimensions()[0];

        if (maxBlockSize <= 0) {
            // Due to a bug on Xilinx platforms, this value can be -1. In that case, we
            // setup the block size to the default value.
            return DEFAULT_GPU_WORK_GROUP;
        }

        if (maxBlockSize == globalWorkSize) {
            maxBlockSize /= 4;
        }

        int value = (int) Math.min(maxBlockSize, globalWorkSize);
        while (globalWorkSize % value != 0) {
            value--;
        }
        return value;
    }

    private static void inspectBinariesFPGA(String taskScheduleName, String graphName, String taskName, boolean sequential) {
        String idTaskGraph = graphName + "." + taskName;
        StringBuilder originalBinaries = TornadoOptions.FPGA_BINARIES;
        if (originalBinaries != null) {
            String[] binaries = originalBinaries.toString().split(",");
            if (binaries.length == 1) {
                binaries = MetaDataUtils.processPrecompiledBinariesFromFile(binaries[0]);
                StringBuilder sb = new StringBuilder();
                for (String binary : binaries) {
                    sb.append(binary.replace(" ", "")).append(",");
                }
                sb = sb.deleteCharAt(sb.length() - 1);
                originalBinaries = new StringBuilder(sb.toString());
            }

            for (int i = 0; i < binaries.length; i += 2) {
                String givenTaskName = binaries[i + 1].split(".device")[0];
                if (givenTaskName.equals(idTaskGraph)) {
                    BackendSelectionContainer info = MetaDataUtils.resolveDriverDeviceIndexes(MetaDataUtils.getProperty(idTaskGraph + ".device"));
                    int deviceNumber = info.deviceIndex();

                    if (!sequential) {
                        originalBinaries.append("," + binaries[i] + "," + taskScheduleName + "." + taskName + ".device=0:" + deviceNumber);
                    } else {
                        originalBinaries.append("," + binaries[i] + "," + taskScheduleName + "." + SEQUENTIAL_TASK_REDUCE_NAME + counterSeqName + ".device=0:" + deviceNumber);
                    }
                }
            }
            TornadoOptions.FPGA_BINARIES = originalBinaries;
        }
    }

    private boolean isAheadOfTime() {
        return TornadoOptions.FPGA_BINARIES != null;
    }

    private BackendSelectionContainer changeDriverAndDeviceIfNeeded(String taskScheduleName, String graphName, String taskName) {
        String idTaskGraph = graphName + "." + taskName;
        boolean isDeviceDefined = MetaDataUtils.getProperty(idTaskGraph + ".device") != null;

        if (isDeviceDefined) {
            BackendSelectionContainer info = MetaDataUtils.resolveDriverDeviceIndexes(MetaDataUtils.getProperty(idTaskGraph + ".device"));
            int backendIndex = info.backendIndex();
            int deviceNumber = info.deviceIndex();
            TornadoRuntimeProvider.setProperty(taskScheduleName + "." + taskName + ".device", backendIndex + ":" + deviceNumber);
            return info;
        }
        return null;
    }

    private void fillOutputArrayWithNeutral(Object reduceArray, Object neutral) {
        switch (reduceArray) {
            case int[] ints -> Arrays.fill(ints, (int) neutral);
            case float[] floats -> Arrays.fill(floats, (float) neutral);
            case double[] doubles -> Arrays.fill(doubles, (double) neutral);
            case long[] longs -> Arrays.fill(longs, (long) neutral);
            case IntArray intArray -> intArray.init((int) neutral);
            case FloatArray floatArray -> floatArray.init((float) neutral);
            case DoubleArray doubleArray -> doubleArray.init((double) neutral);
            case LongArray longArray -> longArray.init((long) neutral);
            case null, default -> throw new TornadoRuntimeException(EXCEPTION_MESSAGE_ERROR + reduceArray.getClass());
        }
    }

    private Object createNewReduceArray(Object reduceVariable, int size) {
        if (size == 1) {
            return reduceVariable;
        }
        return switch (reduceVariable) {
            case int[] ints -> new int[size];
            case float[] floats -> new float[size];
            case double[] doubles -> new double[size];
            case long[] longs -> new long[size];
            case IntArray intArray -> new IntArray(size);
            case FloatArray floatArray -> new FloatArray(size);
            case DoubleArray doubleArray -> new DoubleArray(size);
            case LongArray longArray -> new LongArray(size);
            case null, default -> throw new TornadoRuntimeException(EXCEPTION_MESSAGE_ERROR + reduceVariable.getClass());
        };
    }

    private Object createNewReduceArray(Object reduceVariable) {
        return switch (reduceVariable) {
            case int[] ints -> new int[1];
            case float[] floats -> new float[1];
            case double[] doubles -> new double[1];
            case long[] longs -> new long[1];
            case IntArray intArray -> new IntArray(1);
            case FloatArray floatArray -> new FloatArray(1);
            case DoubleArray doubleArray -> new DoubleArray(1);
            case LongArray longArray -> new LongArray(1);
            case null, default -> throw new TornadoRuntimeException(EXCEPTION_MESSAGE_ERROR + reduceVariable.getClass());
        };
    }

    private Object getNeutralElement(Object originalArray) {
        return switch (originalArray) {
            case int[] ints -> ints[0];
            case float[] floats -> floats[0];
            case double[] doubles -> doubles[0];
            case long[] longs -> longs[0];
            case IntArray intArray -> intArray.get(0);
            case FloatArray floatArray -> floatArray.get(0);
            case DoubleArray doubleArray -> doubleArray.get(0);
            case LongArray longArray -> longArray.get(0);
            case null, default -> throw new TornadoRuntimeException(EXCEPTION_MESSAGE_ERROR + originalArray.getClass());
        };
    }

    private boolean isPowerOfTwo(final long number) {
        return ((number & (number - 1)) == 0);
    }

    /**
     * Returns true if the input size is not power of 2 and the target device is
     * either the GPU or the FPGA.
     *
     * @param targetDeviceToRun
     *     index of the target device within the Tornado device list.
     * @return boolean
     */
    private boolean isTaskEligibleSplitHostAndDevice(final int targetDeviceToRun, final long elementsReductionLeftOver) {
        if (elementsReductionLeftOver > 0) {
            TornadoDeviceType deviceType = TornadoCoreRuntime.getTornadoRuntime().getBackend(0).getDevice(targetDeviceToRun).getDeviceType();
            return (deviceType == TornadoDeviceType.GPU || deviceType == TornadoDeviceType.FPGA || deviceType == TornadoDeviceType.ACCELERATOR);
        }
        return false;
    }

    private void joinHostThreads() {
        if (threadSequentialExecution != null && !threadSequentialExecution.isEmpty()) {
            threadSequentialExecution.forEach(thread -> {
                try {
                    thread.join();
                    hybridInitialized = false;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private ReduceCompilationThread createCompilationThread(final TaskPackage taskPackage, final int sizeTargetDevice) {
        Object codeTask = taskPackage.getTaskParameters()[0];
        return new ReduceCompilationThread(codeTask, sizeTargetDevice);
    }

    private void updateStreamInOutVariables(Map<Integer, MetaReduceTasks> tableReduce) {
        // Update Stream IN and Stream OUT
        for (int taskNumber = 0; taskNumber < taskPackages.size(); taskNumber++) {

            // Update streamIn if needed (substitute if output appears as
            // stream-in with the new created array).
            for (int i = 0; i < streamInObjects.size(); i++) {

                // Update table that consistency between input variables and reduce tasks.
                // This part is used to STREAM_IN data when performing multiple reductions in
                // the same task-schedule
                if (tableReduce.containsKey(taskNumber) && (!reduceOperandTable.containsKey(streamInObjects.get(i)))) {
                    LinkedList<Integer> taskList = new LinkedList<>();
                    taskList.add(taskNumber);
                    reduceOperandTable.put(streamInObjects.get(i), taskList);
                }

                if (originalReduceVariables.containsKey(streamInObjects.get(i))) {
                    streamInObjects.set(i, originalReduceVariables.get(streamInObjects.get(i)));
                }
            }

            // Add the rest of the variables
            for (Entry<Object, Object> reduceArray : originalReduceVariables.entrySet()) {
                streamInObjects.add(reduceArray.getValue());
            }

            TornadoTaskGraph.performStreamInObject(rewrittenTaskGraph, streamInObjects, DataTransferMode.EVERY_EXECUTION);

            for (StreamingObject so : inputModeObjects) {
                if (so.getMode() == DataTransferMode.FIRST_EXECUTION) {
                    TornadoTaskGraph.performStreamInObject(rewrittenTaskGraph, so.getObject(), DataTransferMode.FIRST_EXECUTION);
                }
            }

            for (int i = 0; i < streamOutObjects.size(); i++) {
                if (originalReduceVariables.containsKey(streamOutObjects.get(i))) {
                    Object newArray = originalReduceVariables.get(streamOutObjects.get(i));
                    streamOutObjects.set(i, newArray);
                }
            }
        }
    }

    private boolean isDeviceAnAccelerator(final int deviceToRun) {
        TornadoDeviceType deviceType = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getDevice(deviceToRun).getDeviceType();
        return (deviceType == TornadoDeviceType.ACCELERATOR);
    }

    private void updateGlobalAndLocalDimensionsFPGA(final int deviceToRun, String taskScheduleReduceName, TaskPackage taskPackage, int inputSize) {
        // Update GLOBAL and LOCAL workgroup size if device to run is the FPGA
        if (isAheadOfTime() && isDeviceAnAccelerator(deviceToRun)) {
            TornadoRuntimeProvider.setProperty(taskScheduleReduceName + "." + taskPackage.getId() + TaskDataContext.GLOBAL_WORKGROUP_SUFFIX, Integer.toString(inputSize));
            TornadoRuntimeProvider.setProperty(taskScheduleReduceName + "." + taskPackage.getId() + TaskDataContext.LOCAL_WORKGROUP_SUFFIX, "64");
        }
    }

    private Object createHostArrayForHybridMode(Object originalReduceArray, TaskPackage taskPackage, int sizeTargetDevice) {
        hybridMode = true;
        if (hostHybridVariables == null) {
            hostHybridVariables = new HashMap<>();
        }
        Object hybridArray = createNewReduceArray(originalReduceArray);
        Object neutralElement = getNeutralElement(originalReduceArray);
        fillOutputArrayWithNeutral(hybridArray, neutralElement);
        taskPackage.setNumThreadsToRun(sizeTargetDevice);
        return hybridArray;
    }

    /**
     * Compose and execute the new reduction. It dynamically creates a new
     * task-schedule expression that contains: a) the parallel reduction; b) the
     * final sequential reduction.
     * <p>
     * It also creates a new thread in the case the input size for the reduction is
     * not power of two and the target device is either the FPGA or the GPU. In this
     * case, the new thread will compile the host part with the corresponding
     * sub-range that does not fit into the power-of-two part.
     *
     * @param metaReduceTable
     *     Metadata to create all new tasks for the reductions dynamically.
     * @return {@link TaskGraph} with the new reduction
     */
    TaskGraph scheduleWithReduction(MetaReduceCodeAnalysis metaReduceTable) {

        assert metaReduceTable != null;

        Map<Integer, MetaReduceTasks> tableReduce = metaReduceTable.getTable();

        String taskScheduleReduceName = TornadoTaskGraph.GENERATED_TASK_GRAPH_PREFIX + counterName.get();
        String graphName = idTaskGraph;

        HashMap<Integer, List<Object>> streamReduceTable = new HashMap<>();
        ArrayList<Integer> sizesReductionArray = new ArrayList<>();
        if (originalReduceVariables == null) {
            originalReduceVariables = new HashMap<>();
        }

        if (reduceOperandTable == null) {
            reduceOperandTable = new HashMap<>();
        }

        int backendToRun = DEFAULT_BACKEND_INDEX;
        int deviceToRun = DEFAULT_DEVICE_INDEX;

        // Create new buffer variables and update the corresponding streamIn and
        // streamOut
        for (int taskNumber = 0; taskNumber < taskPackages.size(); taskNumber++) {

            List<Integer> listOfReduceIndexParameters;
            TaskPackage taskPackage = taskPackages.get(taskNumber);

            List<Object> streamReduceList = new ArrayList<>();

            BackendSelectionContainer selectionContainer = changeDriverAndDeviceIfNeeded(taskScheduleReduceName, graphName, taskPackage.getId());
            if (selectionContainer != null) {
                backendToRun = selectionContainer.backendIndex();
                deviceToRun = selectionContainer.deviceIndex();
            }
            inspectBinariesFPGA(taskScheduleReduceName, graphName, taskPackage.getId(), false);

            if (tableReduce.containsKey(taskNumber)) {

                MetaReduceTasks metaReduceTasks = tableReduce.get(taskNumber);
                listOfReduceIndexParameters = metaReduceTasks.getListOfReduceParameters(taskNumber);

                int inputSize = 0;
                for (Integer paramIndex : listOfReduceIndexParameters) {

                    Object originalReduceArray = taskPackage.getTaskParameters()[paramIndex + 1];

                    // If the array has been already created, we don't have to create another one,
                    // just obtain the already created reference from the cache-table.
                    if (originalReduceVariables.containsKey(originalReduceArray)) {
                        continue;
                    }

                    inputSize = metaReduceTasks.getInputSize(taskNumber);

                    updateGlobalAndLocalDimensionsFPGA(deviceToRun, taskScheduleReduceName, taskPackage, inputSize);

                    // Analyse Input Size - if not power of 2 -> split host and device executions
                    boolean isInputPowerOfTwo = isPowerOfTwo(inputSize);
                    Object hostHybridModeArray = null;
                    if (!isInputPowerOfTwo) {
                        int exp = (int) (Math.log(inputSize) / Math.log(2));
                        double closestPowerOf2 = Math.pow(2, exp);
                        int elementsReductionLeftOver = (int) (inputSize - closestPowerOf2);
                        inputSize -= elementsReductionLeftOver;
                        final int sizeTargetDevice = inputSize;
                        if (isTaskEligibleSplitHostAndDevice(deviceToRun, elementsReductionLeftOver)) {
                            hostHybridModeArray = createHostArrayForHybridMode(originalReduceArray, taskPackage, sizeTargetDevice);
                        }
                    }

                    // Set the new array size
                    int sizeReductionArray = obtainSizeArrayResult(backendToRun, deviceToRun, inputSize);
                    Object newDeviceArray = createNewReduceArray(originalReduceArray, sizeReductionArray);
                    Object neutralElement = getNeutralElement(originalReduceArray);
                    fillOutputArrayWithNeutral(newDeviceArray, neutralElement);

                    neutralElementsNew.put(newDeviceArray, neutralElement);
                    neutralElementsOriginal.put(originalReduceArray, neutralElement);

                    // Store metadata
                    streamReduceList.add(newDeviceArray);
                    sizesReductionArray.add(sizeReductionArray);
                    originalReduceVariables.put(originalReduceArray, newDeviceArray);

                    if (hybridMode) {
                        hostHybridVariables.put(newDeviceArray, hostHybridModeArray);
                    }
                }

                streamReduceTable.put(taskNumber, streamReduceList);

                if (hybridMode) {
                    ReduceCompilationThread compilationThread = createCompilationThread(taskPackage, inputSize);
                    compilationThread.start();
                    if (threadSequentialExecution == null) {
                        threadSequentialExecution = new ArrayList<>();
                    }

                    HybridThreadMeta meta = new HybridThreadMeta(taskPackage, compilationThread);
                    if (hybridThreadMetas == null) {
                        hybridThreadMetas = new ArrayList<>();
                    }
                    hybridThreadMetas.add(meta);

                    SequentialExecutionThread sequentialExecutionThread = new SequentialExecutionThread(compilationThread, taskPackage, hostHybridVariables);
                    threadSequentialExecution.add(sequentialExecutionThread);
                    sequentialExecutionThread.start();
                    hybridInitialized = true;
                }
            }
        }

        rewrittenTaskGraph = new TaskGraph(taskScheduleReduceName);
        updateStreamInOutVariables(metaReduceTable.getTable());

        // Compose Task Schedule
        for (int taskNumber = 0; taskNumber < taskPackages.size(); taskNumber++) {

            TaskPackage taskPackage = taskPackages.get(taskNumber);

            // Update the reference for the new tasks if there is a data
            // dependency with the new variables created by the TornadoVM
            int taskType = taskPackages.get(taskNumber).getTaskType();
            for (int i = 0; i < taskType; i++) {
                Object key = taskPackages.get(taskNumber).getTaskParameters()[i + 1];
                if (originalReduceVariables.containsKey(key)) {
                    Object value = originalReduceVariables.get(key);
                    taskPackages.get(taskNumber).getTaskParameters()[i + 1] = value;
                }
            }

            // Analyze of we have multiple reduce tasks in the same task-schedule. In the
            // case we reuse same input data, we need to stream in the input the rest of the
            // reduce parallel tasks
            if (tableReduce.containsKey(taskNumber)) {
                // We only analyze for parallel tasks
                for (int i = 0; i < taskPackages.get(taskNumber).getTaskParameters().length - 1; i++) {
                    Object parameterToMethod = taskPackages.get(taskNumber).getTaskParameters()[i + 1];
                    if (reduceOperandTable.containsKey(parameterToMethod) && (reduceOperandTable.get(parameterToMethod).size() > 1)) {
                        rewrittenTaskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, parameterToMethod);
                    }
                }
            }

            rewrittenTaskGraph.addTask(taskPackages.get(taskNumber));

            // Add extra task with the final reduction
            if (tableReduce.containsKey(taskNumber)) {

                MetaReduceTasks metaReduceTasks = tableReduce.get(taskNumber);
                List<Integer> listOfReduceParameters = metaReduceTasks.getListOfReduceParameters(taskNumber);
                StructuredGraph graph = metaReduceTasks.getGraph();
                List<REDUCE_OPERATION> operations = ReduceCodeAnalysis.getReduceOperation(graph, listOfReduceParameters);

                if (operations.isEmpty()) {
                    // perform analysis with cached graph (after sketch phase)
                    operations = ReduceCodeAnalysis.getReduceOperatorFromSketch(sketchGraph, listOfReduceParameters);
                }

                List<Object> streamUpdateList = streamReduceTable.get(taskNumber);

                for (int i = 0; i < streamUpdateList.size(); i++) {
                    Object newArray = streamUpdateList.get(i);
                    int sizeReduceArray = sizesReductionArray.get(i);
                    for (REDUCE_OPERATION operation : operations) {
                        final String newTaskSequentialName = SEQUENTIAL_TASK_REDUCE_NAME + counterSeqName.get();
                        String fullName = rewrittenTaskGraph.getTaskGraphName() + "." + newTaskSequentialName;
                        TornadoRuntimeProvider.setProperty(fullName + ".device", backendToRun + ":" + deviceToRun);
                        inspectBinariesFPGA(taskScheduleReduceName, graphName, taskPackage.getId(), true);

                        switch (operation) {
                            case SUM -> ReduceFactory.handleAdd(newArray, rewrittenTaskGraph, sizeReduceArray, newTaskSequentialName);
                            case MUL -> ReduceFactory.handleMul(newArray, rewrittenTaskGraph, sizeReduceArray, newTaskSequentialName);
                            case MAX -> ReduceFactory.handleMax(newArray, rewrittenTaskGraph, sizeReduceArray, newTaskSequentialName);
                            case MIN -> ReduceFactory.handleMin(newArray, rewrittenTaskGraph, sizeReduceArray, newTaskSequentialName);
                            default -> throw new TornadoRuntimeException("[ERROR] Reduce operation not supported yet.");
                        }

                        if (hybridMode) {
                            if (hybridMergeTable == null) {
                                hybridMergeTable = new HashMap<>();
                            }
                            hybridMergeTable.put(newArray, operation);
                        }
                        counterSeqName.incrementAndGet();
                    }
                }
            }
        }

        // Copy-OUT Rewritten Rule for Reductions sets the outputs to EVERY_EXECUTION
        // mode.
        TornadoTaskGraph.performStreamOutThreads(DataTransferMode.EVERY_EXECUTION, rewrittenTaskGraph, streamOutObjects);
        ImmutableTaskGraph immutableTaskGraph = rewrittenTaskGraph.snapshot();
        this.executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        executeExpression();
        counterName.incrementAndGet();
        return rewrittenTaskGraph;
    }

    private boolean checkAllArgumentsPerTask() {
        for (TaskPackage task : taskPackages) {
            Object[] taskParameters = task.getTaskParameters();
            // Note: the first element in the object list is a lambda expression
            // (computation)
            for (int i = 1; i < (taskParameters.length - 1); i++) {
                Object parameter = taskParameters[i];
                if (parameter instanceof Number || parameter instanceof KernelContext) {
                    continue;
                }
                if (!rewrittenTaskGraph.getArgumentsLookup().contains(parameter)) {
                    throw new TornadoTaskRuntimeException("Parameter #" + i + " <" + parameter + "> from task <" + task
                            .getId() + "> not specified either in `transferToDevice` or `transferToHost` functions");
                }
            }
        }
        return true;
    }

    TornadoExecutionResult getExecutionResult() {
        return this.executionResult;
    }

    void executeExpression() {

        if (originalTaskGraph.isProfilerEnabled()) {
            executionPlan.withProfiler(originalTaskGraph.getProfilerMode());
        } else {
            executionPlan.withoutProfiler();
        }

        if (originalTaskGraph.meta().isPrintKernelEnabled()) {
            executionPlan.withPrintKernel();
        } else {
            executionPlan.withoutPrintKernel();
        }

        if (originalTaskGraph.meta().isThreadInfoEnabled()) {
            executionPlan.withThreadInfo();
        } else {
            executionPlan.withoutThreadInfo();
        }

        // check parameter list
        if (TornadoOptions.FORCE_CHECK_PARAMETERS) {
            checkAllArgumentsPerTask();
        }

        setNeutralElement();
        if (hybridMode && !hybridInitialized) {
            hybridInitialized = true;
            threadSequentialExecution.clear();
            for (HybridThreadMeta meta : hybridThreadMetas) {
                threadSequentialExecution.add(new SequentialExecutionThread(meta.compilationThread, meta.taskPackage, hostHybridVariables));
            }
            threadSequentialExecution.forEach(Thread::start);
        }

        executionResult = executionPlan.execute();

        updateOutputArrays();
    }

    private void setNeutralElement() {
        for (Entry<Object, Object> pair : neutralElementsNew.entrySet()) {
            Object newArray = pair.getKey();
            Object neutralElement = pair.getValue();
            fillOutputArrayWithNeutral(newArray, neutralElement);

            // Hybrid Execution
            if (hostHybridVariables != null && hostHybridVariables.containsKey(newArray)) {
                Object arrayCPU = hostHybridVariables.get(newArray);
                fillOutputArrayWithNeutral(arrayCPU, neutralElement);
            }

        }

        for (Entry<Object, Object> pair : neutralElementsOriginal.entrySet()) {
            Object originalArray = pair.getKey();
            Object neutralElement = pair.getValue();
            fillOutputArrayWithNeutral(originalArray, neutralElement);
        }
    }

    private int operateFinalReduction(int a, int b, REDUCE_OPERATION operation) {
        return switch (operation) {
            case SUM -> a + b;
            case MUL -> a * b;
            case MAX -> Math.max(a, b);
            case MIN -> Math.min(a, b);
            default -> throw new TornadoRuntimeException(OPERATION_NOT_SUPPORTED_MESSAGE);
        };
    }

    private float operateFinalReduction(float a, float b, REDUCE_OPERATION operation) {
        return switch (operation) {
            case SUM -> a + b;
            case MUL -> a * b;
            case MAX -> Math.max(a, b);
            case MIN -> Math.min(a, b);
            default -> throw new TornadoRuntimeException(OPERATION_NOT_SUPPORTED_MESSAGE);
        };
    }

    private double operateFinalReduction(double a, double b, REDUCE_OPERATION operation) {
        return switch (operation) {
            case SUM -> a + b;
            case MUL -> a * b;
            case MAX -> Math.max(a, b);
            case MIN -> Math.min(a, b);
            default -> throw new TornadoRuntimeException(OPERATION_NOT_SUPPORTED_MESSAGE);
        };
    }

    private long operateFinalReduction(long a, long b, REDUCE_OPERATION operation) {
        return switch (operation) {
            case SUM -> a + b;
            case MUL -> a * b;
            case MAX -> Math.max(a, b);
            case MIN -> Math.min(a, b);
            default -> throw new TornadoRuntimeException(OPERATION_NOT_SUPPORTED_MESSAGE);
        };
    }

    private void updateVariableFromAccelerator(Object originalReduceVariable, Object newArray) {
        switch (newArray) {
            case int[] intArray -> ((int[]) originalReduceVariable)[0] = intArray[0];
            case float[] floatArray -> ((float[]) originalReduceVariable)[0] = floatArray[0];
            case double[] doubleArray -> ((double[]) originalReduceVariable)[0] = doubleArray[0];
            case long[] longArray -> ((long[]) originalReduceVariable)[0] = longArray[0];
            case IntArray panamaIntArray -> ((IntArray) originalReduceVariable).set(0, panamaIntArray.get(0));
            case FloatArray panamaFloatArray -> ((FloatArray) originalReduceVariable).set(0, panamaFloatArray.get(0));
            case DoubleArray panamaDoubleArray -> ((DoubleArray) originalReduceVariable).set(0, panamaDoubleArray.get(0));
            case LongArray panamaLongArray -> ((LongArray) originalReduceVariable).set(0, panamaLongArray.get(0));
            default -> throw new TornadoRuntimeException("[ERROR] Reduce data type not supported yet: " + newArray.getClass().getTypeName());
        }
    }

    private void mergeHybridMode(Object originalReduceVariable, Object newArray) {
        switch (newArray) {
            case int[] intArray -> {
                int a = ((int[]) hostHybridVariables.get(intArray))[0];
                int b = intArray[0];
                ((int[]) originalReduceVariable)[0] = operateFinalReduction(a, b, hybridMergeTable.get(intArray));
            }
            case float[] floatArray -> {
                float af = ((float[]) hostHybridVariables.get(floatArray))[0];
                float bf = floatArray[0];
                ((float[]) originalReduceVariable)[0] = operateFinalReduction(af, bf, hybridMergeTable.get(floatArray));
            }
            case double[] doubleArray -> {
                double ad = ((double[]) hostHybridVariables.get(doubleArray))[0];
                double bd = doubleArray[0];
                ((double[]) originalReduceVariable)[0] = operateFinalReduction(ad, bd, hybridMergeTable.get(doubleArray));
            }
            case long[] longArray -> {
                long al = ((long[]) hostHybridVariables.get(longArray))[0];
                long bl = longArray[0];
                ((long[]) originalReduceVariable)[0] = operateFinalReduction(al, bl, hybridMergeTable.get(longArray));
            }
            case IntArray panamaIntArray -> {
                int ani = ((IntArray) hostHybridVariables.get(panamaIntArray)).get(0);
                int bni = panamaIntArray.get(0);
                ((IntArray) originalReduceVariable).set(0, operateFinalReduction(ani, bni, hybridMergeTable.get(panamaIntArray)));
            }
            case FloatArray panamaFloatArray -> {
                float anf = ((FloatArray) hostHybridVariables.get(panamaFloatArray)).get(0);
                float bnf = panamaFloatArray.get(0);
                ((FloatArray) originalReduceVariable).set(0, operateFinalReduction(anf, bnf, hybridMergeTable.get(panamaFloatArray)));
            }
            case DoubleArray panamaDoubleArray -> {
                double and = ((DoubleArray) hostHybridVariables.get(panamaDoubleArray)).get(0);
                double bnd = panamaDoubleArray.get(0);
                ((DoubleArray) originalReduceVariable).set(0, operateFinalReduction(and, bnd, hybridMergeTable.get(panamaDoubleArray)));
            }
            case LongArray panamaLongArray -> {
                long anl = ((LongArray) hostHybridVariables.get(panamaLongArray)).get(0);
                long bnl = panamaLongArray.get(0);
                ((LongArray) originalReduceVariable).set(0, operateFinalReduction(anl, bnl, hybridMergeTable.get(panamaLongArray)));
            }
            default -> throw new TornadoRuntimeException("[ERROR] Reduce data type not supported yet: " + newArray.getClass().getTypeName());
        }
    }

    /**
     * Copy out the result back to the original buffer.
     *
     * <p>
     * If the hybrid mode is enabled, it performs the final 1D reduction between the
     * two elements left (one from the accelerator and the other from the CPU)
     * </p>
     */
    private void updateOutputArrays() {
        joinHostThreads();
        for (Entry<Object, Object> pair : originalReduceVariables.entrySet()) {
            Object originalReduceVariable = pair.getKey();
            Object newArray = pair.getValue();
            if (hostHybridVariables != null && hostHybridVariables.containsKey(newArray)) {
                mergeHybridMode(originalReduceVariable, newArray);
            } else {
                updateVariableFromAccelerator(originalReduceVariable, newArray);
            }
        }
    }

    private static class ReduceCompilationThread extends Thread {
        private final int sizeTargetDevice;
        private final Object codeTask;
        private InstalledCode code;
        private boolean finished;

        ReduceCompilationThread(Object codeTask, final int sizeTargetDevice) {
            this.codeTask = codeTask;
            this.sizeTargetDevice = sizeTargetDevice;
        }

        public InstalledCode getCode() {
            return this.code;
        }

        public boolean isFinished() {
            return finished;
        }

        @Override
        public void run() {
            StructuredGraph originalGraph = CodeAnalysis.buildHighLevelGraalGraph(codeTask);
            assert originalGraph != null;
            StructuredGraph graph = (StructuredGraph) originalGraph.copy(getDebugContext());
            ReduceCodeAnalysis.performLoopBoundNodeSubstitution(graph, sizeTargetDevice);
            code = CodeAnalysis.compileAndInstallMethod(graph);
            finished = true;
        }
    }

    private static class HybridThreadMeta {
        private final TaskPackage taskPackage;
        private final ReduceCompilationThread compilationThread;

        HybridThreadMeta(TaskPackage taskPackage, ReduceCompilationThread compilationThread) {
            this.taskPackage = taskPackage;
            this.compilationThread = compilationThread;
        }
    }

    private static class SequentialExecutionThread extends Thread {

        final ReduceCompilationThread compilationThread;
        private final TaskPackage taskPackage;
        private final Map<Object, Object> hostHybridVariables;

        SequentialExecutionThread(ReduceCompilationThread compilationThread, TaskPackage taskPackage, Map<Object, Object> hostHybridVariables) {
            this.compilationThread = compilationThread;
            this.taskPackage = taskPackage;
            this.hostHybridVariables = hostHybridVariables;
        }

        /**
         * It runs a compiled method by Graal in HotSpot.
         *
         * @param taskPackage
         *     {@link TaskPackage} metadata that stores the method parameters.
         * @param code
         *     {@link InstalledCode} code to be executed
         * @param hostHybridVariables
         *     HashMap that relates the GPU buffer with the new CPU buffer.
         */
        private void runBinaryCodeForReduction(TaskPackage taskPackage, InstalledCode code, Map<Object, Object> hostHybridVariables) {
            try {
                // Execute the generated binary with Graal with the host loop-bound

                // 1. Set arguments to the method-compiled code
                int numArgs = taskPackage.getTaskParameters().length - 1;
                Object[] args = new Object[numArgs];
                for (int i = 0; i < numArgs; i++) {
                    Object argument = taskPackage.getTaskParameters()[i + 1];
                    args[i] = hostHybridVariables.getOrDefault(argument, argument);
                }

                // 2. Run the binary
                code.executeVarargs(args);

            } catch (InvalidInstalledCodeException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                // We need to wait for the compilation to be finished
                compilationThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            runBinaryCodeForReduction(taskPackage, compilationThread.getCode(), hostHybridVariables);
        }
    }
}
