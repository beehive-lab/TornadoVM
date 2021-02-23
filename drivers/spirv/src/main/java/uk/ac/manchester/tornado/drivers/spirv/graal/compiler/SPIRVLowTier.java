package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.*;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.drivers.common.graal.compiler.DumpLowTierGraph;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoLowTier;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoFeatureExtraction;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoLoopCanonicalization;

import static org.graalvm.compiler.core.common.GraalOptions.ConditionalElimination;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;

public class SPIRVLowTier extends TornadoLowTier {

    public SPIRVLowTier(OptionValues options, TornadoDeviceContext deviceContext, AddressLoweringPhase.AddressLowering addressLowering) {
        CanonicalizerPhase canonicalizer;
        if (ImmutableCode.getValue(options)) {
            canonicalizer = CanonicalizerPhase.createWithoutReadCanonicalization();
        } else {
            canonicalizer = CanonicalizerPhase.create();
        }

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.LOW_TIER));

        appendPhase(new RemoveValueProxyPhase());

        if (ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
        }

        appendPhase(new AddressLoweringPhase(addressLowering));

        appendPhase(new DeadCodeEliminationPhase(Required));

        appendPhase(new TornadoLoopCanonicalization());

        // TODO: SPIRV FMA Phase
        // appendPhase(new SPIRVFMAPhase());

        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS));

        if (TornadoOptions.FEATURE_EXTRACTION) {
            appendPhase(new TornadoFeatureExtraction(deviceContext));
        }

        if (TornadoOptions.DUMP_LOW_TIER_WITH_IGV) {
            appendPhase(new DumpLowTierGraph());
        }
    }
}
