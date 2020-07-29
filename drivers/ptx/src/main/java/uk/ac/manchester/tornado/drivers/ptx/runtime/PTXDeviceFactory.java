package uk.ac.manchester.tornado.drivers.ptx.runtime;

import uk.ac.manchester.tornado.api.AbstractFactoryDevice;
import uk.ac.manchester.tornado.api.common.TornadoDevice;

public class PTXDeviceFactory implements AbstractFactoryDevice {

    @Override
    public TornadoDevice createDevice(int platform, int deviceIndex) {
        return new PTXTornadoDevice(deviceIndex);
    }
}
