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
package uk.ac.manchester.tornado.drivers.cuda.graal;

import static jdk.vm.ci.common.InitTimer.timer;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import tornado.graal.compiler.api.replacements.SnippetReflectionProvider;
import tornado.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import tornado.graal.compiler.hotspot.meta.HotSpotStampProvider;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import tornado.graal.compiler.nodes.loop.LoopsDataProviderImpl;
import tornado.graal.compiler.nodes.spi.LoopsDataProvider;
import tornado.graal.compiler.nodes.spi.LoweringProvider;
import tornado.graal.compiler.nodes.spi.Replacements;
import tornado.graal.compiler.options.OptionValues;
import tornado.graal.compiler.phases.util.Providers;
import tornado.graal.compiler.printer.GraalDebugHandlersFactory;
import tornado.graal.compiler.replacements.StandardGraphBuilderPlugins;
import tornado.graal.compiler.replacements.classfile.ClassfileBytecodeProvider;
import tornado.graal.compiler.word.WordTypes;

import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.runtime.JVMCIBackend;
import uk.ac.manchester.tornado.drivers.cuda.CUDAContextInterface;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContextInterface;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDescription;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDevice;
import uk.ac.manchester.tornado.drivers.cuda.graal.backend.CUDABackend;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilerConfiguration;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.plugins.CUDAGraphBuilderPlugins;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAAddressLowering;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.providers.TornadoMetaAccessExtensionProvider;
import uk.ac.manchester.tornado.drivers.providers.TornadoPlatformConfigurationProvider;
import uk.ac.manchester.tornado.drivers.providers.TornadoWordTypes;
import uk.ac.manchester.tornado.runtime.TornadoVMConfigAccess;
import uk.ac.manchester.tornado.runtime.graal.DummySnippetFactory;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoConstantFieldProvider;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoForeignCallsProvider;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoReplacements;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSnippetReflectionProvider;

public class CUDAHotSpotBackendFactory {

    private static final HotSpotStampProvider stampProvider = new HotSpotStampProvider();
    private static final TornadoSnippetReflectionProvider snippetReflection = new TornadoSnippetReflectionProvider();
    private static final TornadoForeignCallsProvider foreignCalls = new TornadoForeignCallsProvider();
    private static final TornadoConstantFieldProvider constantFieldProvider = new TornadoConstantFieldProvider();
    private static final CUDACompilerConfiguration compilerConfiguration = new CUDACompilerConfiguration();
    private static final CUDAAddressLowering addressLowering = new CUDAAddressLowering();

    public static CUDABackend createJITCompiler(OptionValues options, HotSpotJVMCIRuntime jvmciRuntime, TornadoVMConfigAccess config, CUDAContextInterface tornadoContext, CUDATargetDevice device) {
        JVMCIBackend jvmciBackend = jvmciRuntime.getHostJVMCIBackend();
        HotSpotMetaAccessProvider metaAccess = (HotSpotMetaAccessProvider) jvmciBackend.getMetaAccess();
        HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) jvmciBackend.getConstantReflection();

        CUDAKind wordKind = switch (device.getWordSize()) {
            case 4 -> CUDAKind.UINT;
            case 8 -> CUDAKind.ULONG;
            default -> {
                shouldNotReachHere("unknown word size for device: word size is %d on %s", device.getWordSize(), device.getDeviceName());
                yield CUDAKind.ILLEGAL;
            }
        };

        CUDAArchitecture arch = new CUDAArchitecture(wordKind, device.getByteOrder());
        CUDATargetDescription target = new CUDATargetDescription(arch, device.isDeviceDoubleFPSupported(), device.getDeviceExtensions());
        CUDACodeProvider codeCache = new CUDACodeProvider(target);
        CUDADeviceContextInterface oclDeviceContextImpl = (CUDADeviceContextInterface) tornadoContext.createDeviceContext(device.getIndex());

        CUDAProviders providers;
        CUDALoweringProvider lowerer;
        CUDASuitesProvider suites;
        Plugins plugins;

        try (InitTimer t = timer("create providers")) {
            TornadoPlatformConfigurationProvider platformConfigurationProvider = new TornadoPlatformConfigurationProvider();
            MetaAccessExtensionProvider metaAccessExtensionProvider = new TornadoMetaAccessExtensionProvider();
            lowerer = new CUDALoweringProvider(metaAccess, foreignCalls, platformConfigurationProvider, metaAccessExtensionProvider, constantReflection, config, target);
            WordTypes wordTypes = new TornadoWordTypes(metaAccess, wordKind.asJavaKind());

            LoopsDataProvider lpd = new LoopsDataProviderImpl();
            Providers p = new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, lowerer.getReplacements(), stampProvider,
                    platformConfigurationProvider, metaAccessExtensionProvider, snippetReflection, wordTypes, lpd);
            ClassfileBytecodeProvider bytecodeProvider = new ClassfileBytecodeProvider(metaAccess, snippetReflection);
            GraalDebugHandlersFactory graalDebugHandlersFactory = new GraalDebugHandlersFactory(snippetReflection);
            TornadoReplacements replacements = new TornadoReplacements(graalDebugHandlersFactory, p, snippetReflection, bytecodeProvider, target);
            plugins = createGraphBuilderPlugins(metaAccess, replacements, snippetReflection, lowerer);

            replacements.setGraphBuilderPlugins(plugins);

            suites = new CUDASuitesProvider(options, oclDeviceContextImpl, plugins, metaAccess, compilerConfiguration, addressLowering);

            providers = new CUDAProviders(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider,
                    metaAccessExtensionProvider, snippetReflection, wordTypes, p.getLoopsDataProvider(), suites);

            lowerer.initialize(options, new DummySnippetFactory(), providers);
        }
        try (InitTimer rt = timer("instantiate backend")) {
            return new CUDABackend(options, providers, target, codeCache, oclDeviceContextImpl);
        }
    }

    protected static Plugins createGraphBuilderPlugins(HotSpotMetaAccessProvider metaAccess, Replacements replacements, SnippetReflectionProvider snippetReflectionProvider,
            LoweringProvider loweringProvider) {
        InvocationPlugins invocationPlugins = new InvocationPlugins();
        Plugins plugins = new Plugins(invocationPlugins);

        CUDAGraphBuilderPlugins.registerParameterPlugins(plugins);
        CUDAGraphBuilderPlugins.registerNewInstancePlugins(plugins);

        StandardGraphBuilderPlugins.registerInvocationPlugins(snippetReflectionProvider, //
                invocationPlugins, //
                replacements, //
                false, //
                false, //
                false, //
                loweringProvider);
        CUDAGraphBuilderPlugins.registerInvocationPlugins(plugins, invocationPlugins, metaAccess);
        return plugins;
    }

    @Override
    public String toString() {
        return "CUDADriver";
    }

}
