package uk.ac.manchester.tornado.unittests.common;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;

public interface TornadoTestBaseDefaults {
    public void before();

    public TornadoDeviceType getDefaultDeviceType();

    public TornadoDevice getDevice();
}
