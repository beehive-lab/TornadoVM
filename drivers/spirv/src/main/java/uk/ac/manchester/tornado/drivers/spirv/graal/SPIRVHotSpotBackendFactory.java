package uk.ac.manchester.tornado.drivers.spirv.graal;

import static jdk.vm.ci.common.InitTimer.timer;
import static org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;

import java.util.Collections;

import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotStampProvider;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins;
import org.graalvm.compiler.replacements.classfile.ClassfileBytecodeProvider;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.runtime.JVMCIBackend;
import uk.ac.manchester.tornado.drivers.graal.TornadoMetaAccessExtensionProvider;
import uk.ac.manchester.tornado.drivers.graal.TornadoPlatformConfigurationProvider;
import uk.ac.manchester.tornado.drivers.graal.TornadoWordTypes;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDevice;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilerConfiguration;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.plugins.SPIRVGraphBuilderPlugins;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVAddressLowering;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;
import uk.ac.manchester.tornado.runtime.graal.DummySnippetFactory;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoConstantFieldProvider;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoForeignCallsProvider;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoReplacements;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSnippetReflectionProvider;

public class SPIRVHotSpotBackendFactory {

    public static final int SPIRV_STACK_ALIGNMENT = 8;
    public static final int SPIRV_IMPLICIT_NULL_CHECK_LIMIT = 4096;
    public static final boolean SPIRV_INLINE_OBJECT = true;

    private static final HotSpotStampProvider stampProvider = new HotSpotStampProvider();
    private static final TornadoSnippetReflectionProvider snippetReflection = new TornadoSnippetReflectionProvider();
    private static final TornadoForeignCallsProvider foreignCalls = new TornadoForeignCallsProvider();
    private static final TornadoConstantFieldProvider constantFieldProvider = new TornadoConstantFieldProvider();
    private static final SPIRVCompilerConfiguration compilerConfiguration = new SPIRVCompilerConfiguration();
    private static final SPIRVAddressLowering addressLowering = new SPIRVAddressLowering();

    public static SPIRVBackend createBackend(OptionValues options, HotSpotJVMCIRuntime jvmciRuntime, TornadoVMConfig vmConfig, SPIRVDevice device, SPIRVContext context) {
        JVMCIBackend jvmci = jvmciRuntime.getHostJVMCIBackend();
        HotSpotMetaAccessProvider metaAccess = (HotSpotMetaAccessProvider) jvmci.getMetaAccess();
        HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) jvmci.getConstantReflection();

        // We specify an architecture of 64 bits
        SPIRVArchitecture architecture = new SPIRVArchitecture(SPIRVKind.OP_TYPE_INT_64, device.getByteOrder());
        SPIRVTargetDescription targetDescription = new SPIRVTargetDescription(architecture, false, SPIRV_STACK_ALIGNMENT, SPIRV_IMPLICIT_NULL_CHECK_LIMIT, SPIRV_INLINE_OBJECT,
                device.isDeviceDoubleFPSupported(), device.getDeviceExtensions());

        SPIRVDeviceContext deviceContext = context.getDeviceContext(device.getDeviceIndex());

        SPIRVCodeProvider codeProvider = new SPIRVCodeProvider(targetDescription);

        SPIRVProviders providers;
        SPIRVSuitesProvider suites;
        SPIRVLoweringProvider lowerer;
        Plugins plugins;

        try (InitTimer t = timer("create providers")) {
            TornadoPlatformConfigurationProvider platformConfigurationProvider = new TornadoPlatformConfigurationProvider();
            MetaAccessExtensionProvider metaAccessExtensionProvider = new TornadoMetaAccessExtensionProvider();
            lowerer = new SPIRVLoweringProvider(metaAccess, foreignCalls, platformConfigurationProvider, metaAccessExtensionProvider, constantReflection, vmConfig, targetDescription, false);
            WordTypes wordTypes = new TornadoWordTypes(metaAccess, SPIRVKind.OP_TYPE_FLOAT_32.asJavaKind());
            Providers p = new Providers(metaAccess, codeProvider, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, stampProvider, platformConfigurationProvider,
                    metaAccessExtensionProvider, snippetReflection, wordTypes);
            ClassfileBytecodeProvider bytecodeProvider = new ClassfileBytecodeProvider(metaAccess, snippetReflection);
            GraalDebugHandlersFactory graalDebugHandlersFactory = new GraalDebugHandlersFactory(snippetReflection);
            TornadoReplacements replacements = new TornadoReplacements(graalDebugHandlersFactory, p, snippetReflection, bytecodeProvider, targetDescription);
            plugins = createGraphPlugins(metaAccess, replacements);

            replacements.setGraphBuilderPlugins(plugins);

            suites = new SPIRVSuitesProvider(options, deviceContext, plugins, metaAccess, compilerConfiguration, addressLowering);

            providers = new SPIRVProviders(metaAccess, codeProvider, constantReflection, snippetReflection, constantFieldProvider, //
                    foreignCalls, lowerer, replacements, stampProvider, plugins, suites, //
                    platformConfigurationProvider, metaAccessExtensionProvider, wordTypes);

            lowerer.initialize(options, Collections.singleton(graalDebugHandlersFactory), new DummySnippetFactory(), providers, snippetReflection);
        }

        try (InitTimer rt = timer("Instantiate SPIRV Backend")) {
            return new SPIRVBackend(options, providers, targetDescription, codeProvider, deviceContext);
        }
    }

    /**
     * Create the Plugins and register the SPIRV Plugins
     * 
     * @param metaAccess
     *            {@link HotSpotMetaAccessProvider}
     * @param replacements
     *            {@link TornadoReplacements}
     * @return Plugins for SPIRV
     */
    private static Plugins createGraphPlugins(HotSpotMetaAccessProvider metaAccess, TornadoReplacements replacements) {
        InvocationPlugins invocationPlugins = new InvocationPlugins();
        Plugins plugins = new Plugins(invocationPlugins);

        SPIRVGraphBuilderPlugins.registerParametersPlugins(plugins);
        SPIRVGraphBuilderPlugins.registerNewInstancePlugins(plugins);

        StandardGraphBuilderPlugins.registerInvocationPlugins(metaAccess, snippetReflection, invocationPlugins, replacements, false, false, false);
        SPIRVGraphBuilderPlugins.registerInvocationPlugins(plugins, invocationPlugins);

        return plugins;
    }

    @Override
    public String toString() {
        return "SPIRV";
    }

}
