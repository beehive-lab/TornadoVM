package tornado.drivers.opencl;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import tornado.common.DeviceMapping;
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
import tornado.runtime.TornadoDriver;
import tornado.runtime.TornadoRuntime;
import tornado.runtime.api.CompilableTask;
import tornado.runtime.api.TaskUtils;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.OCLArchitecture;
import tornado.drivers.opencl.graal.OCLProviders;
import tornado.drivers.opencl.graal.OCLSuitesProvider;
import tornado.drivers.opencl.graal.OpenCLCodeCache;
import tornado.drivers.opencl.graal.OCLLoweringProvider;

import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.meta.HotSpotProviders;

public class OCLDriver extends TornadoLogger implements TornadoDriver {

    private final OCLBackend[][] backends;
    private final List<OCLContext> contexts;

    public OCLDriver(final HotSpotGraalRuntimeProvider vmRuntime,
            final HotSpotProviders vmProviders) {
        final int numPlatforms = OpenCL.getNumPlatforms();
        backends = new OCLBackend[numPlatforms][];

        contexts = new ArrayList<OCLContext>();

        discoverDevices(vmRuntime, vmProviders);
    }

    public DeviceMapping getDefaultDevice(){
    	return getDefaultBackend().getDeviceContext().asMapping();
    }
    
    private OCLBackend checkAndInitBackend(final int platform,
            final int device) {
        final OCLBackend backend = backends[platform][device];
        if (!backend.isInitialised()) {
            backend.init();
        }

        return backend;
    }

    private OCLBackend createOCLBackend(
            final HotSpotGraalRuntimeProvider vmRuntime,
            final HotSpotProviders vmProviders, final OCLContext context,
            final int deviceIndex) {
        final OCLDevice device = context.devices().get(deviceIndex);
        info("Creating backend for %s", device.getName());
//        final TornadoTargetDescription target = new TornadoTargetDescription(
//                new OCLArchitecture(device.getWordSize(), device.getByteOrder()));
        final OpenCLCodeCache codeCache = null;
        final TornadoTargetDescription target = new TornadoTargetDescription(
                new OCLArchitecture(device.getWordSize(), device.getByteOrder()));
        final OCLLoweringProvider lowerer = new OCLLoweringProvider(
                vmRuntime, vmProviders.getMetaAccess(),
                vmProviders.getForeignCalls(), target);
        lowerer.initialize(vmProviders, vmRuntime.getConfig());
        final OCLProviders providers = new OCLProviders(vmRuntime,
                vmProviders, vmProviders.getSuites(),
                vmProviders.getGraphBuilderPlugins(), lowerer, codeCache);

        return new OCLBackend(providers, target, context, deviceIndex);
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
}
