package tornado.drivers.opencl.runtime;

import java.util.ArrayList;
import java.util.List;

import tornado.runtime.api.DataMovementAction;
import tornado.api.DeviceMapping;
import tornado.api.Event;
import tornado.common.enums.Access;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.meta.Meta;
import tornado.runtime.EmptyEvent;
import tornado.runtime.ObjectReference;

public class OCLWriteAction implements DataMovementAction {
	final List<Event>	waitEvents;

	public OCLWriteAction(int numObjects) {
		waitEvents = new ArrayList<Event>(numObjects);
	}

	@Override
	public Event apply(Object[] parameters, Access[] access, Meta meta, List<Event> events) {
		Event result = null;
		OCLDeviceContext device = null;
		waitEvents.clear();

		for (int i = 0; i < parameters.length; i++) {
			final Object object = parameters[i];

			if (object instanceof ObjectReference) {
				final ObjectReference<OCLDeviceContext,?> ref = (ObjectReference<OCLDeviceContext,?>) object;
				device = ((OCLDeviceMapping) meta.getProvider(DeviceMapping.class))
						.getDeviceContext();
				ref.requestAccess(device, getAccess());
				waitEvents.add(ref.enqueueWriteAfterAll(events));
			}

		}

		if (!waitEvents.isEmpty()) {
			result = device.enqueueBarrier(waitEvents);
		}

		return (result == null) ? new EmptyEvent() : result;
	}

	@Override
	public Access getAccess() {
		return Access.READ;
	}

	@Override
	public String getName() {
		return "write to device";
	}

	@Override
	public List<Event> getEvents() {
		return waitEvents;
	}

}
