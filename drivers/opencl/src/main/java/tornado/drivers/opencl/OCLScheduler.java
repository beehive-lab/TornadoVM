package tornado.drivers.opencl;

import tornado.common.Tornado;
import tornado.drivers.opencl.enums.OCLDeviceType;
import static tornado.common.Tornado.USE_OPENCL_SCHEDULING;
public class OCLScheduler {
	
	public static final OCLKernelScheduler create(final OCLDeviceContext context){
		
		if (USE_OPENCL_SCHEDULING || context.getDevice().getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_GPU ){
			return new OCLGpuScheduler(context);
		} else if(context.getDevice().getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_CPU || context.getDevice().getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR)
			return new OCLCpuScheduler(context);
		else{
			Tornado.fatal("No scheduler available for device: %s",context);
		}
		
		return null;		
	}
}
