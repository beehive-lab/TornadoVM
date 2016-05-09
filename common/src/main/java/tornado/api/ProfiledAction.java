package tornado.api;

import tornado.api.enums.TornadoExecutionStatus;

public interface ProfiledAction {
	public String getName();
	
	public double getExecutionTime();
	
	public double getQueuedTime();
	
	public TornadoExecutionStatus getStatus();

	public double getTotalTime();

}
