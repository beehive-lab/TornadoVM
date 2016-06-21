package tornado.runtime.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import tornado.common.DeviceMapping;
import tornado.common.RuntimeUtilities;
import tornado.common.SchedulableTask;
import tornado.runtime.TornadoRuntime;
import tornado.runtime.api.LocalObjectState;

public class ExecutionContext {
	
	private final int MAX_TASKS = 100;
	
	private final List<SchedulableTask> tasks;
	private final List<Object> constants;
	private final List<Object> objects;
	private final List<LocalObjectState> objectState;
	private final List<DeviceMapping> devices;
	private final int[] taskToDevice;
	private int nextTask;
	
public ExecutionContext(){
	tasks = new ArrayList<SchedulableTask>();
	constants = new ArrayList<Object>();
	objects = new ArrayList<Object>();
	objectState = new ArrayList<LocalObjectState>();
	devices = new ArrayList<DeviceMapping>();
	taskToDevice = new int[MAX_TASKS];

	// default device
//	devices.add(TornadoRuntime.JVM);
	
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

public int getTaskCount(){
	return nextTask;
}

public void incrGlobalTaskCount(){
	nextTask++;
}

public int addTask(SchedulableTask task){
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

public int getDeviceIndexForTask(int index){
	return taskToDevice[index];
}

public DeviceMapping getDeviceForTask(int index){
	return getDevice(taskToDevice[index]);
}

public DeviceMapping getDevice(int index){
	return devices.get(index);
}

public SchedulableTask getTask(int index){
	return tasks.get(index);
}

public void apply(Consumer<SchedulableTask> consumer) {
	for (SchedulableTask task : tasks) {
		consumer.accept(task);
	}
}

public void mapAllTo(DeviceMapping mapping) {
	int deviceIndex = devices.indexOf(mapping);
	if (deviceIndex == -1) {
		deviceIndex = devices.size();
		devices.add(mapping);
	}

	Arrays.fill(taskToDevice, deviceIndex);
}

public LocalObjectState getObjectState(Object object){
	return objectState.get(insertVariable(object));
}

public void print(){
	System.out.println("constant table:");
	for(int i=0;i<constants.size();i++){
		System.out.printf("[%d]: %s\n",i,constants.get(i));
	}
	
	System.out.println("object table:");
	for(int i=0;i<objects.size();i++){
		System.out.printf("[%d]: %s\n",i,objects.get(i));
	}
}

public List<LocalObjectState> getObjectStates() {
	return objectState;
}

public List<SchedulableTask> getTasks() {
	return tasks;
}

public List<DeviceMapping> getDevices() {
	return devices;
}

}
