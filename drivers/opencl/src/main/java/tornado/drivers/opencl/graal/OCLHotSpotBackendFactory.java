/*
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl.graal;

import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.runtime.JVMCIBackend;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotStampProvider;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins;
import org.graalvm.compiler.replacements.classfile.ClassfileBytecodeProvider;
import tornado.drivers.opencl.OCLContext;
import tornado.drivers.opencl.OCLDevice;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.OCLTargetDescription;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLCompilerConfiguration;
import tornado.drivers.opencl.graal.compiler.plugins.OCLGraphBuilderPlugins;
import tornado.drivers.opencl.graal.lir.OCLAddressLowering;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.graal.DummySnippetFactory;
import tornado.graal.compiler.TornadoConstantFieldProvider;
import tornado.graal.compiler.TornadoForeignCallsProvider;
import tornado.graal.compiler.TornadoReplacements;
import tornado.graal.compiler.TornadoSnippetReflectionProvider;
import tornado.runtime.TornadoVMConfig;

import static jdk.vm.ci.common.InitTimer.timer;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public class OCLHotSpotBackendFactory {

    private static final HotSpotStampProvider stampProvider = new HotSpotStampProvider();
    private static final TornadoSnippetReflectionProvider snippetReflection = new TornadoSnippetReflectionProvider();
    private static final TornadoForeignCallsProvider foreignCalls = new TornadoForeignCallsProvider();
    private static final TornadoConstantFieldProvider constantFieldProvider = new TornadoConstantFieldProvider();
    private static final OCLCompilerConfiguration compilerConfiguration = new OCLCompilerConfiguration();
    private static final OCLAddressLowering addressLowering = new OCLAddressLowering();

    public static OCLBackend createBackend(OptionValues options, JVMCIBackend jvmci, TornadoVMConfig config, OCLContext openclContext, OCLDevice device) {
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
        OCLTargetDescription target = new OCLTargetDescription(arch, device.getDoubleFPConfig() != 0);
        OCLCodeProvider codeCache = new OCLCodeProvider(target);
        OCLDeviceContext deviceContext = openclContext.createDeviceContext(device.getIndex());

        OCLProviders providers;
//        HotSpotRegistersProvider registers;
        OCLLoweringProvider lowerer;
        OCLSuitesProvider suites;
        Plugins plugins;

        try (InitTimer t = timer("create providers")) {
//            registers = createRegisters();

            lowerer = new OCLLoweringProvider(metaAccess, foreignCalls, constantReflection, config, target);

            Providers p = new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, stampProvider);
            ClassfileBytecodeProvider bytecodeProvider = new ClassfileBytecodeProvider(metaAccess, snippetReflection);
            plugins = createGraphBuilderPlugins(metaAccess, bytecodeProvider);
            TornadoReplacements replacements = new TornadoReplacements(options, p, snippetReflection, bytecodeProvider, target);

            replacements.setGraphBuilderPlugins(plugins);

            suites = new OCLSuitesProvider(options, plugins, metaAccess, compilerConfiguration, addressLowering);

            providers = new OCLProviders(metaAccess, codeCache, constantReflection, snippetReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, plugins, suites);

            lowerer.initialize(options, new DummySnippetFactory(), providers, snippetReflection);
        }
        try (InitTimer rt = timer("instantiate backend")) {
            OCLBackend backend = new OCLBackend(options, providers, target, codeCache, openclContext, deviceContext);
            return backend;
        }
    }

    protected static Plugins createGraphBuilderPlugins(
            HotSpotMetaAccessProvider metaAccess, BytecodeProvider bytecodeProvider) {

        InvocationPlugins invocationPlugins = new InvocationPlugins();
        Plugins plugins = new Plugins(invocationPlugins);

        OCLGraphBuilderPlugins.registerParameterPlugins(plugins);
        OCLGraphBuilderPlugins.registerNewInstancePlugins(plugins);

        StandardGraphBuilderPlugins.registerInvocationPlugins(metaAccess, snippetReflection, invocationPlugins, bytecodeProvider, true);
        OCLGraphBuilderPlugins.registerInvocationPlugins(invocationPlugins);
        return plugins;
    }

    @Override
    public String toString() {
        return "OpenCL";
    }

}
