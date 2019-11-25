package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import org.graalvm.compiler.lir.phases.*;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import uk.ac.manchester.tornado.runtime.graal.compiler.*;
import uk.ac.manchester.tornado.runtime.graal.phases.lir.TornadoAllocationStage;

public class PTXCompilerConfiguration implements TornadoCompilerConfiguration {

    @Override
    public TornadoAllocationStage createAllocationStage(OptionValues options) {
        return new TornadoAllocationStage();
    }

    @Override
    public TornadoSketchTier createSketchTier(OptionValues options, CanonicalizerPhase.CustomCanonicalizer canonicalizer) {
        return new TornadoSketchTier(options, canonicalizer);
    }

    @Override
    public TornadoHighTier createHighTier(OptionValues options, CanonicalizerPhase.CustomCanonicalizer canonicalizer) {
        return null;
    }

    @Override
    public TornadoLowTier createLowTier(OptionValues options, AddressLoweringPhase.AddressLowering addressLowering) {
        return null;
    }

    @Override
    public TornadoMidTier createMidTier(OptionValues options) {
        return null;
    }

    @Override
    public LIRPhaseSuite<PostAllocationOptimizationPhase.PostAllocationOptimizationContext> createPostAllocationOptimizationStage(OptionValues options) {
        return new PostAllocationOptimizationStage(options);
    }

    @Override
    public LIRPhaseSuite<PreAllocationOptimizationPhase.PreAllocationOptimizationContext> createPreAllocationOptimizationStage(OptionValues options) {
        return new PreAllocationOptimizationStage(options);
    }

}