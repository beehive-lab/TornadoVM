package tornado.runtime;

import java.util.List;

import tornado.api.Event;
import tornado.api.enums.TornadoSchedulingStrategy;
import tornado.common.CallStack;
import tornado.common.DeviceMapping;
import tornado.common.DeviceObjectState;
import tornado.common.SchedulableTask;
import tornado.common.TornadoInstalledCode;

public class JVMMapping implements DeviceMapping {

	@Override
	public TornadoSchedulingStrategy getPreferedSchedule() {
		return TornadoSchedulingStrategy.PER_BLOCK;
	}
	
	public String toString(){
		return "Host JVM";
	}

	@Override
	public boolean isDistibutedMemory() {
		return false;
	}

	@Override
	public void ensureLoaded() {
		
		
	}

	@Override
	public CallStack createStack(int numArgs) {

		return null;
	}

	@Override
	public TornadoInstalledCode installCode(SchedulableTask task) {
		
		return null;
	}

	@Override
	public Event ensureAllocated(Object object, DeviceObjectState state) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Event ensurePresent(Object object, DeviceObjectState objectState) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Event streamIn(Object object, DeviceObjectState objectState) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Event streamOut(Object object, DeviceObjectState objectState,
			List<Event> list) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void flush() {
		
		
	}

}
