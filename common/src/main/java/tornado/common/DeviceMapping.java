package tornado.common;

import tornado.api.Event;
import tornado.api.enums.TornadoSchedulingStrategy;

public interface DeviceMapping {

	public TornadoSchedulingStrategy getPreferedSchedule();

	public boolean isDistibutedMemory();

	public void ensureLoaded();

	public CallStack createStack(int numArgs);

	public int ensureAllocated(Object object, DeviceObjectState state);

	

	public int ensurePresent(Object object, DeviceObjectState objectState);
	public int streamIn(Object object, DeviceObjectState objectState);
	public int streamOut(Object object, DeviceObjectState objectState,
			int[] list);

	
	public TornadoInstalledCode installCode(SchedulableTask task);

        public Event resolveEvent(int event);
        public void markEvent();
        public void flushEvents();
	public int enqueueBarrier();
        public void sync();
        
        public String getDeviceName();
	
}
