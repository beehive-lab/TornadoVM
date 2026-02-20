/*
 * Copyright (c) 2020-2023, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.metal.graal.compiler;

import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DUMP_COMPILED_METHODS;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.alloc.LinearScanOrder;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.cfg.CodeEmissionOrder;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.phases.AllocationPhase.AllocationContext;
import org.graalvm.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.StructuredGraph.Builder;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.HIRBlock;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.TriState;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.MetalTargetDescription;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalProviders;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalSuitesProvider;
import uk.ac.manchester.tornado.drivers.metal.graal.backend.MetalBackend;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalLIRGenerationPhase.LIRGenerationContext;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.BatchCompilationConfig;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.graal.TornadoLIRSuites;
import uk.ac.manchester.tornado.runtime.graal.TornadoSuites;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCompilerIdentifier;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoLowTierContext;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoMidTierContext;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

/**
 * Static methods for orchestrating the compilation of a
 * {@linkplain StructuredGraph graph}.
 */
public class MetalCompiler {

    private static final AtomicInteger compilationId = new AtomicInteger();

    private static final TimerKey CompilerTimer = DebugContext.timer("MetalGraalCompiler");
    private static final TimerKey FrontEnd = DebugContext.timer("MetalFrontend");
    private static final TimerKey BackEnd = DebugContext.timer("MetalBackend");
    private static final TimerKey EmitLIR = DebugContext.timer("MetalEmitLIR");
    private static final TimerKey EmitCode = DebugContext.timer("MetalEmitCode");

    private static final MetalLIRGenerationPhase LIR_GENERATION_PHASE = new MetalLIRGenerationPhase();

    /**
     * Services a given compilation request.
     *
     * @return the result of the compilation
     */
    public static <T extends MetalCompilationResult> T compile(Request<T> r) {
        assert !r.graph.isFrozen();
        try (DebugContext.Scope s0 = getDebugContext().scope("GraalCompiler", r.graph, r.providers.getCodeCache()); DebugCloseable a = CompilerTimer.start(getDebugContext())) {
            emitFrontEnd(r.providers, r.backend, r.installedCodeOwner, r.args, r.meta, r.graph, r.graphBuilderSuite, r.optimisticOpts, r.profilingInfo, r.suites, r.isKernel, r.buildGraph,
                    r.batchCompilationConfig);
            /*
             * A task is determined as parallel if: (i) it has loops annotated with
             * {@link uk.ac.manchester.tornado.api.annotations.Parallel} which corresponds
             * to use a domain with depth greater than zero, or (ii) it uses the
             * GridScheduler.
             */
            boolean isParallel = r.meta != null && (r.meta.isParallel() || (r.meta.isGridSchedulerEnabled() && !r.meta.isGridSequential()));
            emitBackEnd(r.graph, null, r.installedCodeOwner, r.backend, r.compilationResult, null, r.lirSuites, r.isKernel, isParallel, r.profiler);
        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
        return r.compilationResult;
    }

    public static ProfilingInfo getProfilingInfo(StructuredGraph graph) {
        if (graph.method() != null) {
            return graph.method().getProfilingInfo();
        } else {
            return DefaultProfilingInfo.get(TriState.UNKNOWN);
        }
    }

    private static boolean isGraphEmpty(StructuredGraph graph) {
        return graph.start().next() == null;
    }

    /**
     * Builds the graph, optimizes it.
     */
    private static void emitFrontEnd(Providers providers, MetalBackend backend, ResolvedJavaMethod method, Object[] args, TaskDataContext meta, StructuredGraph graph,
            PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, TornadoSuites suites, boolean isKernel, boolean buildGraph,
            BatchCompilationConfig batchCompilationConfig) {
        try (DebugContext.Scope s = getDebugContext().scope("MetalFrontend", new DebugDumpScope("MetalFrontend")); DebugCloseable a = FrontEnd.start(getDebugContext())) {

            /*
             * Register metadata with all tornado phases
             */
            ((MetalCanonicalizer) suites.getHighTier().getCustomCanonicalizer()).setContext(providers.getMetaAccess(), method, args, meta);

            final TornadoHighTierContext highTierContext = new TornadoHighTierContext(providers, graphBuilderSuite, optimisticOpts, method, args, meta, isKernel, batchCompilationConfig);

            if (buildGraph) {
                if (isGraphEmpty(graph)) {
                    graphBuilderSuite.apply(graph, highTierContext);
                    new DeadCodeEliminationPhase(Optional).apply(graph);
                } else {
                    getDebugContext().dump(DebugContext.INFO_LEVEL, graph, "initial state");
                }
            }
            suites.getHighTier().apply(graph, highTierContext);
            graph.maybeCompress();

            final TornadoMidTierContext midTierContext = new TornadoMidTierContext(providers, backend, optimisticOpts, profilingInfo, method, args, meta);
            suites.getMidTier().apply(graph, midTierContext);

            graph.maybeCompress();

            final TornadoLowTierContext lowTierContext = new TornadoLowTierContext(providers, backend, meta);
            suites.getLowTier().apply(graph, lowTierContext);

            getDebugContext().dump(DebugContext.BASIC_LEVEL, graph.getLastSchedule(), "Final HIR schedule");
        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
    }

    private static <T extends MetalCompilationResult> void emitBackEnd(StructuredGraph graph, Object stub, ResolvedJavaMethod installedCodeOwner, MetalBackend backend, T compilationResult,
            RegisterConfig registerConfig, TornadoLIRSuites lirSuites, boolean isKernel, boolean isParallel, TornadoProfiler profiler) {
        try (DebugContext.Scope s = getDebugContext().scope("MetalBackend", graph.getLastSchedule()); DebugCloseable a = BackEnd.start(getDebugContext())) {
            LIRGenerationResult lirGen = null;
            lirGen = emitLIR(backend, graph, stub, registerConfig, lirSuites, compilationResult, isKernel);
            try (DebugContext.Scope s2 = getDebugContext().scope("MetalCodeGen", lirGen, lirGen.getLIR())) {
                compilationResult.setHasUnsafeAccess(graph.hasUnsafeAccess());
                emitCode(backend, graph.getAssumptions(), graph.method(), graph.getMethods(), lirGen, compilationResult, installedCodeOwner, isKernel, isParallel, profiler);
            } catch (Throwable e) {
                throw getDebugContext().handle(e);
            }
        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
    }

    public static <T extends CompilationResult> LIRGenerationResult emitLIR(MetalBackend backend, StructuredGraph graph, Object stub, RegisterConfig registerConfig, TornadoLIRSuites lirSuites,
            T compilationResult, boolean isKernel) {
        try {
            return emitLIR0(backend, graph, stub, registerConfig, lirSuites, compilationResult, isKernel);
        } catch (Throwable e) {
            throw e;
        }
    }

    protected static <T extends CompilationResult> String getCompilationUnitName(StructuredGraph graph, T compilationResult) {
        if (compilationResult != null && compilationResult.getName() != null) {
            return compilationResult.getName();
        }
        ResolvedJavaMethod method = graph.method();
        if (method == null) {
            return "<unknown>";
        }
        return method.format("%H.%n(%p)");
    }

    private static <T extends CompilationResult> LIRGenerationResult emitLIR0(MetalBackend backend, StructuredGraph graph, Object stub, RegisterConfig registerConfig, TornadoLIRSuites lirSuites,
            T compilationResult, boolean isKernel) {
        try (DebugContext.Scope ds = getDebugContext().scope("EmitLIR"); DebugCloseable a = EmitLIR.start(getDebugContext())) {
            OptionValues options = graph.getOptions();
            ScheduleResult schedule = graph.getLastSchedule();
            HIRBlock[] blocks = schedule.getCFG().getBlocks();
            HIRBlock startBlock = schedule.getCFG().getStartBlock();
            assert startBlock != null;
            assert startBlock.getPredecessorCount() == 0;

            LIR lir = null;
            try (DebugContext.Scope s = getDebugContext().scope("ComputeLinearScanOrder", lir)) {
                CodeEmissionOrder<?> blockOrder = backend.newBlockOrder(blocks.length, startBlock);
                int[] linearScanOrder = LinearScanOrder.computeLinearScanOrder(blocks.length, startBlock);
                lir = new LIR(schedule.getCFG(), linearScanOrder, graph.getOptions(), graph.getDebug());

                getDebugContext().dump(DebugContext.INFO_LEVEL, lir, "After linear scan order");
            } catch (Throwable e) {
                throw getDebugContext().handle(e);
            }
            RegisterAllocationConfig registerAllocationConfig = backend.newRegisterAllocationConfig(registerConfig, new String[] {});
            FrameMapBuilder frameMapBuilder = backend.newFrameMapBuilder(registerConfig);
            LIRGenerationResult lirGenRes = backend.newLIRGenerationResult(graph.compilationId(), lir, frameMapBuilder, registerAllocationConfig);
            LIRGeneratorTool lirGen = backend.newLIRGenerator(lirGenRes);
            NodeLIRBuilderTool nodeLirGen = backend.newNodeLIRBuilder(graph, lirGen);

            // LIR generation
            LIRGenerationContext context = new LIRGenerationContext(lirGen, nodeLirGen, graph, schedule, isKernel);
            LIR_GENERATION_PHASE.apply(backend.getTarget(), lirGenRes, context);

            try (DebugContext.Scope s = getDebugContext().scope("LIRStages", nodeLirGen, lir)) {
                getDebugContext().dump(DebugContext.BASIC_LEVEL, lir, "After LIR generation");
                LIRGenerationResult result = emitLowLevel((MetalTargetDescription) backend.getTarget(), lirGenRes, lirGen, lirSuites, registerAllocationConfig);
                getDebugContext().dump(DebugContext.BASIC_LEVEL, lir, "Before code generation");
                return result;
            } catch (Throwable e) {
                throw getDebugContext().handle(e);
            }
        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
    }

    public static LIRGenerationResult emitLowLevel(MetalTargetDescription target, LIRGenerationResult lirGenRes, LIRGeneratorTool lirGen, TornadoLIRSuites lirSuites,
            RegisterAllocationConfig registerAllocationConfig) {
        final PreAllocationOptimizationContext preAllocOptContext = new PreAllocationOptimizationContext(lirGen);
        lirSuites.getPreAllocationStage().apply(target, lirGenRes, preAllocOptContext);
        AllocationContext allocContext = new AllocationContext(lirGen.getSpillMoveFactory(), registerAllocationConfig);
        lirSuites.getAllocationStage().apply(target, lirGenRes, allocContext);
        return lirGenRes;
    }

    public static void emitCode(MetalBackend backend, Assumptions assumptions, ResolvedJavaMethod rootMethod, List<ResolvedJavaMethod> inlinedMethods, LIRGenerationResult lirGenRes,
            MetalCompilationResult compilationResult, ResolvedJavaMethod installedCodeOwner, boolean isKernel, boolean isParallel, TornadoProfiler profiler) {
        try (DebugCloseable a = EmitCode.start(getDebugContext())) {
            FrameMap frameMap = lirGenRes.getFrameMap();
            final MetalCompilationResultBuilder crb = backend.newCompilationResultBuilder(frameMap, compilationResult, isKernel, isParallel, lirGenRes.getLIR());
            backend.emitCode(crb, lirGenRes.getLIR(), installedCodeOwner, profiler);

            if (assumptions != null && !assumptions.isEmpty()) {
                compilationResult.setAssumptions(assumptions.toArray());
            }
            if (inlinedMethods != null) {
                compilationResult.setMethods(rootMethod, inlinedMethods);
            }

            compilationResult.setNonInlinedMethods(crb.getNonInlinedMethods());
            crb.finish();

            if (getDebugContext().isCountEnabled()) {
                DebugContext.counter("CompilationResults").increment(getDebugContext());
                DebugContext.counter("CodeBytesEmitted").add(getDebugContext(), compilationResult.getTargetCodeSize());
            }

            getDebugContext().dump(DebugContext.BASIC_LEVEL, compilationResult, "After code generation");
        }
    }

    public static MetalCompilationResult compileCodeForDevice(ResolvedJavaMethod resolvedMethod, Object[] args, TaskDataContext meta, MetalProviders providers, MetalBackend backend,
            TornadoProfiler profiler) {
        return compileCodeForDevice(resolvedMethod, args, meta, providers, backend, new BatchCompilationConfig(0, 0, 0), profiler);

    }

    public static MetalCompilationResult compileCodeForDevice(ResolvedJavaMethod resolvedMethod, Object[] args, TaskDataContext meta, MetalProviders providers, MetalBackend backend,
            BatchCompilationConfig batchCompilationConfig, TornadoProfiler profiler) {
        new TornadoLogger().info("Compiling %s on %s", resolvedMethod.getName(), backend.getDeviceContext().getDevice().getDeviceName());
        final TornadoCompilerIdentifier id = new TornadoCompilerIdentifier("compile-kernel" + resolvedMethod.getName(), compilationId.getAndIncrement());

        Builder builder = new Builder(TornadoCoreRuntime.getOptions(), getDebugContext(), AllowAssumptions.YES);
        builder.method(resolvedMethod);
        builder.compilationId(id);
        builder.name("compile-kernel" + resolvedMethod.getName());

        final StructuredGraph kernelGraph = builder.build();

        OptimisticOptimizations optimisticOpts = OptimisticOptimizations.ALL;
        ProfilingInfo profilingInfo = resolvedMethod.getProfilingInfo();

        MetalCompilationResult kernelCompResult = new MetalCompilationResult("internal", resolvedMethod.getName(), meta, backend);
        CompilationResultBuilderFactory factory = CompilationResultBuilderFactory.Default;

        final MetalSuitesProvider suitesProvider = providers.getSuitesProvider();
        Request<MetalCompilationResult> kernelCompilationRequest = new Request<>(kernelGraph, resolvedMethod, args, meta, providers, backend, suitesProvider.getGraphBuilderSuite(), optimisticOpts,
                profilingInfo, suitesProvider.getSuites(), suitesProvider.getLIRSuites(), kernelCompResult, factory, true, true, batchCompilationConfig, profiler);

        kernelCompilationRequest.execute();

        final Deque<ResolvedJavaMethod> workList = new ArrayDeque<>(kernelCompResult.getNonInlinedMethods());

        while (!workList.isEmpty()) {
            Builder builder1 = new Builder(TornadoCoreRuntime.getOptions(), getDebugContext(), AllowAssumptions.YES);
            builder1.method(resolvedMethod);
            builder1.compilationId(id);
            final ResolvedJavaMethod currentMethod = workList.pop();
            builder1.name("internal" + currentMethod.getName());
            final StructuredGraph graph = builder.build();
            final MetalCompilationResult compResult = new MetalCompilationResult("internal", currentMethod.getName(), meta, backend);
            Request<MetalCompilationResult> methodCompilationRequest = new Request<>(graph, currentMethod, null, null, providers, backend, suitesProvider.getGraphBuilderSuite(), optimisticOpts,
                    profilingInfo, suitesProvider.getSuites(), suitesProvider.getLIRSuites(), compResult, factory, false, true, new BatchCompilationConfig(0, 0, 0), profiler);

            methodCompilationRequest.execute();
            workList.addAll(compResult.getNonInlinedMethods());

            kernelCompResult.addCompiledMethodCode(compResult.getTargetCode());
        }

        return kernelCompResult;
    }

    public synchronized static MetalCompilationResult compileSketchForDevice(Sketch sketch, CompilableTask task, MetalProviders providers, MetalBackend backend, TornadoProfiler profiler) {
        final StructuredGraph kernelGraph = (StructuredGraph) sketch.getGraph().copy(getDebugContext());
        ResolvedJavaMethod resolvedMethod = kernelGraph.method();

        new TornadoLogger().info("Compiling sketch %s on %s", resolvedMethod.getName(), backend.getDeviceContext().getDevice().getDeviceName());

        final TaskDataContext taskMeta = task.meta();
        final Object[] args = task.getArguments();
        final long batchThreads = (taskMeta.getNumThreads() > 0) ? taskMeta.getNumThreads() : task.getBatchThreads();
        final int batchNumber = task.getBatchNumber();
        final long batchSize = task.getBatchSize();
        BatchCompilationConfig batchCompilationConfig = new BatchCompilationConfig(batchThreads, batchNumber, batchSize);
        taskMeta.setCompiledGraph(resolvedMethod);

        OptimisticOptimizations optimisticOpts = OptimisticOptimizations.ALL;
        ProfilingInfo profilingInfo = resolvedMethod.getProfilingInfo();

        String kernelName = MetalDeviceContext.checkKernelName(resolvedMethod.getName());
        MetalCompilationResult kernelCompResult = new MetalCompilationResult(task.getId(), kernelName, taskMeta, backend);
        CompilationResultBuilderFactory factory = CompilationResultBuilderFactory.Default;

        Set<ResolvedJavaMethod> methods = new HashSet<>();

        final MetalSuitesProvider suitesProvider = providers.getSuitesProvider();
        Request<MetalCompilationResult> kernelCompilationRequest = new Request<>(kernelGraph, resolvedMethod, args, taskMeta, providers, backend, suitesProvider.getGraphBuilderSuite(), optimisticOpts,
                profilingInfo, suitesProvider.getSuites(), suitesProvider.getLIRSuites(), kernelCompResult, factory, true, false, batchCompilationConfig, profiler);

        kernelCompilationRequest.execute();

        if (DUMP_COMPILED_METHODS) {
            methods.add(kernelGraph.method());
            methods.addAll(kernelGraph.getMethods());
            Collections.addAll(methods, kernelCompResult.getMethods());
        }

        // @formatter:off
        /*
         * Given the non-inlined methods A, B, C, D and the call graph below, method D
         * can be compiled twice.
         *           A → B → D
         *             ↘ C ↗
         * We use hash set below to prevent this.
         */
        // @formatter:on
        final Set<ResolvedJavaMethod> nonInlinedCompiledMethods = new HashSet<>();
        final Deque<ResolvedJavaMethod> workList = new ArrayDeque<>(kernelCompResult.getNonInlinedMethods());
        while (!workList.isEmpty()) {
            final ResolvedJavaMethod currentMethod = workList.pop();
            if (nonInlinedCompiledMethods.contains(currentMethod)) {
                continue;
            } else {
                nonInlinedCompiledMethods.add(currentMethod);
            }
            Sketch currentSketch = TornadoSketcher.lookup(currentMethod, task.meta().getBackendIndex(), task.meta().getDeviceIndex());
            final StructuredGraph graph = (StructuredGraph) currentSketch.getGraph().copy(getDebugContext());

            String subKernelName = MetalDeviceContext.checkKernelName(currentMethod.getName());
            final MetalCompilationResult compResult = new MetalCompilationResult(task.getId(), subKernelName, taskMeta, backend);

            Request<MetalCompilationResult> methodCompilationRequest = new Request<>(graph, currentMethod, //
                    null, null, providers, backend, suitesProvider.getGraphBuilderSuite(), //
                    optimisticOpts, profilingInfo, suitesProvider.getSuites(), suitesProvider.getLIRSuites(), //
                    compResult, factory, false, false, new BatchCompilationConfig(0, 0, 0), profiler);

            methodCompilationRequest.execute();
            workList.addAll(compResult.getNonInlinedMethods());

            if (DUMP_COMPILED_METHODS) {
                methods.add(graph.method());
                methods.addAll(graph.getMethods());
            }

            kernelCompResult.addCompiledMethodCode(compResult.getTargetCode());
        }

        if (DUMP_COMPILED_METHODS) {
            final Path outDir = Paths.get("./metal-compiled-methods");
            if (!Files.exists(outDir)) {
                try {
                    Files.createDirectories(outDir);
                } catch (IOException e) {
                    TornadoLogger logger = new TornadoLogger();
                    logger.error("unable to create cache dir: %s", outDir.toString());
                    logger.error(e.getMessage());
                }
            }

            guarantee(Files.isDirectory(outDir), "cache directory is not a directory: %s", outDir.toAbsolutePath().toString());

            File file = new File(outDir + "/" + task.getId() + "-" + resolvedMethod.getName());
            try (PrintWriter pw = new PrintWriter(file)) {
                for (ResolvedJavaMethod m : methods) {
                    pw.printf("%s,%s\n", m.getDeclaringClass().getName(), m.getName());
                }
            } catch (IOException e) {
                new TornadoLogger().error("unable to dump source: ", e.getMessage());
            }
        }

        return kernelCompResult;
    }

    // FIXME <REFACTOR> Remove the inheritance (See SPIRV and PTX)
    public static class Request<T extends MetalCompilationResult> {

        public final StructuredGraph graph;
        public final ResolvedJavaMethod installedCodeOwner;
        public final Object[] args;
        public final TaskDataContext meta;
        public final Providers providers;
        public final MetalBackend backend;
        public final PhaseSuite<HighTierContext> graphBuilderSuite;
        public final OptimisticOptimizations optimisticOpts;
        public final ProfilingInfo profilingInfo;
        public final TornadoSuites suites;
        public final TornadoLIRSuites lirSuites;
        public final T compilationResult;
        public final CompilationResultBuilderFactory factory;
        public final boolean isKernel;
        public final boolean buildGraph;
        public final BatchCompilationConfig batchCompilationConfig;
        public TornadoProfiler profiler;

        public Request(StructuredGraph graph, ResolvedJavaMethod installedCodeOwner, Object[] args, TaskDataContext meta, Providers providers, MetalBackend backend,
                PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, TornadoSuites suites, TornadoLIRSuites lirSuites,
                T compilationResult, CompilationResultBuilderFactory factory, boolean isKernel, boolean buildGraph, BatchCompilationConfig batchCompilationConfig, TornadoProfiler profiler) {
            this.graph = graph;
            this.installedCodeOwner = installedCodeOwner;
            this.args = args;
            this.meta = meta;
            this.providers = providers;
            this.backend = backend;
            this.graphBuilderSuite = graphBuilderSuite;
            this.optimisticOpts = optimisticOpts;
            this.profilingInfo = profilingInfo;
            this.suites = suites;
            this.lirSuites = lirSuites;
            this.compilationResult = compilationResult;
            this.factory = factory;
            this.isKernel = isKernel;
            this.buildGraph = buildGraph;
            this.batchCompilationConfig = batchCompilationConfig;
            this.profiler = profiler;
        }

        /**
         * Executes this compilation request.
         *
         * @return the result of the compilation
         */
        public T execute() {
            return MetalCompiler.compile(this);
        }
    }
}
