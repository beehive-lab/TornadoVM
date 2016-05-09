package tornado.runtime.api;

import java.util.List;

import tornado.api.Event;
import tornado.common.enums.Access;

public interface DataMovementAction  extends Action {
	public Access getAccess();

	public String getName();
	
	public List<Event> getEvents();
}
