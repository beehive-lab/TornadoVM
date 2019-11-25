package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class CUDADevice extends TornadoLogger implements TornadoTargetDevice {

    public CUDADevice(int index, long id) {

    }

    public long getId() {
        return 1;
    }

    @Override public String getDeviceName() {
        return "NVIDIA GPU";
    }

    @Override public long getDeviceGlobalMemorySize() {
        return 0;
    }

    @Override public long getDeviceLocalMemorySize() {
        return 0;
    }

    @Override public int getDeviceMaxComputeUnits() {
        return 0;
    }

    @Override public Object getDeviceInfo() {
        return null;
    }
}
