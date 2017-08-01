/*
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.runtime.graph;

import java.util.*;
import java.util.function.Consumer;
import tornado.api.meta.ScheduleMetaData;
import tornado.common.SchedulableTask;
import tornado.common.TornadoDevice;

import static tornado.common.Tornado.info;

import tornado.common.DeviceFrame;

public class ExecutionContext {

    private final String name;
    private final int MAX_TASKS = 100;
    private final ScheduleMetaData meta;

    private final List<SchedulableTask> tasks;
    private final List<Object> constants;
    private final List<Object> objects;
    private final List<Object> parameters;
    private DeviceFrame[] stacks;

    private final Map<Integer, Integer> objectMap;
//    private final List<LocalObjectState> objectState;

    private final Set<Object> streamIn;
    private final Set<Object> streamOut;

    private final List<TornadoDevice> devices;
    private final int[] taskToDevice;
    private int nextTask;

    public ExecutionContext(String id) {
        name = id;
        meta = new ScheduleMetaData(name);
        tasks = new ArrayList<>();

        objectMap = new HashMap<>();
        parameters = new ArrayList<>();
        constants = new ArrayList<>();
        objects = new ArrayList<>();
        streamIn = new HashSet<>();
        streamOut = new HashSet<>();
        devices = new ArrayList<>();
        taskToDevice = new int[MAX_TASKS];
        Arrays.fill(taskToDevice, -1);
        nextTask = 0;
    }

    public DeviceFrame[] getStacks() {
        return stacks;
    }

    public void setStacks(DeviceFrame[] value) {
        stacks = value;
    }

    public int insertVariable(int taskId, Object var) {
        int index = parameters.size();
        parameters.add(var);
        return index;
    }

    public int getTaskCount() {
        return nextTask;
    }

    public void incrGlobalTaskCount() {
        nextTask++;
    }

    public int hasTask(SchedulableTask task) {
        return tasks.indexOf(task);
    }

    public int addTask(SchedulableTask task) {
        int index = tasks.indexOf(task);
        if (index == -1) {
            index = tasks.size();
            tasks.add(task);
        }
        return index;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    public List<Object> getConstants() {
        return constants;
    }

    public List<Object> getObjects() {
        return objects;
    }

    public int getDeviceIndexForTask(int index) {
        return taskToDevice[index];
    }

    public TornadoDevice getDeviceForTask(int index) {
        return getDevice(taskToDevice[index]);
    }

    public TornadoDevice getDevice(int index) {
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

    public void mapAllTo(TornadoDevice mapping) {
        apply(task -> task.mapTo(mapping));
        Arrays.fill(taskToDevice, 0);
    }

    private void assignTask(int index, SchedulableTask task) {
        String id = task.getId();
        TornadoDevice target = task.getDevice();

        info("assigning %s to %s", id, target.getDeviceName());

        int deviceIndex = devices.indexOf(target);
        if (deviceIndex == -1) {
            deviceIndex = devices.size();
            devices.add(target);
        }
        taskToDevice[index] = deviceIndex;
    }

    public void assignToDevices() {
        devices.clear();
        for (int i = 0; i < tasks.size(); i++) {
            assignTask(i, tasks.get(i));
        }
    }

    public Set<Object> getStreamInSet() {
        return streamIn;
    }

    public Set<Object> getStreamOutSet() {
        return streamOut;
    }

    public void setStreamIn(Object object) {
        streamIn.add(object);
    }

    public void setStreamOut(Object object) {
        streamOut.add(object);
    }

    public void print() {
        System.out.println("device table:");
        for (int i = 0; i < devices.size(); i++) {
            System.out.printf("[%d]: %s\n", i, devices.get(i));
        }

        System.out.println("constant table:");
        for (int i = 0; i < constants.size(); i++) {
            System.out.printf("[%d]: %s\n", i, constants.get(i));
        }

        System.out.println("object table:");
        for (int i = 0; i < objects.size(); i++) {
            final Object obj = objects.get(i);
            System.out.printf("[%d]: 0x%x %s\n", i, obj.hashCode(), obj.toString());
        }

        System.out.println("stream in set:");
        int index = 0;
        Iterator objIt = streamIn.iterator();
        while (objIt.hasNext()) {
            final Object obj = objIt.next();
            System.out.printf("[%d]: 0x%x %s\n", index, obj.hashCode(), obj.toString());
            index++;
        }

        System.out.println("stream out set:");
        index = 0;
        objIt = streamOut.iterator();
        while (objIt.hasNext()) {
            final Object obj = objIt.next();
            System.out.printf("[%d]: 0x%x %s\n", index, obj.hashCode(), obj.toString());
            index++;
        }

        System.out.println("task table:");
        for (int i = 0; i < tasks.size(); i++) {
            final SchedulableTask task = tasks.get(i);
            System.out.printf("[%d]: %s\n", i, task.getName());
        }
    }

    public List<SchedulableTask> getTasks() {
        return tasks;
    }

    public List<TornadoDevice> getDevices() {
        return devices;
    }

    public TornadoDevice getDefaultDevice() {
        return meta.getDevice();
    }

    public DeviceFrame getStack(String id) {
        for (int i = 0; i < tasks.size(); i++) {
            final String canonicalisedId;
            if (id.startsWith(getId())) {
                canonicalisedId = id;
            } else {
                canonicalisedId = getId() + "." + id;
            }
            if (tasks.get(i).getId().equalsIgnoreCase(canonicalisedId)) {
                return stacks[i];
            }
        }
        return null;
    }

    public SchedulableTask getTask(String id) {
        for (int i = 0; i < tasks.size(); i++) {
            final String canonicalisedId;
            if (id.startsWith(getId())) {
                canonicalisedId = id;
            } else {
                canonicalisedId = getId() + "." + id;
            }
            if (tasks.get(i).getId().equalsIgnoreCase(canonicalisedId)) {
                return tasks.get(i);
            }
        }
        return null;
    }

    public TornadoDevice getDeviceForTask(String id) {
        return getTask(id) == null ? null : getTask(id).getDevice();
    }

    public String getId() {
        return name;
    }

    public ScheduleMetaData meta() {
        return meta;
    }

    public boolean shouldRecompile() {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).meta().shouldRecompile()) {
                return true;
            }
        }
        return meta.shouldRecompile();
    }

    public void setRecompiled() {
        apply((task) -> task.meta().setRecompiled());
        meta.setRecompiled();
    }

}
