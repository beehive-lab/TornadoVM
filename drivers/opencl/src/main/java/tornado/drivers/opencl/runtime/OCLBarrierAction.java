package tornado.drivers.opencl.runtime;

import java.util.List;

import tornado.runtime.api.BarrierAction;
import tornado.api.DeviceMapping;
import tornado.api.Event;
import tornado.common.enums.Access;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.meta.Meta;

public class OCLBarrierAction implements BarrierAction {

	@Override
	public Event apply(Object[] parameters, Access[] accesses, Meta meta, List<Event> events) {
		final OCLDeviceContext device = ((OCLDeviceMapping) meta.getProvider(DeviceMapping.class)).getDeviceContext();

		return device.enqueueBarrier(events);
	}

}
