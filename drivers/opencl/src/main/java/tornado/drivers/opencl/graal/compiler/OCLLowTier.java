package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.phases.common.AddressLoweringPhase.AddressLowering;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.schedule.SchedulePhase;
import tornado.graal.compiler.TornadoLowTier;
import tornado.graal.phases.TornadoLoopCanonicalization;

import static com.oracle.graal.compiler.common.GraalOptions.ConditionalElimination;
import static com.oracle.graal.compiler.common.GraalOptions.ImmutableCode;
import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.Required;

public class OCLLowTier extends TornadoLowTier {

    public OCLLowTier(AddressLowering addressLowering) {
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        if (ImmutableCode.getValue()) {
            canonicalizer.disableReadCanonicalization();
        }

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.LOW_TIER));

        appendPhase(new RemoveValueProxyPhase());

//        appendPhase(new ExpandLogicPhase());

        /*
         * Cleanup IsNull checks resulting from MID_TIER/LOW_TIER lowering and
         * ExpandLogic phase.
         */
        if (ConditionalElimination.getValue()) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
            /*
             * Canonicalizer may create some new ShortCircuitOrNodes so clean
             * them up.
             */
//            appendPhase(new ExpandLogicPhase());
        }

        appendPhase(new AddressLoweringPhase(addressLowering));

        appendPhase(new UseTrappingNullChecksPhase());

        appendPhase(new DeadCodeEliminationPhase(Required));

        appendPhase(new TornadoLoopCanonicalization());
        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.FINAL_SCHEDULE));

    }
}
