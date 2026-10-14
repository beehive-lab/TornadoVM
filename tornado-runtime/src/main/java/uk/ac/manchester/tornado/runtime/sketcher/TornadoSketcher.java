/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, 2023, 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.sketcher;

import static tornado.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getOptions;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getTornadoExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import tornado.graal.compiler.debug.DebugCloseable;
import tornado.graal.compiler.debug.DebugContext;
import tornado.graal.compiler.debug.DebugDumpScope;
import tornado.graal.compiler.debug.TimerKey;
import tornado.graal.compiler.nodes.CallTargetNode;
import tornado.graal.compiler.nodes.ParameterNode;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import tornado.graal.compiler.nodes.StructuredGraph.Builder;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.phases.OptimisticOptimizations;
import tornado.graal.compiler.phases.PhaseSuite;
import tornado.graal.compiler.phases.common.DeadCodeEliminationPhase;
import tornado.graal.compiler.phases.tiers.HighTierContext;
import tornado.graal.compiler.phases.util.Providers;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInliningException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.OCLTokens;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCompilerIdentifier;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSketchTier;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.DeviceKernelLaunch;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoSketchTierContext;

public class TornadoSketcher {

    private static final AtomicInteger sketchId = new AtomicInteger(0);
    private static final Map<ResolvedJavaMethod, List<TornadoSketcherCacheEntry>> cache = new ConcurrentHashMap<>();
    private static final TimerKey Sketcher = DebugContext.timer("Sketcher");
    private static final OptimisticOptimizations optimisticOpts = OptimisticOptimizations.ALL;
    private static TornadoLogger logger = new TornadoLogger();

    public static Sketch lookup(ResolvedJavaMethod resolvedMethod, int driverIndex, int deviceIndex) {
        Sketch sketch = null;
        guarantee(cache.containsKey(resolvedMethod), "cache miss for: %s", resolvedMethod.getName());
        List<TornadoSketcherCacheEntry> entries = cache.get(resolvedMethod);
        try {
            synchronized (entries) {
                for (TornadoSketcherCacheEntry entry : entries) {
                    if (entry.matchesDriverAndDevice(driverIndex, deviceIndex)) {
                        sketch = entry.getSketchFuture().get();
                        break;
                    }
                }
            }
            guarantee(sketch != null, "No sketch available for %d:%d %s", driverIndex, deviceIndex, resolvedMethod.getName());
        } catch (InterruptedException | ExecutionException e) {
            logger.fatal("Failed to retrieve sketch for %d:%d %s ", driverIndex, deviceIndex, resolvedMethod.getName());
            if (TornadoOptions.DEBUG) {
                e.printStackTrace();
            }
            final Throwable cause = e.getCause();
            if (cause instanceof TornadoInliningException inliningException) {
                throw inliningException;
            }
            if (cause instanceof TornadoRuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof TornadoBailoutRuntimeException bailoutRuntimeException) {
                throw bailoutRuntimeException;
            }
            throw new TornadoInternalError(cause);
        }
        return sketch;
    }

    static void buildSketch(SketchRequest request) {
        List<TornadoSketcherCacheEntry> sketches = cache.computeIfAbsent(request.resolvedMethod, k -> Collections.synchronizedList(new ArrayList<>(TornadoVMBackendType.values().length)));
        // Check and add under one lock: two plans built on two threads can ask for the same
        // method's sketch at once, and only one of them should build it.
        synchronized (sketches) {
            for (TornadoSketcherCacheEntry entry : sketches) {
                if (entry.matchesDriverAndDevice(request.driverIndex, request.deviceIndex)) {
                    return;
                }
            }
            Future<Sketch> result = getTornadoExecutor().submit(new TornadoSketcherCallable(request));
            sketches.add(new TornadoSketcherCacheEntry(request.driverIndex, request.deviceIndex, result));
        }
    }

    @SuppressWarnings("checkstyle:LineLength")
    private static Sketch buildSketch(ResolvedJavaMethod resolvedMethod, Providers providers, PhaseSuite<HighTierContext> graphBuilderSuite, TornadoSketchTier sketchTier, int backendIndex,
            int deviceIndex, Set<ResolvedJavaMethod> launchAncestors) {
        logger.info("Building sketch of %s::%s", resolvedMethod.getDeclaringClass().getName(), resolvedMethod.getName());
        TornadoCompilerIdentifier id = new TornadoCompilerIdentifier("sketch-" + resolvedMethod.getName(), sketchId.getAndIncrement());
        Builder builder = new Builder(getOptions(), getDebugContext(), AllowAssumptions.YES);
        builder.method(resolvedMethod);
        builder.compilationId(id);
        builder.name("sketch-" + resolvedMethod.getName());
        final StructuredGraph graph = builder.build();

        // Check legal Kernel Name
        if (OCLTokens.openCLTokens.contains(resolvedMethod.getName())) {
            throw new TornadoRuntimeException("[ERROR] Java method name corresponds to an OpenCL Token. Change the Java method's name: " + resolvedMethod.getName());
        }

        try (DebugContext.Scope ignored = getDebugContext().scope("Tornado-Sketcher", new DebugDumpScope("Tornado-Sketcher")); DebugCloseable ignored1 = Sketcher.start(getDebugContext())) {
            final TornadoSketchTierContext highTierContext = new TornadoSketchTierContext(providers, graphBuilderSuite, optimisticOpts, resolvedMethod, backendIndex, deviceIndex);
            if (graph.start().next() == null) {
                graphBuilderSuite.apply(graph, highTierContext);
                new DeadCodeEliminationPhase(Optional).apply(graph);
            } else {
                getDebugContext().dump(DebugContext.BASIC_LEVEL, graph, "initial state");
            }

            sketchTier.apply(graph, highTierContext);
            graph.maybeCompress();

            // Compile all non-inlined call-targets into a single compilation-unit
            graph.getInvokes() //
                    .forEach(invoke -> { //
                        ResolvedJavaMethod targetMethod = invoke.callTarget().targetMethod();
                        if (OCLTokens.openCLTokens.contains(targetMethod.getName())) {
                            throw new TornadoRuntimeException("[ERROR] Java method name corresponds to an OpenCL Token. Change the Java method's name: " + targetMethod.getName());
                        }
                        // Native / signature-polymorphic call-targets (e.g. MethodHandle.invokeBasic,
                        // Throwable.fillInStackTrace) have no bytecode and cannot be sketched.
                        if (!targetMethod.hasBytecodes()) {
                            return;
                        }
                        SketchRequest newRequest = new SketchRequest(targetMethod, providers, graphBuilderSuite, sketchTier, backendIndex, deviceIndex);
                        buildSketch(newRequest);
                    });

            // Kernels launched from the device are compiled into the same compilation unit too. A
            // launch is not an invoke, so its child is sketched here explicitly. A child that is
            // already being sketched further up the launch chain (a kernel launching itself, or
            // A -> B -> A) cannot be waited on: that sketch is waiting on this one.
            Set<ResolvedJavaMethod> launchChain = new HashSet<>(launchAncestors);
            launchChain.add(resolvedMethod);
            List<DeviceKernelLaunch> deviceLaunches = new ArrayList<>();
            List<DeviceKernelLaunch> cyclicLaunches = new ArrayList<>();
            for (var node : graph.getNodes()) {
                if (node instanceof DeviceKernelLaunch launch) {
                    if (launchChain.contains(launch.getTargetMethod())) {
                        cyclicLaunches.add(launch);
                    } else {
                        deviceLaunches.add(launch);
                        buildSketch(new SketchRequest(launch.getTargetMethod(), providers, graphBuilderSuite, sketchTier, backendIndex, deviceIndex, launchChain));
                    }
                }
            }

            Access[] highTierAccesses = highTierContext.getAccesses();
            // Whatever the child reads or writes through a parent parameter, the parent does too:
            // without this, an array only the child writes would never be copied back to the host.
            for (DeviceKernelLaunch launch : deviceLaunches) {
                Sketch sketch = lookup(launch.getTargetMethod(), backendIndex, deviceIndex);
                mergeAccesses(highTierAccesses, launch.getChildParameterValues(), sketch.getArgumentsAccess());
            }
            // The accesses of a child in a launch cycle are not known yet: assume it reads and writes
            // every array it is given.
            for (DeviceKernelLaunch launch : cyclicLaunches) {
                for (ValueNode value : launch.getChildParameterValues()) {
                    if (value instanceof ParameterNode param && param.getStackKind() == JavaKind.Object) {
                        highTierAccesses[param.index()] = Access.READ_WRITE;
                    }
                }
            }
            graph.getInvokes().forEach(invoke -> {
                ResolvedJavaMethod targetMethod = invoke.callTarget().targetMethod();
                // Skipped above (no bytecode), so there is no sketch to merge.
                if (!targetMethod.hasBytecodes()) {
                    return;
                }
                // Merge the accesses of the caller with the accesses of the callee
                Sketch sketch = lookup(targetMethod, backendIndex, deviceIndex);
                mergeAccesses(highTierAccesses, invoke.callTarget(), sketch.getArgumentsAccess());
            });

            // The accesses go straight into this method's sketch. Sketches of different methods are
            // built concurrently on the sketcher pool, so nothing here may pass through a shared field.
            return new Sketch(graph.copy(TornadoCoreRuntime.getDebugContext()), highTierAccesses, highTierContext.getBatchWriteThreadIndex());

        } catch (Throwable e) {
            logger.fatal("unable to build sketch for method: %s (%s)", resolvedMethod.getName(), e.getMessage());
            if (TornadoOptions.DEBUG) {
                e.printStackTrace();
            }
            if (e instanceof TornadoInliningException) {
                throw new TornadoInliningException(e.getMessage());
            } else {
                throw new TornadoBailoutRuntimeException("Unable to build sketch for method: " + resolvedMethod.getName() + " (" + e.getMessage() + ")");
            }
        }
    }

    /**
     * Merges the {@param calleeAccesses} into the {@param callerAccesses}. For
     * example, given the two {@link Access} arrays below, a merge will look like:
     *
     * <p>
     * Caller accesses: NONE, READ, WRITE, NONE, READ_WRITE Callee accesses: READ,
     * WRITE, NONE, READ_WRITE, NONE
     * </p>
     *
     * <p>
     * Updated caller accesses: READ, READ_WRITE, WRITE, READ_WRITE, READ_WRITE
     * </p>
     *
     * <p>
     * This is needed since caller parameters can have different accesses in a
     * callee.
     * </p>
     */
    private static void mergeAccesses(Access[] callerAccesses, CallTargetNode callTarget, Access[] calleeAccesses) {
        List<ValueNode> callArgs = callTarget.arguments().snapshot();

        int index = callTarget.targetMethod().isStatic() ? 0 : 1;

        for (; index < callArgs.size(); index++) {
            ValueNode callArg = callArgs.get(index);
            if (!(callArg instanceof ParameterNode param)) {
                continue;
            }
            int paramIndex = param.index();

            // The callee's argument-access array can be shorter than the call's argument list (e.g. an
            // intrinsified KernelContext accessor whose sketch is looked up before it is fully built on the
            // concurrent sketch path). A missing callee access is unknown, so leave the caller access as is
            // rather than indexing out of bounds.
            if (index >= calleeAccesses.length) {
                continue;
            }
            Access calleeAcc = calleeAccesses[index];
            Access callerAcc = callerAccesses[paramIndex];

            callerAccesses[paramIndex] = Access.asArray()[callerAcc.position | calleeAcc.position];
        }
    }

    /**
     * Merges the parameter accesses of a kernel launched from the device into its parent's.
     * {@code childValues[i]} is the value the parent passes for the child's parameter {@code i}.
     */
    private static void mergeAccesses(Access[] callerAccesses, ValueNode[] childValues, Access[] calleeAccesses) {
        for (int index = 0; index < childValues.length && index < calleeAccesses.length; index++) {
            if (!(childValues[index] instanceof ParameterNode param)) {
                continue;
            }
            int paramIndex = param.index();
            Access calleeAcc = calleeAccesses[index];
            Access callerAcc = callerAccesses[paramIndex];
            callerAccesses[paramIndex] = Access.asArray()[callerAcc.position | calleeAcc.position];
        }
    }

    private static final class TornadoSketcherCacheEntry {

        private final int driverIndex;
        private final int deviceIndex;
        private final Future<Sketch> sketchFuture;

        private TornadoSketcherCacheEntry(int driverIndex, int deviceIndex, Future<Sketch> sketchFuture) {
            this.driverIndex = driverIndex;
            this.deviceIndex = deviceIndex;
            this.sketchFuture = sketchFuture;
        }

        public boolean matchesDriverAndDevice(int driverIndex, int deviceIndex) {
            return this.driverIndex == driverIndex && this.deviceIndex == deviceIndex;
        }

        public Future<Sketch> getSketchFuture() {
            return sketchFuture;
        }
    }

    private static class TornadoSketcherCallable implements Callable<Sketch> {
        private final SketchRequest request;

        TornadoSketcherCallable(SketchRequest request) {
            this.request = request;
        }

        @Override
        public Sketch call() {
            try (DebugContext.Scope ignored = getDebugContext().scope("SketchCompiler")) {
                return buildSketch(request.resolvedMethod, request.providers, request.graphBuilderSuite, request.sketchTier, request.driverIndex, request.deviceIndex, request.launchAncestors);
            } catch (Throwable e) {
                if (e instanceof TornadoInliningException) {
                    throw (TornadoInliningException) e;
                } else {
                    throw getDebugContext().handle(e);
                }
            }
        }
    }
}
