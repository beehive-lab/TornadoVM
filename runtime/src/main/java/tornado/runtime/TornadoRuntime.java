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
import tornado.common.DeviceMapping;
import tornado.common.TornadoLogger;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import tornado.runtime.api.GlobalObjectState;

public class TornadoRuntime extends TornadoLogger {

    private static final Executor EXECUTOR = Executors.newCachedThreadPool();

    private static final TornadoRuntime runtime = new TornadoRuntime();

    private static final JVMMapping JVM = new JVMMapping();

    public static TornadoRuntime getTornadoRuntime(){
        return runtime;
    }
    
    public static Executor getTornadoExecutor(){
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

    private final TornadoDriver[] loadDrivers() {
//        final String driversString = Tornado.getProperty("tornado.runtime.drivers",
//                "tornado.drivers.opencl.OCLDriver");
//
//        final String[] classNames = driversString.split(",");
//        final TornadoDriver[] drivers = new TornadoDriver[classNames.length];
//
//        debug("loading %d drivers:", drivers.length);
//        for (int i = 0; i < classNames.length; i++) {
//            try {
//                debug("\t[%d] %s", i, classNames[i]);
//                final Class<?> klazz = Class.forName(classNames[i]);
//                final Constructor<?> constructor = klazz.getConstructor(
//                        HotSpotGraalRuntimeProvider.class, HotSpotProviders.class);
//                drivers[i] = (TornadoDriver) constructor.newInstance(vmRuntime,
//                        vmProviders);
//            } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
//                throw new TornadoRuntimeException("Unable to load driver: " + e.getMessage());
//            }
//
//        }

        
        ServiceLoader<TornadoDriverProvider> loader  = ServiceLoader.load(TornadoDriverProvider.class);
        TornadoDriver[] drivers = new TornadoDriver[0];
        for(TornadoDriverProvider provider: loader){
            System.out.printf("found: %s\n",provider.getName());
        }

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
    
    public <D extends TornadoDriver> D getDriver(Class<D> type){
        for(TornadoDriver driver : drivers){
            if(driver.getClass() == type ){
                return (D) driver;
            }
        }
        return null;
    }

    public int getNumDrivers() {
        return drivers.length;
    }
    
    public DeviceMapping getDefaultDevice(){
        return (drivers == null || drivers[defaultDriver] == null) ? JVM : drivers[defaultDriver].getDefaultDevice();
    }
}
