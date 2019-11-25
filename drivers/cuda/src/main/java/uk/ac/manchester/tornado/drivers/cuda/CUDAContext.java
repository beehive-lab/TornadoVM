package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.util.List;

public class CUDAContext extends TornadoLogger {
    public CUDAContext(CUDAPlatform platform, long contextID, List<CUDADevice> devices) {

    }

    public void cleanup() {

    }

    public int getNumDevices() {
        return 1;
    }
}
