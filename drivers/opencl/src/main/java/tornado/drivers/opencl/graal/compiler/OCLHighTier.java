package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.InliningPhase;
import com.oracle.graal.virtual.phases.ea.PartialEscapePhase;
import tornado.drivers.opencl.graal.phases.TornadoTaskSpecialisation;
import tornado.drivers.opencl.graal.phases.*;
import tornado.graal.compiler.TornadoHighTier;
import tornado.graal.phases.*;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.compiler.phases.HighTier.Options.Inline;
import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

public class OCLHighTier extends TornadoHighTier {

    public OCLHighTier(CustomCanonicalizer customCanonicalizer) {
        super(customCanonicalizer);

        final CanonicalizerPhase canonicalizer = new CanonicalizerPhase(customCanonicalizer);

        if (ImmutableCode.getValue()) {
            canonicalizer.disableReadCanonicalization();
        }

        if (OptCanonicalizer.getValue()) {
            appendPhase(canonicalizer);
        }

        if (Inline.getValue()) {
            appendPhase(new InliningPhase(new TornadoInliningPolicy(), canonicalizer));
            appendPhase(new TornadoInvokeCleanup());

            appendPhase(new DeadCodeEliminationPhase(Optional));

            if (ConditionalElimination.getValue() && OptCanonicalizer.getValue()) {
                appendPhase(canonicalizer);
                appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
            }
        }

        appendPhase(new TornadoParameterCleanup());
        appendPhase(new TornadoApiReplacement());

        appendPhase(new TornadoTaskSpecialisation(canonicalizer));
        appendPhase(new TornadoVectorResolver());
        appendPhase(canonicalizer);
        appendPhase(new DeadCodeEliminationPhase(Optional));

//        appendPhase(new CleanTypeProfileProxyPhase(canonicalizer));
//        if (FullUnroll.getValue()) {
//            appendPhase(new LoopFullUnrollPhase(canonicalizer));
//        }
//        appendPhase(new TornadoVectorization());
//        appendPhase(new TornadoVectorizationSimplification());
        appendPhase(canonicalizer);

        if (PartialEscapeAnalysis.getValue()) {
            appendPhase(new PartialEscapePhase(true, canonicalizer));
        }
        appendPhase(new TornadoValueTypeCleanup());

        if (OptConvertDeoptsToGuards.getValue()) {
            appendPhase(new ConvertDeoptimizeToGuardPhase());
        }

        if (OptLoopTransform.getValue()) {
//            if (LoopPeeling.getValue()) {
//                appendPhase(new LoopPeelingPhase());
//            }

//            if (LoopUnswitch.getValue()) {
//                appendPhase(new LoopUnswitchingPhase());
//            }
        }
        appendPhase(new RemoveValueProxyPhase());

        appendPhase(new TornadoShapeAnalysis());
        appendPhase(new TornadoParallelScheduler());

        appendPhase(canonicalizer);

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER));
        appendPhase(new ExceptionSuppression());

        // appendPhase(new TornadoOopsRemoval());
    }
}
