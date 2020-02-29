package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.*;
import org.graalvm.compiler.phases.common.AddressLoweringPhase.AddressLowering;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import uk.ac.manchester.tornado.drivers.cuda.graal.phases.PTXMulAddPhase;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoLowTier;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoFeatureExtraction;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoLoopCanonicalization;

import static org.graalvm.compiler.core.common.GraalOptions.ConditionalElimination;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;

public class PTXLowTier extends TornadoLowTier {
    public PTXLowTier(OptionValues options, AddressLowering addressLowering) {
        CanonicalizerPhase canonicalizer;
        if (ImmutableCode.getValue(options)) {
            canonicalizer = CanonicalizerPhase.createWithoutReadCanonicalization();
        } else {
            canonicalizer = CanonicalizerPhase.create();
        }

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.LOW_TIER));

        appendPhase(new RemoveValueProxyPhase());

        // appendPhase(new ExpandLogicPhase());

        /*
         * Cleanup IsNull checks resulting from MID_TIER/LOW_TIER lowering and
         * ExpandLogic phase.
         */
        if (ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
        }

        appendPhase(new AddressLoweringPhase(addressLowering));

        appendPhase(new UseTrappingNullChecksPhase());

        appendPhase(new DeadCodeEliminationPhase(Required));

        appendPhase(new TornadoLoopCanonicalization());

        appendPhase(new PTXMulAddPhase());

        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS));

        if (TornadoOptions.FEATURE_EXTRACTION) {
            appendPhase(new TornadoFeatureExtraction());
        }

    }
}
