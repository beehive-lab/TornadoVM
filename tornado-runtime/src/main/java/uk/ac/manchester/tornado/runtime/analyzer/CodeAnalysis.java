/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.analyzer;

import jdk.graal.compiler.api.runtime.GraalJVMCICompiler;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.CompilationRequestIdentifier;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.hotspot.HotSpotGraalOptionValues;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.runtime.JVMCI;
import org.graalvm.collections.EconomicMap;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoInternalGraphBuilder;

import java.lang.reflect.Method;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;

public class CodeAnalysis {

    /**
     * Build Graal-IR for an input Java method.
     *
     * @param taskInputCode
     *         Input Java method to be compiled by Graal
     * @return {@link StructuredGraph} Control Flow and DataFlow Graphs for the input method in the Graal-IR format,
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

        SpeculationLog speculationLog = resolvedJavaMethod.getSpeculationLog();
        if (speculationLog != null) {
            speculationLog.collectFailedSpeculations();
        }

        try (DebugContext.Scope ignored = getDebugContext().scope("compileMethodAndInstall", new DebugDumpScope("TornadoVM-Code-Analysis", true))) {
            EconomicMap<OptionKey<?>, Object> opts = OptionValues.newOptionMap();
            opts.putAll(HotSpotGraalOptionValues.defaultOptions().getMap());
            OptionValues options = new OptionValues(opts);
            StructuredGraph graph = new StructuredGraph.Builder(options, getDebugContext(), AllowAssumptions.YES).speculationLog(speculationLog).method(resolvedJavaMethod)
                    .compilationId(compilationIdentifier).build();
            PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
            graphBuilderSuite.appendPhase(new TornadoInternalGraphBuilder(GraphBuilderConfiguration.getDefault(new Plugins(new InvocationPlugins()))));
            graphBuilderSuite.apply(graph, new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.ALL));
            getDebugContext().dump(DebugContext.BASIC_LEVEL, graph, "CodeToAnalyze");
            return graph;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * It compiles and installs the method that represents the object {@code graph}.
     *
     * @param graph
     *         Compile-graph
     * @return {@link InstalledCode}
     */
    public static InstalledCode compileAndInstallMethod(StructuredGraph graph) {
        ResolvedJavaMethod method = graph.method();
        GraalJVMCICompiler graalCompiler = (GraalJVMCICompiler) JVMCI.getRuntime().getCompiler();
        RuntimeProvider capability = graalCompiler.getGraalRuntime().getCapability(RuntimeProvider.class);
        Backend backend = capability.getHostBackend();
        Providers providers = backend.getProviders();
        CompilationIdentifier compilationID = backend.getCompilationIdentifier(method);
        EconomicMap<OptionKey<?>, Object> opts = OptionValues.newOptionMap();
        opts.putAll(HotSpotGraalOptionValues.defaultOptions().getMap());
        OptionValues options = new OptionValues(opts);
        try (DebugContext.Scope ignored = getDebugContext().scope("compileMethodAndInstall", new DebugDumpScope(String.valueOf(compilationID), true))) {
            PhaseSuite<HighTierContext> graphBuilderPhase = backend.getSuites().getDefaultGraphBuilderSuite();
            Suites suites = backend.getSuites().getDefaultSuites(options, providers.getLowerer().getTarget().arch);
            LIRSuites lirSuites = backend.getSuites().getDefaultLIRSuites(options);
            OptimisticOptimizations optimizationsOpts = OptimisticOptimizations.ALL;
            ProfilingInfo profilerInfo = graph.getProfilingInfo(method);
            CompilationResult compilationResult = new CompilationResult(method.getSignature().toMethodDescriptor());
            CompilationResultBuilderFactory factory = CompilationResultBuilderFactory.Default;
            GraalCompiler.Request<CompilationResult> request = new GraalCompiler.Request<>(graph, method, providers, backend, graphBuilderPhase, optimizationsOpts, profilerInfo, suites, lirSuites,
                    compilationResult, factory, false);
            request.execute();
            return backend.addInstalledCode(getDebugContext(), method, CompilationRequestIdentifier.asCompilationRequest(compilationID), compilationResult);
        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
    }

}
