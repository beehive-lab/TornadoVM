package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.loop.DefaultLoopPolicies;
import org.graalvm.compiler.loop.LoopPolicies;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.loop.phases.LoopFullUnrollPhase;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.*;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.*;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoPragmaUnroll;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoTaskSpecialisation;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoHighTier;
import uk.ac.manchester.tornado.runtime.graal.phases.*;

import static org.graalvm.compiler.core.common.GraalOptions.*;
import static org.graalvm.compiler.core.common.GraalOptions.OptConvertDeoptsToGuards;
import static org.graalvm.compiler.core.phases.HighTier.Options.Inline;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

public class SPIRVHighTier extends TornadoHighTier {

    private CanonicalizerPhase createCanonicalizerPhase(OptionValues options, CanonicalizerPhase.CustomCanonicalization customCanonicalizer) {
        CanonicalizerPhase canonicalizer;
        if (ImmutableCode.getValue(options)) {
            canonicalizer = CanonicalizerPhase.createWithoutReadCanonicalization();
        } else {
            canonicalizer = CanonicalizerPhase.create();
        }
        return canonicalizer.copyWithCustomCanonicalization(customCanonicalizer);
    }

    public SPIRVHighTier(OptionValues options, TornadoDeviceContext deviceContext, CanonicalizerPhase.CustomCanonicalization customCanonicalizer, MetaAccessProvider metaAccessProvider) {
        super(customCanonicalizer);

        CanonicalizerPhase canonicalizer = createCanonicalizerPhase(options, customCanonicalizer);
        appendPhase(canonicalizer);

        if (Inline.getValue(options)) {
            TornadoInliningPolicy inliningPolicy = (TornadoOptions.FULL_INLINING) ? new TornadoFullInliningPolicy() : new TornadoPartialInliningPolicy();
            appendPhase(new InliningPhase(inliningPolicy, canonicalizer));
            appendPhase(new DeadCodeEliminationPhase(Optional));
            if (ConditionalElimination.getValue(options)) {
                appendPhase(canonicalizer);
                appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
            }
        }

        appendPhase(new TornadoTaskSpecialisation(canonicalizer));
        appendPhase(canonicalizer);
        appendPhase(new DeadCodeEliminationPhase(Optional));

        appendPhase(canonicalizer);

        appendPhase(new TornadoNewArrayDevirtualizationReplacement());

        if (PartialEscapeAnalysis.getValue(options)) {
            appendPhase(new PartialEscapePhase(true, canonicalizer, options));
        }
        appendPhase(new TornadoValueTypeCleanup());

        if (OptConvertDeoptsToGuards.getValue(options)) {
            appendPhase(new ConvertDeoptimizeToGuardPhase());
        }

        appendPhase(new TornadoShapeAnalysis());
        appendPhase(canonicalizer);
        appendPhase(new TornadoParallelScheduler());
        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST));

        if (deviceContext.isPlatformFPGA()) {
            appendPhase(new TornadoPragmaUnroll());
            appendPhase(new TornadoThreadScheduler());
        } else {
            LoopPolicies loopPolicies = new DefaultLoopPolicies();
            appendPhase(new LoopFullUnrollPhase(canonicalizer, loopPolicies));
        }

        LoopPolicies loopPolicies = new DefaultLoopPolicies();
        appendPhase(new LoopFullUnrollPhase(canonicalizer, loopPolicies));

        appendPhase(canonicalizer);
        appendPhase(new RemoveValueProxyPhase());
        appendPhase(canonicalizer);
        appendPhase(new DeadCodeEliminationPhase(Optional));

        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST));
        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER));

        // TODO: Add SPIRV PTX Intrinsics Replacement

        appendPhase(new TornadoLocalMemoryAllocation());
        appendPhase(new ExceptionSuppression());

    }
}
