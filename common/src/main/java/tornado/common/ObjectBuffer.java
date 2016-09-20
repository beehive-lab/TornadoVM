package tornado.common;

import tornado.common.exceptions.TornadoOutOfMemoryException;

public interface ObjectBuffer {
	
	public long toBuffer();
	public long getBufferOffset();
	public long toAbsoluteAddress();
	public long toRelativeAddress();

	public void read(Object ref);
	public void write(Object ref);
	
	public int enqueueRead(Object ref,int[] events);
	
	public int enqueueWrite(Object ref,int[] events);
	
	public void allocate(Object ref) throws TornadoOutOfMemoryException;
	public int getAlignment();
	
	public boolean isValid();
	public void invalidate();
	public void printHeapTrace();
	public long size();
	
}
