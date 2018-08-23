package uk.ac.manchester.tornado.api.mm;

import uk.ac.manchester.tornado.api.common.GenericDevice;

public interface TornadoObjectState {

    public boolean isShared();

    public boolean isExclusive();

    public GenericDevice getOwner();

    public TornadoDeviceObjectState getDeviceState();

    public TornadoDeviceObjectState getDeviceState(GenericDevice device);

    public void setOwner(GenericDevice device);

    public void invalidate();

    public void clear();

}
