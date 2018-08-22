/*
 * Copyright (c) 2018, APT Group, School of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal;

import static jdk.vm.ci.common.InitTimer.timer;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotStampProvider;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins;
import org.graalvm.compiler.replacements.classfile.ClassfileBytecodeProvider;

import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.runtime.JVMCIBackend;
import uk.ac.manchester.tornado.drivers.opencl.OCLContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLDevice;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDescription;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilerConfiguration;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.plugins.OCLGraphBuilderPlugins;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLAddressLowering;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.graal.DummySnippetFactory;
import uk.ac.manchester.tornado.graal.compiler.TornadoConstantFieldProvider;
import uk.ac.manchester.tornado.graal.compiler.TornadoForeignCallsProvider;
import uk.ac.manchester.tornado.graal.compiler.TornadoReplacements;
import uk.ac.manchester.tornado.graal.compiler.TornadoSnippetReflectionProvider;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;

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
        OCLTargetDescription target = new OCLTargetDescription(arch, device.getDoubleFPConfig() != 0, device.getExtensions());
        OCLCodeProvider codeCache = new OCLCodeProvider(target);
        OCLDeviceContext deviceContext = openclContext.createDeviceContext(device.getIndex());

        OCLProviders providers;
        OCLLoweringProvider lowerer;
        OCLSuitesProvider suites;
        Plugins plugins;

        try (InitTimer t = timer("create providers")) {
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

    protected static Plugins createGraphBuilderPlugins(HotSpotMetaAccessProvider metaAccess, BytecodeProvider bytecodeProvider) {

        InvocationPlugins invocationPlugins = new InvocationPlugins();
        Plugins plugins = new Plugins(invocationPlugins);

        OCLGraphBuilderPlugins.registerParameterPlugins(plugins);
        OCLGraphBuilderPlugins.registerNewInstancePlugins(plugins);

        StandardGraphBuilderPlugins.registerInvocationPlugins(metaAccess, snippetReflection, invocationPlugins, bytecodeProvider, true);
        OCLGraphBuilderPlugins.registerInvocationPlugins(plugins, invocationPlugins);
        return plugins;
    }

    @Override
    public String toString() {
        return "OpenCL";
    }

}
