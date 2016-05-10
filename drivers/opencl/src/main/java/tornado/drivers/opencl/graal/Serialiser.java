package tornado.drivers.opencl.graal;

import tornado.drivers.opencl.mm.OCLMemoryManager;

public interface Serialiser {

	public boolean canSerialise(Object obj);
	public long put(OCLMemoryManager memoryManager, Object obj);
	public void get(OCLMemoryManager memoryManager, Object obj, long address);
}
