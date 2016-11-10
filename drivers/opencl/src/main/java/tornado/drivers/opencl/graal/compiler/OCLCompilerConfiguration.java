package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.lir.phases.LIRPhaseSuite;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PostAllocationOptimizationStage;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PreAllocationOptimizationStage;
import tornado.graal.compiler.TornadoCompilerConfiguration;
import tornado.graal.compiler.TornadoHighTier;
import tornado.graal.compiler.TornadoLowTier;
import tornado.graal.compiler.TornadoMidTier;
import tornado.graal.phases.lir.TornadoAllocationStage;

public class OCLCompilerConfiguration implements TornadoCompilerConfiguration {

    @Override
    public TornadoAllocationStage createAllocationStage() {
        return new TornadoAllocationStage();
    }

    @Override
    public TornadoHighTier createHighTier() {
        return new OCLHighTier(new OCLCanonicalizer());
    }

    @Override
    public TornadoLowTier createLowTier() {
        return new OCLLowTier();
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
