package tornado.drivers.opencl;

import java.util.ArrayList;
import java.util.List;

import tornado.api.Event;
import tornado.meta.Meta;
import tornado.meta.domain.DomainTree;
import static tornado.common.Tornado.*;

public abstract class OCLKernelScheduler {
	
	
	protected final OCLDeviceContext deviceContext;
	
	protected double min;
	protected double max;
	protected double sum;
	protected double mean;
	protected double std;
	protected double samples;
	
	public OCLKernelScheduler(final OCLDeviceContext context){
		deviceContext = context;
	}
	
	public void calcStats(int window){
		
		 
		
	}
	
	public  abstract void calculateGlobalWork(final OCLKernelConfig kernelInfo);
	public 	abstract void calculateLocalWork(final OCLKernelConfig kernelInfo);
	
	public void adjust(){
		
	}
	
	public OCLEvent submit(final OCLKernel kernel, final Meta meta, final List<Event> waitEvents){
		final OCLKernelConfig kernelInfo;
		if(meta.hasProvider(OCLKernelConfig.class)){
			kernelInfo = meta.getProvider(OCLKernelConfig.class);
		} else {
			kernelInfo = OCLKernelConfig.create(meta);
			calculateGlobalWork(kernelInfo);
			calculateLocalWork(kernelInfo);
		}
		
		if(DEBUG)
			kernelInfo.printToLog();
		

		final OCLEvent task = deviceContext.enqueueNDRangeKernel(kernel, kernelInfo.getDims(), kernelInfo.getGlobalOffset(),
				kernelInfo.getGlobalWork(), kernelInfo.getLocalWork(), waitEvents);
		
		return task;
	}

}
