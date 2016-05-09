package tornado.drivers.opencl;

import java.util.ArrayList;
import java.util.List;

import tornado.api.Event;
import tornado.meta.domain.DomainTree;

import static tornado.common.Tornado.*;

public abstract class OCLKernelScheduler {
	
	
	protected final List<OCLKernelInfo> executions;
	protected final OCLDeviceContext deviceContext;
	
	protected double min;
	protected double max;
	protected double sum;
	protected double mean;
	protected double std;
	protected double samples;
	
	public OCLKernelScheduler(final OCLDeviceContext context){
		deviceContext = context;
		executions = new ArrayList<OCLKernelInfo>();
	}
	
	public void calcStats(int window){
		
		 min = Double.MAX_VALUE;
		 max = Double.MIN_VALUE;
		 sum = 0;
		 mean = 0;
		 std = 0;
		 samples = 0;
		int last = executions.size() - 1;
		for(int i=0;i<window;i++){
			if(last - i < 0) continue;
			
			final OCLKernelInfo kernelInfo = executions.get(last - i);
			kernelInfo.waitOn();
			if(kernelInfo.isComplete()){
			final double executionTime = kernelInfo.getExecutionTime();
			min = Math.min(min, executionTime);
			max = Math.max(max, executionTime);
			sum += executionTime;
			samples += 1;
			}
		}
		
		mean = sum / samples;
		final double varience = (sum - (mean * samples)) * (sum - (mean * samples));
		std = Math.sqrt(varience / samples);
		
		//if(samples > 0)
		//	System.out.printf("kernel scheduler: n=%.0f, min=%f, max=%f, mean=%f, std=%f\n",samples,min,max,mean,std);
		
	}
	
	public  abstract void calculateGlobalWork(final OCLKernelInfo kernelInfo);
	public 	abstract void calculateLocalWork(final OCLKernelInfo kernelInfo);
	
	public void adjust(){
		
	}
	
	public OCLEvent submit(final OCLKernel kernel, final DomainTree domainTree, final List<Event> waitEvents){
		final OCLKernelInfo kernelInfo = new OCLKernelInfo(domainTree);
		
		//adjust();
		
		calculateGlobalWork(kernelInfo);
		calculateLocalWork(kernelInfo);

		if(DEBUG)
			kernelInfo.printToLog();
		

		final OCLEvent task = deviceContext.enqueueNDRangeKernel(kernel, kernelInfo.getDims(), kernelInfo.getGlobalOffset(),
				kernelInfo.getGlobalWork(), kernelInfo.getLocalWork(), waitEvents);
		
		//kernelInfo.setEvent(task);
		//executions.add(kernelInfo);
		
		return task;
	}

}
