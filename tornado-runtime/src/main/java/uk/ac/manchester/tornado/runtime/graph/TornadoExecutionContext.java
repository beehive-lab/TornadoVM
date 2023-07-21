/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
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
package uk.ac.manchester.tornado.runtime.graph;

import static uk.ac.manchester.tornado.runtime.common.Tornado.info;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.KernelArgs;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.profiler.TimeProfiler;
import uk.ac.manchester.tornado.runtime.tasks.LocalObjectState;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;

public class TornadoExecutionContext {

    private final int MAX_TASKS = 256;
    private final int INITIAL_DEVICE_CAPACITY = 16;
    private final String name;
    private final ScheduleMetaData meta;
    private final KernelArgs[] callWrappers;
    private List<SchedulableTask> tasks;
    private List<Object> constants;
    private Map<Integer, Integer> objectMap;
    private List<Object> objects;
    private List<LocalObjectState> objectState;
    private List<TornadoAcceleratorDevice> devices;
    private int[] taskToDeviceMapTable;
    private int nextTask;

    private long batchSize;
    private Set<TornadoAcceleratorDevice> lastDevices;

    private boolean redeployOnDevice;
    private boolean defaultScheduler;

    private boolean isDataDependencyDetected;

    private TornadoProfiler profiler;

    public TornadoExecutionContext(String id, TornadoProfiler profiler) {
        name = id;
        meta = new ScheduleMetaData(name);
        tasks = new ArrayList<>();
        constants = new ArrayList<>();
        objectMap = new HashMap<>();
        objects = new ArrayList<>();
        objectState = new ArrayList<>();
        devices = new ArrayList<>(INITIAL_DEVICE_CAPACITY);
        callWrappers = new KernelArgs[MAX_TASKS];
        taskToDeviceMapTable = new int[MAX_TASKS];
        Arrays.fill(taskToDeviceMapTable, -1);
        nextTask = 0;
        batchSize = -1;
        lastDevices = new HashSet<>();
        this.profiler = profiler;
        this.isDataDependencyDetected = isDataDependencyInTaskGraph();
    }

    public KernelArgs[] getCallWrappers() {
        return callWrappers;
    }

    public int insertVariable(Object var) {
        int index = -1;
        if (var.getClass().isPrimitive() || RuntimeUtilities.isBoxedPrimitiveClass(var.getClass())) {
            index = constants.indexOf(var);
            if (index == -1) {
                index = constants.size();
                constants.add(var);
            }
        } else if (objectMap.containsKey(var.hashCode())) {
            index = objectMap.get(var.hashCode());
        } else {
            index = objects.size();
            objects.add(var);
            objectMap.put(var.hashCode(), index);
            objectState.add(index, new LocalObjectState(var));
        }
        return index;
    }

    public long getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(long size) {
        this.batchSize = size;
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

            index = oldIndex;
            objects.add(index, newObj);
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

    public void setTask(int index, SchedulableTask task) {
        tasks.set(index, task);
    }

    public List<Object> getConstants() {
        return constants;
    }

    public List<Object> getObjects() {
        return objects;
    }

    public int getDeviceIndexForTask(int index) {
        return taskToDeviceMapTable[index];
    }

    public TornadoAcceleratorDevice getDeviceForTask(int index) {
        return getDevice(taskToDeviceMapTable[index]);
    }

    public TornadoAcceleratorDevice getDevice(int index) {
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
     *            The {@link TornadoDevice} to which all tasks will be mapped.
     * @throws RuntimeException
     *             if the current device is not supported.
     */
    public void mapAllTasksToSingleDevice(TornadoDevice tornadoDevice) {
        if (tornadoDevice instanceof TornadoAcceleratorDevice) {
            devices.clear();
            devices.add(0, (TornadoAcceleratorDevice) tornadoDevice);
            apply(task -> task.mapTo(tornadoDevice));
            Arrays.fill(taskToDeviceMapTable, 0);
        } else {
            throw new TornadoRuntimeException("Device " + tornadoDevice.getClass() + " not supported yet");
        }
    }

    private void checkDeviceListSize(int deviceIndex) {
        if (deviceIndex >= devices.size()) {
            for (int i = devices.size(); i <= deviceIndex; i++) {
                devices.add(null);
            }
        }
    }

    public void setDevice(int index, TornadoAcceleratorDevice device) {
        checkDeviceListSize(index);
        devices.set(index, device);
    }

    /**
     * It assigns a task to a {@link TornadoAcceleratorDevice} based on the current
     * task scheduling strategy.
     *
     * @param index
     *            The index of the task.
     * @param task
     *            The {@link SchedulableTask} to be assigned.
     * @throws {@link
     *             TornadoRuntimeException} if the target device is not supported.
     */
    private void assignTaskToDevice(int index, SchedulableTask task) {
        String id = task.getId();
        TornadoDevice target = task.getDevice();
        TornadoAcceleratorDevice accelerator;

        if (target instanceof TornadoAcceleratorDevice) {
            accelerator = (TornadoAcceleratorDevice) target;
        } else {
            throw new TornadoRuntimeException("Device " + target.getClass() + " not supported yet");
        }

        int deviceIndex = devices.indexOf(target);
        info("assigning %s to %s", id, target.getDeviceName());

        if (deviceIndex == -1) {
            deviceIndex = task.meta().getDeviceIndex();
            setDevice(deviceIndex, accelerator);
        }

        taskToDeviceMapTable[index] = deviceIndex;
    }

    /**
     * It sets all device entries in the device list to null except the specified
     * device index.
     *
     * @param deviceIndex
     *            The index of the device to exclude from nullification.
     */
    public void nullifyDevicesTableExceptAtIndex(int deviceIndex) {
        devices = devices.stream().map(device -> devices.indexOf(device) == deviceIndex ? device : null).collect(Collectors.toList());
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
        return (int) getDevices().stream().filter(device -> device != null).count();
    }

    /**
     * It gets the {@link TornadoDevice} of the first task.
     *
     * @return The {@link TornadoDevice} of the first task.
     */
    public TornadoDevice getDeviceOfFirstTask() {
        return tasks.get(0).getDevice();
    }

    public LocalObjectState getObjectState(Object object) {
        return objectState.get(insertVariable(object));
    }

    public LocalObjectState replaceObjectState(Object oldObj, Object newObj) {
        return objectState.get(replaceVariable(oldObj, newObj));
    }

    /**
     * Checks if the tasks in the list are mutually independent.
     *
     * @return {@code true} if the tasks are mutually independent, {@code false}
     *         otherwise.
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

    public List<TornadoAcceleratorDevice> getDevices() {
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
            TornadoAcceleratorDevice device = devices.get(i);
            if (device != null) {
                nonNullIndexes.push(i);
            }
        }
        return nonNullIndexes;
    }

    /**
     * It retrieves a list of tasks for a specific device and driver. Both
     * deviceContext and driverIndex are checked to ensure the correct task
     * assignment.
     *
     * @param deviceContext
     *            The device context of the device.
     * @param driverIndex
     *            The index of the driver.
     * @return A list of {@link SchedulableTask} objects associated with the
     *         specified device and driver.
     */
    public List<SchedulableTask> getTasksForDevice(TornadoDeviceContext deviceContext, int driverIndex) {
        List<SchedulableTask> tasksForDevice = new ArrayList<>();
        for (SchedulableTask task : tasks) {
            task.getDevice().getDriverIndex();
            if (task.getDevice().getDeviceContext() == deviceContext) {
                tasksForDevice.add(task);
            }
        }
        return tasksForDevice;
    }

    /**
     * Default device inspects the driver 0 and device 0 of the internal list.
     *
     * @return {@link TornadoAcceleratorDevice}
     */
    @Deprecated
    public TornadoAcceleratorDevice getDefaultDevice() {
        return meta.getLogicDevice();
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

    public TornadoAcceleratorDevice getDeviceForTask(String id) {
        TornadoDevice device = getTask(id).getDevice();
        TornadoAcceleratorDevice tornadoDevice = null;
        if (device instanceof TornadoAcceleratorDevice) {
            tornadoDevice = (TornadoAcceleratorDevice) device;
        } else {
            throw new RuntimeException("Device " + device.getClass() + " not supported yet");
        }
        return getTask(id) == null ? null : tornadoDevice;
    }

    public String getId() {
        return name;
    }

    public ScheduleMetaData meta() {
        return meta;
    }

    public void sync() {
        for (int i = 0; i < objects.size(); i++) {
            Object object = objects.get(i);
            if (object != null) {
                final LocalObjectState localState = objectState.get(i);
                Event event = localState.sync(object, meta().getLogicDevice());

                if (TornadoOptions.isProfilerEnabled() && event != null) {
                    long value = profiler.getTimer(ProfilerType.COPY_OUT_TIME_SYNC);
                    value += event.getElapsedTime();
                    profiler.setTimer(ProfilerType.COPY_OUT_TIME_SYNC, value);
                    DeviceObjectState deviceObjectState = localState.getGlobalState().getDeviceState(meta().getLogicDevice());
                    profiler.addValueToMetric(ProfilerType.COPY_OUT_SIZE_BYTES_SYNC, TimeProfiler.NO_TASK_NAME, deviceObjectState.getObjectBuffer().size());
                }
            }
        }
    }

    public void addLastDevice(TornadoAcceleratorDevice device) {
        lastDevices.add(device);
    }

    public Set<TornadoAcceleratorDevice> getLastDevices() {
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

    public void createImmutableExecutionContext(TornadoExecutionContext executionContext) {

        List<SchedulableTask> schedulableTasksCopy = new ArrayList<>(tasks);
        executionContext.tasks = schedulableTasksCopy;

        List<Object> constantCopy = new ArrayList<>(constants);
        executionContext.constants = constantCopy;

        Map<Integer, Integer> objectsMapCopy = new HashMap<>(objectMap);
        executionContext.objectMap = objectsMapCopy;

        List<Object> objectsCopy = new ArrayList<>(objects);
        executionContext.objects = objectsCopy;

        List<LocalObjectState> objectStateCopy = new ArrayList<>(objectState);
        executionContext.objectState = objectStateCopy;

        List<TornadoAcceleratorDevice> devicesCopy = new ArrayList<>(devices);
        executionContext.devices = devicesCopy;

        executionContext.taskToDeviceMapTable = this.taskToDeviceMapTable.clone();

        Set<TornadoAcceleratorDevice> lastDeviceCopy = new HashSet<>(lastDevices);
        executionContext.lastDevices = lastDeviceCopy;

        executionContext.profiler = this.profiler;
        executionContext.nextTask = this.nextTask;
    }

    public void dumpExecutionContextMeta() {
        final String ANSI_RESET = "\u001B[0m";
        final String ANSI_CYAN = "\u001B[36m";
        final String ANSI_YELLOW = "\u001B[33m";
        final String ANSI_PURPLE = "\u001B[35m";
        final String ANSI_GREEN = "\u001B[32m";
        System.out.println("-----------------------------------");
        System.out.println(ANSI_CYAN + "Device Table:" + ANSI_RESET);
        for (int i = 0; i < devices.size(); i++) {
            System.out.printf("[%d]: %s\n", i, devices.get(i));
        }

        System.out.println(ANSI_YELLOW + "Constant Table:" + ANSI_RESET);
        for (int i = 0; i < constants.size(); i++) {
            System.out.printf("[%d]: %s\n", i, constants.get(i));
        }

        System.out.println(ANSI_PURPLE + "Object Table:" + ANSI_RESET);
        for (int i = 0; i < objects.size(); i++) {
            final Object obj = objects.get(i);
            System.out.printf("[%d]: 0x%x %s\n", i, obj.hashCode(), obj);
        }

        System.out.println(ANSI_GREEN + "Task Table:" + ANSI_RESET);
        for (int i = 0; i < tasks.size(); i++) {
            final SchedulableTask task = tasks.get(i);
            System.out.printf("[%d]: %s\n", i, task.getFullName());
        }
        System.out.println("-----------------------------------");
    }
}
