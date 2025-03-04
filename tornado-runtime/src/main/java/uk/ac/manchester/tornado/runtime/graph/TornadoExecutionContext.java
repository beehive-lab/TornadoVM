/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.graph;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.api.types.collections.TornadoCollectionInterface;
import uk.ac.manchester.tornado.api.types.images.TornadoImagesInterface;
import uk.ac.manchester.tornado.api.types.matrix.TornadoMatrixInterface;
import uk.ac.manchester.tornado.api.types.vectors.TornadoVectorsInterface;
import uk.ac.manchester.tornado.api.types.volumes.TornadoVolumesInterface;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.common.XPUDeviceBufferState;
import uk.ac.manchester.tornado.runtime.common.enums.DataTypeSize;
import uk.ac.manchester.tornado.runtime.profiler.TimeProfiler;
import uk.ac.manchester.tornado.runtime.tasks.LocalObjectState;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleContext;

public class TornadoExecutionContext {

    public static int INIT_VALUE = -1;
    private final int MAX_TASKS = 256;
    private final int INITIAL_DEVICE_CAPACITY = 16;
    private final String name;
    private ScheduleContext meta;
    private KernelStackFrame[] kernelStackFrame;
    private List<SchedulableTask> tasks;
    private List<Object> constants;
    private Map<Integer, Integer> objectMap;
    private HashMap<Object, Access> objectsAccesses;
    private List<Object> objects;
    private List<Object> persistedObjects;
    private Map<String, List<Object>> persistedTaskToObjectsMap;

    private List<LocalObjectState> objectState;
    private List<TornadoXPUDevice> devices;
    private TornadoXPUDevice[] taskToDeviceMapTable;
    private int nextTask;
    private long batchSize;
    private long executionPlanMemoryLimit;
    private Set<TornadoXPUDevice> lastDevices;
    private boolean redeployOnDevice;
    private boolean defaultScheduler;
    private boolean isDataDependencyDetected;
    private TornadoProfiler profiler;
    private boolean isPrintKernel;

    private long executionPlanId;  // This is set at runtime. Thus, no need to clone this value.
    private long currentDeviceMemoryUsage;

    public TornadoExecutionContext(String id) {
        name = id;
        meta = new ScheduleContext(name);
        tasks = new ArrayList<>();
        constants = new ArrayList<>();
        objectMap = new HashMap<>();
        objects = new ArrayList<>();
        persistedObjects = new ArrayList<>();
        objectsAccesses = new HashMap<>();
        objectState = new ArrayList<>();
        persistedTaskToObjectsMap =  new HashMap<>();
        devices = new ArrayList<>(INITIAL_DEVICE_CAPACITY);
        kernelStackFrame = new KernelStackFrame[MAX_TASKS];
        taskToDeviceMapTable = new TornadoXPUDevice[MAX_TASKS];
        Arrays.fill(taskToDeviceMapTable, null);
        nextTask = 0;
        batchSize = INIT_VALUE;
        executionPlanMemoryLimit = INIT_VALUE;
        lastDevices = new HashSet<>();
        currentDeviceMemoryUsage = 0;
        this.profiler = null;
        this.isDataDependencyDetected = isDataDependencyInTaskGraph();
    }

    public KernelStackFrame[] getKernelStackFrame() {
        return kernelStackFrame;
    }

    public int insertVariable(Object parameter, Access access) {
        int index;
        if (parameter.getClass().isPrimitive() || RuntimeUtilities.isBoxedPrimitiveClass(parameter.getClass())) {
            index = constants.indexOf(parameter);
            if (index == -1) {
                index = constants.size();
                constants.add(parameter);
            }
        } else if (objectMap.containsKey(parameter.hashCode())) {
            // update access of the object if the sketcher has deducted it is READ_WRITE
            if (access.name().equals("READ_WRITE") && !objectsAccesses.get(parameter).name().equals(access.name())) {
                objectsAccesses.replace(parameter, access);
            }
            index = objectMap.get(parameter.hashCode());
        } else {
            index = objects.size();
            objects.add(parameter);
            objectsAccesses.put(parameter, access);
            objectMap.put(parameter.hashCode(), index);
            objectState.add(index, new LocalObjectState(parameter));
        }
        return index;
    }

    public long getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(long size) {
        this.batchSize = size;
    }

    public long getExecutionPlanMemoryLimit() {
        return executionPlanMemoryLimit;
    }

    public void setExecutionPlanMemoryLimit(long memoryLimitSize) {
        this.executionPlanMemoryLimit = memoryLimitSize;
    }

    public boolean isMemoryLimited() {
        return getExecutionPlanMemoryLimit() != INIT_VALUE;
    }

    public boolean doesExceedExecutionPlanLimit() {
        long totalSize = 0;

        for (Object parameter : getObjects()) {

            if (parameter.getClass().isArray()) {
                Class<?> componentType = parameter.getClass().getComponentType();
                DataTypeSize dataTypeSize = DataTypeSize.findDataTypeSize(componentType);
                if (dataTypeSize == null) {
                    throw new TornadoRuntimeException("[UNSUPPORTED] Data type not supported for processing in batches");
                }
                long size = Array.getLength(parameter);
                totalSize += (size * dataTypeSize.getSize());
            } else if (parameter instanceof TornadoNativeArray tornadoNativeArray) {
                totalSize += tornadoNativeArray.getNumBytesOfSegment();
            } else if (parameter instanceof TornadoVectorsInterface<?> tornadoVector) {
                totalSize += tornadoVector.getNumBytes();
            } else if (parameter instanceof TornadoCollectionInterface<?> collection) {
                totalSize += collection.getNumBytesWithHeader();
            } else if (parameter instanceof TornadoVolumesInterface<?> tornadoVolume) {
                totalSize += tornadoVolume.getNumBytesWithHeader();
            } else if (parameter instanceof TornadoMatrixInterface<?> tornadoMatrix) {
                totalSize += tornadoMatrix.getNumBytesWithHeader();
            } else if (parameter instanceof TornadoImagesInterface<?> tornadoImage) {
                totalSize += tornadoImage.getNumBytesWithHeader();
            } else if (parameter instanceof KernelContext || parameter instanceof AtomicInteger) {
                // ignore
            } else {
                throw new TornadoRuntimeException("Unsupported type: " + parameter.getClass());
            }
        }

        if (!constants.isEmpty()) {
            for (Object field : constants) {
                DataTypeSize dataTypeSize = DataTypeSize.findDataTypeSize(field.getClass());
                if (dataTypeSize == null) {
                    throw new TornadoRuntimeException("[UNSUPPORTED] Data type not supported for processing in batches");
                }
                totalSize += dataTypeSize.getSize();
            }
        }
        return totalSize > getExecutionPlanMemoryLimit();
    }

    public int replaceVariable(Object oldObj, Object newObj) {
        /*
         * Use the same index the oldObj was assigned. The argument indices are
         * hardcoded in the TornadoVM bytecodes during bytecode generation and must
         * match the indices in the {@link objectState} and {@link objects} arrays.
         */
        int index;
        if (oldObj.getClass().isPrimitive() || RuntimeUtilities.isBoxedPrimitiveClass(oldObj.getClass())) {
            index = constants.indexOf(oldObj);
            constants.set(index, newObj);
        } else {
            int oldIndex = objectMap.get(oldObj.hashCode());
            LocalObjectState oldLocalObjectState = objectState.remove(oldIndex);
            objectMap.remove(oldObj.hashCode());
            objects.remove(oldIndex);

            /* Copy stream-in/out information to the new local object state */
            LocalObjectState newLocalObjectState = new LocalObjectState(newObj);
            newLocalObjectState.setStreamIn(oldLocalObjectState.isStreamIn());
            newLocalObjectState.setForceStreamIn(oldLocalObjectState.isForcedStreamIn());
            newLocalObjectState.setStreamOut(oldLocalObjectState.isStreamOut());
            newLocalObjectState.setOnDevice(oldLocalObjectState.isOnDevice());

            index = oldIndex;
            objects.add(index, newObj);
            Access access = objectsAccesses.get(oldObj);
            objectsAccesses.remove(oldObj);
            objectsAccesses.put(newObj, access);
            objectMap.put(newObj.hashCode(), index);
            objectState.add(index, newLocalObjectState);
        }
        return index;
    }

    public int getTaskCount() {
        return nextTask;
    }

    public int getTaskCountAndIncrement() {
        int taskID = nextTask;
        nextTask++;
        return taskID;
    }

    public int addTask(SchedulableTask task) {
        int index = tasks.indexOf(task);
        if (index == -1) {
            index = tasks.size();
            tasks.add(task);
        }
        return index;
    }

    public void addPersistedObject(Object object) {
        if (object != null) {
            persistedObjects.add(object);
        }
    }

    public List<Object> getPersistedObjects() {
        return persistedObjects;
    }

    public void setTask(int index, SchedulableTask task) {
        tasks.set(index, task);
    }

    public List<Object> getConstants() {
        return constants;
    }

    public List<Object> getObjects() {
        return objects;
    }

    public HashMap<Object, Access> getObjectsAccesses() {
        return objectsAccesses;
    }

    public TornadoXPUDevice getDeviceForTask(int index) {
        return taskToDeviceMapTable[index];
    }

    public TornadoXPUDevice getDevice(int index) {
        return devices.get(index);
    }

    public SchedulableTask getTask(int index) {
        return tasks.get(index);
    }

    public void apply(Consumer<SchedulableTask> consumer) {
        for (SchedulableTask task : tasks) {
            consumer.accept(task);
        }
    }

    /**
     * It maps all tasks to a specific TornadoDevice.
     *
     * @param tornadoDevice
     *     The {@link TornadoDevice} to which all tasks will be mapped.
     * @throws RuntimeException
     *     if the current device is not supported.
     */
    public void mapAllTasksToSingleDevice(TornadoDevice tornadoDevice) {
        if (tornadoDevice instanceof TornadoXPUDevice tornadoAcceleratorDevice) {
            devices.clear();
            devices.addFirst(tornadoAcceleratorDevice);
            apply(task -> task.setDevice(tornadoDevice));
            Arrays.fill(taskToDeviceMapTable, tornadoDevice);
        } else {
            throw new TornadoRuntimeException("Device " + tornadoDevice.getClass() + " not supported yet");
        }
    }

    public void setDevice(TornadoXPUDevice device) {
        // If the device is not in the list of devices, add it
        if (!devices.contains(device)) {
            devices.add(device);
        }
    }

    /**
     * It assigns a task to a {@link TornadoXPUDevice} based on the current
     * task scheduling strategy.
     *
     * @param index
     *     The index of the task.
     * @param task
     *     The {@link SchedulableTask} to be assigned.
     */
    private void assignTaskToDevice(int index, SchedulableTask task) {
        String id = task.getId();
        TornadoDevice target = task.getDevice();
        TornadoXPUDevice accelerator;

        if (target instanceof TornadoXPUDevice tornadoAcceleratorDevice) {
            accelerator = tornadoAcceleratorDevice;
        } else {
            throw new TornadoRuntimeException("Device " + target.getClass() + " not supported yet");
        }

        setDevice(accelerator);

        new TornadoLogger().info("assigning %s to %s", id, target.getDeviceName());

        taskToDeviceMapTable[index] = accelerator;
    }

    public void scheduleTaskToDevices() {
        if (!isDataDependencyDetected) {
            for (int i = 0; i < tasks.size(); i++) {
                assignTaskToDevice(i, tasks.get(i));
            }
        } else {
            mapAllTasksToSingleDevice(getDeviceOfFirstTask());
        }
    }

    /**
     * It calculates the number of valid contexts. A valid context refers to a
     * context that is not null within the list of devices. This behavior is caused
     * in the ExecutionContext that does not append devices sequentially, but they
     * are placed in an order/index to preserve their original order in the driver.
     *
     * @return The number of valid contexts in the {@link TornadoExecutionContext}.
     */
    public int getValidContextSize() {
        // Count the number of null devices in the current context
        // Example of device table when using device in [2]:
        // Device Table:
        // [0]: null
        // [1]: null
        // [2]: [Intel(R) FPGA EmulationPlatform for OpenCL(TM)] -- Intel(R) FPGA
        return (int) getDevices().stream().filter(Objects::nonNull).count();
    }

    /**
     * It gets the {@link TornadoDevice} of the first task.
     *
     * @return The {@link TornadoDevice} of the first task.
     */
    public TornadoDevice getDeviceOfFirstTask() {
        return tasks.get(0).getDevice();
    }

    public LocalObjectState getLocalStateObject(Object object, Access access) {
        return objectState.get(insertVariable(object, access));
    }

    @Deprecated
    public LocalObjectState replaceObjectState(Object oldObj, Object newObj) {
        return objectState.get(replaceVariable(oldObj, newObj));
    }

    /**
     * Checks if the tasks in the list are mutually independent.
     *
     * @return {@code true} if the tasks are mutually independent, {@code false}
     *     otherwise.
     */
    private boolean isDataDependencyInTaskGraph() {
        for (int i = 0; i < tasks.size(); i++) {
            SchedulableTask task = tasks.get(i);
            for (int j = i + 1; j < tasks.size(); j++) {
                SchedulableTask otherTask = tasks.get(j);
                if (!doTasksHaveSameIDs(task, otherTask)) {
                    List<Object> commonArgs = getCommonArgumentsInTasks(task, otherTask);
                    if (commonArgs != Collections.emptyList() && (hasWriteAccess(task, otherTask))) {
                        return true;

                    }
                }
            }
        }
        return false;
    }

    private boolean doTasksHaveSameIDs(SchedulableTask task1, SchedulableTask task2) {
        return task1.getTaskName().equals(task2.getTaskName()) && task1.getId().equals(task2.getId());
    }

    private List<Object> getCommonArgumentsInTasks(SchedulableTask task1, SchedulableTask task2) {
        List<Object> commonArguments = new ArrayList<>();

        for (Object arg1 : task1.getArguments()) {
            for (Object arg2 : task2.getArguments()) {
                if (arg1.equals(arg2)) {
                    commonArguments.add(arg1);
                    break; // Found a common argument, move to the next argument in task1
                }
            }
        }

        return commonArguments;
    }

    private boolean hasWriteAccess(SchedulableTask task, SchedulableTask otherTask) {
        for (int i = 0; i < task.getArguments().length; i++) {
            if (task.getArguments()[i].equals(otherTask.getArguments()[i])) {
                Access access = task.getArgumentsAccess()[i];
                if (access == Access.WRITE_ONLY || access == Access.READ_WRITE) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<LocalObjectState> getObjectStates() {
        return objectState;
    }

    public List<SchedulableTask> getTasks() {
        return tasks;
    }

    public List<TornadoXPUDevice> getDevices() {
        return devices;
    }

    /**
     * It retrieves a deque of non-null indexes in the device table.
     *
     * @return A deque containing the indexes of non-null devices in reverse order.
     */

    public Deque<Integer> getActiveDeviceIndexes() {
        Deque<Integer> nonNullIndexes = new ArrayDeque<>();
        for (int i = devices.size() - 1; i >= 0; i--) {
            TornadoXPUDevice device = devices.get(i);
            if (device != null) {
                nonNullIndexes.push(i);
            }
        }
        return nonNullIndexes;
    }

    /**
     * It retrieves a list of tasks for a specific device and driver. Both
     * deviceContext and backendIndex are checked to ensure the correct task
     * assignment.
     *
     * @param deviceContext
     *     The device context of the device.
     * @return A list of {@link SchedulableTask} objects associated with the
     *     specified device and driver.
     */
    public List<SchedulableTask> getTasksForDevice(TornadoDeviceContext deviceContext) {
        List<SchedulableTask> tasksForDevice = new ArrayList<>();
        for (SchedulableTask task : tasks) {
            task.getDevice().getBackendIndex();
            if (task.getDevice().getDeviceContext() == deviceContext) {
                tasksForDevice.add(task);
            }
        }
        return tasksForDevice;
    }

    /**
     * Default device inspects the driver 0 and device 0 of the internal list.
     *
     * @return {@link TornadoXPUDevice}
     */
    @Deprecated
    public TornadoXPUDevice getDefaultDevice() {
        return meta.getXPUDevice();
    }

    public SchedulableTask getTask(String id) {
        for (SchedulableTask task : tasks) {
            String canonicalId = canonicalizeId(id);
            if (task.getId().equalsIgnoreCase(canonicalId)) {
                return task;
            }
        }
        return null;
    }

    private String canonicalizeId(String id) {
        return id.startsWith(getId()) ? id : getId() + "." + id;
    }

    public TornadoXPUDevice getDeviceForTask(String id) {
        TornadoDevice device = getTask(id).getDevice();
        TornadoXPUDevice tornadoDevice;
        if (device instanceof TornadoXPUDevice) {
            tornadoDevice = (TornadoXPUDevice) device;
        } else {
            throw new RuntimeException("Device " + device.getClass() + " not supported yet");
        }
        return getTask(id) == null ? null : tornadoDevice;
    }

    public String getId() {
        return name;
    }

    public ScheduleContext meta() {
        return meta;
    }

    public void sync() {
        for (int i = 0; i < objects.size(); i++) {
            Object object = objects.get(i);
            if (object != null) {
                final LocalObjectState localState = objectState.get(i);
                Event event = localState.sync(executionPlanId, object, meta().getXPUDevice());

                if (TornadoOptions.isProfilerEnabled() && event != null) {
                    long value = profiler.getTimer(ProfilerType.COPY_OUT_TIME_SYNC);
                    value += event.getElapsedTime();
                    profiler.setTimer(ProfilerType.COPY_OUT_TIME_SYNC, value);
                    XPUDeviceBufferState deviceObjectState = localState.getDataObjectState().getDeviceBufferState(meta().getXPUDevice());
                    profiler.addValueToMetric(ProfilerType.COPY_OUT_SIZE_BYTES_SYNC, TimeProfiler.NO_TASK_NAME, deviceObjectState.getXPUBuffer().size());
                }
            }
        }
    }

    public void addLastDevice(TornadoXPUDevice device) {
        lastDevices.add(device);
    }

    public Set<TornadoXPUDevice> getLastDevices() {
        return lastDevices;
    }

    public void newCallWrapper(boolean newCallWrapper) {
        this.redeployOnDevice = newCallWrapper;
    }

    public boolean redeployOnDevice() {
        return this.redeployOnDevice;
    }

    public void setDefaultThreadScheduler(boolean use) {
        defaultScheduler = use;
    }

    public boolean useDefaultThreadScheduler() {
        return defaultScheduler;
    }

    public void dumpExecutionContextMeta() {
        final String ansiReset = "\u001B[0m";
        final String ansiCyan = "\u001B[36m";
        final String ansiYellow = "\u001B[33m";
        final String ansiPurple = "\u001B[35m";
        final String ansiGreen = "\u001B[32m";
        System.out.println("-----------------------------------");
        System.out.println(ansiCyan + "Device Table:" + ansiReset);
        for (int i = 0; i < devices.size(); i++) {
            System.out.printf("[%d]: %s\n", i, devices.get(i));
        }

        System.out.println(ansiYellow + "Constant Table:" + ansiReset);
        for (int i = 0; i < constants.size(); i++) {
            System.out.printf("[%d]: %s\n", i, constants.get(i));
        }

        System.out.println(ansiPurple + "Object Table:" + ansiReset);
        for (int i = 0; i < objects.size(); i++) {
            final Object obj = objects.get(i);
            System.out.printf("[%d]: 0x%x %s\n", i, obj.hashCode(), obj);
        }

        System.out.println(ansiGreen + "Task Table:" + ansiReset);
        for (int i = 0; i < tasks.size(); i++) {
            final SchedulableTask task = tasks.get(i);
            System.out.printf("[%d]: %s\n", i, task.getFullName());
        }

        System.out.println("-----------------------------------");
    }

    public void withProfiler(TornadoProfiler timeProfiler) {
        this.profiler = timeProfiler;
    }

    @Override
    public TornadoExecutionContext clone() {
        TornadoExecutionContext newExecutionContext = new TornadoExecutionContext(this.getId());

        newExecutionContext.tasks = new ArrayList<>(tasks);

        newExecutionContext.kernelStackFrame = this.kernelStackFrame.clone();

        newExecutionContext.constants = new ArrayList<>(this.constants);

        newExecutionContext.objectMap = new HashMap<>(objectMap);

        newExecutionContext.objectsAccesses = new HashMap<>(objectsAccesses);

        newExecutionContext.objects = new ArrayList<>(objects);

        newExecutionContext.persistedObjects = new ArrayList<>(persistedObjects);

        newExecutionContext.persistedTaskToObjectsMap = new HashMap<>(persistedTaskToObjectsMap);

        List<LocalObjectState> objectStateCopy = new ArrayList<>();
        for (LocalObjectState localObjectState : objectState) {
            objectStateCopy.add(localObjectState.clone());
        }
        newExecutionContext.objectState = objectStateCopy;

        newExecutionContext.devices = new ArrayList<>(devices);

        newExecutionContext.taskToDeviceMapTable = this.taskToDeviceMapTable.clone();

        newExecutionContext.lastDevices = new HashSet<>(lastDevices);

        newExecutionContext.isPrintKernel = this.isPrintKernel;

        newExecutionContext.profiler = this.profiler;
        newExecutionContext.nextTask = this.nextTask;
        newExecutionContext.executionPlanMemoryLimit = this.executionPlanMemoryLimit;

        return newExecutionContext;
    }

    public long getExecutionPlanId() {
        return this.executionPlanId;
    }

    public void setExecutionPlanId(long executionPlanId) {
        this.executionPlanId = executionPlanId;
    }

    public long getCurrentDeviceMemoryUsage() {
        return currentDeviceMemoryUsage;
    }

    public void setCurrentDeviceMemoryUsage(long currentDeviceMemoryUsage) {
        this.currentDeviceMemoryUsage = currentDeviceMemoryUsage;
    }


    public void addPersistedObject(String taskgraphUniqueName, Object value) {
        persistedTaskToObjectsMap.computeIfAbsent(taskgraphUniqueName, k -> new ArrayList<>()).add(value);
    }

    public Map<String, List<Object>> getPersistedTaskToObjectsMap() {
        return persistedTaskToObjectsMap;
    }

}
