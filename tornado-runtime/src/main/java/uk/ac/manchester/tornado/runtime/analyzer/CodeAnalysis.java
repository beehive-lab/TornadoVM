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

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getOptions;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.collections.EconomicMap;
import tornado.graal.compiler.api.runtime.GraalJVMCICompiler;
import tornado.graal.compiler.code.CompilationResult;
import tornado.graal.compiler.core.GraalCompiler;
import tornado.graal.compiler.core.common.CompilationIdentifier;
import tornado.graal.compiler.core.common.CompilationRequestIdentifier;
import tornado.graal.compiler.core.target.Backend;
import tornado.graal.compiler.debug.DebugContext;
import tornado.graal.compiler.debug.DebugDumpScope;
import tornado.graal.compiler.hotspot.CompilerConfigurationFactory;
import tornado.graal.compiler.hotspot.HotSpotGraalCompilerFactory;
import tornado.graal.compiler.hotspot.HotSpotGraalOptionValues;
import tornado.graal.compiler.java.GraphBuilderPhase;
import tornado.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import tornado.graal.compiler.lir.phases.LIRSuites;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import tornado.graal.compiler.options.OptionKey;
import tornado.graal.compiler.options.OptionValues;
import tornado.graal.compiler.phases.OptimisticOptimizations;
import tornado.graal.compiler.phases.PhaseSuite;
import tornado.graal.compiler.phases.tiers.HighTierContext;
import tornado.graal.compiler.phases.tiers.Suites;
import tornado.graal.compiler.phases.util.Providers;
import tornado.graal.compiler.runtime.RuntimeProvider;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCompilerIdentifier;
import uk.ac.manchester.tornado.runtime.jvmci.TornadoMetaAccessProvider;

public class CodeAnalysis {

    /**
     * The relocated Graal is vendored as the {@code tornado.graal} application module and
     * is therefore no longer registered with JVMCI as the HotSpot compiler
     * ({@code JVMCI.getRuntime().getCompiler()} returns the dummy factory). We instead
     * construct our own Graal {@link GraalJVMCICompiler} directly from the HotSpot JVMCI
     * runtime and cache it; it only provides the host backend/providers used to build and
     * install analysis graphs, so a single shared instance is sufficient.
     */
    private static volatile GraalJVMCICompiler graalCompiler;

    private static GraalJVMCICompiler getGraalCompiler() {
        if (graalCompiler == null) {
            synchronized (CodeAnalysis.class) {
                if (graalCompiler == null) {
                    HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();
                    OptionValues options = HotSpotGraalOptionValues.defaultOptions();
                    CompilerConfigurationFactory factory = CompilerConfigurationFactory.selectFactory(null, options, jvmciRuntime);
                    graalCompiler = HotSpotGraalCompilerFactory.createCompiler("Tornado", jvmciRuntime, options, factory);
                }
            }
        }
        return graalCompiler;
    }

    /**
     * Build Graal-IR for an input Java method.
     *
     * @param taskInputCode
     *     Input Java method to be compiled by Graal
     * @return {@link StructuredGraph} Control Flow and DataFlow Graphs for the
     *     input method in the Graal-IR format,
     */
    private static final AtomicInteger codeAnalysisId = new AtomicInteger(0);

    public static StructuredGraph buildHighLevelGraalGraph(Object taskInputCode) {
        Method methodToCompile = TaskUtils.resolveMethodHandle(taskInputCode);

        final Providers providers;
        final ResolvedJavaMethod resolvedJavaMethod;
        final CompilationIdentifier compilationIdentifier;
        final SpeculationLog speculationLog;
        final OptionValues options;

        if (TornadoMetaAccessProvider.USE_REFLECTION_FULL) {
            // JVMCI-absent path (JDK 27+): HotSpotJVMCIRuntime's host backend is dead. Use the TornadoVM
            // backend's reflection-backed providers. The default (empty-plugin) graph builder reads the
            // kernel bytecode via ResolvedJavaMethod.getCode() (Classfile parser), and keeps IntArray.set /
            // ArrayLength as high-level nodes that the reduce loop-bound + operator analysis relies on.
            providers = TornadoCoreRuntime.getTornadoRuntime().getBackend(0).getProviders();
            resolvedJavaMethod = providers.getMetaAccess().lookupJavaMethod(methodToCompile);
            compilationIdentifier = new TornadoCompilerIdentifier("code-analysis-" + resolvedJavaMethod.getName(), codeAnalysisId.getAndIncrement());
            speculationLog = null;
            options = getOptions();
        } else {
            GraalJVMCICompiler graalCompiler = getGraalCompiler();
            RuntimeProvider capability = graalCompiler.getGraalRuntime().getCapability(RuntimeProvider.class);
            Backend backend = capability.getHostBackend();
            providers = backend.getProviders();
            resolvedJavaMethod = providers.getMetaAccess().lookupJavaMethod(methodToCompile);
            compilationIdentifier = backend.getCompilationIdentifier(resolvedJavaMethod);
            speculationLog = resolvedJavaMethod.getSpeculationLog();
            if (speculationLog != null) {
                speculationLog.collectFailedSpeculations();
            }
            EconomicMap<OptionKey<?>, Object> opts = OptionValues.newOptionMap();
            opts.putAll(HotSpotGraalOptionValues.defaultOptions().getMap());
            options = new OptionValues(opts);
        }

        try (DebugContext.Scope ignored = getDebugContext().scope("compileMethodAndInstall", new DebugDumpScope("TornadoVM-Code-Analysis", true))) {
            StructuredGraph graph = new StructuredGraph.Builder(options, getDebugContext(), AllowAssumptions.YES).speculationLog(speculationLog).method(resolvedJavaMethod).compilationId(
                    compilationIdentifier).build();
            PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
            graphBuilderSuite.appendPhase(new GraphBuilderPhase(GraphBuilderConfiguration.getDefault(new Plugins(new InvocationPlugins()))));
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
     *     Compile-graph
     * @return {@link InstalledCode}
     */
    public static InstalledCode compileAndInstallMethod(StructuredGraph graph) {
        ResolvedJavaMethod method = graph.method();
        GraalJVMCICompiler graalCompiler = getGraalCompiler();
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
            GraalCompiler.compileGraph(graph, method, providers, backend, graphBuilderPhase, optimizationsOpts, profilerInfo, suites, lirSuites, compilationResult, factory, false);
            return backend.addInstalledCode(getDebugContext(), method, CompilationRequestIdentifier.asCompilationRequest(compilationID), compilationResult);
        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
    }

}
