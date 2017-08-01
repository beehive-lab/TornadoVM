package tornado.drivers.rmi;

import org.graalvm.compiler.phases.util.Providers;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import tornado.common.Tornado;
import tornado.common.TornadoLogger;
import tornado.drivers.rmi.impl.ProxiedRemoteDevice;
import tornado.runtime.TornadoDriver;
import tornado.runtime.compiler.TornadoSuitesProvider;
import tornado.runtime.newapi.TornadoDevice;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class RMIDriver extends TornadoLogger implements TornadoDriver {

    private boolean available;
    private RemoteTornadoDriver remoteDriver;
    private String[] remoteDevices;
    private Registry registry;

    private Remote tryLookup(Registry registry, String name) {
        try {
            return registry.lookup(name);
        } catch (RemoteException | NotBoundException e) {
            error(e.toString());
        }
        return null;
    }

    public RMIDriver() {
        available = false;
        final String host = Tornado.getProperty("tornado.rmi.host");
        try {
            registry = LocateRegistry.getRegistry(host);
            remoteDriver = (RemoteTornadoDriver) tryLookup(registry, "RemoteTornadoDriver");
            available = true;

            remoteDevices = remoteDriver.getRMIDevices();
        } catch (RemoteException e) {
            fatal("RMI unable to register: %s", e.toString());
        }

    }

    @Override
    public TornadoDevice getDefaultDevice() {
        return getDevice(0);
    }

    @Override
    public TornadoDevice getDevice(int index) {
        if (remoteDevices[index] != null) {
            try {
                RemoteTornadoDevice remoteDevice = (RemoteTornadoDevice) tryLookup(registry, remoteDevices[index]);
                RemoteTornadoMemory remoteMemory = (RemoteTornadoMemory) tryLookup(registry, remoteDevice.getMemoryProvider());
                return new ProxiedRemoteDevice(remoteDevice, remoteMemory);
            } catch (RemoteException e) {
                fatal("unable to locate memory provider: %s", e.toString());
            }
        }
        return null;
    }

    @Override
    public int getDeviceCount() {
        if (available) {
            try {
                return remoteDriver.getDeviceCount();
            } catch (RemoteException e) {
                fatal("RMI unable to register: %s", e.toString());
                available = false;
            }
        }
        return 0;
    }

    @Override
    public String getName() {
        if (available) {
            try {
                return remoteDriver.getName() + " (rmi)";
            } catch (RemoteException e) {
                fatal("RMI unable to register: %s", e.toString());
                available = false;
            }
        }
        return "rmi-unavailable";
    }

    @Override
    public Providers getProviders() {
        unimplemented();
        return null;
    }

    @Override
    public TornadoSuitesProvider getSuitesProvider() {
        unimplemented();
        return null;
    }

}
