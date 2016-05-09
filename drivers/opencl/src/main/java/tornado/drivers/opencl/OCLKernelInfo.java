package tornado.drivers.opencl;

import tornado.drivers.opencl.enums.OCLCommandExecutionStatus;
import tornado.meta.domain.DomainTree;

public class OCLKernelInfo {
	private final DomainTree domainTree;
	private final long[] globalOffset;
	private final long[] globalWork;
	private final long[] localWork;
	private OCLEvent event;
	
	public OCLKernelInfo(final DomainTree domainTree){
		this.domainTree = domainTree;
		final int dims = domainTree.getDepth();
		this.globalOffset = new long[dims];
		this.globalWork = new long[dims];
		this.localWork = new long[dims];
	}
	
	public void setEvent(final OCLEvent value){
		event = value;
	}

	public long[] getGlobalOffset() {
		return globalOffset;
	}

	public long[] getGlobalWork() {
		return globalWork;
	}

	public long[] getLocalWork() {
		return localWork;
	}
	
	public double getExecutionTime(){
		return event.getExecutionTime();
	}

	public int getDims() {
		return domainTree.getDepth();
	}
	
	public DomainTree getDomain(){
		return domainTree;
	}

	private static final String formatArray(final long[] array) {
		final StringBuilder sb = new StringBuilder();

		sb.append("[");
		for (final long value : array) {
			sb.append(" " + value);
		}
		sb.append(" ]");

		return sb.toString();
	}
	
	public void printToLog(){
			System.out.printf("kernel info:\n");
			System.out.printf("\tdims              : %d\n", domainTree.getDepth());
			System.out.printf("\tglobal work offset: %s\n", formatArray(globalOffset));
			System.out.printf("\tglobal work size  : %s\n", formatArray(globalWork));
			System.out.printf("\tlocal  work size  : %s\n", formatArray(localWork));
	}
	
	public boolean isComplete(){
		return event.getCLStatus() == OCLCommandExecutionStatus.CL_COMPLETE;
	}
	
	public void waitOn(){
		event.waitOn();
	}
	
	
}
