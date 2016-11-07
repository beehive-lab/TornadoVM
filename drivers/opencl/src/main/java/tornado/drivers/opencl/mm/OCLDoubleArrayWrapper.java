package tornado.drivers.opencl.mm;


import jdk.vm.ci.meta.JavaKind;
import tornado.drivers.opencl.OCLDeviceContext;

public class OCLDoubleArrayWrapper extends OCLArrayWrapper<double[]> {

	public OCLDoubleArrayWrapper(OCLDeviceContext deviceContext) {
		this(deviceContext, false);
	}
	
	public OCLDoubleArrayWrapper(OCLDeviceContext deviceContext, boolean isFinal) {
		super(deviceContext, JavaKind.Double, isFinal);
	}

	@Override
	protected void readArrayData(long bufferId, long offset, long bytes,
			double[] value, int[] waitEvents) {
		deviceContext.readBuffer(bufferId, offset, bytes, value, waitEvents);		
	}

	@Override
	protected void writeArrayData(long bufferId, long offset, long bytes,
			double[] value, int[] waitEvents) {
		deviceContext.writeBuffer(bufferId, offset, bytes, value, waitEvents);
	}

	@Override
	protected int enqueueReadArrayData(long bufferId, long offset,
			long bytes, double[] value, int[] waitEvents) {
		return deviceContext.enqueueReadBuffer(bufferId, offset, bytes, value, waitEvents);
	}

	@Override
	protected int enqueueWriteArrayData(long bufferId, long offset,
			long bytes, double[] value, int[] waitEvents) {
		return deviceContext.enqueueWriteBuffer(bufferId, offset, bytes, value, waitEvents);
	}
	
}
