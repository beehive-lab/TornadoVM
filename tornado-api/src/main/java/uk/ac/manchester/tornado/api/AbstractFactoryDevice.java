package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.common.TornadoDevice;

public interface AbstractFactoryDevice {

    public TornadoDevice createDevice(int platform, int device);

}
