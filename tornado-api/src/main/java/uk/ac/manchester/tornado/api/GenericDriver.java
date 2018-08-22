package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.common.GenericDevice;

public interface GenericDriver {

    public GenericDevice getDefaultDevice();

    public int getDeviceCount();

    public GenericDevice getDevice(int index);

    public String getName();
}