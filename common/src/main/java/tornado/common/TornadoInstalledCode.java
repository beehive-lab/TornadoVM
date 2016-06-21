package tornado.common;

import java.util.List;

import tornado.api.Event;
import tornado.meta.Meta;

public interface TornadoInstalledCode {

	public Event launch(CallStack stack, Meta meta, List<Event> waitEvents);
	
}
