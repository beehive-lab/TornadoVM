package uk.ac.manchester.tornado.drivers.spirv.runtime;

import uk.ac.manchester.tornado.api.AbstractFactoryDevice;
import uk.ac.manchester.tornado.api.common.TornadoDevice;

public class SPIRVDeviceFactory implements AbstractFactoryDevice {

    @Override
    public TornadoDevice createDevice(int platform, int device) {
        return new SPIRVTornadoDevice(platform, device);
    }
}