/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.sketcher;

import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getOptions;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getTornadoExecutor;
import static uk.ac.manchester.tornado.runtime.common.Tornado.fatal;
import static uk.ac.manchester.tornado.runtime.common.Tornado.info;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.graph.CachedGraph;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.StructuredGraph.Builder;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCompilerIdentifier;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSketchTier;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoSketchTierContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class TornadoSketcher {

    private static final AtomicInteger sketchId = new AtomicInteger(0);

    private static final Map<ResolvedJavaMethod, Future<Sketch>> cache = new ConcurrentHashMap<>();

    private static final TimerKey Sketcher = DebugContext.timer("Sketcher");

    private static final OptimisticOptimizations optimisticOpts = OptimisticOptimizations.ALL;

    private static HashSet<String> openclTokens = new HashSet<>();
    static {
        // XXX: To be completed
        openclTokens.add("kernel");
        openclTokens.add("__global");
        openclTokens.add("global");
        openclTokens.add("local");
        openclTokens.add("__local");
        openclTokens.add("private");
        openclTokens.add("__private");
    }

    public static Sketch lookup(ResolvedJavaMethod resolvedMethod) {
        guarantee(cache.containsKey(resolvedMethod), "cache miss for: %s", resolvedMethod.getName());
        try {
            return cache.get(resolvedMethod).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new TornadoInternalError(e);
        }
    }

    static void buildSketch(SketchRequest request) {
        if (cache.containsKey(request.resolvedMethod)) {
            return;
        }
        cache.put(request.resolvedMethod, request);
            try (DebugContext.Scope ignored = getDebugContext().scope("SketchCompiler")) {
                request.result = buildSketch(request.meta, request.resolvedMethod, request.providers, request.graphBuilderSuite, request.sketchTier);
            } catch (Throwable e) {
                throw getDebugContext().handle(e);
            }
    }

    private static Sketch buildSketch(TaskMetaData meta, ResolvedJavaMethod resolvedMethod, Providers providers, PhaseSuite<HighTierContext> graphBuilderSuite, TornadoSketchTier sketchTier) {
        info("Building sketch of %s", resolvedMethod.getName());
        TornadoCompilerIdentifier id = new TornadoCompilerIdentifier("sketch-" + resolvedMethod.getName(), sketchId.getAndIncrement());
        Builder builder = new Builder(getOptions(), getDebugContext(), AllowAssumptions.YES);
        builder.method(resolvedMethod);
        builder.compilationId(id);
        builder.name("sketch-" + resolvedMethod.getName());
        final StructuredGraph graph = builder.build();

        // Check legal Kernel Name
        if (openclTokens.contains(resolvedMethod.getName())) {
            throw new TornadoRuntimeException("[ERROR] Java method name corresponds to an OpenCL Token. Change the Java method's name: " + resolvedMethod.getName());
        }

        try (DebugContext.Scope ignored = getDebugContext().scope("Tornado-Sketcher", new DebugDumpScope("Tornado-Sketcher")); DebugCloseable ignored1 = Sketcher.start(getDebugContext())) {
            final TornadoSketchTierContext highTierContext = new TornadoSketchTierContext(providers, graphBuilderSuite, optimisticOpts, resolvedMethod, meta);
            if (graph.start().next() == null) {
                graphBuilderSuite.apply(graph, highTierContext);
                new DeadCodeEliminationPhase(Optional).apply(graph);
            } else {
                getDebugContext().dump(DebugContext.BASIC_LEVEL, graph, "initial state");
            }

            sketchTier.apply(graph, highTierContext);
            graph.maybeCompress();

            // Compile all non-inlined call-targets into a single compilation-unit

            // @formatter:off
            graph.getInvokes()
                 .forEach(invoke -> {
                     if (openclTokens.contains(invoke.callTarget().targetMethod().getName())) {
                         throw new TornadoRuntimeException("[ERROR] Java method name corresponds to an OpenCL Token. Change the Java method's name: " + invoke.callTarget().targetMethod().getName());
                     }
                     getTornadoExecutor().execute(new SketchRequest(meta,invoke.callTarget().targetMethod(),providers,graphBuilderSuite,sketchTier));
                 });
            // @formatter:on
            return new Sketch(CachedGraph.fromReadonlyCopy(graph), meta);

        } catch (Throwable e) {
            fatal("unable to build sketch for method: %s (%s)", resolvedMethod.getName(), e.getMessage());
            throw new TornadoInternalError(e);
        }
    }
}
