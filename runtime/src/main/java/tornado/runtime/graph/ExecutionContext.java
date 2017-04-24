package tornado.runtime.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import tornado.common.RuntimeUtilities;
import tornado.common.SchedulableTask;
import tornado.common.Tornado;
import tornado.common.TornadoDevice;
import tornado.runtime.TornadoDriver;
import tornado.runtime.api.LocalObjectState;

import static tornado.common.Tornado.getProperty;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

public class ExecutionContext {

    private final String name;
    private final int MAX_TASKS = 100;

    private final List<SchedulableTask> tasks;
    private final List<Object> constants;
    private final List<Object> objects;
    private final List<LocalObjectState> objectState;
    private final List<TornadoDevice> devices;
    private final int[] taskToDevice;
    private int nextTask;
    private final TornadoDevice defaultDevice;

    private static TornadoDevice resolveDevice(String device) {
        final String[] ids = device.split(":");
        final TornadoDriver driver = getTornadoRuntime().getDriver(Integer.parseInt(ids[0]));
        return driver.getDevice(Integer.parseInt(ids[1]));
    }

    public ExecutionContext(String id) {
        name = id;
        tasks = new ArrayList<>();
        constants = new ArrayList<>();
        objects = new ArrayList<>();
        objectState = new ArrayList<>();
        devices = new ArrayList<>();
        taskToDevice = new int[MAX_TASKS];
        Arrays.fill(taskToDevice, -1);

        // default device
        defaultDevice = resolveDevice(getProperty(name + ".device", "0:0"));

        nextTask = 0;
    }

    public int insertVariable(Object var) {
        int index = -1;
        if (var.getClass().isPrimitive()
                || RuntimeUtilities.isBoxedPrimitiveClass(var.getClass())) {
            index = constants.indexOf(var);
            if (index == -1) {
                index = constants.size();
                constants.add(var);
            }
        } else {
            index = objects.indexOf(var);
            if (index == -1) {
                index = objects.size();
                objects.add(var);
                objectState.add(new LocalObjectState(var));
            }
        }
        return index;
    }

    public int getTaskCount() {
        return nextTask;
    }

    public void incrGlobalTaskCount() {
        nextTask++;
    }

    public int addTask(SchedulableTask task) {
        int index = tasks.indexOf(task);
        if (index == -1) {
            index = tasks.size();
            tasks.add(task);
        }
        return index;
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
        devices.clear();
        devices.add(0, mapping);
        apply(task -> task.mapTo(mapping));
        Arrays.fill(taskToDevice, 0);
    }

    private void assignTask(int index, SchedulableTask task) {
        if (taskToDevice[index] != -1) {
            return;
        }

        String id = task.getId();
        String device = getProperty(name + "." + id + ".device");
        TornadoDevice target = (device != null) ? resolveDevice(device) : defaultDevice;
        task.mapTo(target);

        Tornado.info("assigning %s.%s to %s\n", name, id, target.getDeviceName());

        int deviceIndex = devices.indexOf(target);
        if (deviceIndex == -1) {
            deviceIndex = devices.size();
            devices.add(defaultDevice);
        }
        taskToDevice[index] = deviceIndex;
    }

    public void assignToDevices() {
        for (int i = 0; i < tasks.size(); i++) {
            assignTask(i, tasks.get(i));
        }
    }

    public LocalObjectState getObjectState(Object object) {
        return objectState.get(insertVariable(object));
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
            System.out.printf("[%d]: %s\n", i, objects.get(i));
        }
    }

    public List<LocalObjectState> getObjectStates() {
        return objectState;
    }

    public List<SchedulableTask> getTasks() {
        return tasks;
    }

    public List<TornadoDevice> getDevices() {
        return devices;
    }

    public TornadoDevice getDefaultDevice() {
        return defaultDevice;
    }

    public TornadoDevice getDeviceForTask(String id) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId().equalsIgnoreCase(id)) {
                return devices.get(taskToDevice[i]);
            }
        }
        return null;
    }

    public String getId() {
        return name;
    }
}
