package tornado.drivers.opencl.graal.compiler;

import tornado.graal.compiler.TornadoCompilerConfiguration;
import tornado.graal.compiler.TornadoHighTier;
import tornado.graal.compiler.TornadoLowTier;
import tornado.graal.compiler.TornadoMidTier;
import tornado.graal.phases.lir.TornadoAllocationStage;

import com.oracle.graal.lir.phases.LIRPhaseSuite;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import com.oracle.graal.phases.tiers.Suites;

public class OCLCompilerConfiguration  implements TornadoCompilerConfiguration {
	
	private final LIRPhaseSuite<PreAllocationOptimizationContext>  preAllocStage;
	private final LIRPhaseSuite<PostAllocationOptimizationContext> postAllocStage;
	
	
	public OCLCompilerConfiguration(){
		/*
		 * At the moment we borrow some stages from graal
		 * ...these will likely be replaced in the future
		 */
		LIRSuites lirSuites = Suites.createDefaultLIRSuites();
		preAllocStage = lirSuites.getPreAllocationOptimizationStage();
		postAllocStage = lirSuites.getPostAllocationOptimizationStage();
	}

	public TornadoAllocationStage createAllocationStage() {
		return new TornadoAllocationStage();
	}

	public TornadoHighTier createHighTier(CustomCanonicalizer canonicalizer) {
		return new OCLHighTier(canonicalizer); 
	}

	public TornadoLowTier createLowTier() {
		return new OCLLowTier();
	}

	public TornadoMidTier createMidTier() {
		return new OCLMidTier();
	}

	public LIRPhaseSuite<PostAllocationOptimizationContext> createPostAllocationOptimizationStage() {
		return postAllocStage;
	}

	public LIRPhaseSuite<PreAllocationOptimizationContext> createPreAllocationOptimizationStage() {
		return preAllocStage;
	}

}
