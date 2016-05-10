package tornado.graal.compiler;

import tornado.graal.compiler.TornadoHighTier;
import tornado.graal.compiler.TornadoLowTier;
import tornado.graal.compiler.TornadoMidTier;
import tornado.graal.phases.lir.TornadoAllocationStage;

import com.oracle.graal.api.runtime.Service;
import com.oracle.graal.lir.phases.LIRPhaseSuite;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;

public interface TornadoCompilerConfiguration  extends Service {
	
	public TornadoAllocationStage createAllocationStage(); 
	public TornadoHighTier createHighTier(CustomCanonicalizer canonicalizer);
	public TornadoLowTier createLowTier();
	public TornadoMidTier createMidTier();
	public LIRPhaseSuite<PostAllocationOptimizationContext> createPostAllocationOptimizationStage();
	public LIRPhaseSuite<PreAllocationOptimizationContext> createPreAllocationOptimizationStage();

}
