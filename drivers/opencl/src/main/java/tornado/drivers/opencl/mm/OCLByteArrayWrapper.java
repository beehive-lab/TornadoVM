package tornado.drivers.opencl.mm;


import com.oracle.graal.api.meta.Kind;
import tornado.drivers.opencl.OCLDeviceContext;

public class OCLByteArrayWrapper extends OCLArrayWrapper<byte[]> {

	public OCLByteArrayWrapper(OCLDeviceContext device) {
		this(device, false);
	}
	
	public OCLByteArrayWrapper(OCLDeviceContext device,boolean isFinal) {
		super(device, Kind.Byte, isFinal);
	}

	@Override
	protected void readArrayData(long bufferId, long offset, long bytes,
			byte[] value, int[] waitEvents) {
		deviceContext.readBuffer(bufferId, offset, bytes, value, waitEvents);		
	}

	@Override
	protected void writeArrayData(long bufferId, long offset, long bytes,
			byte[] value, int[] waitEvents) {
		deviceContext.writeBuffer(bufferId, offset, bytes, value, waitEvents);
	}

	@Override
	protected int enqueueReadArrayData(long bufferId, long offset,
			long bytes, byte[] value, int[] waitEvents) {
		return deviceContext.enqueueReadBuffer(bufferId, offset, bytes, value, waitEvents);
	}

	@Override
	protected int enqueueWriteArrayData(long bufferId, long offset,
			long bytes, byte[] value, int[] waitEvents) {
		return deviceContext.enqueueWriteBuffer(bufferId, offset, bytes, value, waitEvents);
	}


}
