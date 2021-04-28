package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import org.graalvm.compiler.lir.phases.LIRPhaseSuite;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationStage;
import org.graalvm.compiler.lir.phases.PreAllocationOptimizationPhase;
import org.graalvm.compiler.lir.phases.PreAllocationOptimizationStage;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;

import jdk.vm.ci.meta.MetaAccessProvider;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCompilerConfiguration;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoHighTier;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoLowTier;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoMidTier;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSketchTier;
import uk.ac.manchester.tornado.runtime.graal.phases.lir.TornadoAllocationStage;

public class SPIRVCompilerConfiguration implements TornadoCompilerConfiguration {

    @Override
    public TornadoAllocationStage createAllocationStage(OptionValues options) {
        return new TornadoAllocationStage();
    }

    @Override
    public TornadoSketchTier createSketchTier(OptionValues options, CanonicalizerPhase.CustomCanonicalization canonicalizer) {
        return new TornadoSketchTier(options, canonicalizer);
    }

    @Override
    public TornadoHighTier createHighTier(OptionValues options, TornadoDeviceContext deviceContext, CanonicalizerPhase.CustomCanonicalization canonicalizer, MetaAccessProvider metaAccessProvider) {
        return new SPIRVHighTier(options, deviceContext, canonicalizer, metaAccessProvider);
    }

    @Override
    public TornadoMidTier createMidTier(OptionValues options) {
        // FIXME: <REFACTOR> PTX can reuse some of the phases in MIDtier from OCL and
        // SPIRV
        return new SPIRVMidTier(options);
    }

    @Override
    public TornadoLowTier createLowTier(OptionValues options, TornadoDeviceContext deviceContext, AddressLoweringPhase.AddressLowering addressLowering) {
        return new SPIRVLowTier(options, deviceContext, addressLowering);
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
