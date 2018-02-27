/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

import uk.ac.manchester.tornado.api.meta.TaskMetaData;
import uk.ac.manchester.tornado.common.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.graal.compiler.TornadoCompilerIdentifier;
import uk.ac.manchester.tornado.graal.compiler.TornadoSketchTier;
import uk.ac.manchester.tornado.graal.phases.TornadoSketchTierContext;

import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;
import static tornado.runtime.TornadoRuntime.getTornadoExecutor;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;
import static uk.ac.manchester.tornado.common.Tornado.fatal;
import static uk.ac.manchester.tornado.common.Tornado.info;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.guarantee;

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
