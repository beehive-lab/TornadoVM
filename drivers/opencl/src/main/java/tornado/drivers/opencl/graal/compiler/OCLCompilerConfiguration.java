package tornado.drivers.opencl.graal.compiler;

import tornado.graal.compiler.TornadoSketchTier;
import com.oracle.graal.lir.phases.LIRPhaseSuite;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PostAllocationOptimizationStage;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PreAllocationOptimizationStage;
import com.oracle.graal.phases.common.AddressLoweringPhase.AddressLowering;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import tornado.graal.compiler.*;
import tornado.graal.phases.lir.TornadoAllocationStage;

public class OCLCompilerConfiguration implements TornadoCompilerConfiguration {

    @Override
    public TornadoAllocationStage createAllocationStage() {
        return new TornadoAllocationStage();
    }

    @Override
    public TornadoSketchTier createSketchTier(CustomCanonicalizer canonicalizer) {
        return new TornadoSketchTier(canonicalizer);
    }

    @Override
    public TornadoHighTier createHighTier(CustomCanonicalizer canonicalizer) {
        return new OCLHighTier(canonicalizer);
    }

    @Override
    public TornadoLowTier createLowTier(AddressLowering addressLowering) {
        return new OCLLowTier(addressLowering);
    }

    @Override
    public TornadoMidTier createMidTier() {
        return new OCLMidTier();
    }

    @Override
    public LIRPhaseSuite<PostAllocationOptimizationContext> createPostAllocationOptimizationStage() {
        return new PostAllocationOptimizationStage();
    }

    @Override
    public LIRPhaseSuite<PreAllocationOptimizationContext> createPreAllocationOptimizationStage() {
        return new PreAllocationOptimizationStage();
    }

}
