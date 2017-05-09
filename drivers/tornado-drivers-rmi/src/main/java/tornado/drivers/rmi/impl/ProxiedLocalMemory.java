package tornado.drivers.rmi.impl;

import java.rmi.RemoteException;
import tornado.common.TornadoMemoryProvider;
import tornado.drivers.rmi.RemoteTornadoMemory;

public class ProxiedLocalMemory implements RemoteTornadoMemory {

    private final TornadoMemoryProvider localMemory;

    public ProxiedLocalMemory(TornadoMemoryProvider memory) {
        localMemory = memory;
    }

    @Override
    public long getCallStackAllocated() throws RemoteException {
        return localMemory.getCallStackAllocated();
    }

    @Override
    public long getCallStackRemaining() throws RemoteException {
        return localMemory.getCallStackRemaining();
    }

    @Override
    public long getCallStackSize() throws RemoteException {
        return localMemory.getCallStackSize();
    }

    @Override
    public long getHeapAllocated() throws RemoteException {
        return localMemory.getHeapAllocated();
    }

    @Override
    public long getHeapRemaining() throws RemoteException {
        return localMemory.getHeapRemaining();
    }

    @Override
    public long getHeapSize() throws RemoteException {
        return localMemory.getHeapSize();
    }

    @Override
    public boolean isInitialised() throws RemoteException {
        return localMemory.isInitialised();
    }

    public String getRMIName() {
        return String.format("RemoteTornadoMemory-%x", localMemory.hashCode());
    }

}
