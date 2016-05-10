package tornado.drivers.opencl.graal.compiler;

import static com.oracle.graal.compiler.MethodFilter.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import tornado.common.Tornado;
import tornado.drivers.opencl.graal.OCLProviders;
import tornado.drivers.opencl.graal.OpenCLCodeCache;
import tornado.drivers.opencl.graal.OpenCLCodeUtil;
import tornado.drivers.opencl.graal.OpenCLInstalledCode;
import tornado.drivers.opencl.graal.OCLSuitesProvider;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLLIRGenerationPhase.LIRGenerationContext;
import tornado.graal.TornadoLIRGenerator;
import tornado.graal.TornadoLIRSuites;
import tornado.graal.TornadoSuites;
import tornado.graal.phases.TornadoHighTierContext;
import tornado.graal.phases.TornadoMidTierContext;
import tornado.meta.Meta;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CompilationResult.ConstantReference;
import com.oracle.graal.api.code.CompilationResult.DataPatch;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.MethodFilter;
import com.oracle.graal.compiler.common.GraalOptions;
import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.constopt.ConstantLoadOptimization;
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.phases.AllocationPhase.AllocationContext;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.schedule.SchedulePhase.SchedulingStrategy;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;

/**
 * Static methods for orchestrating the compilation of a {@linkplain StructuredGraph graph}.
 */
public class OCLCompiler {

	private static AtomicInteger		compilationId	= new AtomicInteger();

	private static final DebugTimer		FrontEnd		= Debug.timer("FrontEnd");
	private static final DebugTimer		BackEnd			= Debug.timer("BackEnd");
	private static final DebugTimer		EmitLIR			= Debug.timer("EmitLIR");
	private static final DebugTimer		EmitCode		= Debug.timer("EmitCode");

	/**
	 * The set of positive filters specified by the {@code -G:IntrinsificationsEnabled} option. To
	 * enable a fast path in {@link #shouldIntrinsify(JavaMethod)}, this field is {@code null} when
	 * no enabling/disabling filters are specified.
	 */
	private static final MethodFilter[]	positiveIntrinsificationFilter;

	/**
	 * The set of negative filters specified by the {@code -G:IntrinsificationsDisabled} option.
	 */
	private static final MethodFilter[]	negativeIntrinsificationFilter;

	static class Options {

		// @formatter:off
		/**
		 * @see MethodFilter
		 */
		@Option(
				help = "Pattern for method(s) to which intrinsification (if available) will be applied. "
						+ "By default, all available intrinsifications are applied except for methods matched "
						+ "by IntrinsificationsDisabled. See MethodFilter class for pattern syntax.",
				type = OptionType.Debug)
		public static final OptionValue<String>		IntrinsificationsEnabled	= new OptionValue<>(
																						null);
		/**
		 * @see MethodFilter
		 */
		@Option(help = "Pattern for method(s) to which intrinsification will not be applied. "
				+ "See MethodFilter class for pattern syntax.", type = OptionType.Debug)
		public static final OptionValue<String>		IntrinsificationsDisabled	= new OptionValue<>(
																						null);

		@Option(
				help = "Repeatedly run the LIR code generation pass to improve statistical profiling results.",
				type = OptionType.Debug)
		public static final OptionValue<Integer>	EmitLIRRepeatCount			= new OptionValue<>(
																						0);
		// @formatter:on

	}

	static {
		// if (IntrinsificationsDisabled.getValue() != null) {
		// negativeIntrinsificationFilter = parse(IntrinsificationsDisabled.getValue());
		// } else {
		negativeIntrinsificationFilter = null;
		// }

		// if (Options.IntrinsificationsEnabled.getValue() != null) {
		// positiveIntrinsificationFilter = parse(IntrinsificationsEnabled.getValue());
		// } else if (negativeIntrinsificationFilter != null) {
		// positiveIntrinsificationFilter = new MethodFilter[0];
		// } else {
		positiveIntrinsificationFilter = null;
		// }
	}

	/**
	 * Determines if a given method should be intrinsified based on the values of
	 * {@link Options#IntrinsificationsEnabled} and {@link Options#IntrinsificationsDisabled}.
	 */
	public static boolean shouldIntrinsify(JavaMethod method) {
		if (positiveIntrinsificationFilter == null) {
			return true;
		}
		if (positiveIntrinsificationFilter.length == 0
				|| matches(positiveIntrinsificationFilter, method)) {
			return negativeIntrinsificationFilter == null
					|| !matches(negativeIntrinsificationFilter, method);
		}
		return false;
	}

	/**
	 * Encapsulates all the inputs to a {@linkplain GraalCompiler#compile(Request) compilation}.
	 */
	public static class Request<T extends OCLCompilationResult> {
		public final StructuredGraph					graph;
		public final CallingConvention					cc;
		public final ResolvedJavaMethod					installedCodeOwner;
		public final Object[]							args;
		public final Meta								meta;
		public final Providers							providers;
		public final OCLBackend				backend;
		public final TargetDescription					target;
		public final PhaseSuite<HighTierContext>		graphBuilderSuite;
		public final OptimisticOptimizations			optimisticOpts;
		public final ProfilingInfo						profilingInfo;
		public final SpeculationLog						speculationLog;
		public final TornadoSuites								suites;
		public final TornadoLIRSuites	lirSuites;
		public final T									compilationResult;
		public final CompilationResultBuilderFactory	factory;
		public final boolean							isKernel;

		/**
		 * @param graph
		 *            the graph to be compiled
		 * @param cc
		 *            the calling convention for calls to the code compiled for {@code graph}
		 * @param installedCodeOwner
		 *            the method the compiled code will be associated with once
		 *            installed. This argument can be null.
		 * @param args
		 *            the arguments for the method
		 * @param meta
		 *            Tornado metadata associated with this method
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
				CallingConvention cc,
				ResolvedJavaMethod installedCodeOwner,
				Object[] args,
				Meta meta,
				Providers providers,
				OCLBackend backend,
				TargetDescription target,
				PhaseSuite<HighTierContext> graphBuilderSuite,
				OptimisticOptimizations optimisticOpts,
				ProfilingInfo profilingInfo,
				SpeculationLog speculationLog,
				TornadoSuites suites,
				TornadoLIRSuites lirSuites,
				T compilationResult,
				CompilationResultBuilderFactory factory,
				boolean isKernel) {
			this.graph = graph;
			this.cc = cc;
			this.installedCodeOwner = installedCodeOwner;
			this.args = args;
			this.meta = meta;
			this.providers = providers;
			this.backend = backend;
			this.target = target;
			this.graphBuilderSuite = graphBuilderSuite;
			this.optimisticOpts = optimisticOpts;
			this.profilingInfo = profilingInfo;
			this.speculationLog = speculationLog;
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
	 * @param graph
	 *            the graph to be compiled
	 * @param cc
	 *            the calling convention for calls to the code compiled for {@code graph}
	 * @param installedCodeOwner
	 *            the method the compiled code will be associated with once
	 *            installed. This argument can be null.
	 * @return the result of the compilation
	 */
	public static <T extends OCLCompilationResult> T compileGraph(StructuredGraph graph,
			CallingConvention cc, ResolvedJavaMethod installedCodeOwner, Object[] args, Meta meta,
			Providers providers, OCLBackend backend, TargetDescription target,
			PhaseSuite<HighTierContext> graphBuilderSuite,
			OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo,
			SpeculationLog speculationLog, TornadoSuites suites, TornadoLIRSuites lirSuites, T compilationResult,
			CompilationResultBuilderFactory factory, boolean isKernel) {
		return compile(new Request<>(graph, cc, installedCodeOwner, args, meta, providers, backend,
				target, graphBuilderSuite, optimisticOpts, profilingInfo, speculationLog, suites,
				lirSuites, compilationResult, factory, isKernel));
	}

	/**
	 * Services a given compilation request.
	 *
	 * @return the result of the compilation
	 */
	public static <T extends OCLCompilationResult> T compile(Request<T> r) {
		assert !r.graph.isFrozen();
		try (Scope s0 = Debug.scope("TornadoCompiler", new DebugDumpScope(r.graph.method()
				.getName() + ":" + String.valueOf(compilationId.incrementAndGet())))) {
			SchedulePhase schedule = emitFrontEnd(r.providers, r.target, r.installedCodeOwner,
					r.args, r.meta, r.graph, r.graphBuilderSuite, r.optimisticOpts,
					r.profilingInfo, r.speculationLog, r.suites, r.isKernel);

			emitBackEnd(r.graph, null, r.cc, r.installedCodeOwner, r.backend, r.target,
					r.compilationResult, r.factory, schedule, null, r.lirSuites, r.isKernel);
		} catch (Throwable e) {
			throw Debug.handle(e);
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

	/**
	 * Builds the graph, optimizes it.
	 */
	public static SchedulePhase emitFrontEnd(Providers providers, TargetDescription target,
			ResolvedJavaMethod method, Object[] args, Meta meta, StructuredGraph graph,
			PhaseSuite<HighTierContext> graphBuilderSuite,
			OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo,
			SpeculationLog speculationLog, TornadoSuites suites, boolean isKernel) {
		try (Scope s = Debug.scope("FrontEnd", new DebugDumpScope("FrontEnd"));
				DebugCloseable a = FrontEnd.start()) {
			if (speculationLog != null) {
				speculationLog.collectFailedSpeculations();
			}

			GraalOptions.OmitHotExceptionStacktrace.setValue(false);
			GraalOptions.MatchExpressions.setValue(true);
			GraalOptions.SSA_LIR.setValue(true);

			/*
			 * Register metadata with all tornado phases
			 */
			((TornadoCanonicalizer)suites.getHighTier().getCustomCanonicalizer()).setContext(providers.getMetaAccess(),method,
					args, meta);

			final TornadoHighTierContext highTierContext = new TornadoHighTierContext(providers,
					graphBuilderSuite, optimisticOpts, method, args, meta, isKernel);
			if (graph.start().next() == null) {

				graphBuilderSuite.apply(graph, highTierContext);

				new DeadCodeEliminationPhase().apply(graph);
			} else {
				Debug.dump(graph, "initial state");
			}

			suites.getHighTier().apply(graph,
					highTierContext);

			graph.maybeCompress();

			final TornadoMidTierContext midTierContext = new TornadoMidTierContext(providers,
					target, optimisticOpts, profilingInfo, speculationLog, method, args, meta);
			suites.getMidTier().apply(graph, midTierContext);

			graph.maybeCompress();

			final LowTierContext lowTierContext = new LowTierContext(providers, target);
			suites.getLowTier().apply(graph, lowTierContext);
			graph.maybeCompress();

			// System.out.printf("Scheduling strategy = %s\n",OptScheduleOutOfLoops.getValue());
			final SchedulePhase schedule = new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS);
			schedule.apply(graph);
			
			Debug.dump(schedule, "Final HIR schedule");
			return schedule;
		} catch (Throwable e) {
			throw Debug.handle(e);
		}
	}

	public static <T extends OCLCompilationResult> void emitBackEnd(StructuredGraph graph,
			Object stub, CallingConvention cc, ResolvedJavaMethod installedCodeOwner,
			OCLBackend backend, TargetDescription target, T compilationResult,
			CompilationResultBuilderFactory factory, SchedulePhase schedule,
			RegisterConfig registerConfig, TornadoLIRSuites lirSuites, boolean isKernel) {
		try (Scope s = Debug.scope("BackEnd", new DebugDumpScope("BackEnd"));
				DebugCloseable a = BackEnd.start()) {
			
			LIRGenerationResult lirGen = null;
			lirGen = emitLIR(backend, target, schedule, graph, stub, cc, registerConfig, lirSuites, isKernel);
			try (Scope s2 = Debug.scope("CodeGen", lirGen, lirGen.getLIR())) {
				emitCode(backend, graph.getAssumptions(), graph.method(),
						graph.getInlinedMethods(), lirGen, compilationResult, installedCodeOwner,
						factory, isKernel);
			} catch (Throwable e) {
				throw Debug.handle(e);
			}
		} catch (Throwable e) {
			throw Debug.handle(e);
		}
	}

	public static LIRGenerationResult emitLIR(Backend backend, TargetDescription target,
			SchedulePhase schedule, StructuredGraph graph, Object stub, CallingConvention cc,
			RegisterConfig registerConfig, TornadoLIRSuites lirSuites, boolean isKernel) {
		try (Scope ds = Debug.scope("EmitLIR", new DebugDumpScope("EmitLIR"));
				DebugCloseable a = EmitLIR.start()) {
			TornadoLIRGenerator.trace("starting LIR generation...");
			
			List<Block> blocks = schedule.getCFG().getBlocks();
			Block startBlock = schedule.getCFG().getStartBlock();
			assert startBlock != null;
			assert startBlock.getPredecessorCount() == 0;

			LIR lir = null;
			List<Block> codeEmittingOrder = null;
			List<Block> linearScanOrder = null;
//			 List<Block> openclOrder =null;
			try (Scope s = Debug.scope("ComputeLinearScanOrder", lir)) {
				codeEmittingOrder = ComputeBlockOrder.computeCodeEmittingOrder(blocks.size(),
						startBlock);
				linearScanOrder = ComputeBlockOrder.computeLinearScanOrder(blocks.size(),
						startBlock);
//				 openclOrder = OCLSourceEmitter.computeCodeEmittingOrder(blocks.size(),
//				 startBlock);
//
//				 System.out.printf("%s - OpenCL block order:\n",graph.method().getName());
//				 for(Block b: openclOrder){
//				 System.out.printf("\tblock %s\n",b.toString());
//				 }

				// System.exit(-1);
				lir = new LIR(schedule.getCFG(), linearScanOrder, codeEmittingOrder);
				Debug.dump(lir, "After linear scan order");
			} catch (Throwable e) {
				throw Debug.handle(e);
			}
			FrameMapBuilder frameMapBuilder = backend.newFrameMapBuilder(registerConfig);
			String compilationUnitName;
			ResolvedJavaMethod method = graph.method();
			if (method == null) {
				compilationUnitName = "<unknown>";
			} else {
				compilationUnitName = method.format("%H.%n(%p)");
			}

			final LIRGenerationResult lirGenRes = backend.newLIRGenerationResult(compilationUnitName,
					lir, frameMapBuilder, graph.method(), stub);
			final LIRGeneratorTool lirGen = backend.newLIRGenerator(cc, lirGenRes);
			final NodeLIRBuilderTool nodeLirGen = backend.newNodeLIRBuilder(graph, lirGen);

			// LIR generation

			final LIRGenerationContext context = new LIRGenerationContext(lirGen, nodeLirGen, graph,
					schedule, isKernel);
			new OCLLIRGenerationPhase().apply(target, lirGenRes, codeEmittingOrder,
					linearScanOrder, context);

			Debug.dump(lir, "after LIR generation", lir);

			TornadoLIRGenerator.trace("completed LIR generation...");

			try (Scope s = Debug.scope("LIRStages", nodeLirGen, lir)) {
				return emitLowLevel(target, codeEmittingOrder, linearScanOrder, lirGenRes, lirGen,
						lirSuites);
			} catch (Throwable e) {
				throw Debug.handle(e);
			}
		} catch (Throwable e) {
			throw Debug.handle(e);
		}
	}

	public static <T extends AbstractBlockBase<T>> LIRGenerationResult emitLowLevel(
			TargetDescription target, List<T> codeEmittingOrder, List<T> linearScanOrder,
			LIRGenerationResult lirGenRes, LIRGeneratorTool lirGen, TornadoLIRSuites lirSuites) {
		ConstantLoadOptimization.Options.LIROptConstantLoadOptimization.setValue(false);

		final PreAllocationOptimizationContext preAllocOptContext = new PreAllocationOptimizationContext(
				lirGen);
		lirSuites.getPreAllocationStage().apply(target, lirGenRes, codeEmittingOrder,
				linearScanOrder, preAllocOptContext);

		AllocationContext allocContext = new AllocationContext(lirGen.getSpillMoveFactory());
		lirSuites.getAllocationStage().apply(target,
				lirGenRes, codeEmittingOrder, linearScanOrder, allocContext);

		PostAllocationOptimizationContext postAllocOptContext = new PostAllocationOptimizationContext(
				lirGen);
		lirSuites
				.getPostAllocationStage().apply(target, lirGenRes, codeEmittingOrder,
				linearScanOrder, postAllocOptContext);

		return lirGenRes;
	}

	public static void emitCode(OCLBackend backend, Assumptions assumptions,
			ResolvedJavaMethod rootMethod, Set<ResolvedJavaMethod> inlinedMethods,
			LIRGenerationResult lirGenRes, OCLCompilationResult compilationResult,
			ResolvedJavaMethod installedCodeOwner, CompilationResultBuilderFactory factory,
			boolean isKernel) {
		try (DebugCloseable a = EmitCode.start()) {
			FrameMap frameMap = lirGenRes.getFrameMap();
			final OCLCompilationResultBuilder crb = backend.newCompilationResultBuilder(lirGenRes,
					frameMap, compilationResult, factory, isKernel);
			backend.emitCode(crb, lirGenRes.getLIR(), installedCodeOwner);
			crb.finish();
			if (assumptions != null && !assumptions.isEmpty()) {
				compilationResult.setAssumptions(assumptions.toArray());
			}
			if (inlinedMethods != null) {
				compilationResult.setMethods(rootMethod, inlinedMethods);
			}

			compilationResult.setNonInlinedMethods(crb.getNonInlinedMethods());

			if (Debug.isMeterEnabled()) {
				List<DataPatch> ldp = compilationResult.getDataPatches();
				Kind[] kindValues = Kind.values();
				DebugMetric[] dms = new DebugMetric[kindValues.length];
				for (int i = 0; i < dms.length; i++) {
					dms[i] = Debug.metric("DataPatches-%s", kindValues[i]);
				}

				for (DataPatch dp : ldp) {
					Kind kind = Kind.Illegal;
					if (dp.reference instanceof ConstantReference) {
						VMConstant constant = ((ConstantReference) dp.reference).getConstant();
						kind = ((JavaConstant) constant).getKind();
					}
					dms[kind.ordinal()].add(1);
				}

				Debug.metric("CompilationResults").increment();
				Debug.metric("CodeBytesEmitted").add(compilationResult.getTargetCodeSize());
				Debug.metric("InfopointsEmitted").add(compilationResult.getInfopoints().size());
				Debug.metric("DataPatches").add(ldp.size());
				Debug.metric("ExceptionHandlersEmitted").add(
						compilationResult.getExceptionHandlers().size());
			}

			if (Debug.isLogEnabled()) {
				Debug.log("%s", backend.getCodeCache().disassemble(compilationResult, null));
			}

			Debug.dump(compilationResult, "After code generation");
		}

	}

	public static OpenCLInstalledCode compileCodeForDevice(ResolvedJavaMethod resolvedMethod,
			Object[] args, Meta meta, OCLProviders providers, OCLBackend backend) {
		Tornado.info("Compiling %s on %s", resolvedMethod.getName(), backend.getDeviceContext()
				.getDevice().getName());
		final StructuredGraph kernelGraph = new StructuredGraph(resolvedMethod,
				AllowAssumptions.YES);
		final OpenCLCodeCache codeCache = backend.getCodeCache();

		CallingConvention cc = OpenCLCodeUtil.getCallingConvention(codeCache,
				CallingConvention.Type.JavaCallee, resolvedMethod, false);

		OptimisticOptimizations optimisticOpts = OptimisticOptimizations.ALL;
		ProfilingInfo profilingInfo = resolvedMethod.getProfilingInfo();

		SpeculationLog speculationLog = null;

		OCLCompilationResult kernelCompResult = new OCLCompilationResult();
		CompilationResultBuilderFactory factory = CompilationResultBuilderFactory.Default;

		final OCLSuitesProvider suitesProvider = providers.getSuitesProvider();
		Request<OCLCompilationResult> kernelCompilationRequest = new Request<OCLCompilationResult>(
				kernelGraph, cc, resolvedMethod, args, meta, providers, backend,
				backend.getTarget(), suitesProvider.getDefaultGraphBuilderSuite(), optimisticOpts,
				profilingInfo, speculationLog, suitesProvider.createSuites(),
				suitesProvider.createLIRSuites(), kernelCompResult, factory, true);

		kernelCompilationRequest.execute();

		final Set<ResolvedJavaMethod> includedMethods = new HashSet<ResolvedJavaMethod>();
		final Deque<ResolvedJavaMethod> worklist = new ArrayDeque<ResolvedJavaMethod>();
		worklist.addAll(kernelCompResult.getNonInlinedMethods());

		while (!worklist.isEmpty()) {
			final ResolvedJavaMethod currentMethod = worklist.pop();
			if (!includedMethods.contains(currentMethod)) {
//				System.out.printf("including method: %s\n", currentMethod);
				final OCLCompilationResult compResult = new OCLCompilationResult();
				final StructuredGraph graph = new StructuredGraph(currentMethod,
						AllowAssumptions.YES);
				Request<OCLCompilationResult> methodcompilationRequest = new Request<OCLCompilationResult>(
						graph, cc, currentMethod, null, null, providers, backend,
						backend.getTarget(), suitesProvider.getDefaultGraphBuilderSuite(),
						optimisticOpts, profilingInfo, speculationLog,
						suitesProvider.createSuites(), suitesProvider.createLIRSuites(),
						compResult, factory, false);

				methodcompilationRequest.execute();
				worklist.addAll(compResult.getNonInlinedMethods());
				
//				for(byte b : compResult.getTargetCode()){
//					System.out.printf("%c",b);
//				}
//				System.out.println();

				kernelCompResult.addCompiledMethodCode(compResult.getTargetCode());
			}
		}

		return codeCache.addMethod(resolvedMethod, kernelCompResult, speculationLog, null);
	}
}
