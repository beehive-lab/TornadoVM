/*
 * Copyright 2012 James Clarkson.
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
 */
package tornado.runtime.sketcher;

import com.oracle.graal.compiler.common.GraalOptions;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.DebugScope;
import com.oracle.graal.debug.internal.method.MethodMetricsRootScopeInfo;
import com.oracle.graal.graph.CachedGraph;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.util.Providers;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.api.meta.TaskMetaData;
import tornado.common.exceptions.TornadoInternalError;
import tornado.graal.TornadoDebugEnvironment;
import tornado.graal.compiler.TornadoCompilerIdentifier;
import tornado.graal.compiler.TornadoSketchTier;
import tornado.graal.phases.TornadoSketchTierContext;

import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.Optional;
import static tornado.common.Tornado.fatal;
import static tornado.common.Tornado.info;
import static tornado.common.exceptions.TornadoInternalError.guarantee;
import static tornado.runtime.TornadoRuntime.getTornadoExecutor;

public class TornadoSketcher {

    private static final AtomicInteger sketchId = new AtomicInteger(0);

    private static final Map<ResolvedJavaMethod, Future<Sketch>> cache = new ConcurrentHashMap<>();

    private static final DebugTimer Sketcher = Debug.timer("Sketcher");

    private static final OptimisticOptimizations optimisticOpts = OptimisticOptimizations.ALL;

    static {
        GraalOptions.OmitHotExceptionStacktrace.setValue(false);
        GraalOptions.MatchExpressions.setValue(true);
        GraalOptions.RemoveNeverExecutedCode.setValue(false);
    }

    public static Sketch lookup(ResolvedJavaMethod resolvedMethod) {
        guarantee(cache.containsKey(resolvedMethod), "cache miss for: %s", resolvedMethod.getName());
        try {
            return cache.get(resolvedMethod).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new TornadoInternalError(e);
        }
    }

    public static void buildSketch(SketchRequest request) {
        if (Debug.isEnabled() && DebugScope.getConfig() == null) {
            TornadoDebugEnvironment.initialize(TTY.out);
        }
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
        final StructuredGraph graph = new StructuredGraph(resolvedMethod,
                AllowAssumptions.YES, id);

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
                Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "initial state");
            }

            sketchTier.apply(graph, highTierContext);
            graph.maybeCompress();

            // compile any non-inlined call targets
            graph.getInvokes().forEach(invoke -> getTornadoExecutor().execute(new SketchRequest(meta, invoke.callTarget().targetMethod(), providers, graphBuilderSuite, sketchTier)));

            return new Sketch(CachedGraph.fromReadonlyCopy(graph), meta);
        } catch (Throwable e) {
            fatal("unable to build sketch for method: %s (%s)", resolvedMethod.getName(), e.getMessage());
            throw new TornadoInternalError(e);
        }
    }

}
