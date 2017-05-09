package tornado.drivers.rmi.impl;

import java.rmi.RemoteException;
import tornado.common.TornadoLogger;
import tornado.common.TornadoMemoryProvider;
import tornado.drivers.rmi.RemoteTornadoMemory;

public class ProxiedRemoteMemory extends TornadoLogger implements TornadoMemoryProvider {

    private RemoteTornadoMemory remoteMemory;

    public ProxiedRemoteMemory(RemoteTornadoMemory memory) {
        remoteMemory = memory;
    }

    @Override
    public long getCallStackAllocated() {
        try {
            return remoteMemory.getCallStackAllocated();
        } catch (RemoteException e) {
            error(e.toString());
        }
        return -1;
    }

    @Override
    public long getCallStackRemaining() {
        try {
            return remoteMemory.getCallStackRemaining();
        } catch (RemoteException e) {
            error(e.toString());
        }
        return -1;
    }

    @Override
    public long getCallStackSize() {
        try {
            return remoteMemory.getCallStackSize();
        } catch (RemoteException e) {
            error(e.toString());
        }
        return -1;
    }

    @Override
    public long getHeapAllocated() {
        try {
            return remoteMemory.getHeapAllocated();
        } catch (RemoteException e) {
            error(e.toString());
        }
        return -1;
    }

    @Override
    public long getHeapRemaining() {
        try {
            return remoteMemory.getHeapRemaining();
        } catch (RemoteException e) {
            error(e.toString());
        }
        return -1;
    }

    @Override
    public long getHeapSize() {
        try {
            return remoteMemory.getHeapSize();
        } catch (RemoteException e) {
            error(e.toString());
        }
        return -1;
    }

    @Override
    public boolean isInitialised() {
        try {
            return remoteMemory.isInitialised();
        } catch (RemoteException e) {
            error(e.toString());
        }
        return false;
    }

}
