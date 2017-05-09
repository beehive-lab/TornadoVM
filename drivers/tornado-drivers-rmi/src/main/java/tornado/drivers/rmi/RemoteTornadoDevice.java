package tornado.drivers.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteTornadoDevice extends Remote {

    public String getDeviceName() throws RemoteException;

    public String getDescription() throws RemoteException;

    public String getMemoryProvider() throws RemoteException;

}
