package tornado.runtime.api;

import java.util.List;
import java.util.Set;

import tornado.api.Event;
import tornado.common.enums.Access;
import tornado.runtime.ObjectReference;

public interface CallStack<D> {

	public int getReservedSlots();

	public int getMaxArgs();
	
	public int getSlotCount();
	
	public void pushArgs(Object[] args, Access[] access, List<Event> waitEvents);
	
	public Event getEvent();

	public void reset();
	
	public long getDeoptValue(); 

	public long getReturnValue(); 
	
	public int getArgCount(); 

	public void dump();
	
	public Set<ObjectReference<D,?>> getReadSet();
	public Set<ObjectReference<D,?>> getWriteSet();
	
}
