package tornado.drivers.opencl.mm;

import com.oracle.graal.api.meta.Kind;
import tornado.drivers.opencl.OCLDeviceContext;

public class OCLIntArrayWrapper extends OCLArrayWrapper<int[]> {

	public OCLIntArrayWrapper(OCLDeviceContext device) {
		this(device, false);
	}
	
	public OCLIntArrayWrapper(OCLDeviceContext device,boolean isFinal) {
		super(device, Kind.Int, isFinal);
	}

	@Override
	protected void readArrayData(long bufferId, long offset, long bytes,
			int[] value, int[] waitEvents) {
		deviceContext.readBuffer(bufferId, offset, bytes, value, waitEvents);		
	}

	@Override
	protected void writeArrayData(long bufferId, long offset, long bytes,
			int[] value, int[] waitEvents) {
		deviceContext.writeBuffer(bufferId, offset, bytes, value, waitEvents);
	}

	@Override
	protected int enqueueReadArrayData(long bufferId, long offset,
			long bytes, int[] value, int[] waitEvents) {
		return deviceContext.enqueueReadBuffer(bufferId, offset, bytes, value, waitEvents);
	}

	@Override
	protected int enqueueWriteArrayData(long bufferId, long offset,
			long bytes, int[] value, int[] waitEvents) {
		return deviceContext.enqueueWriteBuffer(bufferId, offset, bytes, value, waitEvents);
	}


}
