package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import static org.graalvm.compiler.core.common.GraalOptions.ConditionalElimination;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.core.common.GraalOptions.OptConvertDeoptsToGuards;
import static org.graalvm.compiler.core.common.GraalOptions.PartialEscapeAnalysis;
import static org.graalvm.compiler.core.phases.HighTier.Options.Inline;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.loop.phases.LoopFullUnrollPhase;
import org.graalvm.compiler.nodes.loop.DefaultLoopPolicies;
import org.graalvm.compiler.nodes.loop.LoopPolicies;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;

import jdk.vm.ci.meta.MetaAccessProvider;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.graal.phases.TornadoNewArrayDevirtualizationReplacement;
import uk.ac.manchester.tornado.drivers.spirv.graal.phases.TornadoParallelScheduler;
import uk.ac.manchester.tornado.drivers.spirv.graal.phases.TornadoSPIRVIntrinsicsReplacements;
import uk.ac.manchester.tornado.drivers.spirv.graal.phases.TornadoTaskSpecialization;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoHighTier;
import uk.ac.manchester.tornado.runtime.graal.phases.ExceptionSuppression;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoFullInliningPolicy;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoInliningPolicy;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoLocalMemoryAllocation;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoPartialInliningPolicy;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoShapeAnalysis;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoValueTypeCleanup;

public class SPIRVHighTier extends TornadoHighTier {

    private CanonicalizerPhase createCanonicalizerPhase(OptionValues options, CanonicalizerPhase.CustomSimplification customCanonicalizer) {
        CanonicalizerPhase canonicalizer;
        if (ImmutableCode.getValue(options)) {
            canonicalizer = CanonicalizerPhase.createWithoutReadCanonicalization();
        } else {
            canonicalizer = CanonicalizerPhase.create();
        }
        return canonicalizer.copyWithCustomSimplification(customCanonicalizer);
    }

    public SPIRVHighTier(OptionValues options, TornadoDeviceContext deviceContext, CanonicalizerPhase.CustomSimplification customCanonicalizer, MetaAccessProvider metaAccessProvider) {
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

        appendPhase(new TornadoTaskSpecialization(canonicalizer));
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

        if (!deviceContext.isPlatformFPGA()) {
            LoopPolicies loopPolicies = new DefaultLoopPolicies();
            appendPhase(new LoopFullUnrollPhase(canonicalizer, loopPolicies));
        }

        appendPhase(canonicalizer);
        appendPhase(new RemoveValueProxyPhase());
        appendPhase(canonicalizer);
        appendPhase(new DeadCodeEliminationPhase(Optional));

        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST));
        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER));

        appendPhase(new TornadoSPIRVIntrinsicsReplacements(metaAccessProvider));

        appendPhase(new TornadoLocalMemoryAllocation());
        appendPhase(new ExceptionSuppression());

    }
}
