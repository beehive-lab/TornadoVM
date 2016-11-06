package tornado.graal.compiler;

import com.oracle.graal.lir.phases.LIRPhaseSuite;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import tornado.graal.phases.lir.TornadoAllocationStage;

public interface TornadoCompilerConfiguration {

    public TornadoAllocationStage createAllocationStage();

    public TornadoHighTier createHighTier();

    public TornadoLowTier createLowTier();

    public TornadoMidTier createMidTier();

    public LIRPhaseSuite<PostAllocationOptimizationContext> createPostAllocationOptimizationStage();

    public LIRPhaseSuite<PreAllocationOptimizationContext> createPreAllocationOptimizationStage();

}
