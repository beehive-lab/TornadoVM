/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import static jdk.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DUMP_COMPILED_METHODS;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.alloc.LinearScanOrder;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.lir.framemap.FrameMapBuilder;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.phases.AllocationPhase;
import jdk.graal.compiler.lir.phases.PreAllocationOptimizationPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.util.Providers;

import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVModule;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVProviders;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVSuitesProvider;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.runtime.common.BatchCompilationConfig;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.TornadoLIRSuites;
import uk.ac.manchester.tornado.runtime.graal.TornadoSuites;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoLowTierContext;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoMidTierContext;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

/**
 * SPIR-V Compiler and Code Optimizer. It optimizes the Graal IR for SPIR-V devices, and it
 * generates SPIR-V binary code.
 */
public class SPIRVCompiler {

    private static final TimerKey CompilerTimer = DebugContext.timer("SPIRVGraalCompiler");
    private static final TimerKey FrontEnd = DebugContext.timer("SPIRVFrontend");
    private static final TimerKey BackEnd = DebugContext.timer("SPIRVBackend");
    private static final TimerKey EmitLIR = DebugContext.timer("SPIRVEmitLIR");
    private static final TimerKey EmitCode = DebugContext.timer("SPIRVEmitCode");

    private static final SPIRVIRGenerationPhase LIR_GENERATION_PHASE = new SPIRVIRGenerationPhase();

    private synchronized static SPIRVCompilationResult compile(SPIRVCompilationRequest r) {
        assert !r.graph.isFrozen();
        try (DebugContext.Scope s0 = getDebugContext().scope("GraalCompiler", r.graph, r.providers.getCodeCache()); DebugCloseable a = CompilerTimer.start(getDebugContext())) {
            emitFrontEnd(r.providers, r.backend, r.installedCodeOwner, r.args, r.meta, r.graph, r.graphBuilderSuite, r.optimisticOpts, r.profilingInfo, r.suites, r.isKernel, r.buildGraph,
                    r.batchCompilationConfig);
            /*
             * A task is determined as parallel if: (i) it has loops annotated with {@link
             * uk.ac.manchester.tornado.api.annotations.Parallel} which corresponds to use a
             * domain with depth greater than zero, or (ii) it uses the GridScheduler.
             */
            boolean isParallel = r.meta != null && (r.meta.isParallel() || (r.meta.isGridSchedulerEnabled() && !r.meta.isGridSequential()));
            emitBackEnd(r.graph, null, r.installedCodeOwner, r.backend, r.compilationResult, null, r.lirSuites, r.isKernel, isParallel, r.profiler);
        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
        return r.compilationResult;
    }

    private static boolean isGraphEmpty(StructuredGraph graph) {
        return graph.start().next() == null;
    }

    private static void emitFrontEnd(Providers providers, SPIRVBackend backend, ResolvedJavaMethod installedCodeOwner, Object[] args, TaskDataContext meta, StructuredGraph graph,
            PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, TornadoSuites suites, boolean isKernel, boolean buildGraph,
            BatchCompilationConfig batchCompilationConfig) {

        try (DebugContext.Scope s = getDebugContext().scope("SPIRVFrontend", new DebugDumpScope("SPIRVFrontend")); DebugCloseable a = FrontEnd.start(getDebugContext())) {

            /*
             * Register metadata with all tornado phases
             */
            ((SPIRVCanonicalizer) suites.getHighTier().getCustomCanonicalizer()).setContext(providers.getMetaAccess(), installedCodeOwner, args, meta);

            final TornadoHighTierContext highTierContext = new TornadoHighTierContext(providers, graphBuilderSuite, optimisticOpts, installedCodeOwner, args, meta, isKernel, batchCompilationConfig);
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

            final TornadoMidTierContext midTierContext = new TornadoMidTierContext(providers, backend, optimisticOpts, profilingInfo, installedCodeOwner, args, meta);
            suites.getMidTier().apply(graph, midTierContext);

            graph.maybeCompress();

            final TornadoLowTierContext lowTierContext = new TornadoLowTierContext(providers, backend, meta);
            suites.getLowTier().apply(graph, lowTierContext);

            getDebugContext().dump(DebugContext.BASIC_LEVEL, graph.getLastSchedule(), "Final LIR schedule");

        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
    }

    private static void emitBackEnd(StructuredGraph graph, Object stub, ResolvedJavaMethod installedCodeOwner, SPIRVBackend backend, SPIRVCompilationResult compilationResult,
            RegisterConfig registerConfig, TornadoLIRSuites lirSuites, boolean isKernel, boolean isParallel, TornadoProfiler profiler) {
        try (DebugContext.Scope s = getDebugContext().scope("SPIRVBackend", graph.getLastSchedule()); DebugCloseable a = BackEnd.start(getDebugContext())) {
            LIRGenerationResult lirGen = null;
            lirGen = emitLIR(backend, graph, stub, registerConfig, lirSuites, compilationResult, isKernel);
            try (DebugContext.Scope s2 = getDebugContext().scope("SPIRVCodeGen", lirGen, lirGen.getLIR())) {
                int bytecodeSize = graph.method() == null ? 0 : graph.getBytecodeSize();
                compilationResult.setHasUnsafeAccess(graph.hasUnsafeAccess());
                emitCode(backend, graph.getAssumptions(), graph.method(), graph.getMethods(), bytecodeSize, lirGen, compilationResult, installedCodeOwner, isKernel, isParallel, profiler);
            } catch (Throwable e) {
                throw getDebugContext().handle(e);
            }
        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
    }

    private static LIRGenerationResult emitLIR(SPIRVBackend backend, StructuredGraph graph, Object stub, RegisterConfig registerConfig, TornadoLIRSuites lirSuites,
            SPIRVCompilationResult compilationResult, boolean isKernel) {
        try {
            return emitLIR0(backend, graph, stub, registerConfig, lirSuites, compilationResult, isKernel);
        } catch (Throwable e) {
            throw new TornadoInternalError(e);
        }
    }

    private static <T extends CompilationResult> LIRGenerationResult emitLIR0(SPIRVBackend backend, StructuredGraph graph, Object stub, RegisterConfig registerConfig, TornadoLIRSuites lirSuites,
            T compilationResult, boolean isKernel) {
        try (DebugContext.Scope ds = getDebugContext().scope("EmitLIR"); DebugCloseable a = EmitLIR.start(getDebugContext())) {
            OptionValues options = graph.getOptions();
            StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
            BasicBlock[] blocks = schedule.getCFG().getBlocks();
            BasicBlock startBlock = schedule.getCFG().getStartBlock();
            assert startBlock != null;
            assert startBlock.getPredecessorCount() == 0;

            LIR lir = null;
            try (DebugContext.Scope s = getDebugContext().scope("ComputeLinearScanOrder", lir)) {
                int[] linearScanOrder = LinearScanOrder.computeLinearScanOrder(blocks.length, startBlock);
                lir = new LIR(schedule.getCFG(), linearScanOrder, graph.getOptions(), graph.getDebug());
                getDebugContext().dump(DebugContext.INFO_LEVEL, lir, "After linear scan order");
            } catch (Throwable e) {
                throw getDebugContext().handle(e);
            }
            RegisterAllocationConfig registerAllocationConfig = backend.newRegisterAllocationConfig(registerConfig, new String[] {}, new Object() {});
            FrameMapBuilder frameMapBuilder = backend.newFrameMapBuilder(registerConfig);
            SPIRVLIRGenerationResult lirGenRes = (SPIRVLIRGenerationResult) backend.newLIRGenerationResult(graph.compilationId(), lir, frameMapBuilder, registerAllocationConfig);
            lirGenRes.setMethodIndex(backend.getMethodIndex());
            LIRGeneratorTool lirGen = backend.newLIRGenerator(lirGenRes);
            NodeLIRBuilderTool nodeLirGen = backend.newNodeLIRBuilder(graph, lirGen);

            // LIR generation
            SPIRVIRGenerationPhase.LIRGenerationContext context = new SPIRVIRGenerationPhase.LIRGenerationContext(lirGen, nodeLirGen, graph, schedule, isKernel);
            LIR_GENERATION_PHASE.apply(backend.getTarget(), lirGenRes, context);

            try (DebugContext.Scope s = getDebugContext().scope("LIRStages", nodeLirGen, lir)) {
                getDebugContext().dump(DebugContext.BASIC_LEVEL, lir, "After LIR generation");
                LIRGenerationResult result = emitLowLevel(backend.getTarget(), lirGenRes, lirGen, lirSuites, registerAllocationConfig);
                getDebugContext().dump(DebugContext.BASIC_LEVEL, lir, "Before code generation");
                return result;
            } catch (Throwable e) {
                throw getDebugContext().handle(e);
            }
        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
    }

    private static LIRGenerationResult emitLowLevel(TargetDescription target, LIRGenerationResult lirGenRes, LIRGeneratorTool lirGen, TornadoLIRSuites lirSuites,
            RegisterAllocationConfig registerAllocationConfig) {
        final PreAllocationOptimizationPhase.PreAllocationOptimizationContext preAllocOptContext = new PreAllocationOptimizationPhase.PreAllocationOptimizationContext(lirGen);
        lirSuites.getPreAllocationStage().apply(target, lirGenRes, preAllocOptContext);
        AllocationPhase.AllocationContext allocContext = new AllocationPhase.AllocationContext(lirGen.getSpillMoveFactory(), registerAllocationConfig);
        lirSuites.getAllocationStage().apply(target, lirGenRes, allocContext);
        return lirGenRes;
    }

    private static void emitCode(SPIRVBackend backend, Assumptions assumptions, ResolvedJavaMethod rootMethod, List<ResolvedJavaMethod> methods, int bytecodeSize, LIRGenerationResult lirGen,
            SPIRVCompilationResult compilationResult, ResolvedJavaMethod installedCodeOwner, boolean isKernel, boolean isParallel, TornadoProfiler profiler) {
        try (DebugCloseable a = EmitCode.start(getDebugContext())) {
            FrameMap frameMap = lirGen.getFrameMap();
            final SPIRVCompilationResultBuilder crb = backend.newCompilationResultBuilder(frameMap, compilationResult, isKernel, isParallel, lirGen.getLIR());
            backend.emitCode(crb, lirGen.getLIR(), installedCodeOwner, profiler);

            if (assumptions != null && !assumptions.isEmpty()) {
                compilationResult.setAssumptions(assumptions.toArray());
            }
            if (methods != null) {
                compilationResult.setMethods(rootMethod, methods);
            }

            compilationResult.setNonInlinedMethods(crb.getNonInlinedMethods());

            // We need to reuse the assembler instance for all methods to be compiled in the
            // same SPIR-V compilation unit because we need to obtain symbols from the main
            // module.
            compilationResult.setAssembler((SPIRVAssembler) crb.asm);

            if (getDebugContext().isCountEnabled()) {
                DebugContext.counter("CompilationResults").increment(getDebugContext());
                DebugContext.counter("CodeBytesEmitted").add(getDebugContext(), compilationResult.getTargetCodeSize());
            }

            getDebugContext().dump(DebugContext.BASIC_LEVEL, compilationResult, "After code generation");
        } catch (TornadoRuntimeException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    private static String buildFullKernelName(String methodName, SchedulableTask task) {
        StringBuilder sb = new StringBuilder(methodName);
        for (Object arg : task.getArguments()) {
            // Object is either array or primitive
            sb.append('_');
            Class<?> argClass = arg.getClass();
            if (RuntimeUtilities.isBoxedPrimitiveClass(argClass)) {
                // Only need to append value.
                // If negative value, remove the minus sign in front
                sb.append(arg.toString().replace('.', '_').replaceAll("-", ""));
            } else if (argClass.isArray() && RuntimeUtilities.isPrimitiveArray(argClass)) {
                // Need to append type and length
                sb.append(argClass.getComponentType().getName());
                sb.append(Array.getLength(arg));
            } else {
                sb.append(argClass.getName().replace('.', '_'));
                // Since with objects there is no way to know what will be a
                // constant differentiate using the hashcode of the object
                sb.append('_');
                sb.append(arg.hashCode());
            }
        }
        return sb.toString();
    }

    public static String buildKernelName(String methodName) {
        StringBuilder sb = new StringBuilder(methodName);
        return sb.toString();
    }

    public static SPIRVCompilationResult compileSketchForDevice(Sketch sketch, CompilableTask task, SPIRVProviders providers, SPIRVBackend backend, TornadoProfiler profiler) {
        final StructuredGraph kernelGraph = (StructuredGraph) sketch.getGraph().copy(getDebugContext());
        ResolvedJavaMethod resolvedJavaMethod = kernelGraph.method();

        new TornadoLogger().info("Compiling sketch %s on %s", resolvedJavaMethod.getName(), backend.getDeviceContext().getDevice().getDeviceName());

        final TaskDataContext taskMeta = task.meta();
        final Object[] args = task.getArguments();
        final long batchThreads = (taskMeta.getNumThreads() > 0) ? taskMeta.getNumThreads() : task.getBatchThreads();
        final int batchNumber = task.getBatchNumber();
        final long batchSize = task.getBatchSize();
        BatchCompilationConfig batchCompilationConfig = new BatchCompilationConfig(batchThreads, batchNumber, batchSize);

        OptimisticOptimizations optimisticOptimizations = OptimisticOptimizations.ALL;
        ProfilingInfo profilingInfo = resolvedJavaMethod.getProfilingInfo();

        SPIRVCompilationResult kernelCompilationResult = new SPIRVCompilationResult(task.getId(), buildKernelName(resolvedJavaMethod.getName()), taskMeta);
        CompilationResultBuilderFactory factory = CompilationResultBuilderFactory.Default;

        Set<ResolvedJavaMethod> methods = new HashSet<>();

        final SPIRVSuitesProvider suitesProvider = providers.getSuitesProvider();

        // @formatter:off
        SPIRVCompilationRequest kernelCompilationRequest = new SPIRVCompilationRequest(
                kernelGraph,
                resolvedJavaMethod,
                args,
                taskMeta,
                providers,
                backend,
                suitesProvider.getGraphBuilderSuite(),
                optimisticOptimizations,
                profilingInfo,
                suitesProvider.getSuites(),
                suitesProvider.getLIRSuites(),
                kernelCompilationResult,
                factory,
                true,
                false,
                batchCompilationConfig,
                profiler);
        // @formatter:on

        kernelCompilationRequest.execute();

        if (TornadoOptions.DUMP_COMPILED_METHODS) {
            methods.add(kernelGraph.method());
            methods.addAll(kernelGraph.getMethods());
            Collections.addAll(methods, kernelCompilationResult.getMethods());
        }

        final Deque<ResolvedJavaMethod> workList = new ArrayDeque<>(kernelCompilationResult.getNonInlinedMethods());
        while (!workList.isEmpty()) {
            final ResolvedJavaMethod currentMethod = workList.pop();
            Sketch currentSketch = TornadoSketcher.lookup(currentMethod, task.meta().getBackendIndex(), taskMeta.getDeviceIndex());
            final StructuredGraph graph = (StructuredGraph) currentSketch.getGraph().copy(getDebugContext());

            final SPIRVCompilationResult compilationResult = new SPIRVCompilationResult(task.getId(), currentMethod.getName(), taskMeta);

            // Share assembler across compilation results
            compilationResult.setAssembler(kernelCompilationRequest.compilationResult.getAssembler());

            // @formatter:off
            SPIRVCompilationRequest methodCompilationRequest = new SPIRVCompilationRequest(
                    graph,
                    currentMethod,
                    null,
                    null,
                    providers,
                    backend,
                    suitesProvider.getGraphBuilderSuite(),
                    optimisticOptimizations,
                    profilingInfo,
                    suitesProvider.getSuites(),
                    suitesProvider.getLIRSuites(),
                    compilationResult,
                    factory,
                    false,
                    false,
                    new BatchCompilationConfig(0, 0, 0),
                    profiler
                    );
            // @formatter:on

            methodCompilationRequest.execute();
            if (DUMP_COMPILED_METHODS) {
                methods.add(graph.method());
                methods.addAll(graph.getMethods());
            }

            // Update the assembler
            kernelCompilationRequest.compilationResult.setAssembler(compilationResult.getAssembler());

            // Update the list of pending methods to compile
            workList.addAll(compilationResult.getNonInlinedMethods());

            // kernelCompilationResult.addCompiledMethodCode(compilationResult.getTargetCode());
        }

        // ==================================================================
        // End of the compilation unit: close the byte buffer
        // ==================================================================
        SPIRVAssembler asm = kernelCompilationResult.getAssembler();
        SPIRVModule module = asm.module;
        ByteBuffer out = ByteBuffer.allocate(module.getByteCount());
        out.order(ByteOrder.LITTLE_ENDIAN);

        // SPIRVAssembler asm = kernelCompilationResult.getAssembler();
        // asm.module.close().write(asm.getSPIRVByteBuffer());
        asm.module.close().write(out);
        out.flip();
        asm.setSPIRVByteBuffer(out);
        // asm.getSPIRVByteBuffer().flip();

        kernelCompilationResult.setSPIRVBinary(out);
        kernelCompilationResult.setAssembler(asm);

        if (DUMP_COMPILED_METHODS) {
            final Path outDir = Paths.get("./spirv-compiled-methods");
            if (!Files.exists(outDir)) {
                try {
                    Files.createDirectories(outDir);
                } catch (IOException e) {
                    TornadoLogger tornadoLogger = new TornadoLogger();
                    tornadoLogger.error("unable to create cache dir: %s", outDir.toString());
                    tornadoLogger.error(e.getMessage());
                }
            }

            guarantee(Files.isDirectory(outDir), "cache directory is not a directory: %s", outDir.toAbsolutePath().toString());

            File file = new File(outDir + "/" + task.getId() + "-" + resolvedJavaMethod.getName());
            try (PrintWriter pw = new PrintWriter(file)) {
                for (ResolvedJavaMethod m : methods) {
                    pw.printf("%s,%s\n", m.getDeclaringClass().getName(), m.getName());
                }
            } catch (IOException e) {
                new TornadoLogger().error("unable to dump source: ", e.getMessage());
            }
        }

        return kernelCompilationResult;
    }

    public static class SPIRVCompilationRequest {
        public final StructuredGraph graph;
        public final ResolvedJavaMethod installedCodeOwner;
        public final Object[] args;
        public final TaskDataContext meta;
        public final Providers providers;
        public final SPIRVBackend backend;
        public final PhaseSuite<HighTierContext> graphBuilderSuite;
        public final OptimisticOptimizations optimisticOpts;
        public final ProfilingInfo profilingInfo;
        public final TornadoSuites suites;
        public final TornadoLIRSuites lirSuites;
        public final SPIRVCompilationResult compilationResult;
        public final CompilationResultBuilderFactory factory;
        public final boolean isKernel;
        public final boolean buildGraph;
        public final BatchCompilationConfig batchCompilationConfig;
        public TornadoProfiler profiler;

        public SPIRVCompilationRequest(StructuredGraph graph, ResolvedJavaMethod installedCodeOwner, Object[] args, TaskDataContext meta, Providers providers, SPIRVBackend backend,
                PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, TornadoSuites suites, TornadoLIRSuites lirSuites,
                SPIRVCompilationResult compilationResult, CompilationResultBuilderFactory factory, boolean isKernel, boolean buildGraph, BatchCompilationConfig batchCompilationConfig,
                TornadoProfiler profiler) {
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

        public SPIRVCompilationResult execute() {
            return SPIRVCompiler.compile(this);
        }
    }
}
