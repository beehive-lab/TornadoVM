package tornado.runtime.api;

import tornado.common.DeviceMapping;

public class ScalaTaskGraph extends AbstractTaskGraph {

	
	public ScalaTaskGraph add(Object function, Object...args){
		addInner(TaskUtils.scalaTask(function, args));
		return this;
	}
	
	public ScalaTaskGraph mapAllTo(DeviceMapping mapping){
		mapAllToInner(mapping);
		return this;
	}
	
	public ScalaTaskGraph streamIn(Object... objects){
		streamInInner(objects);
		return this;
	}
	
	public ScalaTaskGraph streamOut(Object... objects){
		streamOutInner(objects);
		return this;
	}
	
	public ScalaTaskGraph schedule(){
		scheduleInner();
		return this;
	}
}
