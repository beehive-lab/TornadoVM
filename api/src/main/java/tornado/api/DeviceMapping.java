package tornado.api;

import tornado.api.enums.TornadoSchedulingStrategy;

public interface DeviceMapping {

	public TornadoSchedulingStrategy getPreferedSchedule();
	
}
