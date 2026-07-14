/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal;

import static jdk.vm.ci.common.InitTimer.timer;
import static tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;

import tornado.graal.compiler.api.replacements.SnippetReflectionProvider;
import tornado.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import tornado.graal.compiler.hotspot.meta.HotSpotStampProvider;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import tornado.graal.compiler.nodes.loop.LoopsDataProviderImpl;
import tornado.graal.compiler.nodes.spi.LoopsDataProvider;
import tornado.graal.compiler.nodes.spi.LoweringProvider;
import tornado.graal.compiler.options.OptionValues;
import tornado.graal.compiler.phases.util.Providers;
import tornado.graal.compiler.printer.GraalDebugHandlersFactory;
import tornado.graal.compiler.replacements.StandardGraphBuilderPlugins;
import tornado.graal.compiler.replacements.classfile.ClassfileBytecodeProvider;
import tornado.graal.compiler.word.WordTypes;

import jdk.vm.ci.common.InitTimer;
import uk.ac.manchester.tornado.drivers.providers.TornadoMetaAccessExtensionProvider;
import uk.ac.manchester.tornado.drivers.providers.TornadoPlatformConfigurationProvider;
import uk.ac.manchester.tornado.drivers.providers.TornadoWordTypes;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDevice;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVRuntimeType;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilerConfiguration;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.plugins.SPIRVGraphBuilderPlugins;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVAddressLowering;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.runtime.TornadoVMConfigAccess;
import uk.ac.manchester.tornado.runtime.graal.DummySnippetFactory;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoConstantFieldProvider;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoForeignCallsProvider;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoReplacements;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSnippetReflectionProvider;
import uk.ac.manchester.tornado.runtime.jvmci.TornadoConstantReflectionProvider;
import uk.ac.manchester.tornado.runtime.jvmci.TornadoMetaAccessProvider;

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

    public static SPIRVBackend createJITCompiler(OptionValues options, TornadoVMConfigAccess vmConfig, SPIRVDevice device, SPIRVContext context,
            SPIRVRuntimeType spirvRuntime) {
        MetaAccessProvider metaAccess = new TornadoMetaAccessProvider();
        ConstantReflectionProvider constantReflection = new TornadoConstantReflectionProvider(snippetReflection);

        // We specify an architecture of 64 bits
        SPIRVArchitecture architecture = new SPIRVArchitecture(SPIRVKind.OP_TYPE_INT_64, device.getByteOrder(), spirvRuntime);
        SPIRVTargetDescription targetDescription = new SPIRVTargetDescription(architecture, false, SPIRV_STACK_ALIGNMENT, SPIRV_IMPLICIT_NULL_CHECK_LIMIT, SPIRV_INLINE_OBJECT, device
                .isDeviceDoubleFPSupported(), device.getDeviceExtensions());

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

            LoopsDataProvider lpd = new LoopsDataProviderImpl();
            Providers p = new Providers(metaAccess, codeProvider, constantReflection, constantFieldProvider, foreignCalls, lowerer, lowerer.getReplacements(), stampProvider,
                    platformConfigurationProvider, metaAccessExtensionProvider, snippetReflection, wordTypes, lpd);

            ClassfileBytecodeProvider bytecodeProvider = new ClassfileBytecodeProvider(metaAccess, snippetReflection);
            GraalDebugHandlersFactory graalDebugHandlersFactory = new GraalDebugHandlersFactory(snippetReflection);
            TornadoReplacements replacements = new TornadoReplacements(graalDebugHandlersFactory, p, snippetReflection, bytecodeProvider, targetDescription);
            plugins = createGraphPlugins(metaAccess, replacements, snippetReflection, lowerer);

            replacements.setGraphBuilderPlugins(plugins);

            suites = new SPIRVSuitesProvider(options, deviceContext, plugins, metaAccess, compilerConfiguration, addressLowering);

            providers = new SPIRVProviders(metaAccess, codeProvider, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider,
                    metaAccessExtensionProvider, snippetReflection, wordTypes, p.getLoopsDataProvider(), suites);

            lowerer.initialize(options, new DummySnippetFactory(), providers);
        }

        try (InitTimer rt = timer("Instantiate SPIRV Backend")) {
            return new SPIRVBackend(options, providers, targetDescription, codeProvider, deviceContext);
        }
    }

    /**
     * Create the Plugins and register the SPIRV Plugins
     *
     * @param metaAccess
     *     {@link jdk.vm.ci.meta.MetaAccessProvider}
     * @param replacements
     *     {@link TornadoReplacements}
     * @return Plugins for SPIRV
     */
    private static Plugins createGraphPlugins(MetaAccessProvider metaAccess, TornadoReplacements replacements, SnippetReflectionProvider snippetReflectionProvider,
            LoweringProvider loweringProvider) {
        InvocationPlugins invocationPlugins = new InvocationPlugins();
        Plugins plugins = new Plugins(invocationPlugins);

        SPIRVGraphBuilderPlugins.registerParametersPlugins(plugins);
        SPIRVGraphBuilderPlugins.registerNewInstancePlugins(plugins);

        StandardGraphBuilderPlugins.registerInvocationPlugins(snippetReflectionProvider, //
                invocationPlugins, //
                replacements, //
                false, //
                false, //
                false, //
                loweringProvider);
        SPIRVGraphBuilderPlugins.registerInvocationPlugins(plugins, invocationPlugins, metaAccess);

        return plugins;
    }

    @Override
    public String toString() {
        return "SPIRV";
    }

}
