package uk.ac.manchester.tornado.drivers.opencl.runtime;

import uk.ac.manchester.tornado.api.AbstractFactoryDevice;
import uk.ac.manchester.tornado.api.common.TornadoDevice;

public class TornadoDeviceFactory implements AbstractFactoryDevice {

    @Override
    public TornadoDevice createDevice(int platform, int device) {
        return new OCLTornadoDevice(platform, device);
    }
}
