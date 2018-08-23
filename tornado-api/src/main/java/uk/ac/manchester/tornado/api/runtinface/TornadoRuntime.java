package uk.ac.manchester.tornado.api.runtinface;

import uk.ac.manchester.tornado.api.common.GenericDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoAPIProvider;

public class TornadoRuntime {

    private static TornadoRuntimeCI runtimeImpl;
    private static TornadoCI tornadoImpl;

    public static TornadoRuntimeCI getTornadoRuntime() {
        if (runtimeImpl == null) {
            runtimeImpl = TornadoAPIProvider.loadRuntime();
        }
        if (tornadoImpl == null) {
            tornadoImpl = TornadoAPIProvider.loadTornado();
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

    public static void setProperty(String key, String value) {
        tornadoImpl.setTornadoProperty(key, value);
    }
}
