package tornado.drivers.opencl.graal.compiler;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.*;
import tornado.graal.compiler.TornadoLowTier;
import tornado.graal.phases.TornadoLoopCanonicalization;

import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.common.*;

public class OCLLowTier extends TornadoLowTier {


    public OCLLowTier() {
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        if (ImmutableCode.getValue()) {
            canonicalizer.disableReadCanonicalization();
        }

       

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.LOW_TIER));

        appendPhase(new RemoveValueProxyPhase());

       // appendPhase(new ExpandLogicPhase());

        /* Cleanup IsNull checks resulting from MID_TIER/LOW_TIER lowering and ExpandLogic phase. */
        if (ConditionalElimination.getValue() && OptCanonicalizer.getValue()) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
            /* Canonicalizer may create some new ShortCircuitOrNodes so clean them up. */
            //appendPhase(new ExpandLogicPhase());
        }

        appendPhase(new UseTrappingNullChecksPhase());

        appendPhase(new DeadCodeEliminationPhase(Required));
        
        appendPhase(new TornadoLoopCanonicalization());
        
    }
}
