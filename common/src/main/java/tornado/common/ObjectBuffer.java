package tornado.common;

import java.util.List;

import tornado.api.Event;
import tornado.common.exceptions.TornadoOutOfMemoryException;

public interface ObjectBuffer<T> {
	
	public long toBuffer();
	public long getBufferOffset();
	public long toAbsoluteAddress();
	public long toRelativeAddress();

	public void read(T ref);
	public void write(T ref);
	
	public Event enqueueRead(T ref);
	public Event enqueueReadAfter(T ref,Event event);
	public Event enqueueReadAfterAll(T ref,List<Event> events);
	
	public Event enqueueWrite(T ref);
	public Event enqueueWriteAfter(T ref, Event event);
	public Event enqueueWriteAfterAll(T ref,List<Event> events);
	
	public Event enqueueZeroMemory();
	
	public void allocate(T ref) throws TornadoOutOfMemoryException;
	public int getAlignment();
	
	public boolean isValid();
	public void invalidate();
	public void printHeapTrace();
	public long size();
	
}
