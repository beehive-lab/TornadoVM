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
import java.util.List;
import java.util.Map;
import java.util.Set;

import tornado.api.DeviceMapping;
import tornado.common.Tornado;
import tornado.common.TornadoLogger;
import com.oracle.graal.api.meta.MetaAccessProvider;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.api.runtime.Graal;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.runtime.RuntimeProvider;

public class TornadoRuntime extends TornadoLogger {

    public static RuntimeInstance<?> runtime;
    public static final TornadoRuntime inner = new TornadoRuntime();

    public static ResolvedJavaMethod resolveMethod(final Method method) {
        return inner.resolveMethodInner(method);
    }

    private final static RuntimeInstance<?> loadRuntime(HotSpotGraalRuntimeProvider vmRuntime, HotSpotProviders vmProviders) {
    	final String className = Tornado.getProperty("tornado.runtime.class", "tornado.drivers.opencl.runtime.OCLRuntime");
    	
    	try {
    	final Class<?> klazz = Class.forName(className);
    	final Constructor<?> constructor = klazz.getConstructor(HotSpotGraalRuntimeProvider.class,HotSpotProviders.class);
    		return (RuntimeInstance<?>) constructor.newInstance(vmRuntime,vmProviders);
    	} catch(ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e){
    		e.printStackTrace();
    	}
		return null;
	}

	public static <T> ObjectReference<?,T> resolveObject(final T object) {
		return (objectMappings.containsKey(object)) ? (ObjectReference<?,T>) objectMappings
                .get(object) : inner.registerObject(object);
    }


    private final HotSpotProviders vmProviders;
    private final HotSpotGraalRuntimeProvider vmRuntime;

    private static final Set<ObjectReference<?,?>> aliveReferences = new HashSet<ObjectReference<?,?>>();
    private static final ReferenceQueue<ObjectReference<?,?>> deadReferences= new ReferenceQueue<ObjectReference<?,?>>();

    private final static int HISTORY_LENGTH = 5;

    private static final Map<Object, ObjectReference<?,?>> objectMappings= new HashMap<Object, ObjectReference<?,?>>();

    public TornadoRuntime() {
        vmRuntime = (HotSpotGraalRuntimeProvider) Graal
                .getRequiredCapability(RuntimeProvider.class);
        vmProviders = vmRuntime.getHostBackend().getProviders();
        runtime = loadRuntime(vmRuntime, vmProviders);
    }

    protected <T> ObjectReference<?,T> registerObject(T object) {
        info("registering object: %s", object);
        ObjectReference<?,T> ref = runtime.register(object);
        aliveReferences.add(ref);
        objectMappings.put(object, ref);
        return ref;
    }

    public ObjectReference<?,?>[] registerObjects(Object... objects) {
        ObjectReference<?,?>[] refs = new ObjectReference<?,?>[objects.length];
        for (int i = 0; i < objects.length; i++)
            refs[i] = registerObject(objects[i]);
        return refs;
    }

    public void printHeapTrace(DeviceMapping deviceMapping) {
        final List<ObjectReference<?,?>> refs = new ArrayList<ObjectReference<?,?>>(
                objectMappings.size());
        for (ObjectReference<?,?> ref : objectMappings.values()) {
            if (ref.isOnDevice(deviceMapping))
                refs.add(ref);
        }

        refs.sort(new Comparator<ObjectReference<?,?>>() {

            @Override
            public int compare(ObjectReference<?,?> o1, ObjectReference<?,?> o2) {
                return Long.compareUnsigned(
                        o1.toAbsoluteAddress(deviceMapping),
                        o2.toAbsoluteAddress(deviceMapping));
            }

        });

        for (ObjectReference<?,?> ref : refs) {
            ref.printHeapTrace(deviceMapping);
        }
    }

    public static void resetDevices() {
        inner.clearObjectCache();
    }

    public void clearObjectCache() {
        objectMappings.clear();
        aliveReferences.clear();

        // for (int platformIndex = 0; platformIndex <
        // oclRuntime.getNumPlatforms(); platformIndex++) {
        // for (int deviceIndex = 0; deviceIndex <
        // oclRuntime.getNumDevices(platformIndex); deviceIndex++) {
        // final OCLDeviceContext device = oclRuntime.getBackend(platformIndex,
        // deviceIndex)
        // .getDeviceContext();
        // device.reset();
        // }
        // }

        System.gc();
    }

    public void sync() {
        runtime.sync();
    }

//    protected void syncInner() {
//        Map<OCLDeviceContext, List<Event>> waitEvents = new HashMap<OCLDeviceContext, List<Event>>();
//        for (ObjectReference<?> ref : aliveReferences) {
//            if (ref.isAlive() && ref.isModified())
//                ref.enqueueSync(waitEvents);
//        }
//
//        for (OCLDeviceContext device : waitEvents.keySet()) {
//            Tornado.info("syncing device %s", device.getDevice().getName());
//            List<Event> events = waitEvents.get(device);
//            device.enqueueBarrier(events).waitOn();
//        }
//    }

    public void gc() {
        fatal("garbage collection triggered...");

        /*
         * De-allocated all space associated with garbaged collected objects
         */
        Reference<? extends ObjectReference<?,?>> refToRef = deadReferences
                .poll();
        while (refToRef != null) {
            ObjectReference<?,?> ref = refToRef.get();
            ref.clear();
        }

        /*
		 * 
		 */
        // for(OpenCLTornadoBackend backend : oclRuntime.backends()){
        // if(backend.isInitialised()){
        // backend.getDeviceContext().getMemoryManager().printObjectBuffsers();
        // }
        // }
    }

    public MetaAccessProvider getMetaAccess() {
        return vmProviders.getMetaAccess();
    }

    public ResolvedJavaMethod resolveMethodInner(final Method method) {
        return getMetaAccess().lookupJavaMethod(method);
    }

    public static HotSpotGraalRuntimeProvider getVMRuntimeProvider() {
        return inner.vmRuntime;
    }

    public static HotSpotProviders getVMProviders() {
        return inner.vmProviders;
    }

//    public static void dumpEvents() {
//        for (OpenCLTornadoBackend backend : runtime.getOCLRuntime().backends()) {
//            OCLDeviceContext device = backend.getDeviceContext();
//            if (device.events().isEmpty())
//                continue;
//
//            device.events().sort(new Comparator<OCLEvent>() {
//
//                @Override
//                public int compare(OCLEvent o1, OCLEvent o2) {
//                    if (o1.getCLStartTime() < o2.getCLStartTime()) {
//                        return -1;
//                    } else if (o1.getCLStartTime() > o2.getCLStartTime()) {
//                        return 1;
//                    } else {
//                        return 0;
//
//                    }
//                }
//
//            });
//
//            for (OCLEvent event : device.events()) {
//                event.waitOn();
//                System.out
//                        .printf("\tname=%-15s, queued=%f, execution=%f, total=%f, status=%s, device=%s\n",
//                                event.getName(), event.getQueuedTime(),
//                                event.getExecutionTime(), event.getTotalTime(),
//                                event.getStatus(), device.getDevice().getName());
//            }
//        }
//    }

}
