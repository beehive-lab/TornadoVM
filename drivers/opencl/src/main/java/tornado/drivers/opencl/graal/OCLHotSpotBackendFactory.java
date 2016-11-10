package tornado.drivers.opencl.graal;

import com.oracle.graal.bytecode.BytecodeProvider;
import com.oracle.graal.hotspot.meta.HotSpotStampProvider;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.replacements.StandardGraphBuilderPlugins;
import com.oracle.graal.replacements.classfile.ClassfileBytecodeProvider;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.runtime.JVMCIBackend;
import tornado.drivers.opencl.OCLContext;
import tornado.drivers.opencl.OCLDevice;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.OCLTargetDescription;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLCompilerConfiguration;
import tornado.drivers.opencl.graal.compiler.plugins.OCLGraphBuilderPlugins;
import tornado.drivers.opencl.graal.lir.OCLAddressLowering;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.graal.compiler.*;
import tornado.runtime.TornadoVMConfig;

import static jdk.vm.ci.common.InitTimer.timer;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public class OCLHotSpotBackendFactory {

    private static final HotSpotStampProvider stampProvider = new HotSpotStampProvider();
    private static final TornadoSnippetReflectionProvider snippetReflection = new TornadoSnippetReflectionProvider();
    private static final TornadoNodeCostProvider nodeCostProvider = new TornadoNodeCostProvider();
    private static final TornadoForeignCallsProvider foreignCalls = new TornadoForeignCallsProvider();
    private static final TornadoConstantFieldProvider constantFieldProvider = new TornadoConstantFieldProvider();
    private static final OCLCompilerConfiguration compilerConfiguration = new OCLCompilerConfiguration();
    private static final OCLAddressLowering addressLowering = new OCLAddressLowering();

    public static OCLBackend createBackend(JVMCIBackend jvmci, TornadoVMConfig config, OCLContext openclContext, OCLDevice device) {
        HotSpotMetaAccessProvider metaAccess = (HotSpotMetaAccessProvider) jvmci.getMetaAccess();
        HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) jvmci.getConstantReflection();

        OCLKind wordKind = OCLKind.ILLEGAL;
        switch (device.getWordSize()) {
            case 4:
                wordKind = OCLKind.UINT;
                break;
            case 8:
                wordKind = OCLKind.ULONG;
                break;
            default:
                shouldNotReachHere("unknown word size for device: word size is %d on %s", device.getWordSize(), device.getName());
                break;
        }

        OCLArchitecture arch = new OCLArchitecture(wordKind, device.getByteOrder());
        OCLTargetDescription target = new OCLTargetDescription(arch);
        OCLCodeCache codeCache = new OCLCodeCache(target);
        OCLDeviceContext deviceContext = openclContext.createDeviceContext(device.getIndex());

        OCLProviders providers;
//        HotSpotRegistersProvider registers;
        OCLLoweringProvider lowerer;
        OCLSuitesProvider suites;
        Plugins plugins;

        try (InitTimer t = timer("create providers")) {
//            registers = createRegisters();

            lowerer = new OCLLoweringProvider(metaAccess, foreignCalls, constantReflection, config, target);

            Providers p = new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, stampProvider, nodeCostProvider);
            ClassfileBytecodeProvider bytecodeProvider = new ClassfileBytecodeProvider(metaAccess, snippetReflection);
            plugins = createGraphBuilderPlugins(metaAccess, bytecodeProvider);
            TornadoReplacements replacements = new TornadoReplacements(p, snippetReflection, bytecodeProvider, target);

            replacements.setGraphBuilderPlugins(plugins);

            suites = new OCLSuitesProvider(plugins, metaAccess, compilerConfiguration, addressLowering);

            providers = new OCLProviders(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, nodeCostProvider, plugins, suites);

            lowerer.initialize(providers, snippetReflection);
        }
        try (InitTimer rt = timer("instantiate backend")) {
            OCLBackend backend = new OCLBackend(providers, target, codeCache, openclContext, deviceContext);
            codeCache.setBackend(backend);
            return backend;
        }
    }

    protected static Plugins createGraphBuilderPlugins(
            HotSpotMetaAccessProvider metaAccess, BytecodeProvider bytecodeProvider) {

        InvocationPlugins invocationPlugins = new InvocationPlugins(metaAccess);
        Plugins plugins = new Plugins(invocationPlugins);

        OCLGraphBuilderPlugins.registerParameterPlugins(plugins);
        OCLGraphBuilderPlugins.registerNewInstancePlugins(plugins);

//        invocationPlugins.defer(new Runnable() {
//            @Override
//            public void run() {
        StandardGraphBuilderPlugins.registerInvocationPlugins(metaAccess, snippetReflection, invocationPlugins, bytecodeProvider, true);
        OCLGraphBuilderPlugins.registerInvocationPlugins(invocationPlugins);
//            }
//        });

        return plugins;
    }

    @Override
    public String toString() {
        return "OpenCL";
    }

}
