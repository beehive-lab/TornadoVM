package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.util.List;

public class CUDAContext extends TornadoLogger {
    private List<CUDADevice> devices;

    public CUDAContext(CUDAPlatform platform, long contextID, List<CUDADevice> devices) {
        this.devices = devices;
    }

    public List<CUDADevice> devices() {
        return devices;
    }

    public void cleanup() {

    }

    public int getNumDevices() {
        return 1;
    }
}
