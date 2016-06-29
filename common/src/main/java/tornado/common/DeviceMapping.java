package tornado.common;

import java.util.List;

import tornado.api.Event;
import tornado.api.enums.TornadoSchedulingStrategy;

public interface DeviceMapping {

	public TornadoSchedulingStrategy getPreferedSchedule();

	public boolean isDistibutedMemory();

	public void ensureLoaded();

	public CallStack createStack(int numArgs);

	public Event ensureAllocated(Object object, DeviceObjectState state);

	

	public Event ensurePresent(Object object, DeviceObjectState objectState);
	public Event streamIn(Object object, DeviceObjectState objectState);
	public Event streamOut(Object object, DeviceObjectState objectState,
			List<Event> list);

	
	public TornadoInstalledCode installCode(SchedulableTask task);

	public void flush();
	
}
