package tornado.runtime;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;
import tornado.common.TornadoDevice;
import tornado.common.TornadoLogger;
import tornado.runtime.api.GlobalObjectState;

import static tornado.common.Tornado.SHOULD_LOAD_RMI;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public class TornadoRuntime extends TornadoLogger {

    private static final Executor EXECUTOR = Executors.newCachedThreadPool();

    private static final TornadoRuntime runtime = new TornadoRuntime();

    private static final JVMMapping JVM = new JVMMapping();

    public static TornadoRuntime getTornadoRuntime() {
        return runtime;
    }

    public static Executor getTornadoExecutor() {
        return EXECUTOR;
    }

    public static JVMCIBackend getVMBackend() {
        return runtime.vmBackend;
    }

    public static HotSpotJVMCIRuntime getVMRuntime() {
        return runtime.vmRuntime;
    }

    public static TornadoVMConfig getVMConfig() {
        return runtime.vmConfig;
    }

    private final Map<Object, GlobalObjectState> objectMappings;
    private TornadoDriver[] drivers;
    private int driverCount;
    private final JVMCIBackend vmBackend;
    private final HotSpotJVMCIRuntime vmRuntime;
    private final TornadoVMConfig vmConfig;
    private final int defaultDriver = 0;

    public TornadoRuntime() {
        objectMappings = new WeakHashMap<>();

        if (!(JVMCI.getRuntime() instanceof HotSpotJVMCIRuntime)) {
            shouldNotReachHere("Unsupported JVMCIRuntime: ", JVMCI.getRuntime().getClass().getName());
        }
        vmRuntime = (HotSpotJVMCIRuntime) JVMCI.getRuntime();

        vmBackend = vmRuntime.getHostJVMCIBackend();
        vmConfig = new TornadoVMConfig(vmRuntime.getConfigStore());

        drivers = loadDrivers();
    }

    public void clearObjectState() {
        objectMappings.clear();
    }

    private TornadoDriver[] loadDrivers() {
        ServiceLoader<TornadoDriverProvider> loader = ServiceLoader.load(TornadoDriverProvider.class);
        drivers = new TornadoDriver[2];
        int index = 0;
        for (TornadoDriverProvider provider : loader) {
            boolean isRMI = provider.getName().equalsIgnoreCase("RMI Driver");
            if ((!isRMI) || (isRMI && SHOULD_LOAD_RMI)) {
                drivers[index] = provider.createDriver(vmRuntime, vmConfig);
                if (drivers[index] != null) {
                    index++;
                }
            }
        }

        driverCount = index;

        return drivers;
    }

    public GlobalObjectState resolveObject(Object object) {
        if (!objectMappings.containsKey(object)) {
            final GlobalObjectState state = new GlobalObjectState();
            objectMappings.put(object, state);
        }
        return objectMappings.get(object);
    }

    public MetaAccessProvider getMetaAccess() {
        return vmBackend.getMetaAccess();
    }

    public ResolvedJavaMethod resolveMethod(final Method method) {
        return getMetaAccess().lookupJavaMethod(method);
    }

    public TornadoDriver getDriver(int index) {
        return drivers[index];
    }

    public <D extends TornadoDriver> D getDriver(Class<D> type) {
        for (TornadoDriver driver : drivers) {
            if (driver.getClass() == type) {
                return (D) driver;
            }
        }
        return null;
    }

    public int getNumDrivers() {
        return driverCount;
    }

    public TornadoDevice getDefaultDevice() {
        return (drivers == null || drivers[defaultDriver] == null) ? JVM : drivers[defaultDriver].getDefaultDevice();
    }
}
