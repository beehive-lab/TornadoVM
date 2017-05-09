package tornado.drivers.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteTornadoDriver extends Remote {

    public String getName() throws RemoteException;

    public int getDeviceCount() throws RemoteException;

    public String getDeviceName(int index) throws RemoteException;

    public String getDeviceDescription(int index) throws RemoteException;

    public String[] getRMIDevices() throws RemoteException;
}
