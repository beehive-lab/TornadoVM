package tornado.drivers.opencl;

import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import java.util.ArrayList;
import java.util.List;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import tornado.common.DeviceMapping;
import tornado.common.TornadoLogger;
import tornado.drivers.opencl.graal.*;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLCompilerConfiguration;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.runtime.TornadoDriver;
import tornado.runtime.TornadoVMConfig;

import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public final class OCLDriver extends TornadoLogger implements TornadoDriver {

    private final OCLBackend[][] backends;
    private final List<OCLContext> contexts;

    public OCLDriver(final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        final int numPlatforms = OpenCL.getNumPlatforms();
        backends = new OCLBackend[numPlatforms][];

        contexts = new ArrayList<>();

        discoverDevices(vmRuntime, vmConfig);
    }

    @Override
    public DeviceMapping getDefaultDevice() {
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
            final HotSpotJVMCIRuntime jvmciRuntime, TornadoVMConfig vmConfig, final OCLContext context,
            final int deviceIndex) {
        final OCLDevice device = context.devices().get(deviceIndex);
        info("Creating backend for %s", device.getName());

        OCLKind wordKind = OCLKind.ILLEGAL;
        switch (device.getWordSize()) {
            case 32:
                wordKind = OCLKind.UINT;
                break;
            case 64:
                wordKind = OCLKind.ULONG;
                break;
            default:
                shouldNotReachHere("unknown word size for device: ", device.getName());
                break;
        }

        OCLCompilerConfiguration compilerConfig = new OCLCompilerConfiguration();

        final OCLTargetDescription target = new OCLTargetDescription(
                new OCLArchitecture(wordKind, device.getByteOrder()));
        final OpenCLCodeCache codeCache = new OpenCLCodeCache(target);

        final OCLLoweringProvider lowerer = new OCLLoweringProvider(((HotSpotGraalRuntimeProvider) jvmciRuntime.getHostJVMCIBackend()).getHostProviders(), vmConfig, target);

//        Plugins plugins = OCLGraphBuilderPlugins.create(jvmciRuntime.getHostJVMCIBackend().getMetaAccess());
        final OCLProviders providers = new OCLProviders(compilerConfig, jvmciRuntime.getHostJVMCIBackend(),
                ((HotSpotGraalRuntimeProvider) jvmciRuntime.getHostJVMCIBackend()).getHostProviders().getGraphBuilderPlugins(), lowerer, codeCache);

        return new OCLBackend(providers, target, context, deviceIndex);
    }

    protected void discoverDevices(final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
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

                    backends[i][j] = createOCLBackend(vmRuntime, vmConfig,
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
