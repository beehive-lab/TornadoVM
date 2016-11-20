package tornado.graal.compiler;

import com.oracle.graal.lir.phases.LIRPhaseSuite;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import com.oracle.graal.phases.common.AddressLoweringPhase.AddressLowering;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import tornado.graal.phases.lir.TornadoAllocationStage;

public interface TornadoCompilerConfiguration {

    public TornadoAllocationStage createAllocationStage();

    public TornadoSketchTier createSketchTier(CustomCanonicalizer canonicalizer);

    public TornadoHighTier createHighTier(CustomCanonicalizer canonicalizer);

    public TornadoLowTier createLowTier(AddressLowering addressLowering);

    public TornadoMidTier createMidTier();

    public LIRPhaseSuite<PostAllocationOptimizationContext> createPostAllocationOptimizationStage();

    public LIRPhaseSuite<PreAllocationOptimizationContext> createPreAllocationOptimizationStage();

}
