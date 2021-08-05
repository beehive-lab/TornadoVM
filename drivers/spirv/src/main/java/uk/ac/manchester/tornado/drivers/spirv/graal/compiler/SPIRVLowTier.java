package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import static org.graalvm.compiler.core.common.GraalOptions.ConditionalElimination;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;

import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.FixReadsPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.phases.common.UseTrappingNullChecksPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.drivers.common.graal.compiler.DumpLowTierGraph;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.OCLFPGAPragmaPhase;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.OCLFPGAThreadScheduler;
import uk.ac.manchester.tornado.drivers.spirv.graal.phases.SPIRVFMAPhase;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoLowTier;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoFeatureExtraction;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoLoopCanonicalization;

public class SPIRVLowTier extends TornadoLowTier {

    private CanonicalizerPhase getCannonicalizer(OptionValues options) {
        if (ImmutableCode.getValue(options)) {
            return CanonicalizerPhase.createWithoutReadCanonicalization();
        } else {
            return CanonicalizerPhase.create();
        }
    }

    public SPIRVLowTier(OptionValues options, TornadoDeviceContext deviceContext, AddressLoweringPhase.AddressLowering addressLowering) {
        CanonicalizerPhase canonicalizer = getCannonicalizer(options);

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.LOW_TIER));

        appendPhase(new RemoveValueProxyPhase());

        if (ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
        }

        if (TornadoOptions.ENABLE_FIX_READS) {
            appendPhase(new FixReadsPhase(true, new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS), canonicalizer));
        }

        appendPhase(new AddressLoweringPhase(addressLowering));

        appendPhase(new UseTrappingNullChecksPhase());

        appendPhase(new DeadCodeEliminationPhase(Required));

        if (deviceContext.isPlatformFPGA()) {
            appendPhase(new OCLFPGAPragmaPhase(deviceContext));
            appendPhase(new OCLFPGAThreadScheduler());
        }

        appendPhase(new TornadoLoopCanonicalization());

        if (TornadoOptions.ENABLE_FMA) {
            appendPhase(new SPIRVFMAPhase());
        }

        // TODO Atomics Phase for SPIRV (this is the last thing to support)

        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS));

        if (TornadoOptions.FEATURE_EXTRACTION) {
            appendPhase(new TornadoFeatureExtraction(deviceContext));
        }

        if (TornadoOptions.DUMP_LOW_TIER_WITH_IGV) {
            appendPhase(new DumpLowTierGraph());
        }
    }
}
