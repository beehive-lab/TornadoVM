package tornado.drivers.opencl.mm;


import jdk.vm.ci.meta.JavaKind;
import tornado.drivers.opencl.OCLDeviceContext;


public class OCLFloatArrayWrapper extends OCLArrayWrapper<float[]> {

	public OCLFloatArrayWrapper(OCLDeviceContext deviceContext) {
		this(deviceContext, false);
	}

	public OCLFloatArrayWrapper(OCLDeviceContext deviceContext, boolean isFinal) {
		super(deviceContext, JavaKind.Float, isFinal);
	}

	@Override
	protected void readArrayData(long bufferId, long offset, long bytes,
			float[] value, int[] waitEvents) {
		deviceContext.readBuffer(bufferId, offset, bytes, value, waitEvents);		
	}

	@Override
	protected void writeArrayData(long bufferId, long offset, long bytes,
			float[] value, int[] waitEvents) {
		deviceContext.writeBuffer(bufferId, offset, bytes, value, waitEvents);
	}

	@Override
	protected int enqueueReadArrayData(long bufferId, long offset,
			long bytes, float[] value, int[] waitEvents) {
		return deviceContext.enqueueReadBuffer(bufferId, offset, bytes, value, waitEvents);
	}

	@Override
	protected int enqueueWriteArrayData(long bufferId, long offset,
			long bytes, float[] value, int[] waitEvents) {
		return deviceContext.enqueueWriteBuffer(bufferId, offset, bytes, value, waitEvents);
	}

}
