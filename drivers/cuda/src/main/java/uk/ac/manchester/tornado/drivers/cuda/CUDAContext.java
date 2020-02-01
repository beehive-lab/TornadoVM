package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.util.ArrayList;
import java.util.List;

public class CUDAContext extends TornadoLogger {

    private final List<CUDADevice> devices;
    private final List<CUDADeviceContext> deviceContexts;
    private final CUDAPlatform platform;

    public CUDAContext(CUDAPlatform platform, long contextID, List<CUDADevice> devices) {
        this.platform = platform;
        this.devices = devices;
        deviceContexts = new ArrayList<>(devices.size());
    }

    public List<CUDADevice> devices() {
        return devices;
    }

    public void cleanup() {

    }

    public int getNumDevices() {
        return 1;
    }

    public int getPlatformIndex() {
        return platform.getIndex();
    }

    public CUDADeviceContext createDeviceContext(int index) {
        debug("creating device context for device: %s", devices.get(index).toString());
        //createCommandQueue(index);
        final CUDADeviceContext deviceContext = new CUDADeviceContext(devices.get(index), this);
        deviceContexts.add(deviceContext);
        return deviceContext;
    }
}
