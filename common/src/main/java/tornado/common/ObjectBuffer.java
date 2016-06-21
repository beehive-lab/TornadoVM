package tornado.common;

import java.util.List;

import tornado.api.Event;
import tornado.common.exceptions.TornadoOutOfMemoryException;

public interface ObjectBuffer {
	
	public long toBuffer();
	public long getBufferOffset();
	public long toAbsoluteAddress();
	public long toRelativeAddress();

	public void read(Object ref);
	public void write(Object ref);
	
	public Event enqueueRead(Object ref);
	public Event enqueueReadAfter(Object ref,Event event);
	public Event enqueueReadAfterAll(Object ref,List<Event> events);
	
	public Event enqueueWrite(Object ref);
	public Event enqueueWriteAfter(Object ref, Event event);
	public Event enqueueWriteAfterAll(Object ref,List<Event> events);
	
	public void allocate(Object ref) throws TornadoOutOfMemoryException;
//	public boolean needsInitialisation();
//	public Event initialise();
	public int getAlignment();
	
	public boolean isValid();
	public void invalidate();
	public void printHeapTrace();
	public long size();
	
}
