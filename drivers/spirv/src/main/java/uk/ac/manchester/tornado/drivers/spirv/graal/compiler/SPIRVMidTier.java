package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import static org.graalvm.compiler.core.common.GraalOptions.ConditionalElimination;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.core.common.GraalOptions.OptFloatingReads;
import static org.graalvm.compiler.core.common.GraalOptions.ReassociateInvariants;

import org.graalvm.compiler.loop.phases.ReassociateInvariantPhase;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.FrameStateAssignmentPhase;
import org.graalvm.compiler.phases.common.GuardLoweringPhase;
import org.graalvm.compiler.phases.common.IncrementalCanonicalizerPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;

import uk.ac.manchester.tornado.drivers.opencl.graal.phases.BoundCheckEliminationPhase;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoFloatingReadReplacement;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoPartialLoopUnroll;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoMidTier;
import uk.ac.manchester.tornado.runtime.graal.phases.ExceptionCheckingElimination;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoMemoryPhiElimination;

public class SPIRVMidTier extends TornadoMidTier {

    public SPIRVMidTier(OptionValues options) {
        appendPhase(new ExceptionCheckingElimination());

        CanonicalizerPhase canonicalizer;
        if (ImmutableCode.getValue(options)) {
            canonicalizer = CanonicalizerPhase.createWithoutReadCanonicalization();
        } else {
            canonicalizer = CanonicalizerPhase.create();
        }

        appendPhase(canonicalizer);

        appendPhase((new BoundCheckEliminationPhase()));
        appendPhase(new ExceptionCheckingElimination());

        if (OptFloatingReads.getValue(options)) {
            appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new TornadoFloatingReadReplacement()));
        }

        appendPhase(new TornadoMemoryPhiElimination());
        appendPhase(new RemoveValueProxyPhase());

        appendPhase(canonicalizer);

        if (ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, true));
        }

        appendPhase(new GuardLoweringPhase());

        appendPhase(canonicalizer);

        if (TornadoOptions.PARTIAL_UNROLL()) {
            appendPhase(new TornadoPartialLoopUnroll());
        }

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.MID_TIER));

        appendPhase(new FrameStateAssignmentPhase());

        if (ReassociateInvariants.getValue(options)) {
            appendPhase(new ReassociateInvariantPhase());
        }

        appendPhase(canonicalizer);
    }
}
