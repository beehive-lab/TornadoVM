package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.loop.phases.LoopSafepointEliminationPhase;
import com.oracle.graal.loop.phases.ReassociateInvariantPhase;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.virtual.phases.ea.EarlyReadEliminationPhase;
import tornado.graal.compiler.TornadoMidTier;
import tornado.graal.phases.ExceptionCheckingElimination;
import tornado.graal.phases.TornadoMemoryPhiElimination;

import static com.oracle.graal.compiler.common.GraalOptions.*;

public class OCLMidTier extends TornadoMidTier {

    public OCLMidTier() {

        appendPhase(new ExceptionCheckingElimination());

        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        if (ImmutableCode.getValue()) {
            canonicalizer.disableReadCanonicalization();
        }

        if (OptPushThroughPi.getValue()) {
            appendPhase(new PushThroughPiPhase());
        }
//        if (OptCanonicalizer.getValue()) {
        appendPhase(canonicalizer);
//        }

        //if(!OpenCLTornadoBackend.ENABLE_EXCEPTIONS)
        appendPhase(new ExceptionCheckingElimination());

        appendPhase(new ValueAnchorCleanupPhase());
        // appendPhase(new LockEliminationPhase());

        if (OptReadElimination.getValue()) {
            appendPhase(new EarlyReadEliminationPhase(canonicalizer));
        }

        if (OptFloatingReads.getValue()) {
            appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new FloatingReadPhase()));
        }
        appendPhase(new TornadoMemoryPhiElimination());
        appendPhase(new RemoveValueProxyPhase());

//        if (OptCanonicalizer.getValue()) {
        appendPhase(canonicalizer);
//        }

        if (OptEliminatePartiallyRedundantGuards.getValue()) {
            appendPhase(new OptimizeGuardAnchorsPhase());
        }

        if (ConditionalElimination.getValue()) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, true));
        }

        if (OptEliminatePartiallyRedundantGuards.getValue()) {
            appendPhase(new OptimizeGuardAnchorsPhase());
        }

//        if (OptCanonicalizer.getValue()) {
        appendPhase(canonicalizer);
//        }

        appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new LoopSafepointEliminationPhase()));

        //appendPhase(new LoopSafepointInsertionPhase());
        appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new GuardLoweringPhase()));

        //if (VerifyHeapAtReturn.getValue()) {
        //    appendPhase(new VerifyHeapAtReturnPhase());
        //}
        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.MID_TIER));

        appendPhase(new FrameStateAssignmentPhase());

        if (ReassociateInvariants.getValue()) {
            appendPhase(new ReassociateInvariantPhase());
        }

        //appendPhase(new TornadoIfCanonicalization());
        //appendPhase(canonicalizer);
        //if (OptDeoptimizationGrouping.getValue()) {
        //    appendPhase(new DeoptimizationGroupingPhase());
        //}
//        if (OptCanonicalizer.getValue()) {
        appendPhase(canonicalizer);
//        }
    }
}
