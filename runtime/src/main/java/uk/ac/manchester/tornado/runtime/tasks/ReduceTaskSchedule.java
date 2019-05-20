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
 */
package uk.ac.manchester.tornado.runtime.tasks;

import static org.graalvm.compiler.core.common.CompilationRequestIdentifier.asCompilationRequest;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getTornadoRuntime;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;

import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.util.EconomicMap;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.runtime.analyzer.CodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.MetaReduceCodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.MetaReduceTasks;
import uk.ac.manchester.tornado.runtime.analyzer.ReduceCodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.ReduceCodeAnalysis.REDUCE_OPERATION;
import uk.ac.manchester.tornado.runtime.tasks.meta.MetaDataUtils;

class ReduceTaskSchedule {

    private static final String TASK_SCHEDULE_PREFIX = "XXX__GENERATED_REDUCE";
    static final String SEQUENTIAL_TASK_REDUCE_NAME = "reduce-seq";

    private static final int DEFAULT_GPU_WORK_GROUP = 256;
    private String idTaskSchedule;
    private ArrayList<TaskPackage> taskPackages;
    private ArrayList<Object> streamOutObjects;
    private ArrayList<Object> streamInObjects;
    private HashMap<Object, Object> originalReduceVariables;
    private long elementsReductionLeftOver = 0;
    private ArrayList<Thread> threadSequentialCompilation;

    ReduceTaskSchedule(String taskScheduleID, ArrayList<TaskPackage> taskPackages, ArrayList<Object> streamInObjects, ArrayList<Object> streamOutObjects) {
        this.taskPackages = taskPackages;
        this.idTaskSchedule = taskScheduleID;
        this.streamInObjects = streamInObjects;
        this.streamOutObjects = streamOutObjects;
    }

    private int changeDeviceIfNeeded(String taskScheduleName, String tsName, String taskName) {
        String idTaskName = tsName + "." + taskName;
        boolean isDeviceDefined = MetaDataUtils.getProperty(idTaskName + ".device") != null;
        if (isDeviceDefined) {
            int[] info = MetaDataUtils.resolveDriverDeviceIndexes(MetaDataUtils.getProperty(idTaskName + ".device"));
            int taskScheduleNumber = info[1];
            TornadoRuntime.setProperty(taskScheduleName + "." + taskName + ".device", "0:" + taskScheduleNumber);
            return taskScheduleNumber;
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
        Object newArray;
        if (reduceVariable instanceof int[]) {
            newArray = new int[size];
            Arrays.fill((int[]) newArray, ((int[]) reduceVariable)[0]);
        } else if (reduceVariable instanceof float[]) {
            newArray = new float[size];
            Arrays.fill((float[]) newArray, ((float[]) reduceVariable)[0]);
        } else if (reduceVariable instanceof double[]) {
            newArray = new double[size];
            Arrays.fill((double[]) newArray, ((double[]) reduceVariable)[0]);
        } else if (reduceVariable instanceof long[]) {
            newArray = new long[size];
            Arrays.fill((long[]) newArray, ((long[]) reduceVariable)[0]);
        } else {
            throw new TornadoRuntimeException("[ERROR] reduce type not supported yet: " + reduceVariable.getClass());
        }
        return newArray;
    }

    private boolean isPowerOfTwo(final long number) {
        return ((number & (number - 1)) == 0);
    }

    /**
     * It compiles and installs the method that represents the object
     * {@code graph}.
     * 
     * @param graph
     *            Compile-graph
     * @return {@link InstalledCode}
     */
    private static InstalledCode compileAndInstallMethod(StructuredGraph graph) {
        ResolvedJavaMethod method = graph.method();
        GraalJVMCICompiler graalCompiler = (GraalJVMCICompiler) JVMCI.getRuntime().getCompiler();
        RuntimeProvider capability = graalCompiler.getGraalRuntime().getCapability(RuntimeProvider.class);
        Backend backend = capability.getHostBackend();
        Providers providers = backend.getProviders();
        CompilationIdentifier compilationID = backend.getCompilationIdentifier(method);
        EconomicMap<OptionKey<?>, Object> opts = OptionValues.newOptionMap();
        opts.putAll(HotSpotGraalOptionValues.HOTSPOT_OPTIONS.getMap());
        OptionValues options = new OptionValues(opts);
        try (Scope ignored = Debug.scope("compileMethodAndInstall", new DebugDumpScope(String.valueOf(compilationID), true))) {
            PhaseSuite<HighTierContext> graphBuilderPhase = backend.getSuites().getDefaultGraphBuilderSuite();
            Suites suites = backend.getSuites().getDefaultSuites(options);
            LIRSuites lirSuites = backend.getSuites().getDefaultLIRSuites(options);
            OptimisticOptimizations optimizationsOpts = OptimisticOptimizations.ALL;
            ProfilingInfo profilerInfo = graph.getProfilingInfo(method);
            CompilationResult compilationResult = new CompilationResult();
            CompilationResultBuilderFactory factory = CompilationResultBuilderFactory.Default;
            GraalCompiler.compileGraph(graph, method, providers, backend, graphBuilderPhase, optimizationsOpts, profilerInfo, suites, lirSuites, compilationResult, factory);
            return backend.addInstalledCode(method, asCompilationRequest(compilationID), compilationResult);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    /**
     * It performs a loop-range substitution for the lower part of the
     * reduction.
     * 
     * @param graph
     *            Input Graal {@link StructuredGraph}
     * @param lowValue
     *            Low value to include in the compile-graph
     */
    private void performLoopBoundNodeSubstitution(StructuredGraph graph, int lowValue) {
        for (Node n : graph.getNodes()) {
            if (n instanceof LoopBeginNode) {
                LoopBeginNode beginNode = (LoopBeginNode) n;
                FixedNode node = beginNode.next();
                while (!(node instanceof IfNode)) {
                    node = (FixedNode) node.successors().first();
                }

                IfNode ifNode = (IfNode) node;
                LogicNode condition = ifNode.condition();
                if (condition instanceof IntegerLessThanNode) {
                    IntegerLessThanNode integer = (IntegerLessThanNode) condition;
                    ValueNode x = integer.getX();
                    final ConstantNode low = graph.addOrUnique(ConstantNode.forInt(lowValue));
                    if (x instanceof PhiNode) {
                        // Node substitution
                        PhiNode phi = (PhiNode) x;
                        if (phi.valueAt(0) instanceof ConstantNode) {
                            phi.setValueAt(0, low);
                        }
                    }
                }
            }
        }
    }

    /**
     * It runs a compiled method by Graal in HotSpot.
     * 
     * @param taskPackage
     *            {@link TaskPackage} metadata that stores the method
     *            parameters.
     * @param code
     *            {@link InstalledCode} code to be executed
     */
    private void runBinaryCodeForReduction(TaskPackage taskPackage, InstalledCode code) {
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
    private boolean isTaskElegibleSplitHostAndDevice(final int targetDeviceToRun) {
        if (elementsReductionLeftOver > 0) {
            TornadoDeviceType deviceType = getTornadoRuntime().getDriver(0).getDevice(targetDeviceToRun).getDeviceType();
            return deviceType == TornadoDeviceType.GPU || deviceType == TornadoDeviceType.FPGA;
        }
        return false;
    }

    private void runHostThreads() {
        if (threadSequentialCompilation != null && !threadSequentialCompilation.isEmpty()) {
            for (Thread t : threadSequentialCompilation) {
                t.start();
            }
            for (Thread t : threadSequentialCompilation) {
                try {
                    t.join();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }

    /**
     * Compose and execute the new reduction. It dynamically creates a new
     * task-schedule expression that contains: a) the parallel reduction; b) the
     * final sequential reduction.
     * 
     * It also creates a new thread in the case the input size for the reduction
     * is not power of two and the target device is either the FPGA or the GPU.
     * In this case, the new thread will compile the host part with the
     * corresponding sub-range that does not fit into the power-of-two part.
     * 
     * @param metaReduceTable
     *            Metadata to create all new tasks for the reductions
     *            dynamically.
     * @return {@link TaskSchedule} with the new reduction
     */
    TaskSchedule scheduleWithReduction(MetaReduceCodeAnalysis metaReduceTable) {

        assert metaReduceTable != null;

        HashMap<Integer, MetaReduceTasks> tableReduce = metaReduceTable.getTable();

        String taskScheduleReduceName = TASK_SCHEDULE_PREFIX;
        TaskSchedule rewrittenTaskSchedule = new TaskSchedule(taskScheduleReduceName);
        String tsName = idTaskSchedule;

        ArrayList<Object> streamReduceUpdatedList = new ArrayList<>();
        ArrayList<Integer> sizesReductionArray = new ArrayList<>();
        if (originalReduceVariables == null) {
            originalReduceVariables = new HashMap<>();
        }

        int deviceToRun = 0;

        // Create new buffer variables and update the corresponding streamIn and
        // streamOut
        for (int taskNumber = 0; taskNumber < taskPackages.size(); taskNumber++) {

            ArrayList<Integer> listOfReduceIndexParameters;
            MetaReduceTasks metaReduceTasks;
            TaskPackage taskPackage = taskPackages.get(taskNumber);

            deviceToRun = changeDeviceIfNeeded(taskScheduleReduceName, tsName, taskPackage.getId());
            final int targetDeviceToRun = deviceToRun;

            ArrayList<Object> neutralElements = new ArrayList<>();

            if (tableReduce.containsKey(taskNumber)) {

                metaReduceTasks = tableReduce.get(taskNumber);
                listOfReduceIndexParameters = metaReduceTasks.getListOfReduceParameters(taskNumber);

                for (Integer paramIndex : listOfReduceIndexParameters) {

                    Object originalReduceVariable = taskPackage.getTaskParameters()[paramIndex + 1];
                    Object codeTask = taskPackage.getTaskParameters()[0];
                    int inputSize = metaReduceTasks.getInputSize(taskNumber);

                    Object neutralElement = Array.get(originalReduceVariable, 0);

                    // Analyse Input Size - if not power of 2 -> split host and
                    // device executions
                    if (!isPowerOfTwo(inputSize)) {
                        int exp = (int) (Math.log(inputSize) / Math.log(2));
                        double closestPowerOf2 = Math.pow(2, exp);
                        elementsReductionLeftOver = (long) (inputSize - closestPowerOf2);
                        inputSize -= elementsReductionLeftOver;
                    }
                    final int sizeTargetDevice = inputSize;

                    int sizeReductionArray = obtainSizeArrayResult(deviceToRun, inputSize);

                    if (isTaskElegibleSplitHostAndDevice(targetDeviceToRun)) {
                        if (threadSequentialCompilation == null) {
                            threadSequentialCompilation = new ArrayList<>();
                        }

                        threadSequentialCompilation.add(new Thread(() -> {
                            StructuredGraph originalGraph = CodeAnalysis.buildHighLevelGraalGraph(codeTask);
                            assert originalGraph != null;
                            StructuredGraph graph = (StructuredGraph) originalGraph.copy();
                            performLoopBoundNodeSubstitution(graph, sizeTargetDevice);
                            InstalledCode code = compileAndInstallMethod(graph);
                            // fillOutputArrayWithNeutral(originalReduceVariable,
                            // neutralElement);
                            runBinaryCodeForReduction(taskPackage, code);
                        }));
                        taskPackage.setNumThreadsToRun(sizeTargetDevice);
                    }

                    // Set the new array size
                    Object newArray = createNewReduceArray(originalReduceVariable, sizeReductionArray);
                    taskPackage.getTaskParameters()[paramIndex + 1] = newArray;

                    // Store metadata
                    streamReduceUpdatedList.add(newArray);
                    sizesReductionArray.add(sizeReductionArray);
                    originalReduceVariables.put(originalReduceVariable, newArray);
                }
            }
        }

        // Update Stream IN and Stream OUT
        for (int taskNumber = 0; taskNumber < taskPackages.size(); taskNumber++) {

            // Update streamIn if needed
            for (int i = 0; i < streamInObjects.size(); i++) {
                if (originalReduceVariables.containsKey(streamInObjects.get(i))) {
                    streamInObjects.set(i, originalReduceVariables.get(streamInObjects.get(i)));
                }
            }
            TornadoTaskSchedule.performStreamInThread(rewrittenTaskSchedule, streamInObjects);

            for (int i = 0; i < streamOutObjects.size(); i++) {
                if (originalReduceVariables.containsKey(streamOutObjects.get(i))) {
                    Object newArray = originalReduceVariables.get(streamOutObjects.get(i));
                    streamOutObjects.set(i, newArray);
                }
            }
        }

        // Compose Task Schedule
        for (int taskNumber = 0; taskNumber < taskPackages.size(); taskNumber++) {

            rewrittenTaskSchedule.addTask(taskPackages.get(taskNumber));

            // Ad extra task with the final reduction
            if (tableReduce.containsKey(taskNumber)) {
                MetaReduceTasks metaReduceTasks = tableReduce.get(taskNumber);
                ArrayList<Integer> listOfReduceParameters = metaReduceTasks.getListOfReduceParameters(taskNumber);
                StructuredGraph graph = metaReduceTasks.getGraph();
                ArrayList<REDUCE_OPERATION> operations = ReduceCodeAnalysis.getReduceOperation(graph, listOfReduceParameters);

                for (int i = 0; i < streamReduceUpdatedList.size(); i++) {
                    Object newArray = streamReduceUpdatedList.get(i);
                    int sizeReduceArray = sizesReductionArray.get(i);
                    for (REDUCE_OPERATION op : operations) {
                        TornadoRuntime.setProperty(taskScheduleReduceName + "." + SEQUENTIAL_TASK_REDUCE_NAME + ".device", "0:" + deviceToRun);
                        switch (op) {
                            case ADD:
                                ReduceFactory.handleAdd(newArray, rewrittenTaskSchedule, sizeReduceArray);
                                break;
                            case MUL:
                                ReduceFactory.handleMul(newArray, rewrittenTaskSchedule, sizeReduceArray);
                                break;
                            case MAX:
                                ReduceFactory.handleMax(newArray, rewrittenTaskSchedule, sizeReduceArray);
                                break;
                            case MIN:
                                ReduceFactory.handleMin(newArray, rewrittenTaskSchedule, sizeReduceArray);
                                break;
                            default:
                                throw new TornadoRuntimeException("[ERROR] Reduce operation not supported yet.");
                        }
                    }
                }
            }
        }

        TornadoTaskSchedule.performStreamOutThreads(rewrittenTaskSchedule, streamOutObjects);
        rewrittenTaskSchedule.execute();
        runHostThreads();
        updateOutputArray();
        return rewrittenTaskSchedule;
    }

    /**
     * Copy out the result back to the original buffer.
     */
    void updateOutputArray() {
        for (Entry<Object, Object> pair : originalReduceVariables.entrySet()) {
            Object reduceVariable = pair.getKey();
            Object newArray = pair.getValue();
            switch (newArray.getClass().getTypeName()) {
                case "int[]":
                    ((int[]) reduceVariable)[0] = ((int[]) newArray)[0];
                    break;
                case "float[]":
                    ((float[]) reduceVariable)[0] = ((float[]) newArray)[0];
                    break;
                case "double[]":
                    ((double[]) reduceVariable)[0] = ((double[]) newArray)[0];
                    break;
                case "long[]":
                    ((long[]) reduceVariable)[0] = ((long[]) newArray)[0];
                    break;
                default:
                    throw new TornadoRuntimeException("[ERROR] Reduce data type not supported yet: " + newArray.getClass().getTypeName());
            }
        }
    }

    /**
     * 
     * @param device
     *            Index of the device within the Tornado's device list.
     * @param inputSize
     *            Input size
     * @return int
     */
    private static int obtainSizeArrayResult(int device, int inputSize) {
        TornadoDeviceType deviceType = getTornadoRuntime().getDriver(0).getDevice(device).getDeviceType();
        switch (deviceType) {
            case CPU:
                return Runtime.getRuntime().availableProcessors() + 1;
            case GPU:
            case ACCELERATOR:
                return inputSize > DEFAULT_GPU_WORK_GROUP ? inputSize / DEFAULT_GPU_WORK_GROUP : 1;
            default:
                break;
        }
        return 0;
    }

    public ArrayList<Thread> getHostThreadReduction() {
        return this.threadSequentialCompilation;
    }
}
