package tornado.drivers.opencl.runtime;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import tornado.common.TornadoLogger;
import tornado.drivers.opencl.OCLContext;
import tornado.drivers.opencl.OCLDevice;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.OCLPlatform;
import tornado.drivers.opencl.OpenCL;
import tornado.graal.TornadoTargetDescription;
import tornado.runtime.DataMovementTask;
import tornado.runtime.ObjectReference;
import tornado.runtime.RuntimeInstance;
import tornado.runtime.TornadoRuntime;
import tornado.runtime.api.ExecutableTask;
import tornado.runtime.api.TaskUtils;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.OCLArchitecture;
import tornado.drivers.opencl.graal.OCLProviders;
import tornado.drivers.opencl.graal.OCLSuitesProvider;
import tornado.drivers.opencl.graal.OpenCLCodeCache;
import tornado.drivers.opencl.graal.OCLLoweringProvider;

import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.meta.HotSpotProviders;

public class OCLRuntime extends TornadoLogger implements RuntimeInstance<OCLDeviceContext> {

    private final OCLBackend[][] backends;
    private final List<OCLContext> contexts;

    public OCLRuntime(final HotSpotGraalRuntimeProvider vmRuntime,
            final HotSpotProviders vmProviders) {
    	super();
        final int numPlatforms = OpenCL.getNumPlatforms();
        backends = new OCLBackend[numPlatforms][];

        contexts = new ArrayList<OCLContext>();

        discoverDevices(vmRuntime, vmProviders);
    }

    private OCLBackend checkAndInitBackend(final int platform,
            final int device) {
        final OCLBackend backend = backends[platform][device];
        if (!backend.isInitialised()) {
            backend.init();
        }

        return backend;
    }

    public void clearObjectCache() {
		// TODO Auto-generated method stub
		
	}

    private OCLBackend createOCLBackend(
            final HotSpotGraalRuntimeProvider vmRuntime,
            final HotSpotProviders vmProviders, final OCLContext context,
            final int deviceIndex) {
        final OCLDevice device = context.devices().get(deviceIndex);
        info("Creating backend for %s", device.getName());
        final TornadoTargetDescription target = new TornadoTargetDescription(
                new OCLArchitecture(device.getWordSize(), device.getByteOrder()));
        final OpenCLCodeCache codeCache = null;
        final OCLLoweringProvider lowerer = new OCLLoweringProvider(
                vmRuntime, vmProviders.getMetaAccess(),
                vmProviders.getForeignCalls(), target);
        lowerer.initialize(vmProviders, vmRuntime.getConfig());
        final OCLProviders providers = new OCLProviders(vmRuntime,
                vmProviders, vmProviders.getSuites(),
                vmProviders.getGraphBuilderPlugins(), lowerer, codeCache);

        return new OCLBackend(providers, target, context, deviceIndex);
    }

    @Override
	public ExecutableTask<OCLDeviceContext> createTask(Method method, Object code,
			boolean extractCVs, Object... args) {
		 final int numArgs;
	        final Object[] cvs;

	        if (extractCVs) {
	            cvs = TaskUtils.extractCapturedVariables(code);
	            numArgs = cvs.length + args.length;
	        } else {
	            cvs = null;
	            numArgs = args.length;
	        }
	        final boolean isStatic = Modifier.isStatic(method.getModifiers());

	        final Object[] parameters = new Object[numArgs];
	        int index = 0;
	        if (extractCVs) {
	            for (Object cv : cvs) {
	                parameters[index] = cv;
	                index++;
	            }
	        }

	        for (Object arg : args) {
	            parameters[index] = arg;
	            index++;
	        }

	        final Object thisObject = (isStatic) ? null : code;
	        return new OCLExecutableTask(method, thisObject, parameters);
	}

    protected void discoverDevices(final HotSpotGraalRuntimeProvider vmRuntime,
            final HotSpotProviders vmProviders) {
        final int numPlatforms = OpenCL.getNumPlatforms();
        if (numPlatforms > 0) {

            for (int i = 0; i < numPlatforms; i++) {
                final OCLPlatform platform = OpenCL.getPlatform(i);

                info("OpenCL[%d]: Platform %s", i, platform.getName());
                final OCLContext context = platform.createContext();
                contexts.add(context);
                final int numDevices = context.getNumDevices();
                info("OpenCL[%d]: Has %d devices...", i, numDevices);

                backends[i] = new OCLBackend[numDevices];

                for (int j = 0; j < numDevices; j++) {
                    final OCLDevice device = context.devices().get(j);
                    info("OpenCL[%d]: device=%s", i, device.getName());

                    backends[i][j] = createOCLBackend(vmRuntime, vmProviders,
                            context, j);

                }
            }
        }
    }

    public void dumpEvents() {
		// TODO Auto-generated method stub
		
	}

    public OCLBackend getBackend(int platform, int device) {
        return checkAndInitBackend(platform, device);
    }

    public OCLBackend getDefaultBackend() {
        return checkAndInitBackend(0, 0);
    }

	public int getNumDevices(int platform) {
        return backends[platform].length;
    }

	public int getNumPlatforms() {
        return backends.length;
    }

	public OCLContext getPlatformContext(final int index) {
        return contexts.get(index);
    }

	public OCLSuitesProvider getSuitesProvider() {
		return null;
	}

	@Override
	public void makeVolatile(Object... objects) {
		for(Object object : objects){
			TornadoRuntime.resolveObject(object).setVolatile(true);
		}
		
	}

	public DataMovementTask markHostDirty(Object... objects) {
		return new DataMovementTask(new OCLMarkHostDirtyAction(), objects);
	}

	@Override
	public DataMovementTask read(Object... objects) {
		return new DataMovementTask(new OCLReadAction(), objects);
	}

	public <T> ObjectReference<OCLDeviceContext,T> register(T object) {
		return new OCLObjectReference<T>(object,5);
	}

	public void sync() {
		// TODO Auto-generated method stub
		
	}

	public DataMovementTask write(Object... objects) {
		 return new DataMovementTask(new OCLWriteAction(objects.length), objects);
	}
}
