package uk.ac.manchester.tornado.api.runtime;

import uk.ac.manchester.tornado.api.AbstractFactoryDevice;
import uk.ac.manchester.tornado.api.TornadoCI;
import uk.ac.manchester.tornado.api.TornadoRuntimeCI;
import uk.ac.manchester.tornado.api.common.TornadoDevice;

public class TornadoRuntime {

    private static TornadoRuntimeCI runtimeImpl;
    private static TornadoCI tornadoImpl;
    private static AbstractFactoryDevice device;

    static {
        init();
    }

    private static void init() {
        if (runtimeImpl == null) {
            runtimeImpl = TornadoAPIProvider.loadRuntime();
        }
        if (tornadoImpl == null) {
            tornadoImpl = TornadoAPIProvider.loadTornado();
        }
        if (device == null) {
            device = TornadoAPIProvider.loadDeviceImpl();
        }
    }

    public static TornadoRuntimeCI getTornadoRuntime() {
        return runtimeImpl;
    }

    public void clearObjectState() {
        runtimeImpl.clearObjectState();
    }

    public static void setProperty(String key, String value) {
        tornadoImpl.setTornadoProperty(key, value);
    }

    public static String getProperty(String key, String value) {
        return tornadoImpl.getTornadoProperty(key, value);
    }

    public static String getProperty(String key) {
        return tornadoImpl.getTornadoProperty(key);
    }

    public static void loadSettings(String property) {
        tornadoImpl.loadTornadoSettngs(property);
    }

    public static TornadoDevice createDevice(int platformIndex, int deviceIndex) {
        return device.createDevice(platformIndex, deviceIndex);
    }
}
