package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.lir.phases.*;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import uk.ac.manchester.tornado.runtime.graal.compiler.*;
import uk.ac.manchester.tornado.runtime.graal.phases.lir.TornadoAllocationStage;

import static org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase.*;
import static org.graalvm.compiler.lir.phases.PreAllocationOptimizationPhase.*;

public class PTXCompilerConfiguration implements TornadoCompilerConfiguration {

    @Override
    public TornadoAllocationStage createAllocationStage(OptionValues options) {
        return new TornadoAllocationStage();
    }

    @Override
    public TornadoSketchTier createSketchTier(OptionValues options, CanonicalizerPhase.CustomCanonicalization canonicalizer) {
        return new TornadoSketchTier(options, canonicalizer);
    }

    @Override
    public TornadoHighTier createHighTier(OptionValues options, CanonicalizerPhase.CustomCanonicalization canonicalizer, MetaAccessProvider metaAccessProvider) {
        return new PTXHighTier(options, canonicalizer, metaAccessProvider);
    }

    @Override
    public TornadoMidTier createMidTier(OptionValues options) {
        return new PTXMidTier(options);
    }

    @Override
    public TornadoLowTier createLowTier(OptionValues options, AddressLoweringPhase.AddressLowering addressLowering) {
        return new PTXLowTier(options, addressLowering);
    }

    @Override
    public LIRPhaseSuite<PostAllocationOptimizationContext> createPostAllocationOptimizationStage(OptionValues options) {
        return new PostAllocationOptimizationStage(options);
    }

    @Override
    public LIRPhaseSuite<PreAllocationOptimizationContext> createPreAllocationOptimizationStage(OptionValues options) {
        return new PreAllocationOptimizationStage(options);
    }

}