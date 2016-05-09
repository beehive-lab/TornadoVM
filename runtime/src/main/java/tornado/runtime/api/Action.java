package tornado.runtime.api;

import java.util.List;

import tornado.api.Event;
import tornado.common.enums.Access;
import tornado.meta.Meta;

public interface Action {
	public Event apply(Object[] parameters, Access[] access, Meta meta, List<Event> events);
}
