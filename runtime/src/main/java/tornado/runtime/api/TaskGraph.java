package tornado.runtime.api;

import tornado.common.DeviceMapping;
import tornado.common.SchedulableTask;
import tornado.runtime.api.TornadoFunctions.Task1;
import tornado.runtime.api.TornadoFunctions.Task10;
import tornado.runtime.api.TornadoFunctions.Task2;
import tornado.runtime.api.TornadoFunctions.Task3;
import tornado.runtime.api.TornadoFunctions.Task4;
import tornado.runtime.api.TornadoFunctions.Task5;
import tornado.runtime.api.TornadoFunctions.Task6;
import tornado.runtime.api.TornadoFunctions.Task7;
import tornado.runtime.api.TornadoFunctions.Task8;
import tornado.runtime.api.TornadoFunctions.Task9;

public class TaskGraph extends AbstractTaskGraph {

	public <T1> TaskGraph add(Task1<T1> code, T1 arg) {
		addInner(TaskUtils.createTask(code, arg));
		return this;
	}

	public <T1, T2> TaskGraph add(Task2<T1, T2> code, T1 arg1, T2 arg2) {
		addInner(TaskUtils.createTask(code, arg1, arg2));
		return this;
	}

	public <T1, T2, T3> TaskGraph add(Task3<T1, T2, T3> code, T1 arg1, T2 arg2,
			T3 arg3) {
		addInner(TaskUtils.createTask(code, arg1, arg2, arg3));
		return this;
	}

	public <T1, T2, T3, T4> TaskGraph add(Task4<T1, T2, T3, T4> code, T1 arg1,
			T2 arg2, T3 arg3, T4 arg4) {
		addInner(TaskUtils.createTask(code, arg1, arg2, arg3, arg4));
		return this;
	}

	public <T1, T2, T3, T4, T5> TaskGraph add(Task5<T1, T2, T3, T4, T5> code,
			T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5) {
		addInner(TaskUtils.createTask(code, arg1, arg2, arg3, arg4, arg5));
		return this;
	}

	public <T1, T2, T3, T4, T5, T6> TaskGraph add(
			Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3,
			T4 arg4, T5 arg5, T6 arg6) {
		addInner(TaskUtils.createTask(code, arg1, arg2, arg3, arg4, arg5,
				arg6));
		return this;
	}

	public <T1, T2, T3, T4, T5, T6, T7> TaskGraph add(
			Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3,
			T4 arg4, T5 arg5, T6 arg6, T7 arg7) {
		addInner(TaskUtils.createTask(code, arg1, arg2, arg3, arg4, arg5,
				arg6, arg7));
		return this;
	}

	public <T1, T2, T3, T4, T5, T6, T7, T8> TaskGraph add(
			Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2,
			T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8) {
		addInner(TaskUtils.createTask(code, arg1, arg2, arg3, arg4, arg5,
				arg6, arg7, arg8));
		return this;
	}

	public <T1, T2, T3, T4, T5, T6, T7, T8, T9> TaskGraph add(
			Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2,
			T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9) {
		addInner(TaskUtils.createTask(code, arg1, arg2, arg3, arg4, arg5,
				arg6, arg7, arg8, arg9));
		return this;
	}

	public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> TaskGraph add(
			Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1,
			T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8,
			T9 arg9, T10 arg10) {
		addInner(TaskUtils.createTask(code, arg1, arg2, arg3, arg4, arg5,
				arg6, arg7, arg8, arg9, arg10));
		return this;
	}
	
	public TaskGraph add(SchedulableTask task){
		addInner(task);
		return this;
	}
	
	public TaskGraph mapAllTo(DeviceMapping mapping){
		mapAllToInner(mapping);
		return this;
	}
	
	public TaskGraph streamIn(Object... objects){
		streamInInner(objects);
		return this;
	}
	
	public TaskGraph streamOut(Object... objects){
		streamOutInner(objects);
		return this;
	}
	
	public TaskGraph schedule(){
		scheduleInner();
		return this;
	}
	
	public void printCompileTimes(){
		
	}
	
}
