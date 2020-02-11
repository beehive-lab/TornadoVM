package uk.ac.manchester.tornado.drivers.cuda.graal;

import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.runtime.JVMCIBackend;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotStampProvider;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins;
import org.graalvm.compiler.replacements.classfile.ClassfileBytecodeProvider;
import uk.ac.manchester.tornado.drivers.cuda.CUDAContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDADevice;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDescription;
import uk.ac.manchester.tornado.drivers.cuda.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilerConfiguration;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXAddressLowering;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;
import uk.ac.manchester.tornado.runtime.graal.DummySnippetFactory;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoConstantFieldProvider;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoForeignCallsProvider;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoReplacements;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSnippetReflectionProvider;

import static jdk.vm.ci.common.InitTimer.timer;

public class PTXHotSpotBackendFactory {

    private static final HotSpotStampProvider stampProvider = new HotSpotStampProvider();
    private static final TornadoSnippetReflectionProvider snippetReflection = new TornadoSnippetReflectionProvider();
    private static final TornadoForeignCallsProvider foreignCalls = new TornadoForeignCallsProvider();
    private static final TornadoConstantFieldProvider constantFieldProvider = new TornadoConstantFieldProvider();
    private static final PTXCompilerConfiguration compilerConfiguration = new PTXCompilerConfiguration();
    private static final PTXAddressLowering addressLowering = new PTXAddressLowering();

    public static PTXBackend createBackend(OptionValues options, JVMCIBackend jvmci, TornadoVMConfig config, CUDADevice device) {
        HotSpotMetaAccessProvider metaAccess = (HotSpotMetaAccessProvider) jvmci.getMetaAccess();
        HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) jvmci.getConstantReflection();

        PTXArchitecture arch = new PTXArchitecture(PTXKind.UINT, device.getByteOrder());
        CUDATargetDescription target = new CUDATargetDescription(arch);
        CUDADeviceContext deviceContext = device.getContext().getDeviceContext();

        PTXProviders providers;
        PTXSuitesProvider suites;
        GraphBuilderConfiguration.Plugins plugins;
        PTXLoweringProvider lowerer;

        try (InitTimer t = timer("create providers")) {
            lowerer = new PTXLoweringProvider(metaAccess, foreignCalls, target);

            ClassfileBytecodeProvider bytecodeProvider = new ClassfileBytecodeProvider(metaAccess, snippetReflection);
            plugins = createGraphBuilderPlugins(metaAccess, bytecodeProvider);

            suites = new PTXSuitesProvider(options, plugins, metaAccess, compilerConfiguration, addressLowering);
            providers = new PTXProviders(metaAccess, null, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, stampProvider, suites);

        }
        try (InitTimer rt = timer("instantiate backend")) {
            PTXBackend backend = new PTXBackend(providers, deviceContext);
            return backend;
        }


    }

    protected static GraphBuilderConfiguration.Plugins createGraphBuilderPlugins(HotSpotMetaAccessProvider metaAccess, BytecodeProvider bytecodeProvider) {
        InvocationPlugins invocationPlugins = new InvocationPlugins();
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(invocationPlugins);

        StandardGraphBuilderPlugins.registerInvocationPlugins(metaAccess, snippetReflection, invocationPlugins, bytecodeProvider, true);
        return plugins;
    }
}
