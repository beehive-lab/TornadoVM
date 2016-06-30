package tornado.common;

import tornado.common.DeviceObjectState;

public interface CallStack {

	public void reset();	
	public long getDeoptValue(); 
	public long getReturnValue(); 
	public int getArgCount(); 
	public void push(Object arg);
	public void push(Object arg, DeviceObjectState state);

	public boolean isOnDevice();
	public void dump();
	
	public void clearProfiling();
	public long getInvokeCount();
	public double getTimeTotal();
	public double getTimeMean();
	public double getTimeMin();
	public double getTimeMax();
	public double getTimeSD();
}
