/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.sketcher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.*;
import org.graalvm.compiler.debug.internal.method.MethodMetricsRootScopeInfo;
import org.graalvm.compiler.graph.CachedGraph;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.StructuredGraph.Builder;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;

import uk.ac.manchester.tornado.api.common.TornadoInternalError;
import uk.ac.manchester.tornado.api.meta.TaskMetaData;
import uk.ac.manchester.tornado.graal.compiler.TornadoCompilerIdentifier;
import uk.ac.manchester.tornado.graal.compiler.TornadoSketchTier;
import uk.ac.manchester.tornado.graal.phases.TornadoSketchTierContext;

import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;
import static uk.ac.manchester.tornado.api.common.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.common.Tornado.fatal;
import static uk.ac.manchester.tornado.common.Tornado.info;
import static uk.ac.manchester.tornado.runtime.TornadoRuntime.getTornadoExecutor;
import static uk.ac.manchester.tornado.runtime.TornadoRuntime.getTornadoRuntime;

public class TornadoSketcher {

    private static final AtomicInteger sketchId = new AtomicInteger(0);

    private static final Map<ResolvedJavaMethod, Future<Sketch>> cache = new ConcurrentHashMap<>();

    private static final DebugTimer Sketcher = Debug.timer("Sketcher");

    private static final OptimisticOptimizations optimisticOpts = OptimisticOptimizations.ALL;

    public static Sketch lookup(ResolvedJavaMethod resolvedMethod) {
        guarantee(cache.containsKey(resolvedMethod), "cache miss for: %s", resolvedMethod.getName());
        try {
            return cache.get(resolvedMethod).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new TornadoInternalError(e);
        }
    }

    public static void buildSketch(SketchRequest request) {
        DebugEnvironment.ensureInitialized(getTornadoRuntime().getOptions());

        if (cache.containsKey(request.resolvedMethod)) {
            return;
        }
        cache.put(request.resolvedMethod, request);
        try (Scope s = MethodMetricsRootScopeInfo.createRootScopeIfAbsent(request.resolvedMethod)) {
            try (Scope s0 = Debug.scope("SketchCompiler")) {
                request.result = buildSketch(request.meta, request.resolvedMethod, request.providers, request.graphBuilderSuite, request.sketchTier);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        }
    }

    private static Sketch buildSketch(TaskMetaData meta, ResolvedJavaMethod resolvedMethod, Providers providers, PhaseSuite<HighTierContext> graphBuilderSuite, TornadoSketchTier sketchTier) {

        info("Building sketch of %s", resolvedMethod.getName());

        TornadoCompilerIdentifier id = new TornadoCompilerIdentifier("sketch-" + resolvedMethod.getName(), sketchId.getAndIncrement());
        Builder builder = new Builder(getTornadoRuntime().getOptions(), AllowAssumptions.YES);
        builder.method(resolvedMethod);
        builder.compilationId(id);
        builder.name("sketch-" + resolvedMethod.getName());
        final StructuredGraph graph = builder.build();

//        TaskMetaData meta = new TaskMetaData(resolvedMethod.isStatic() ? resolvedMethod.getParameters().length : resolvedMethod.getParameters().length + 1);
        // may need to deprecate this...?
        //providers.getSuitesProvider().setContext(null, resolvedMethod, null, meta);
        try (Scope s = Debug.scope("Sketcher", new DebugDumpScope("Sketcher"));
                DebugCloseable a = Sketcher.start()) {
//            PhaseSuite<HighTierContext> graphBuilderSuite = providers.g.getDefaultGraphBuilderSuite();
            final TornadoSketchTierContext highTierContext = new TornadoSketchTierContext(providers,
                    graphBuilderSuite, optimisticOpts, resolvedMethod, meta);
            if (graph.start().next() == null) {
                graphBuilderSuite.apply(graph, highTierContext);
                new DeadCodeEliminationPhase(Optional).apply(graph);
            } else {
                Debug.dump(Debug.BASIC_LEVEL, graph, "initial state");
            }

//            graph.getMethods().forEach(System.out::println);
            sketchTier.apply(graph, highTierContext);
            graph.maybeCompress();

//            graph.getMethods().forEach(System.out::println);
            // compile any non-inlined call targets
            graph.getInvokes().forEach(invoke -> getTornadoExecutor().execute(new SketchRequest(meta, invoke.callTarget().targetMethod(), providers, graphBuilderSuite, sketchTier)));

            return new Sketch(CachedGraph.fromReadonlyCopy(graph), meta);
        } catch (Throwable e) {
            fatal("unable to build sketch for method: %s (%s)", resolvedMethod.getName(), e.getMessage());
            throw new TornadoInternalError(e);
        }
    }

}
