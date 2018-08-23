package uk.ac.manchester.tornado.api.runtime;

import uk.ac.manchester.tornado.api.TornadoCI;
import uk.ac.manchester.tornado.api.TornadoRuntimeCI;

public class TornadoRuntime {

    private static TornadoRuntimeCI runtimeImpl;
    private static TornadoCI tornadoImpl;

    private static void init() {
        if (runtimeImpl == null) {
            runtimeImpl = TornadoAPIProvider.loadRuntime();
        }
        if (tornadoImpl == null) {
            tornadoImpl = TornadoAPIProvider.loadTornado();
        }
    }

    public static TornadoRuntimeCI getTornadoRuntime() {
        init();
        return runtimeImpl;
    }

    public void clearObjectState() {
        init();
        runtimeImpl.clearObjectState();
    }

    public static void setProperty(String key, String value) {
        init();
        tornadoImpl.setTornadoProperty(key, value);
    }

    public static String getProperty(String key, String value) {
        init();
        return tornadoImpl.getTornadoProperty(key, value);
    }

    public static String getProperty(String key) {
        init();
        return tornadoImpl.getTornadoProperty(key);
    }

    public static void loadSettings(String property) {
        init();
        tornadoImpl.loadTornadoSettngs(property);
    }

}
