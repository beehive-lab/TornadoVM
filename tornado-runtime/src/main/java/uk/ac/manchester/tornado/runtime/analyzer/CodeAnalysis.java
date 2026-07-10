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

import tornado.graal.compiler.core.common.CompilationIdentifier;
import tornado.graal.compiler.debug.DebugContext;
import tornado.graal.compiler.debug.DebugDumpScope;
import tornado.graal.compiler.java.GraphBuilderPhase;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import tornado.graal.compiler.options.OptionValues;
import tornado.graal.compiler.phases.OptimisticOptimizations;
import tornado.graal.compiler.phases.PhaseSuite;
import tornado.graal.compiler.phases.tiers.HighTierContext;
import tornado.graal.compiler.phases.util.Providers;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCompilerIdentifier;

public class CodeAnalysis {

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

        // Build the analysis graph from the TornadoVM backend's reflection-backed providers. The default
        // (empty-plugin) graph builder reads the kernel bytecode via ResolvedJavaMethod.getCode() (Classfile
        // parser), and keeps IntArray.set / ArrayLength as high-level nodes that the reduce loop-bound +
        // operator analysis relies on.
        providers = TornadoCoreRuntime.getTornadoRuntime().getBackend(0).getProviders();
        resolvedJavaMethod = providers.getMetaAccess().lookupJavaMethod(methodToCompile);
        compilationIdentifier = new TornadoCompilerIdentifier("code-analysis-" + resolvedJavaMethod.getName(), codeAnalysisId.getAndIncrement());
        speculationLog = null;
        options = getOptions();

        try (DebugContext.Scope ignored = getDebugContext().scope("compileMethodAndInstall", new DebugDumpScope("TornadoVM-Code-Analysis", true))) {
            StructuredGraph graph = new StructuredGraph.Builder(options, getDebugContext(), AllowAssumptions.YES).speculationLog(speculationLog).method(resolvedJavaMethod).compilationId(
                    compilationIdentifier).build();
            PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
            graphBuilderSuite.appendPhase(new GraphBuilderPhase(GraphBuilderConfiguration.getDefault(new Plugins(new InvocationPlugins()))));
            graphBuilderSuite.apply(graph, new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.ALL));
            getDebugContext().dump(DebugContext.BASIC_LEVEL, graph, "CodeToAnalyze");
            return graph;
        } catch (Throwable e) {
            // Do not swallow the failure: returning null here leaves callers (e.g. reduce loop-bound analysis)
            // to NPE downstream with no trace of the real cause. Log it and bail out so the compilation aborts
            // cleanly with the underlying exception attached.
            new TornadoLogger(CodeAnalysis.class).error("Code analysis graph build failed for %s: %s", resolvedJavaMethod.getName(), e);
            throw new TornadoBailoutRuntimeException("Code analysis graph build failed for " + resolvedJavaMethod.getName(), e instanceof Exception ex ? ex : new RuntimeException(e));
        }
    }

}
