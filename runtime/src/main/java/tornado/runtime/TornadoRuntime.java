package tornado.runtime;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import tornado.common.DeviceMapping;
import tornado.common.Tornado;
import tornado.common.TornadoLogger;
import tornado.common.exceptions.TornadoRuntimeException;
import tornado.runtime.api.GlobalObjectState;

import com.oracle.graal.api.meta.MetaAccessProvider;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.api.runtime.Graal;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.runtime.RuntimeProvider;


public class TornadoRuntime extends TornadoLogger {

	public static final TornadoRuntime runtime = new TornadoRuntime();

	public static final JVMMapping JVM = new JVMMapping();

	private final Map<Object, GlobalObjectState> objectMappings;
	private TornadoDriver[] drivers;
	
	public TornadoRuntime(){
	 objectMappings = new WeakHashMap<Object, GlobalObjectState>();
		vmRuntime = (HotSpotGraalRuntimeProvider) Graal
				.getRequiredCapability(RuntimeProvider.class);
		vmProviders = vmRuntime.getHostBackend().getProviders();
		try {
			drivers = loadDrivers(vmRuntime, vmProviders);
		} catch (TornadoRuntimeException e) {
			drivers = null;
			System.exit(-1);
		}
	}
	
	public void clearObjectState() {
		objectMappings.clear();
	}
	
	public static HotSpotProviders getVMProviders() {
		return runtime.vmProviders;
	}

	public static HotSpotGraalRuntimeProvider getVMRuntimeProvider() {
		return runtime.vmRuntime;
	}

	private final TornadoDriver[] loadDrivers(
			HotSpotGraalRuntimeProvider vmRuntime, HotSpotProviders vmProviders) throws TornadoRuntimeException {
		final String driversString = Tornado.getProperty("tornado.runtime.drivers",
				"tornado.drivers.opencl.OCLDriver");
		
		final String[] classNames = driversString.split(",");
		final TornadoDriver[] drivers = new TornadoDriver[classNames.length];

		debug("loading %d drivers:",drivers.length);
		for(int i=0;i<classNames.length;i++){
		try {
			debug("\t[%d] %s",i,classNames[i]);
			final Class<?> klazz = Class.forName(classNames[i]);
			final Constructor<?> constructor = klazz.getConstructor(
					HotSpotGraalRuntimeProvider.class, HotSpotProviders.class);
			drivers[i] = (TornadoDriver) constructor.newInstance(vmRuntime,
					vmProviders);
		} catch (ClassNotFoundException | NoSuchMethodException
				| SecurityException | InstantiationException
				| IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new TornadoRuntimeException("Unable to load driver: " + e.getMessage());
		}
		
		}
		
		return drivers;
	}

	public GlobalObjectState resolveObject(Object object) {
		if(!objectMappings.containsKey(object)){
			final GlobalObjectState state = new GlobalObjectState();
			objectMappings.put(object, state);
		}
		return objectMappings.get(object);
	}

	private final HotSpotProviders vmProviders;

	private final HotSpotGraalRuntimeProvider vmRuntime;

	public MetaAccessProvider getMetaAccess() {
		return vmProviders.getMetaAccess();
	}

	public ResolvedJavaMethod resolveMethod(final Method method) {
		return getMetaAccess().lookupJavaMethod(method);
	}
	
	public TornadoDriver getDriver(int index){
		return drivers[index];
	}
	
	public int getNumDrivers(){
		return drivers.length;
	}

}
