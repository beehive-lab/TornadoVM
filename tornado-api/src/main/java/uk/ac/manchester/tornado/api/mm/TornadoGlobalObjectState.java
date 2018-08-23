package uk.ac.manchester.tornado.api.mm;

import uk.ac.manchester.tornado.api.common.TornadoDevice;

public interface TornadoGlobalObjectState {

    public boolean isShared();

    public boolean isExclusive();

    public TornadoDevice getOwner();

    public TornadoDeviceObjectState getDeviceState();

    public TornadoDeviceObjectState getDeviceState(TornadoDevice device);

    public void setOwner(TornadoDevice device);

    public void invalidate();

    public void clear();

}
