/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.ptx.graal;

import static jdk.vm.ci.common.InitTimer.timer;

import tornado.graal.compiler.api.replacements.SnippetReflectionProvider;
import tornado.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import tornado.graal.compiler.hotspot.meta.HotSpotStampProvider;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
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
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.providers.TornadoMetaAccessExtensionProvider;
import uk.ac.manchester.tornado.drivers.providers.TornadoPlatformConfigurationProvider;
import uk.ac.manchester.tornado.drivers.providers.TornadoWordTypes;
import uk.ac.manchester.tornado.drivers.ptx.PTXDevice;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.drivers.ptx.PTXTargetDescription;
import uk.ac.manchester.tornado.drivers.ptx.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilerConfiguration;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.plugins.PTXGraphBuilderPlugins;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXAddressLowering;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.runtime.TornadoVMConfigAccess;
import uk.ac.manchester.tornado.runtime.graal.DummySnippetFactory;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoConstantFieldProvider;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoForeignCallsProvider;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoReplacements;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSnippetReflectionProvider;
import uk.ac.manchester.tornado.runtime.jvmci.TornadoConstantReflectionProvider;
import uk.ac.manchester.tornado.runtime.jvmci.TornadoMetaAccessProvider;

public class PTXHotSpotBackendFactory {

    private static final HotSpotStampProvider stampProvider = new HotSpotStampProvider();
    private static final TornadoSnippetReflectionProvider snippetReflection = new TornadoSnippetReflectionProvider();
    private static final TornadoForeignCallsProvider foreignCalls = new TornadoForeignCallsProvider();
    private static final TornadoConstantFieldProvider constantFieldProvider = new TornadoConstantFieldProvider();
    private static final PTXCompilerConfiguration compilerConfiguration = new PTXCompilerConfiguration();
    private static final PTXAddressLowering addressLowering = new PTXAddressLowering();

    public static PTXBackend createJITCompiler(OptionValues options, TornadoVMConfigAccess vmConfig, PTXDevice device) {
        MetaAccessProvider metaAccess = new TornadoMetaAccessProvider(null);
        ConstantReflectionProvider constantReflection = new TornadoConstantReflectionProvider(null, null, snippetReflection);

        PTXArchitecture arch = new PTXArchitecture(PTXKind.U64, device.getByteOrder());
        PTXTargetDescription target = new PTXTargetDescription(arch);
        PTXDeviceContext deviceContext = device.getPTXContext().getDeviceContext();
        PTXCodeProvider codeCache = new PTXCodeProvider(target);

        PTXProviders providers;
        PTXSuitesProvider suites;
        GraphBuilderConfiguration.Plugins plugins;
        PTXLoweringProvider lowerer;

        try (InitTimer ignored = timer("create providers")) {
            TornadoPlatformConfigurationProvider platformConfigurationProvider = new TornadoPlatformConfigurationProvider();
            MetaAccessExtensionProvider metaAccessExtensionProvider = new TornadoMetaAccessExtensionProvider();
            lowerer = new PTXLoweringProvider(metaAccess, foreignCalls, platformConfigurationProvider, metaAccessExtensionProvider, constantReflection, target, vmConfig);
            WordTypes wordTypes = new TornadoWordTypes(metaAccess, JavaKind.Long);

            LoopsDataProvider lpd = new LoopsDataProviderImpl();
            Providers p = new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, lowerer.getReplacements(), stampProvider,
                    platformConfigurationProvider, metaAccessExtensionProvider, snippetReflection, wordTypes, lpd);

            ClassfileBytecodeProvider bytecodeProvider = new ClassfileBytecodeProvider(metaAccess, snippetReflection);
            GraalDebugHandlersFactory graalDebugHandlersFactory = new GraalDebugHandlersFactory(snippetReflection);
            TornadoReplacements replacements = new TornadoReplacements(graalDebugHandlersFactory, p, snippetReflection, bytecodeProvider, target);
            plugins = createGraphBuilderPlugins(metaAccess, replacements, snippetReflection, lowerer);

            replacements.setGraphBuilderPlugins(plugins);

            suites = new PTXSuitesProvider(options, deviceContext, plugins, metaAccess, compilerConfiguration, addressLowering);

            providers = new PTXProviders(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider,
                    metaAccessExtensionProvider, snippetReflection, wordTypes, p.getLoopsDataProvider(), suites);

            lowerer.initialize(options, new DummySnippetFactory(), providers);

        }
        try (InitTimer ignored = timer("instantiate backend")) {
            return new PTXBackend(providers, deviceContext, target, codeCache, options);
        }

    }

    protected static GraphBuilderConfiguration.Plugins createGraphBuilderPlugins(MetaAccessProvider metaAccess, Replacements replacements, SnippetReflectionProvider snippetReflectionProvider,
            LoweringProvider loweringProvider) {
        InvocationPlugins invocationPlugins = new InvocationPlugins();
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(invocationPlugins);

        PTXGraphBuilderPlugins.registerParameterPlugins(plugins);
        PTXGraphBuilderPlugins.registerNewInstancePlugins(plugins);

        StandardGraphBuilderPlugins.registerInvocationPlugins(snippetReflectionProvider, //
                invocationPlugins, //
                replacements, //
                false, //
                false, //
                false, //
                loweringProvider);
        PTXGraphBuilderPlugins.registerInvocationPlugins(plugins, invocationPlugins, metaAccess);
        return plugins;
    }
}
