package tornado.drivers.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteTornadoMemory extends Remote {

    public long getCallStackSize() throws RemoteException;

    public long getCallStackAllocated() throws RemoteException;

    public long getCallStackRemaining() throws RemoteException;

    public long getHeapSize() throws RemoteException;

    public long getHeapRemaining() throws RemoteException;

    public long getHeapAllocated() throws RemoteException;

    public boolean isInitialised() throws RemoteException;

}
