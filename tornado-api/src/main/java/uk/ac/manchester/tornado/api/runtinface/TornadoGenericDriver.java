package uk.ac.manchester.tornado.api.runtinface;

import uk.ac.manchester.tornado.api.common.GenericDevice;

public interface TornadoGenericDriver {

    public GenericDevice getDefaultDevice();

    public int getDeviceCount();

    public GenericDevice getDevice(int index);

    public String getName();

}
