package tornado.drivers.opencl.mm;

import jdk.vm.ci.meta.JavaKind;
import tornado.drivers.opencl.OCLDeviceContext;

public class OCLByteArrayWrapper extends OCLArrayWrapper<byte[]> {

	public OCLByteArrayWrapper(OCLDeviceContext device) {
		this(device, false);
	}

	public OCLByteArrayWrapper(OCLDeviceContext device,boolean isFinal) {
		super(device, JavaKind.Byte, isFinal);
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
