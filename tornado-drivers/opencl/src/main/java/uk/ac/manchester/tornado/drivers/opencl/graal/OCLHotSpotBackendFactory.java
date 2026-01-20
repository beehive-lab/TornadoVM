/*
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal;

import static jdk.vm.ci.common.InitTimer.timer;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotIdentityHashCodeProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotStampProvider;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.loop.LoopsDataProviderImpl;
import jdk.graal.compiler.nodes.spi.IdentityHashCodeProvider;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.replacements.StandardGraphBuilderPlugins;
import jdk.graal.compiler.replacements.classfile.ClassfileBytecodeProvider;
import jdk.graal.compiler.word.WordTypes;

import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.runtime.JVMCIBackend;
import uk.ac.manchester.tornado.drivers.opencl.OCLContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDescription;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilerConfiguration;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.plugins.OCLGraphBuilderPlugins;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLAddressLowering;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.providers.TornadoMetaAccessExtensionProvider;
import uk.ac.manchester.tornado.drivers.providers.TornadoPlatformConfigurationProvider;
import uk.ac.manchester.tornado.drivers.providers.TornadoWordTypes;
import uk.ac.manchester.tornado.runtime.TornadoVMConfigAccess;
import uk.ac.manchester.tornado.runtime.graal.DummySnippetFactory;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoConstantFieldProvider;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoForeignCallsProvider;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoReplacements;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSnippetReflectionProvider;

public class OCLHotSpotBackendFactory {

    private static final HotSpotStampProvider stampProvider = new HotSpotStampProvider();
    private static final TornadoSnippetReflectionProvider snippetReflection = new TornadoSnippetReflectionProvider();
    private static final TornadoForeignCallsProvider foreignCalls = new TornadoForeignCallsProvider();
    private static final TornadoConstantFieldProvider constantFieldProvider = new TornadoConstantFieldProvider();
    private static final OCLCompilerConfiguration compilerConfiguration = new OCLCompilerConfiguration();
    private static final OCLAddressLowering addressLowering = new OCLAddressLowering();

    public static OCLBackend createJITCompiler(OptionValues options, HotSpotJVMCIRuntime jvmciRuntime, TornadoVMConfigAccess config, OCLContextInterface tornadoContext, OCLTargetDevice device) {
        JVMCIBackend jvmciBackend = jvmciRuntime.getHostJVMCIBackend();
        HotSpotMetaAccessProvider metaAccess = (HotSpotMetaAccessProvider) jvmciBackend.getMetaAccess();
        HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) jvmciBackend.getConstantReflection();

        OCLKind wordKind = switch (device.getWordSize()) {
            case 4 -> OCLKind.UINT;
            case 8 -> OCLKind.ULONG;
            default -> {
                shouldNotReachHere("unknown word size for device: word size is %d on %s", device.getWordSize(), device.getDeviceName());
                yield OCLKind.ILLEGAL;
            }
        };

        OCLArchitecture arch = new OCLArchitecture(wordKind, device.getByteOrder());
        OCLTargetDescription target = new OCLTargetDescription(arch, device.isDeviceDoubleFPSupported(), device.getDeviceExtensions());
        OCLCodeProvider codeCache = new OCLCodeProvider(target);
        OCLDeviceContextInterface oclDeviceContextImpl = (OCLDeviceContextInterface) tornadoContext.createDeviceContext(device.getIndex());

        OCLProviders providers;
        OCLLoweringProvider lowerer;
        OCLSuitesProvider suites;
        Plugins plugins;

        try (InitTimer t = timer("create providers")) {
            TornadoPlatformConfigurationProvider platformConfigurationProvider = new TornadoPlatformConfigurationProvider();
            MetaAccessExtensionProvider metaAccessExtensionProvider = new TornadoMetaAccessExtensionProvider();
            lowerer = new OCLLoweringProvider(metaAccess, foreignCalls, platformConfigurationProvider, metaAccessExtensionProvider, constantReflection, config, target);
            WordTypes wordTypes = new TornadoWordTypes(metaAccess, wordKind.asJavaKind());

            LoopsDataProvider lpd = new LoopsDataProviderImpl();
            HotSpotIdentityHashCodeProvider hotSpotIdentityHashCodeProvider = new HotSpotIdentityHashCodeProvider();
            Providers p = new Providers(metaAccess, //
                                    codeCache, constantReflection, constantFieldProvider, //
                                    foreignCalls, lowerer, lowerer.getReplacements(), stampProvider, //
                                    platformConfigurationProvider, metaAccessExtensionProvider, snippetReflection, //
                                    wordTypes, lpd , hotSpotIdentityHashCodeProvider);
            ClassfileBytecodeProvider bytecodeProvider = new ClassfileBytecodeProvider(metaAccess, snippetReflection);
            GraalDebugHandlersFactory graalDebugHandlersFactory = new GraalDebugHandlersFactory(snippetReflection);
            TornadoReplacements replacements = new TornadoReplacements(graalDebugHandlersFactory, p, bytecodeProvider, target);
            plugins = createGraphBuilderPlugins(metaAccess, replacements, snippetReflection, lowerer);

            replacements.setGraphBuilderPlugins(plugins);

            suites = new OCLSuitesProvider(options, oclDeviceContextImpl, plugins, metaAccess, compilerConfiguration, addressLowering);

            providers = new OCLProviders(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider,
                    metaAccessExtensionProvider, snippetReflection, wordTypes, p.getLoopsDataProvider(), suites, hotSpotIdentityHashCodeProvider);

            lowerer.initialize(options, new DummySnippetFactory(), providers);
        }
        try (InitTimer rt = timer("instantiate backend")) {
            return new OCLBackend(options, providers, target, codeCache, oclDeviceContextImpl);
        }
    }

    protected static Plugins createGraphBuilderPlugins(HotSpotMetaAccessProvider metaAccess, Replacements replacements, SnippetReflectionProvider snippetReflectionProvider,
            LoweringProvider loweringProvider) {
        InvocationPlugins invocationPlugins = new InvocationPlugins();
        Plugins plugins = new Plugins(invocationPlugins);

        OCLGraphBuilderPlugins.registerParameterPlugins(plugins);
        OCLGraphBuilderPlugins.registerNewInstancePlugins(plugins);

//        StandardGraphBuilderPlugins.registerInvocationPlugins(snippetReflectionProvider, //
//                invocationPlugins, //
//                replacements, //
//                false, //
//                false, //
//                false, //
//                loweringProvider);

        StandardGraphBuilderPlugins.registerInvocationPlugins(snippetReflectionProvider, //
                invocationPlugins, //
                false, //
                false, //
                false);
        OCLGraphBuilderPlugins.registerInvocationPlugins(plugins, invocationPlugins, metaAccess);
        return plugins;
    }



    @Override
    public String toString() {
        return "OpenCL";
    }

}
