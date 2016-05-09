package tornado.runtime;

import tornado.api.Event;
import tornado.api.enums.TornadoExecutionStatus;

public class EmptyEvent implements Event {

	private final String name;
	
	public EmptyEvent(String name){
		this.name = name;
	}
	
	public EmptyEvent(){
		this("Empty Event");
	}
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getExecutionTime() {
		return 0;
	}

	@Override
	public double getQueuedTime() {
		return 0;
	}

	@Override
	public TornadoExecutionStatus getStatus() {
		return TornadoExecutionStatus.COMPLETE;
	}

	@Override
	public double getTotalTime() {
		return 0;
	}

	@Override
	public void waitOn() {

	}

}
