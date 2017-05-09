package tornado.api.meta;

import tornado.common.TornadoDevice;
import tornado.runtime.TornadoDriver;

import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

public final class MetaDataUtils {

    protected static TornadoDevice resolveDevice(String device) {
        final String[] ids = device.split(":");
        final TornadoDriver driver = getTornadoRuntime().getDriver(Integer.parseInt(ids[0]));
        return driver.getDevice(Integer.parseInt(ids[1]));
    }
}
