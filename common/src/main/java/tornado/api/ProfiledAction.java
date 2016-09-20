package tornado.api;

import tornado.api.enums.TornadoExecutionStatus;

public interface ProfiledAction {
	public String getName();
	
	public long getSubmitTime();
	public long getStartTime();
	public long getEndTime();
	
	public double getExecutionTime();
	
	public double getQueuedTime();
	
	public TornadoExecutionStatus getStatus();

	public double getTotalTime();

}
