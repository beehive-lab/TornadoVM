/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
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
 */
package uk.ac.manchester.tornado.runtime.tasks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.nodes.StructuredGraph;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.analyzer.CodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.MetaReduceCodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.MetaReduceTasks;
import uk.ac.manchester.tornado.runtime.analyzer.ReduceCodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.ReduceCodeAnalysis.REDUCE_OPERATION;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.MetaDataUtils;

class ReduceTaskSchedule {

    private static final String SEQUENTIAL_TASK_REDUCE_NAME = "reduce-seq";

    private static final String TASK_SCHEDULE_PREFIX = "XXX__GENERATED_REDUCE";
    private static final int DEFAULT_GPU_WORK_GROUP = 256;
    private static final int DEFAULT_DRIVER_INDEX = 0;
    private static AtomicInteger counterName = new AtomicInteger(0);
    private static AtomicInteger counterSeqName = new AtomicInteger(0);

    private String idTaskSchedule;
    private ArrayList<TaskPackage> taskPackages;
    private ArrayList<Object> streamOutObjects;
    private ArrayList<Object> streamInObjects;
    private HashMap<Object, Object> originalReduceVariables;
    private ArrayList<Thread> threadSequentialExecution;
    private HashMap<Object, Object> neutralElementsNew = new HashMap<>();
    private HashMap<Object, Object> neutralElementsOriginal = new HashMap<>();
    private TaskSchedule rewrittenTaskSchedule;
    private HashMap<Object, LinkedList<Integer>> reduceOperandTable;

    ReduceTaskSchedule(String taskScheduleID, ArrayList<TaskPackage> taskPackages, ArrayList<Object> streamInObjects, ArrayList<Object> streamOutObjects) {
        this.taskPackages = taskPackages;
        this.idTaskSchedule = taskScheduleID;
        this.streamInObjects = streamInObjects;
        this.streamOutObjects = streamOutObjects;
    }

    private void inspectBinariesFPGA(String taskScheduleName, String tsName, String taskName, boolean sequential) {
        String idTaskName = tsName + "." + taskName;
        StringBuffer originalBinaries = TornadoOptions.FPGA_BINARIES;
        if (originalBinaries != null) {
            String[] binaries = originalBinaries.toString().split(",");
            if (binaries.length == 1) {
                binaries = MetaDataUtils.processPrecompiledBinariesFromFile(binaries[0]);
                StringBuilder sb = new StringBuilder();
                for (String binary : binaries) {
                    sb.append(binary.replaceAll(" ", "")).append(",");
                }
                sb = sb.deleteCharAt(sb.length() - 1);
                originalBinaries = new StringBuffer(sb.toString());
            }

            for (int i = 0; i < binaries.length; i += 2) {
                String givenTaskName = binaries[i + 1].split(".device")[0];
                if (givenTaskName.equals(idTaskName)) {
                    int[] info = MetaDataUtils.resolveDriverDeviceIndexes(MetaDataUtils.getProperty(idTaskName + ".device"));
                    int deviceNumber = info[1];

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

    private int changeDeviceIfNeeded(String taskScheduleName, String tsName, String taskName) {
        String idTaskName = tsName + "." + taskName;
        boolean isDeviceDefined = MetaDataUtils.getProperty(idTaskName + ".device") != null;
        if (isDeviceDefined) {
            int[] info = MetaDataUtils.resolveDriverDeviceIndexes(MetaDataUtils.getProperty(idTaskName + ".device"));
            int deviceNumber = info[1];
            TornadoRuntime.setProperty(taskScheduleName + "." + taskName + ".device", "0:" + deviceNumber);
            return deviceNumber;
        }
        return 0;
    }

    private void fillOutputArrayWithNeutral(Object reduceArray, Object neutral) {
        if (reduceArray instanceof int[]) {
            Arrays.fill((int[]) reduceArray, (int) neutral);
        } else if (reduceArray instanceof float[]) {
            Arrays.fill((float[]) reduceArray, (float) neutral);
        } else if (reduceArray instanceof double[]) {
            Arrays.fill((double[]) reduceArray, (double) neutral);
        } else if (reduceArray instanceof long[]) {
            Arrays.fill((long[]) reduceArray, (long) neutral);
        } else {
            throw new TornadoRuntimeException("[ERROR] reduce type not supported yet: " + reduceArray.getClass());
        }
    }

    private Object createNewReduceArray(Object reduceVariable, int size) {

        if (size == 1) {
            return reduceVariable;
        }

        Object newArray;
        if (reduceVariable instanceof int[]) {
            newArray = new int[size];
        } else if (reduceVariable instanceof float[]) {
            newArray = new float[size];
        } else if (reduceVariable instanceof double[]) {
            newArray = new double[size];
        } else if (reduceVariable instanceof long[]) {
            newArray = new long[size];
        } else {
            throw new TornadoRuntimeException("[ERROR] reduce type not supported yet: " + reduceVariable.getClass());
        }
        return newArray;
    }

    private Object getNeutralElement(Object originalArray) {
        if (originalArray instanceof int[]) {
            return ((int[]) originalArray)[0];
        } else if (originalArray instanceof float[]) {
            return ((float[]) originalArray)[0];
        } else if (originalArray instanceof double[]) {
            return ((double[]) originalArray)[0];
        } else if (originalArray instanceof long[]) {
            return ((long[]) originalArray)[0];
        } else {
            throw new TornadoRuntimeException("[ERROR] reduce type not supported yet: " + originalArray.getClass());
        }
    }

    private boolean isPowerOfTwo(final long number) {
        return ((number & (number - 1)) == 0);
    }

    /**
     * It runs a compiled method by Graal in HotSpot.
     * 
     * @param taskPackage
     *            {@link TaskPackage} metadata that stores the method parameters.
     * @param code
     *            {@link InstalledCode} code to be executed
     */
    private static void runBinaryCodeForReduction(TaskPackage taskPackage, InstalledCode code) {
        try {
            // Execute the generated binary with Graal with
            // the host loop-bound

            // 1. Set arguments to the method-compiled code
            int numArgs = taskPackage.getTaskParameters().length - 1;
            Object[] args = new Object[numArgs];
            for (int i = 0; i < numArgs; i++) {
                args[i] = taskPackage.getTaskParameters()[i + 1];
            }

            // 2. Run the binary
            code.executeVarargs(args);

            // 3. The result is returned in the corresponding object from the
            // parameter list
        } catch (InvalidInstalledCodeException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns true if the input size is not power of 2 and the target device is
     * either the GPU or the FPGA.
     *
     * @param targetDeviceToRun
     *            index of the target device within the Tornado device list.
     * @return boolean
     */
    private boolean isTaskEligibleSplitHostAndDevice(final int targetDeviceToRun, final long elementsReductionLeftOver, final boolean isPowerOfTwo) {
        if (!isPowerOfTwo && elementsReductionLeftOver > 0) {
            TornadoDeviceType deviceType = TornadoCoreRuntime.getTornadoRuntime().getDriver(0).getDevice(targetDeviceToRun).getDeviceType();
            return deviceType == TornadoDeviceType.GPU || deviceType == TornadoDeviceType.FPGA || deviceType == TornadoDeviceType.ACCELERATOR;
        }
        return false;
    }

    private void runHostThreads() {
        if (threadSequentialExecution != null && !threadSequentialExecution.isEmpty()) {
            for (Thread t : threadSequentialExecution) {
                t.run();
            }
            for (Thread t : threadSequentialExecution) {
                try {
                    t.join();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }

    private static class CompilationThread extends Thread {

        private Object codeTask;
        private final long sizeTargetDevice;
        private InstalledCode code;

        CompilationThread(Object codeTask, final long sizeTargetDevice) {
            this.codeTask = codeTask;
            this.sizeTargetDevice = sizeTargetDevice;
        }

        public InstalledCode getCode() {
            return this.code;
        }

        @Override
        public void run() {
            StructuredGraph originalGraph = CodeAnalysis.buildHighLevelGraalGraph(codeTask);
            assert originalGraph != null;
            StructuredGraph graph = (StructuredGraph) originalGraph.copy();
            ReduceCodeAnalysis.performLoopBoundNodeSubstitution(graph, sizeTargetDevice);
            code = CodeAnalysis.compileAndInstallMethod(graph);
        }
    }

    private static class SequentialExecutionThread extends Thread {

        final CompilationThread compilationThread;
        private TaskPackage taskPackage;

        SequentialExecutionThread(CompilationThread c, TaskPackage taskPackage) {
            this.compilationThread = c;
            this.taskPackage = taskPackage;
        }

        @Override
        public void run() {
            try {
                compilationThread.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
            runBinaryCodeForReduction(taskPackage, compilationThread.getCode());
        }
    }

    private void createThreads(Object codeTask, final TaskPackage taskPackage, final long sizeTargetDevice) {
        if (threadSequentialExecution == null) {
            threadSequentialExecution = new ArrayList<>();
        }

        CompilationThread compilationThread = new CompilationThread(codeTask, sizeTargetDevice);
        compilationThread.start();
        threadSequentialExecution.add(new SequentialExecutionThread(compilationThread, taskPackage));

        // We change the amount of threads to run on the device-side
        taskPackage.setNumThreadsToRun(sizeTargetDevice);
    }

    private void updateStreamInOutVariables(HashMap<Integer, MetaReduceTasks> tableReduce) {
        // Update Stream IN and Stream OUT
        for (int taskNumber = 0; taskNumber < taskPackages.size(); taskNumber++) {

            // Update streamIn if needed (substitute if output appears as
            // stream-in with the new created array).
            for (int i = 0; i < streamInObjects.size(); i++) {

                // Update table that consistency between input variables and reduce tasks.
                // This part is used to STREAM_IN data when performing multiple reductions in
                // the same task-schedule
                if (tableReduce.containsKey(taskNumber)) {
                    if (!reduceOperandTable.containsKey(streamInObjects.get(i))) {
                        LinkedList<Integer> taskList = new LinkedList<>();
                        taskList.add(taskNumber);
                        reduceOperandTable.put(streamInObjects.get(i), taskList);
                    } else {
                        reduceOperandTable.get(streamInObjects.get(i)).add(taskNumber);
                    }
                }

                if (originalReduceVariables.containsKey(streamInObjects.get(i))) {
                    streamInObjects.set(i, originalReduceVariables.get(streamInObjects.get(i)));
                }
            }

            // Add the rest of the variables
            for (Entry<Object, Object> pair : originalReduceVariables.entrySet()) {
                streamInObjects.add(pair.getValue());
            }

            TornadoTaskSchedule.performStreamInThread(rewrittenTaskSchedule, streamInObjects);

            for (int i = 0; i < streamOutObjects.size(); i++) {
                if (originalReduceVariables.containsKey(streamOutObjects.get(i))) {
                    Object newArray = originalReduceVariables.get(streamOutObjects.get(i));
                    streamOutObjects.set(i, newArray);
                }
            }
        }
    }

    private void updateGlobalAndLocalDimensionsFPGA(final int deviceToRun, String taskScheduleReduceName, TaskPackage taskPackage, int inputSize) {
        // Update GLOBAL and LOCAL Dims if device to run is the FPGA
        TornadoDeviceType deviceType = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(deviceToRun).getDeviceType();
        if (deviceType == TornadoDeviceType.ACCELERATOR) {
            TornadoRuntime.setProperty(taskScheduleReduceName + "." + taskPackage.getId() + ".global.dims", Integer.toString(inputSize));
            TornadoRuntime.setProperty(taskScheduleReduceName + "." + taskPackage.getId() + ".local.dims", "64");
        }
    }

    /**
     * Compose and execute the new reduction. It dynamically creates a new
     * task-schedule expression that contains: a) the parallel reduction; b) the
     * final sequential reduction.
     *
     * It also creates a new thread in the case the input size for the reduction is
     * not power of two and the target device is either the FPGA or the GPU. In this
     * case, the new thread will compile the host part with the corresponding
     * sub-range that does not fit into the power-of-two part.
     *
     * @param metaReduceTable
     *            Metadata to create all new tasks for the reductions dynamically.
     * @return {@link TaskSchedule} with the new reduction
     */
    TaskSchedule scheduleWithReduction(MetaReduceCodeAnalysis metaReduceTable) {

        assert metaReduceTable != null;

        HashMap<Integer, MetaReduceTasks> tableReduce = metaReduceTable.getTable();

        String taskScheduleReduceName = TASK_SCHEDULE_PREFIX + counterName.get();
        String tsName = idTaskSchedule;

        HashMap<Integer, ArrayList<Object>> streamReduceTable = new HashMap<>();
        ArrayList<Integer> sizesReductionArray = new ArrayList<>();
        if (originalReduceVariables == null) {
            originalReduceVariables = new HashMap<>();
        }

        if (reduceOperandTable == null) {
            reduceOperandTable = new HashMap<>();
        }

        int deviceToRun = 0;

        // Create new buffer variables and update the corresponding streamIn and
        // streamOut
        for (int taskNumber = 0; taskNumber < taskPackages.size(); taskNumber++) {

            ArrayList<Integer> listOfReduceIndexParameters;
            MetaReduceTasks metaReduceTasks;
            TaskPackage taskPackage = taskPackages.get(taskNumber);

            ArrayList<Object> streamReduceList = new ArrayList<>();

            deviceToRun = changeDeviceIfNeeded(taskScheduleReduceName, tsName, taskPackage.getId());
            final int targetDeviceToRun = deviceToRun;
            inspectBinariesFPGA(taskScheduleReduceName, tsName, taskPackage.getId(), false);

            if (tableReduce.containsKey(taskNumber)) {

                metaReduceTasks = tableReduce.get(taskNumber);
                listOfReduceIndexParameters = metaReduceTasks.getListOfReduceParameters(taskNumber);

                for (Integer paramIndex : listOfReduceIndexParameters) {

                    Object originalReduceArray = taskPackage.getTaskParameters()[paramIndex + 1];

                    // If the array has been already created, we don't have to create another one,
                    // just obtain the already created reference from the cache-table.
                    if (originalReduceVariables.containsKey(originalReduceArray)) {
                        continue;
                    }

                    int inputSize = metaReduceTasks.getInputSize(taskNumber);

                    updateGlobalAndLocalDimensionsFPGA(targetDeviceToRun, taskScheduleReduceName, taskPackage, inputSize);

                    // Analyse Input Size - if not power of 2 -> split host and
                    // device executions
                    boolean isInputPowerOfTwo = isPowerOfTwo(inputSize);
                    if (!isInputPowerOfTwo) {
                        int exp = (int) (Math.log(inputSize) / Math.log(2));
                        double closestPowerOf2 = Math.pow(2, exp);
                        long elementsReductionLeftOver = (long) (inputSize - closestPowerOf2);
                        inputSize -= elementsReductionLeftOver;

                        final int sizeTargetDevice = inputSize;
                        if (isTaskEligibleSplitHostAndDevice(targetDeviceToRun, elementsReductionLeftOver, false)) {
                            Object codeTask = taskPackage.getTaskParameters()[0];
                            createThreads(codeTask, taskPackage, sizeTargetDevice);
                        }
                    }

                    // Set the new array size
                    int sizeReductionArray = obtainSizeArrayResult(DEFAULT_DRIVER_INDEX, deviceToRun, inputSize);
                    Object newArray = createNewReduceArray(originalReduceArray, sizeReductionArray);
                    Object neutralElement = getNeutralElement(originalReduceArray);
                    fillOutputArrayWithNeutral(newArray, neutralElement);

                    neutralElementsNew.put(newArray, neutralElement);
                    neutralElementsOriginal.put(originalReduceArray, neutralElement);

                    taskPackage.getTaskParameters()[paramIndex + 1] = newArray;

                    // Store metadata
                    streamReduceList.add(newArray);
                    sizesReductionArray.add(sizeReductionArray);
                    originalReduceVariables.put(originalReduceArray, newArray);
                }
                streamReduceTable.put(taskNumber, streamReduceList);
            }
        }

        rewrittenTaskSchedule = new TaskSchedule(taskScheduleReduceName);
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
                    if (reduceOperandTable.containsKey(parameterToMethod)) {
                        if (reduceOperandTable.get(parameterToMethod).size() > 1) {
                            rewrittenTaskSchedule.forceCopyIn(parameterToMethod);
                        }
                    }
                }
            }

            rewrittenTaskSchedule.addTask(taskPackages.get(taskNumber));

            // Add extra task with the final reduction
            if (tableReduce.containsKey(taskNumber)) {

                MetaReduceTasks metaReduceTasks = tableReduce.get(taskNumber);
                ArrayList<Integer> listOfReduceParameters = metaReduceTasks.getListOfReduceParameters(taskNumber);
                StructuredGraph graph = metaReduceTasks.getGraph();
                ArrayList<REDUCE_OPERATION> operations = ReduceCodeAnalysis.getReduceOperation(graph, listOfReduceParameters);

                ArrayList<Object> streamUpdateList = streamReduceTable.get(taskNumber);

                for (int i = 0; i < streamUpdateList.size(); i++) {
                    Object newArray = streamUpdateList.get(i);
                    int sizeReduceArray = sizesReductionArray.get(i);
                    for (REDUCE_OPERATION operation : operations) {
                        final String newTaskSequentialName = SEQUENTIAL_TASK_REDUCE_NAME + counterSeqName.get();
                        String fullName = rewrittenTaskSchedule.getTaskScheduleName() + "." + newTaskSequentialName;
                        TornadoRuntime.setProperty(fullName + ".device", "0:" + deviceToRun);
                        inspectBinariesFPGA(taskScheduleReduceName, tsName, taskPackage.getId(), true);

                        switch (operation) {
                            case ADD:
                                ReduceFactory.handleAdd(newArray, rewrittenTaskSchedule, sizeReduceArray, newTaskSequentialName);
                                break;
                            case MUL:
                                ReduceFactory.handleMul(newArray, rewrittenTaskSchedule, sizeReduceArray, newTaskSequentialName);
                                break;
                            case MAX:
                                ReduceFactory.handleMax(newArray, rewrittenTaskSchedule, sizeReduceArray, newTaskSequentialName);
                                break;
                            case MIN:
                                ReduceFactory.handleMin(newArray, rewrittenTaskSchedule, sizeReduceArray, newTaskSequentialName);
                                break;
                            default:
                                throw new TornadoRuntimeException("[ERROR] Reduce operation not supported yet.");
                        }
                        counterSeqName.incrementAndGet();
                    }
                }
            }
        }
        TornadoTaskSchedule.performStreamOutThreads(rewrittenTaskSchedule, streamOutObjects);
        executeExpression();
        counterName.incrementAndGet();
        return rewrittenTaskSchedule;
    }

    void executeExpression() {
        setNeutralElement();
        rewrittenTaskSchedule.execute();
        updateOutputArray();
    }

    private void setNeutralElement() {
        for (Entry<Object, Object> pair : neutralElementsNew.entrySet()) {
            Object newArray = pair.getKey();
            Object neutralElement = pair.getValue();
            fillOutputArrayWithNeutral(newArray, neutralElement);
        }

        for (Entry<Object, Object> pair : neutralElementsOriginal.entrySet()) {
            Object newArray = pair.getKey();
            Object neutralElement = pair.getValue();
            fillOutputArrayWithNeutral(newArray, neutralElement);
        }
    }

    /**
     * Copy out the result back to the original buffer.
     */
    private void updateOutputArray() {

        if (getHostThreadReduction() != null) {
            runHostThreads();
        }

        for (Entry<Object, Object> pair : originalReduceVariables.entrySet()) {
            Object originalReduceVariable = pair.getKey();
            Object newArray = pair.getValue();
            switch (newArray.getClass().getTypeName()) {
                case "int[]":
                    ((int[]) originalReduceVariable)[0] = ((int[]) newArray)[0];
                    break;
                case "float[]":
                    ((float[]) originalReduceVariable)[0] = ((float[]) newArray)[0];
                    break;
                case "double[]":
                    ((double[]) originalReduceVariable)[0] = ((double[]) newArray)[0];
                    break;
                case "long[]":
                    ((long[]) originalReduceVariable)[0] = ((long[]) newArray)[0];
                    break;
                default:
                    throw new TornadoRuntimeException("[ERROR] Reduce data type not supported yet: " + newArray.getClass().getTypeName());
            }
        }
    }

    /**
     * 
     * @param driverIndex
     *            Index within the Tornado drivers' index
     * @param device
     *            Index of the device within the Tornado's device list.
     * @param inputSize
     *            Input size
     * @return Output array size
     */
    private static int obtainSizeArrayResult(int driverIndex, int device, int inputSize) {
        TornadoDeviceType deviceType = TornadoCoreRuntime.getTornadoRuntime().getDriver(driverIndex).getDevice(device).getDeviceType();
        switch (deviceType) {
            case CPU:
                return Runtime.getRuntime().availableProcessors() + 1;
            case GPU:
            case ACCELERATOR:
                return inputSize > DEFAULT_GPU_WORK_GROUP ? (inputSize / DEFAULT_GPU_WORK_GROUP) + 1 : 2;
            default:
                break;
        }
        return 0;
    }

    private ArrayList<Thread> getHostThreadReduction() {
        return this.threadSequentialExecution;
    }
}
