package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;

public interface TornadoDriver {

    public TornadoDevice getDefaultDevice();

    public int getDeviceCount();

    public TornadoDevice getDevice(int index);

    public TornadoDeviceType getTypeDefaultDevice();

    public String getName();

}
