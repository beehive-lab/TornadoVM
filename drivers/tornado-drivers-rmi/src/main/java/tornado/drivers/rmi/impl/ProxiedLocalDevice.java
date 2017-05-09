package tornado.drivers.rmi.impl;

import java.rmi.RemoteException;
import tornado.drivers.rmi.RemoteTornadoDevice;
import tornado.runtime.newapi.TornadoDevice;

public class ProxiedLocalDevice implements RemoteTornadoDevice {

    private final TornadoDevice localDevice;

    public ProxiedLocalDevice(TornadoDevice device) {
        localDevice = device;
    }

    @Override
    public String getDescription() {
        return localDevice.getDescription();
    }

    @Override
    public String getDeviceName() {
        return localDevice.getDeviceName();
    }

    @Override
    public String getMemoryProvider() throws RemoteException {
        return String.format("RemoteTornadoMemory-%x", localDevice.getMemoryProvider().hashCode());
    }

    public String getRMIName() {
        return String.format("RemoteTornadoDevice-%x", localDevice.hashCode());
    }

}
