package tornado.drivers.opencl;

import tornado.common.Tornado;
import tornado.meta.domain.DomainTree;

public class OCLGpuScheduler extends OCLKernelScheduler {
	
	private final double				GPU_COMPUTE_UNIT_COEFF			= 1;
	private final double				GPU_COMPUTE_UNIT_QUEUE_COEFF	= 128;
	private final double				GPU_WORK_GROUP_COEFF			= .125;

	private double maxComputeUnits;
	private double workGroupUtil;
	private double maxWorkGroupSize;
	
	private final long[] maxWorkItemSizes;
	
	public OCLGpuScheduler(final OCLDeviceContext context) {
		super(context);
		OCLDevice device = context.getDevice();
		
		maxWorkItemSizes = deviceContext.getDevice().getMaxWorkItemSizes();
		maxComputeUnits = device.getMaxComputeUnits();
		maxWorkGroupSize = device.getMaxWorkGroupSize();
		workGroupUtil = GPU_WORK_GROUP_COEFF;
		
		//System.out.printf("kernel scheduler: compute units=%f, max work group=%f, group util=%f\n",maxComputeUnits,maxWorkGroupSize,workGroupUtil);
	}

	@Override
	public void calculateGlobalWork(final OCLKernelConfig kernelInfo) {
		final long[] globalWork = kernelInfo.getGlobalWork();
		if(kernelInfo.getDims() > 1)
			utilsIndex = 0;
		
		for(int i=0;i<kernelInfo.getDims();i++){
			long value = (long) (kernelInfo.getDomain().get(i).cardinality()); /// utils[utilsIndex]);
			/*if( value % 32 != 0){
				value = ((value / 32) + 1) * 32;
			}*/
			globalWork[i] = value;
		}

	}

	@Override
	public void calculateLocalWork(OCLKernelConfig kernelInfo) {
		final int maxWorkGroupSize = (int) ((int) deviceContext.getDevice()
				.getMaxWorkGroupSize() * GPU_WORK_GROUP_COEFF);
		
		
		
		//System.out.printf("max item size: {%d, %d, %d}\n",maxWorkItemSizes[0],maxWorkItemSizes[1],maxWorkItemSizes[2]);
		
		final DomainTree domain = kernelInfo.getDomain();
		final long[] localWork = kernelInfo.getLocalWork();
		switch (kernelInfo.getDims()) {
			case 3:
				localWork[2] = 1;
				
			case 2:
				final int sqrtMaxWorkGroupSize = (int) Math
				.sqrt(maxWorkGroupSize);
					
				localWork[1] = calculateGroupSize(maxWorkItemSizes[1],Tornado.OPENCL_GPU_BLOCK_2D_Y,kernelInfo.getGlobalWork()[1]);
				localWork[0] = calculateGroupSize(maxWorkItemSizes[0],Tornado.OPENCL_GPU_BLOCK_2D_X,kernelInfo.getGlobalWork()[0]);
				
				break;
			case 1:
	
				localWork[0] =  calculateGroupSize(maxWorkItemSizes[0], Tornado.OPENCL_GPU_BLOCK_X,kernelInfo.getGlobalWork()[0]);
				break;
			default:
				break;
		}
	}
	
	private long[] sizes = {32,128, 256, 512,1024};
	private long[] utils = {1, 4, 8, 16, 32};
	private int sizeIndex = 2;
	private int utilsIndex = 2;
	private int counter = 0;
	
	private double[][] means = new double[5][5];
	private boolean found = false;
	
	private void findMinimum(){
		double best = Double.MAX_VALUE;
		for(int i=0;i<5;i++){
			for(int j=0;j<5;j++){
				if(means[i][j] < best){
					best = means[i][j];
					sizeIndex=i;
					utilsIndex=j;
				}
			}
		}
		System.out.printf("kernel scheduler: best config is sizes=%d, util=%d, mean=%f\n",sizes[sizeIndex],utils[utilsIndex],means[sizeIndex][utilsIndex]);
	}
	
	@Override
	public void adjust() {
		
//		if(counter < 125){
//			calcStats(5);
//			means[sizeIndex][utilsIndex] = mean;
//			if(counter % 5 == 0)
//				sizeIndex = (sizeIndex + 1) % sizes.length;
//			
//			if(counter % 25 == 0)
//				utilsIndex = (utilsIndex + 1) % utils.length;
//		} else if (!found){
//			findMinimum();
//			found = true;
//		} 
//		
//		counter++;
		
	}

	private int calculateGroupSize(long maxWorkItemSizes, long sizes2, long l){
		 int value  = (int) Math.min(Math.min(maxWorkItemSizes,sizes2), l);
		while(l % value != 0)
			value--;
		return value;
	}

}
