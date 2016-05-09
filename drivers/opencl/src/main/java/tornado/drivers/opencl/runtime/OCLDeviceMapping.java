package tornado.drivers.opencl.runtime;

import tornado.api.DeviceMapping;
import tornado.api.enums.TornadoSchedulingStrategy;
import tornado.drivers.opencl.OCLDevice;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.enums.OCLDeviceType;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.runtime.TornadoRuntime;

public class OCLDeviceMapping implements DeviceMapping {
	private final OCLDevice	device;
	private final int		deviceIndex;
	private final int		platformIndex;

	public OCLDeviceMapping(final int platformIndex, final int deviceIndex) {
		this.platformIndex = platformIndex;
		this.deviceIndex = deviceIndex;
		device = ((OCLRuntime)TornadoRuntime.runtime).getPlatformContext(platformIndex).devices().get(deviceIndex);
		
	}

	public OCLDevice getDevice() {
		return device;
	}

	public int getDeviceIndex() {
		return deviceIndex;
	}

	public int getPlatformIndex() {
		return platformIndex;
	}
	
	public OCLDeviceContext getDeviceContext(){
		return getBackend().getDeviceContext();
	}
	
	public OCLBackend getBackend(){
		return ((OCLRuntime)TornadoRuntime.runtime).getBackend(platformIndex, deviceIndex);
	}
	
	public void reset(){
		getBackend().reset();
	}

	@Override
	public String toString() {
		return String.format(device.getName());
	}

	@Override
	public TornadoSchedulingStrategy getPreferedSchedule() {
		if(device.getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_CPU)
			return TornadoSchedulingStrategy.PER_BLOCK;
		else if(device.getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_GPU)
			return TornadoSchedulingStrategy.PER_ITERATION;
		else
			return TornadoSchedulingStrategy.PER_ITERATION;
	}
}
