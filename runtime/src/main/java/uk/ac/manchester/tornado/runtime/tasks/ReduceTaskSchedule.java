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

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getTornadoRuntime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.graalvm.compiler.nodes.StructuredGraph;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.runtime.analyzer.CodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.CodeAnalysis.REDUCE_OPERATION;
import uk.ac.manchester.tornado.runtime.analyzer.MetaReduceCodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.MetaReduceTasks;
import uk.ac.manchester.tornado.runtime.tasks.meta.MetaDataUtils;

public class ReduceTaskSchedule {

    public static final String TASK_SCHEDULE_PREFIX = "XXX__GENERATED_REDUCE";
    public static final String SEQUENTIAL_TASK_REDUCE_NAME = "reduce-seq";

    private String idTaskSchedule;
    private ArrayList<TaskPackage> taskPackages = new ArrayList<>();
    private ArrayList<Object> streamOutObjects = new ArrayList<>();
    private ArrayList<Object> streamInObjects = new ArrayList<>();
    private HashMap<Object, Object> originalReduceVariables;

    public ReduceTaskSchedule(String taskScheduleID, ArrayList<TaskPackage> taskPackages, ArrayList<Object> streamInObjects, ArrayList<Object> streamOutObjects) {
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

    private Object createNewReduceArray(Object reduceVariable, int size) {
        Object newArray = null;
        if (reduceVariable instanceof int[]) {
            newArray = new int[size];
            Arrays.fill((int[]) newArray, ((int[]) reduceVariable)[0]);
        } else if (reduceVariable instanceof float[]) {
            newArray = new float[size];
            Arrays.fill((float[]) newArray, ((float[]) reduceVariable)[0]);
        } else if (reduceVariable instanceof double[]) {
            newArray = new double[size];
            Arrays.fill((double[]) newArray, ((double[]) reduceVariable)[0]);
        } else {
            throw new TornadoRuntimeException("[ERROR] reduce type not supported yet: " + reduceVariable.getClass());
        }
        return newArray;
    }

    public TaskSchedule scheduleWithReduction(MetaReduceCodeAnalysis metaReduceTable) {

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

            ArrayList<Integer> listOfReduceParameters = null;
            MetaReduceTasks metaReduceTasks = null;
            TaskPackage taskPackage = taskPackages.get(taskNumber);

            deviceToRun = changeDeviceIfNeeded(taskScheduleReduceName, tsName, taskPackage.getId());

            if (tableReduce.containsKey(taskNumber)) {

                metaReduceTasks = tableReduce.get(taskNumber);
                listOfReduceParameters = metaReduceTasks.getListOfReduceParameters(taskNumber);

                for (Integer paramIndex : listOfReduceParameters) {

                    Object originalReduceVariable = taskPackage.getTaskParameters()[paramIndex + 1];
                    int inputSize = metaReduceTasks.getInputSize(taskNumber);
                    int sizeReduceArray = obtainSizeArrayResult(deviceToRun, inputSize);

                    // Set the new array size
                    Object newArray = createNewReduceArray(originalReduceVariable, sizeReduceArray);
                    taskPackage.getTaskParameters()[paramIndex + 1] = newArray;

                    // Store metadata
                    streamReduceUpdatedList.add(newArray);
                    sizesReductionArray.add(sizeReduceArray);
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
                ArrayList<REDUCE_OPERATION> operations = CodeAnalysis.getReduceOperation(graph, listOfReduceParameters);

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
        updateOutputArray();

        return rewrittenTaskSchedule;
    }

    public void updateOutputArray() {
        // Copy out the result back to the original buffer
        Iterator<Entry<Object, Object>> it = originalReduceVariables.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Object, Object> pair = it.next();
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
                default:
                    throw new TornadoRuntimeException("[ERROR] Reduce data type not supported yet: " + newArray.getClass().getTypeName());
            }
        }
    }

    public static int obtainSizeArrayResult(int device, int inputSize) {
        TornadoDeviceType deviceType = getTornadoRuntime().getDriver(0).getDevice(device).getDeviceType();
        switch (deviceType) {
            case CPU:
                return Runtime.getRuntime().availableProcessors() + 1;
            case GPU:
            case ACCELERATOR:
                return inputSize > 266 ? inputSize / 256 : 1;
            default:
                break;
        }
        return 0;
    }

}
