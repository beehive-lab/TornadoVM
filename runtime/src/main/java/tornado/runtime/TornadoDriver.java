package tornado.runtime;

import com.oracle.graal.phases.util.Providers;
import tornado.common.DeviceMapping;
import tornado.graal.compiler.TornadoSuitesProvider;

public interface TornadoDriver {

    public DeviceMapping getDefaultDevice();

    public Providers getProviders();

    public TornadoSuitesProvider getSuitesProvider();
}
