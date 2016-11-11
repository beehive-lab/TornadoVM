package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.compiler.common.GraalOptions;
import com.oracle.graal.compiler.common.alloc.ComputeBlockOrder;
import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.DebugScope;
import com.oracle.graal.debug.internal.method.MethodMetricsRootScopeInfo;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.lir.constopt.ConstantLoadOptimization;
import com.oracle.graal.lir.framemap.FrameMap;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.lir.phases.AllocationPhase.AllocationContext;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.StructuredGraph.ScheduleResult;
import com.oracle.graal.nodes.cfg.Block;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.LowTierContext;
import com.oracle.graal.phases.util.Providers;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.meta.*;
import tornado.common.Tornado;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.OCLTargetDescription;
import tornado.drivers.opencl.graal.*;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLLIRGenerationPhase.LIRGenerationContext;
import tornado.drivers.opencl.runtime.OCLMemoryRegions;
import tornado.graal.TornadoLIRSuites;
import tornado.graal.TornadoSuites;
import tornado.graal.phases.TornadoHighTierContext;
import tornado.graal.phases.TornadoMidTierContext;
import tornado.meta.Meta;

import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.Optional;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;

/**
 * Static methods for orchestrating the compilation of a
 * {@linkplain StructuredGraph graph}.
 */
public class OCLCompiler {

    private static final AtomicInteger compilationId = new AtomicInteger();

    private static final DebugTimer CompilerTimer = Debug.timer("GraalCompiler");
    private static final DebugTimer FrontEnd = Debug.timer("FrontEnd");
    private static final DebugTimer BackEnd = Debug.timer("BackEnd");
    private static final DebugTimer EmitLIR = Debug.timer("EmitLIR");
    private static final DebugTimer EmitCode = Debug.timer("EmitCode");

    private static final OCLLIRGenerationPhase LIR_GENERATION_PHASE = new OCLLIRGenerationPhase();

    /**
     * Encapsulates all the inputs to a
     * {@linkplain GraalCompiler#compile(Request) compilation}.
     */
    public static class Request<T extends OCLCompilationResult> {

        public final StructuredGraph graph;
        public final ResolvedJavaMethod installedCodeOwner;
        public final Object[] args;
        public final Meta meta;
        public final Providers providers;
        public final OCLBackend backend;
        public final PhaseSuite<HighTierContext> graphBuilderSuite;
        public final OptimisticOptimizations optimisticOpts;
        public final ProfilingInfo profilingInfo;
        public final TornadoSuites suites;
        public final TornadoLIRSuites lirSuites;
        public final T compilationResult;
        public final CompilationResultBuilderFactory factory;
        public final boolean isKernel;

        /**
         * @param graph              the graph to be compiled
         * @param cc                 the calling convention for calls to the
         *                           code compiled for {@code graph}
         * @param installedCodeOwner the method the compiled code will be
         *                           associated with once installed. This
         *                           argument can be null.
         * @param args               the arguments for the method
         * @param meta               Tornado metadata associated with this
         *                           method
         * @param providers
         * @param backend
         * @param target
         * @param graphBuilderSuite
         * @param optimisticOpts
         * @param profilingInfo
         * @param speculationLog
         * @param suites
         * @param lirSuites
         * @param compilationResult
         * @param factory
         */
        public Request(
                StructuredGraph graph,
                ResolvedJavaMethod installedCodeOwner,
                Object[] args,
                Meta meta,
                Providers providers,
                OCLBackend backend,
                PhaseSuite<HighTierContext> graphBuilderSuite,
                OptimisticOptimizations optimisticOpts,
                ProfilingInfo profilingInfo,
                TornadoSuites suites,
                TornadoLIRSuites lirSuites,
                T compilationResult,
                CompilationResultBuilderFactory factory,
                boolean isKernel) {
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
        }

        /**
         * Executes this compilation request.
         *
         * @return the result of the compilation
         */
        public T execute() {

            return OCLCompiler.compile(this);
        }
    }

    /**
     * Requests compilation of a given graph.
     *
     * @param graph              the graph to be compiled
     * @param cc                 the calling convention for calls to the code
     *                           compiled for {@code graph}
     * @param installedCodeOwner the method the compiled code will be associated
     *                           with once installed. This argument can be null.
     *
     * @return the result of the compilation
     */
    public static <T extends OCLCompilationResult> T compileGraph(StructuredGraph graph,
            ResolvedJavaMethod installedCodeOwner, Object[] args, Meta meta,
            Providers providers, OCLBackend backend,
            PhaseSuite<HighTierContext> graphBuilderSuite,
            OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo,
            TornadoSuites suites, TornadoLIRSuites lirSuites, T compilationResult,
            CompilationResultBuilderFactory factory, boolean isKernel) {
        // Ensure a debug configuration for this thread is initialized

        return compile(new Request<>(graph, installedCodeOwner, args, meta, providers, backend,
                graphBuilderSuite, optimisticOpts, profilingInfo, suites,
                lirSuites, compilationResult, factory, isKernel));

    }

    /**
     * Services a given compilation request.
     *
     * @return the result of the compilation
     */
    public static <T extends OCLCompilationResult> T compile(Request<T> r) {
        if (Debug.isEnabled() && DebugScope.getConfig() == null) {
            OCLDebugEnvironment.initialize(TTY.out);
        }
        try (Scope s = MethodMetricsRootScopeInfo.createRootScopeIfAbsent(r.installedCodeOwner)) {
            assert !r.graph.isFrozen();

            try (Scope s0 = Debug.scope("GraalCompiler", r.graph, r.providers.getCodeCache()); DebugCloseable a = CompilerTimer.start()) {
                emitFrontEnd(r.providers, r.backend, r.installedCodeOwner, r.args, r.meta, r.graph, r.graphBuilderSuite, r.optimisticOpts, r.profilingInfo, r.suites, r.isKernel);
                emitBackEnd(r.graph, null, r.installedCodeOwner, r.backend, r.compilationResult, r.factory, null, r.lirSuites, r.isKernel);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
            return r.compilationResult;
        }
    }

    public static ProfilingInfo getProfilingInfo(StructuredGraph graph) {
        if (graph.method() != null) {
            return graph.method().getProfilingInfo();
        } else {
            return DefaultProfilingInfo.get(TriState.UNKNOWN);
        }
    }

    /**
     * Builds the graph, optimizes it.
     */
    public static void emitFrontEnd(Providers providers, OCLBackend backend,
            ResolvedJavaMethod method, Object[] args, Meta meta, StructuredGraph graph,
            PhaseSuite<HighTierContext> graphBuilderSuite,
            OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo,
            TornadoSuites suites, boolean isKernel) {
        try (Scope s = Debug.scope("FrontEnd", new DebugDumpScope("FrontEnd"));
                DebugCloseable a = FrontEnd.start()) {

            GraalOptions.OmitHotExceptionStacktrace.setValue(false);
            GraalOptions.MatchExpressions.setValue(true);
            GraalOptions.RemoveNeverExecutedCode.setValue(false);
//            GraalOptions.SSA_LIR.setValue(true);

            /*
             * Register metadata with all tornado phases
             */
            ((OCLCanonicalizer) suites.getHighTier().getCustomCanonicalizer()).setContext(providers.getMetaAccess(), method,
                    args, meta);

            final TornadoHighTierContext highTierContext = new TornadoHighTierContext(providers,
                    graphBuilderSuite, optimisticOpts, method, args, meta, isKernel);
            if (graph.start().next() == null) {
                graphBuilderSuite.apply(graph, highTierContext);
                new DeadCodeEliminationPhase(Optional).apply(graph);
            } else {
                Debug.dump(Debug.INFO_LOG_LEVEL, graph, "initial state");
            }

            suites.getHighTier().apply(graph, highTierContext);
            graph.maybeCompress();

            final TornadoMidTierContext midTierContext = new TornadoMidTierContext(providers,
                    backend, optimisticOpts, profilingInfo, method, args, meta);
            suites.getMidTier().apply(graph, midTierContext);

            graph.maybeCompress();

            final LowTierContext lowTierContext = new LowTierContext(providers, backend);
            suites.getLowTier().apply(graph, lowTierContext);

            Debug.dump(Debug.BASIC_LOG_LEVEL, graph.getLastSchedule(), "Final HIR schedule");
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    public static <T extends OCLCompilationResult> void emitBackEnd(StructuredGraph graph,
            Object stub, ResolvedJavaMethod installedCodeOwner,
            OCLBackend backend, T compilationResult,
            CompilationResultBuilderFactory factory,
            RegisterConfig registerConfig, TornadoLIRSuites lirSuites, boolean isKernel) {
        try (Scope s = Debug.scope("BackEnd", graph.getLastSchedule()); DebugCloseable a = BackEnd.start()) {
            LIRGenerationResult lirGen = null;
            lirGen = emitLIR(backend, graph, stub, registerConfig, lirSuites, compilationResult, isKernel);
            try (Scope s2 = Debug.scope("CodeGen", lirGen, lirGen.getLIR())) {
                int bytecodeSize = graph.method() == null ? 0 : graph.getBytecodeSize();
                compilationResult.setHasUnsafeAccess(graph.hasUnsafeAccess());
                emitCode(backend, graph.getAssumptions(), graph.method(),
                        graph.getMethods(), bytecodeSize, lirGen, compilationResult, installedCodeOwner,
                        factory, isKernel);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    public static <T extends CompilationResult> LIRGenerationResult emitLIR(OCLBackend backend,
            StructuredGraph graph, Object stub,
            RegisterConfig registerConfig, TornadoLIRSuites lirSuites, T compilationResult, boolean isKernel) {
        try {
            return emitLIR0(backend, graph, stub, registerConfig, lirSuites, compilationResult, isKernel);
        } catch (Throwable e) {
            throw new TornadoInternalError(e);
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

    @SuppressWarnings("try")
    private static <T extends CompilationResult> LIRGenerationResult emitLIR0(OCLBackend backend, StructuredGraph graph, Object stub, RegisterConfig registerConfig, TornadoLIRSuites lirSuites,
            T compilationResult, boolean isKernel) {
        try (Scope ds = Debug.scope("EmitLIR"); DebugCloseable a = EmitLIR.start()) {
            ScheduleResult schedule = graph.getLastSchedule();
            Block[] blocks = schedule.getCFG().getBlocks();
            Block startBlock = schedule.getCFG().getStartBlock();
            assert startBlock != null;
            assert startBlock.getPredecessorCount() == 0;

            LIR lir = null;
            AbstractBlockBase<?>[] codeEmittingOrder = null;
            AbstractBlockBase<?>[] linearScanOrder = null;
            try (Scope s = Debug.scope("ComputeLinearScanOrder", lir)) {
                codeEmittingOrder = ComputeBlockOrder.computeCodeEmittingOrder(blocks.length, startBlock);
                linearScanOrder = ComputeBlockOrder.computeLinearScanOrder(blocks.length, startBlock);

                lir = new LIR(schedule.getCFG(), linearScanOrder, codeEmittingOrder);
                Debug.dump(Debug.INFO_LOG_LEVEL, lir, "After linear scan order");
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
            FrameMapBuilder frameMapBuilder = backend.newFrameMapBuilder(registerConfig);
            String compilationUnitName = getCompilationUnitName(graph, compilationResult);
            LIRGenerationResult lirGenRes = backend.newLIRGenerationResult(compilationUnitName, lir, frameMapBuilder, graph, stub);
            LIRGeneratorTool lirGen = backend.newLIRGenerator(lirGenRes);
            NodeLIRBuilderTool nodeLirGen = backend.newNodeLIRBuilder(graph, lirGen);

            // LIR generation
            LIRGenerationContext context = new LIRGenerationContext(lirGen, nodeLirGen, graph, schedule, isKernel);
            LIR_GENERATION_PHASE.apply(backend.getTarget(), lirGenRes, context);

            try (Scope s = Debug.scope("LIRStages", nodeLirGen, lir)) {
                Debug.dump(Debug.BASIC_LOG_LEVEL, lir, "After LIR generation");
                LIRGenerationResult result = emitLowLevel(backend.getTarget(), lirGenRes, lirGen, lirSuites, backend.newRegisterAllocationConfig(registerConfig));
                Debug.dump(Debug.BASIC_LOG_LEVEL, lir, "Before code generation");
                return result;
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    public static LIRGenerationResult emitLowLevel(
            OCLTargetDescription target, LIRGenerationResult lirGenRes, LIRGeneratorTool lirGen, TornadoLIRSuites lirSuites, RegisterAllocationConfig registerAllocationConfig) {
        ConstantLoadOptimization.Options.LIROptConstantLoadOptimization.setValue(false);

        final PreAllocationOptimizationContext preAllocOptContext = new PreAllocationOptimizationContext(
                lirGen);
        lirSuites.getPreAllocationStage().apply(target, lirGenRes, preAllocOptContext);

        AllocationContext allocContext = new AllocationContext(lirGen.getSpillMoveFactory(), registerAllocationConfig);
        lirSuites.getAllocationStage().apply(target, lirGenRes, allocContext);

//        PostAllocationOptimizationContext postAllocOptContext = new PostAllocationOptimizationContext(
//                lirGen);
//        lirSuites.getPostAllocationStage().apply(target, lirGenRes, postAllocOptContext);
        return lirGenRes;
    }

    public static void emitCode(OCLBackend backend, Assumptions assumptions,
            ResolvedJavaMethod rootMethod, List<ResolvedJavaMethod> inlinedMethods,
            int bytecodeSize,
            LIRGenerationResult lirGenRes, OCLCompilationResult compilationResult,
            ResolvedJavaMethod installedCodeOwner, CompilationResultBuilderFactory factory,
            boolean isKernel) {
        try (DebugCloseable a = EmitCode.start()) {
            FrameMap frameMap = lirGenRes.getFrameMap();
            final OCLCompilationResultBuilder crb = backend.newCompilationResultBuilder(lirGenRes,
                    frameMap, compilationResult, factory, isKernel);
            backend.emitCode(crb, lirGenRes.getLIR(), installedCodeOwner);

            if (assumptions != null && !assumptions.isEmpty()) {
                compilationResult.setAssumptions(assumptions.toArray());
            }
            if (inlinedMethods != null) {
                compilationResult.setMethods(rootMethod, inlinedMethods);
            }

            compilationResult.setNonInlinedMethods(crb.getNonInlinedMethods());
            crb.finish();

            if (Debug.isCountEnabled()) {
                Debug.counter("CompilationResults").increment();
                Debug.counter("CodeBytesEmitted").add(compilationResult.getTargetCodeSize());
            }

            Debug.dump(Debug.BASIC_LOG_LEVEL, compilationResult, "After code generation");
        }

    }

    public static byte[] compileGraphForDevice(StructuredGraph graph, Meta meta, String entryPoint, OCLProviders providers, OCLBackend backend) {
        throw unimplemented();
//        final OCLCodeCache codeCache = backend.getCodeCache();
//
//        if (!meta.hasProvider(OCLMemoryRegions.class)) {
//            meta.addProvider(OCLMemoryRegions.class, new OCLMemoryRegions());
//        }
//
//        graph.maybeCompress();
//        guarantee(graph.verify(), "graph is invalid");
//        OCLCompilationResult compilationResult = new OCLCompilationResult(entryPoint);
//        CompilationResultBuilderFactory factory = CompilationResultBuilderFactory.Default;
//
//        final SchedulePhase schedule = new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS);
//        schedule.apply(graph);
//
//        List<Block> blocks = schedule.getCFG().getBlocks();
//        Block startBlock = schedule.getCFG().getStartBlock();
//        assert startBlock != null;
//        assert startBlock.getPredecessorCount() == 0;
//
//        LIR lir = null;
//        List<Block> codeEmittingOrder = null;
//        List<Block> linearScanOrder = null;
//        try (Scope s = Debug.scope("ComputeLinearScanOrder", lir)) {
//            codeEmittingOrder = ComputeBlockOrder.computeCodeEmittingOrder(blocks.size(),
//                    startBlock);
//            linearScanOrder = ComputeBlockOrder.computeLinearScanOrder(blocks.size(),
//                    startBlock);
//
//            lir = new LIR(schedule.getCFG(), linearScanOrder, codeEmittingOrder);
//            Debug.dump(lir, "after LIR generation", lir);
//        } catch (Throwable e) {
//            throw Debug.handle(e);
//        }
//        Debug.dump(lir, "after LIR generation", lir);
//        FrameMapBuilder frameMapBuilder = backend.newFrameMapBuilder(null);
//        final LIRGenerationResult lirGenRes = backend.newLIRGenerationResult("<unknown>",
//                lir, frameMapBuilder, graph.method(), null);
//        CallingConvention cc = OCLCodeUtil.getCallingConvention(codeCache,
//                CallingConvention.Type.JavaCallee, graph.method(), false);
//        final LIRGeneratorTool lirGen = backend.newLIRGenerator(cc, lirGenRes);
//        final NodeLIRBuilderTool nodeLirGen = backend.newNodeLIRBuilder(graph, lirGen);
//
//        // LIR generation
//        final LIRGenerationContext context = new LIRGenerationContext(lirGen, nodeLirGen, graph,
//                schedule, true);
//        new OCLLIRGenerationPhase().apply(backend.getTarget(), lirGenRes, codeEmittingOrder,
//                linearScanOrder, context);
//
//        emitCode(backend, null,
//                null, Collections.EMPTY_SET,
//                lirGenRes, compilationResult,
//                null, factory,
//                true);
//
//        return compilationResult.getTargetCode();
    }

    public static OCLInstalledCode compileCodeForDevice(ResolvedJavaMethod resolvedMethod,
            Object[] args, Meta meta, OCLProviders providers, OCLBackend backend) {
        Tornado.info("Compiling %s on %s", resolvedMethod.getName(), backend.getDeviceContext()
                .getDevice().getName());
        final StructuredGraph kernelGraph = new StructuredGraph(resolvedMethod,
                AllowAssumptions.YES);
        final OCLCodeCache codeCache = backend.getCodeCache();

        if (meta != null && !meta.hasProvider(OCLMemoryRegions.class)) {
            meta.addProvider(OCLMemoryRegions.class, new OCLMemoryRegions());
        }

        CallingConvention cc = OCLCodeUtil.getCallingConvention(codeCache,
                HotSpotCallingConventionType.JavaCallee, resolvedMethod, false);

        OptimisticOptimizations optimisticOpts = OptimisticOptimizations.ALL;
        ProfilingInfo profilingInfo = resolvedMethod.getProfilingInfo();

        SpeculationLog speculationLog = null;

        OCLCompilationResult kernelCompResult = new OCLCompilationResult(resolvedMethod.getName());
        CompilationResultBuilderFactory factory = CompilationResultBuilderFactory.Default;

        final OCLSuitesProvider suitesProvider = providers.getSuitesProvider();
        Request<OCLCompilationResult> kernelCompilationRequest = new Request<>(
                kernelGraph, resolvedMethod, args, meta, providers, backend,
                suitesProvider.getDefaultGraphBuilderSuite(), optimisticOpts,
                profilingInfo, suitesProvider.createSuites(),
                suitesProvider.createLIRSuites(), kernelCompResult, factory, true);

        kernelCompilationRequest.execute();

        final Set<ResolvedJavaMethod> includedMethods = new HashSet<>();
        final Deque<ResolvedJavaMethod> worklist = new ArrayDeque<>();
        worklist.addAll(kernelCompResult.getNonInlinedMethods());

        while (!worklist.isEmpty()) {
            final ResolvedJavaMethod currentMethod = worklist.pop();
            if (!includedMethods.contains(currentMethod)) {
                final OCLCompilationResult compResult = new OCLCompilationResult(currentMethod.getName());
                final StructuredGraph graph = new StructuredGraph(currentMethod,
                        AllowAssumptions.YES);
                Request<OCLCompilationResult> methodcompilationRequest = new Request<>(
                        graph, currentMethod, null, null, providers, backend,
                        suitesProvider.getDefaultGraphBuilderSuite(),
                        optimisticOpts, profilingInfo,
                        suitesProvider.createSuites(), suitesProvider.createLIRSuites(),
                        compResult, factory, false);

                methodcompilationRequest.execute();
                worklist.addAll(compResult.getNonInlinedMethods());

                kernelCompResult.addCompiledMethodCode(compResult.getTargetCode());
            }
        }

        return codeCache.addMethod(resolvedMethod, kernelCompResult.getTargetCode());
    }
}
