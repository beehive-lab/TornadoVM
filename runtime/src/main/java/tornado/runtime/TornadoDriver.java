package tornado.runtime;

import com.oracle.graal.phases.util.Providers;
import tornado.graal.compiler.TornadoSuitesProvider;
import tornado.common.TornadoDevice;

public interface TornadoDriver {

    public TornadoDevice getDefaultDevice();

    public int getDeviceCount();

    public TornadoDevice getDevice(int index);

    public Providers getProviders();

    public TornadoSuitesProvider getSuitesProvider();

    public String getName();
}
