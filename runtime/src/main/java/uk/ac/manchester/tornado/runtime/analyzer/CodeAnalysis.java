/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.runtime.analyzer;

import java.lang.reflect.Method;

import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.util.EconomicMap;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;

public class CodeAnalysis {

    /**
     * Build Graal-IR for an input Java method
     * 
     * @param taskInputCode
     *            Input Java method to be compiled by Graal
     * @return {@link StructuredGraph} Control Flow and DataFlow Graphs for the
     *         input method in the Graal-IR format,
     */
    public static StructuredGraph buildHighLevelGraalGraph(Object taskInputCode) {
        Method methodToCompile = TaskUtils.resolveMethodHandle(taskInputCode);
        GraalJVMCICompiler graalCompiler = (GraalJVMCICompiler) JVMCI.getRuntime().getCompiler();
        RuntimeProvider capability = graalCompiler.getGraalRuntime().getCapability(RuntimeProvider.class);
        Backend backend = capability.getHostBackend();
        Providers providers = backend.getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaMethod resolvedJavaMethod = metaAccess.lookupJavaMethod(methodToCompile);
        CompilationIdentifier compilationIdentifier = backend.getCompilationIdentifier(resolvedJavaMethod);
        EconomicMap<OptionKey<?>, Object> opts = OptionValues.newOptionMap();
        opts.putAll(HotSpotGraalOptionValues.HOTSPOT_OPTIONS.getMap());
        OptionValues options = new OptionValues(opts);
        StructuredGraph graph = new StructuredGraph.Builder(options, AllowAssumptions.YES).method(resolvedJavaMethod).compilationId(compilationIdentifier).build();
        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(GraphBuilderConfiguration.getDefault(new Plugins(new InvocationPlugins()))));
        graphBuilderSuite.apply(graph, new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.ALL));
        return graph;
    }

}
