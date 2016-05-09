package tornado.drivers.opencl;

public class OCLCpuScheduler extends OCLKernelScheduler {
	
	private final double				CPU_COMPUTE_UNIT_COEFF			= .5;

	public OCLCpuScheduler(final OCLDeviceContext context) {
		super(context);
	}

	@Override
	public void calculateGlobalWork(final OCLKernelInfo kernelInfo) {
		final int maxWorkGroupSize = (int) ((int) deviceContext.getDevice()
				.getMaxComputeUnits() * CPU_COMPUTE_UNIT_COEFF);
		
		final long[] globalWork = kernelInfo.getGlobalWork();
		switch (kernelInfo.getDims()) {
			case 3:
				globalWork[2] = 1;
				
			case 2:
				globalWork[1] = 1;
				globalWork[0] = maxWorkGroupSize;
				break;
			case 1:
				globalWork[0] = maxWorkGroupSize;
				break;
			default:
				break;
		}
	}

	@Override
	public void calculateLocalWork(OCLKernelInfo kernelInfo) {
		final long[] globalWork = kernelInfo.getGlobalWork();
		final long[] localWork = kernelInfo.getLocalWork();
		
		for(int i=0;i<globalWork.length;i++)
			localWork[i] = 1; //globalWork[i];
	}

}
