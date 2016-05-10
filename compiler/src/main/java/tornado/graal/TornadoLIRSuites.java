package tornado.graal;

import tornado.graal.phases.lir.TornadoAllocationStage;

import com.oracle.graal.lir.phases.LIRPhaseSuite;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;

public class TornadoLIRSuites {

	private final LIRPhaseSuite<PreAllocationOptimizationContext> preAllocStage;
	private final TornadoAllocationStage allocStage;
	private final LIRPhaseSuite<PostAllocationOptimizationContext> postAllocStage;
	
	public TornadoLIRSuites(
			LIRPhaseSuite<PreAllocationOptimizationContext> preAllocStage,
			TornadoAllocationStage allocStage,
			LIRPhaseSuite<PostAllocationOptimizationContext> postAllocStage) {
		this.preAllocStage = preAllocStage;
		this.allocStage = allocStage;
		this.postAllocStage = postAllocStage;
	}

	public LIRPhaseSuite<PreAllocationOptimizationContext> getPreAllocationStage() {
		return preAllocStage;
	}

	public TornadoAllocationStage getAllocationStage() {
		return allocStage;
	}

	public LIRPhaseSuite<PostAllocationOptimizationContext> getPostAllocationStage() {
		return postAllocStage;
	}

}
