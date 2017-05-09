package tornado.drivers.rmi;

import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Level;
import java.util.logging.Logger;
import tornado.drivers.rmi.impl.ProxiedLocalDevice;
import tornado.drivers.rmi.impl.ProxiedLocalMemory;
import tornado.runtime.TornadoDriver;

import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

public class RemoteTornadoServer implements RemoteTornadoDriver {

    final TornadoDriver localDriver;
    final ProxiedLocalDevice[] proxyDevices;
    final ProxiedLocalMemory[] proxyMemories;

    public RemoteTornadoServer() {
        final int index = Integer.parseInt(System.getProperty("tornado.rmi.driver", "0"));
        localDriver = getTornadoRuntime().getDriver(index);
        proxyDevices = new ProxiedLocalDevice[localDriver.getDeviceCount()];
        proxyMemories = new ProxiedLocalMemory[proxyDevices.length];
        for (int i = 0; i < proxyDevices.length; i++) {
            proxyDevices[i] = new ProxiedLocalDevice(localDriver.getDevice(i));
            proxyMemories[i] = new ProxiedLocalMemory(localDriver.getDevice(i).getMemoryProvider());

        }
    }

    @Override
    public int getDeviceCount() {
        return localDriver.getDeviceCount();
    }

    @Override
    public String getDeviceDescription(int index) {
        return localDriver.getDevice(index).getDescription();
    }

    @Override
    public String getDeviceName(int index) {
        return localDriver.getDevice(index).getDeviceName();
    }

    @Override
    public String getName() {
        return localDriver.getName();
    }

    @Override
    public String[] getRMIDevices() {
        final String[] names = new String[proxyDevices.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = proxyDevices[i].getRMIName();
        }
        return names;
    }

    public void publishDevices(Registry registry) {
        for (ProxiedLocalDevice device : proxyDevices) {
            tryPublish(registry, device.getRMIName(), device);
        }

        for (ProxiedLocalMemory memory : proxyMemories) {
            tryPublish(registry, memory.getRMIName(), memory);
        }
    }

    private static void tryUnbind(Registry registry, String name) {
        try {
            registry.unbind(name);
        } catch (NotBoundException | AccessException ex) {
            Logger.getLogger(RemoteTornadoServer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteException ex) {
            Logger.getLogger(RemoteTornadoServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void tryPublish(Registry registry, String name, Remote object) {
        try {
            Remote remote = UnicastRemoteObject.exportObject(object, 0);
            registry.bind(name, remote);
            System.out.println("published " + name);
        } catch (RemoteException | AlreadyBoundException ex) {
            Logger.getLogger(RemoteTornadoServer.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static void main(String args[]) {

        try {
            RemoteTornadoServer server = new RemoteTornadoServer();

            Registry registry = LocateRegistry.getRegistry();

            tryUnbind(registry, "RemoteTornadoDriver");
            tryPublish(registry, "RemoteTornadoDriver", server);

            server.publishDevices(registry);

            System.out.println("server ready");
        } catch (RemoteException ex) {
            Logger.getLogger(RemoteTornadoServer.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
