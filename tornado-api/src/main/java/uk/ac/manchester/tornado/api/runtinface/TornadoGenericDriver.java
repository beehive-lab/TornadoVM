package uk.ac.manchester.tornado.api.runtinface;

import uk.ac.manchester.tornado.api.common.GenericDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;

public interface TornadoGenericDriver {

    public GenericDevice getDefaultDevice();

    public int getDeviceCount();

    public GenericDevice getDevice(int index);

    public TornadoDeviceType getTypeDefaultDevice();

    public String getName();

}
