package uk.ac.manchester.tornado.api.runtinface;

import uk.ac.manchester.tornado.api.common.GenericDevice;

public class TornadoRuntime {

    private static TornadoRuntimeInterface runtimeImpl;

    public static TornadoRuntimeInterface getTornadoRuntime() {
        if (runtimeImpl == null) {
            // Use reflection to call TornadoRuntime in core
        }
        return runtimeImpl;
    }

    public void clearObjectState() {
        runtimeImpl.clearObjectState();
    }

    public TornadoGenericDriver getDriver(int index) {
        return runtimeImpl.getDriver(index);
    }

    public <D extends TornadoGenericDriver> D getDriver(Class<D> type) {
        return runtimeImpl.getDriver(type);
    }

    public int getNumDrivers() {
        return runtimeImpl.getNumDrivers();
    }

    public GenericDevice getDefaultDevice() {
        return runtimeImpl.getDefaultDevice();
    }
}
