package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.loop.DefaultLoopPolicies;
import com.oracle.graal.loop.LoopPolicies;
import com.oracle.graal.loop.phases.LoopFullUnrollPhase;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.InliningPhase;
import com.oracle.graal.phases.schedule.SchedulePhase;
import com.oracle.graal.virtual.phases.ea.PartialEscapePhase;
import tornado.drivers.opencl.graal.phases.OCLThreadCoarsener;
import tornado.drivers.opencl.graal.phases.TornadoParallelScheduler;
import tornado.drivers.opencl.graal.phases.TornadoTaskSpecialisation;
import tornado.graal.compiler.TornadoHighTier;
import tornado.graal.phases.ExceptionSuppression;
import tornado.graal.phases.TornadoInliningPolicy;
import tornado.graal.phases.TornadoShapeAnalysis;
import tornado.graal.phases.TornadoValueTypeCleanup;

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
        appendPhase(canonicalizer);

        if (Inline.getValue()) {
            appendPhase(new InliningPhase(new TornadoInliningPolicy(), canonicalizer));

            appendPhase(new DeadCodeEliminationPhase(Optional));

            if (ConditionalElimination.getValue()) {
                appendPhase(canonicalizer);
                appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
            }
        }

        appendPhase(new TornadoTaskSpecialisation(canonicalizer));
//        appendPhase(new TornadoVectorResolver());
        appendPhase(canonicalizer);
        appendPhase(new DeadCodeEliminationPhase(Optional));

        appendPhase(canonicalizer);

        if (PartialEscapeAnalysis.getValue()) {
            appendPhase(new PartialEscapePhase(true, canonicalizer));
        }
        appendPhase(new TornadoValueTypeCleanup());

        if (OptConvertDeoptsToGuards.getValue()) {
            appendPhase(new ConvertDeoptimizeToGuardPhase());
        }

        appendPhase(new RemoveValueProxyPhase());

        appendPhase(new TornadoShapeAnalysis());
        appendPhase(new OCLThreadCoarsener());
        appendPhase(new TornadoParallelScheduler());
        appendPhase(canonicalizer);

        LoopPolicies loopPolicies = new DefaultLoopPolicies();
        appendPhase(new LoopFullUnrollPhase(canonicalizer, loopPolicies));
        appendPhase(canonicalizer);
        appendPhase(new DeadCodeEliminationPhase(Optional));

        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST));

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER));
        appendPhase(new ExceptionSuppression());
    }
}
