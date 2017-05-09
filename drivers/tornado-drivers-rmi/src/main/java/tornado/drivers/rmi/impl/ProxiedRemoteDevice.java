package tornado.drivers.rmi.impl;

import java.lang.reflect.Method;
import java.rmi.RemoteException;
import tornado.api.Event;
import tornado.api.enums.TornadoSchedulingStrategy;
import tornado.common.CallStack;
import tornado.common.DeviceObjectState;
import tornado.common.TornadoLogger;
import tornado.common.TornadoMemoryProvider;
import tornado.drivers.rmi.RemoteTornadoDevice;
import tornado.drivers.rmi.RemoteTornadoMemory;
import tornado.runtime.newapi.Meta;
import tornado.runtime.newapi.TornadoDevice;
import tornado.runtime.newapi.TornadoInstalledCode;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

import tornado.runtime.api.CodeProvider;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class ProxiedRemoteDevice extends TornadoLogger implements TornadoDevice {

    private final RemoteTornadoDevice remoteDevice;
    private final ProxiedRemoteMemory remoteMemory;

    public ProxiedRemoteDevice(RemoteTornadoDevice device, RemoteTornadoMemory memory) {
        remoteDevice = device;
        remoteMemory = new ProxiedRemoteMemory(memory);
    }

    @Override
    public Meta createMeta(Method method) {
        unimplemented();
        return null;
    }

    @Override
    public Meta createMeta(int numParameters) {
        unimplemented();
        return null;
    }

    @Override
    public CallStack createStack(int numArgs) {
        unimplemented();
        return null;
    }

    @Override
    public void dumpEvents() {
        unimplemented();
    }

    @Override
    public int enqueueBarrier() {
        unimplemented();
        return -1;
    }

    @Override
    public int ensureAllocated(Object object, DeviceObjectState state) {
        unimplemented();
        return -1;
    }

    @Override
    public void ensureLoaded() {
        unimplemented();
    }

    @Override
    public int ensurePresent(Object object, DeviceObjectState objectState) {
        unimplemented();
        return -1;
    }

    @Override
    public void flushEvents() {
        unimplemented();
    }

    @Override
    public String getDescription() {
        try {
            return remoteDevice.getDescription();
        } catch (RemoteException e) {
            error(e.toString());
        }
        return "rmi-error";
    }

    @Override
    public String getDeviceName() {
        try {
            return remoteDevice.getDeviceName();
        } catch (RemoteException e) {
            error(e.toString());
        }
        return "rmi-error";
    }

    @Override
    public TornadoMemoryProvider getMemoryProvider() {
        return remoteMemory;
    }

    @Override
    public TornadoSchedulingStrategy getPreferedSchedule() {
        unimplemented();
        return TornadoSchedulingStrategy.PER_BLOCK;
    }

    @Override
    public TornadoInstalledCode installCode(CodeProvider task) {
        unimplemented();
        return null;
    }

    @Override
    public boolean isDistibutedMemory() {
        unimplemented();
        return true;
    }

    @Override
    public void markEvent() {
        unimplemented();
    }

    @Override
    public void reset() {
        unimplemented();
    }

    @Override
    public Event resolveEvent(int event) {
        unimplemented();
        return null;
    }

    @Override
    public int streamIn(Object object, DeviceObjectState objectState) {
        unimplemented();
        return -1;
    }

    @Override
    public int streamOut(Object object, DeviceObjectState objectState, int[] list) {
        unimplemented();
        return -1;
    }

    @Override
    public void sync() {
        unimplemented();
    }

}
