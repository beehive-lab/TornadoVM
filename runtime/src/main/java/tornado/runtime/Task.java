package tornado.runtime;

import java.util.List;

import tornado.runtime.api.Action;
import tornado.api.DeviceMapping;
import tornado.api.Event;
import tornado.api.ProfiledAction;
import tornado.api.SynchronisationPoint;
import tornado.common.enums.Access;
import tornado.meta.Meta;

public interface Task<A extends Action> extends ProfiledAction,SynchronisationPoint {
	public Object[] getArguments();
	public Access[] getArgumentsAccess();
	
	public Meta meta();
	public A	 action();
	public Event getEvent();
	
	
	public void mapTo(DeviceMapping mapping);
	public DeviceMapping getDeviceMapping();
	
	public void schedule();
	public void schedule(Event ...waitEvents);
	public void schedule(List<Event> waitEvents);
}
