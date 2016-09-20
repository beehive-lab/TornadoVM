package tornado.runtime.api;

import java.util.HashMap;
import java.util.Map;

import tornado.common.DeviceMapping;
import tornado.common.DeviceObjectState;

public class GlobalObjectState {

	private boolean shared;
	private boolean exclusive;
	
	private DeviceMapping owner;
	
	private Map<DeviceMapping,DeviceObjectState> deviceStates;
	
	public GlobalObjectState(){
		shared = false;
		exclusive = false;
		owner = null;
		deviceStates = new HashMap<DeviceMapping,DeviceObjectState>();
	}
	
	public boolean isShared(){
		return shared;
	}
	
	public boolean isExclusive(){
		return exclusive;
	}
	
	public DeviceMapping getOwner(){
		return owner;
	}
	
	public DeviceObjectState getDeviceState(DeviceMapping device){
		if(!deviceStates.containsKey(device)){
			deviceStates.put(device, new DeviceObjectState());
		}
		return deviceStates.get(device);
	}
	
	public void setOwner(DeviceMapping device){
		owner = device;
		if(!deviceStates.containsKey(owner)){
			deviceStates.put(device, new DeviceObjectState());
		}
	}
	
	public void invalidate(){
		for(DeviceMapping device : deviceStates.keySet()){
			final DeviceObjectState deviceState = deviceStates.get(device);
			deviceState.invalidate();
		}
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder();
		
		sb.append((isExclusive())? "X" : "-");
		sb.append((isShared())?    "S" : "-");
		sb.append(" ");
		
		if(owner != null){
			sb.append("owner=" + owner.toString() + ", devices=[");
		}
		
		for(DeviceMapping device : deviceStates.keySet()){
			if(device != owner){
			sb.append(device.toString() + " ");
			}
		}
		
		sb.append("]");
		
		return sb.toString();
	}
	
}
