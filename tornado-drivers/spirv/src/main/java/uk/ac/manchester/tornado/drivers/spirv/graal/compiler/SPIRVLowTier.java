/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, 2023, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import static jdk.graal.compiler.core.common.GraalOptions.ConditionalElimination;
import static jdk.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.AddressLoweringByNodePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.FixReadsPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.LowTierLoweringPhase;
import jdk.graal.compiler.phases.common.UseTrappingNullChecksPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.analysis.TornadoFeatureExtraction;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.loops.TornadoLoopCanonicalization;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.utils.DumpLowTierGraph;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.OCLFPGAPragmaPhase;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.OCLFPGAThreadScheduler;
import uk.ac.manchester.tornado.drivers.spirv.graal.phases.InverseSquareRootPhase;
import uk.ac.manchester.tornado.drivers.spirv.graal.phases.InfinityReplacementPhase;
import uk.ac.manchester.tornado.drivers.spirv.graal.phases.PartialLoopUnrollPhase;
import uk.ac.manchester.tornado.drivers.spirv.graal.phases.SPIRVFMAPhase;
import uk.ac.manchester.tornado.drivers.spirv.graal.phases.SPIRVFP64SupportPhase;
import uk.ac.manchester.tornado.drivers.spirv.graal.phases.SPIRVFieldCoopsAccessPhase;
import uk.ac.manchester.tornado.drivers.spirv.graal.phases.TornadoFixedArrayCopyPhase;
import uk.ac.manchester.tornado.drivers.spirv.graal.phases.TornadoHalfFloatVectorOffset;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoLowTier;

public class SPIRVLowTier extends TornadoLowTier {

    public SPIRVLowTier(OptionValues options, TornadoDeviceContext deviceContext, AddressLoweringByNodePhase.AddressLowering addressLowering) {

        CanonicalizerPhase canonicalizer = getCannonicalizer();

        appendPhase(new SPIRVFP64SupportPhase(deviceContext));

        appendPhase(new LowTierLoweringPhase(canonicalizer));

        if (TornadoOptions.ENABLE_SPIRV_LOOP_UNROLL) {
            appendPhase(new PartialLoopUnrollPhase());
        }

        if (ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
        }

        appendPhase(new TornadoHalfFloatVectorOffset());

        appendPhase(new FixReadsPhase(true, new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS)));

        appendPhase(new UseTrappingNullChecksPhase());

        appendPhase(new TornadoFixedArrayCopyPhase());

        appendPhase(new AddressLoweringByNodePhase(addressLowering));

        appendPhase(new DeadCodeEliminationPhase(Required));

        if (deviceContext.isPlatformFPGA()) {
            appendPhase(new OCLFPGAPragmaPhase(deviceContext));
            appendPhase(new OCLFPGAThreadScheduler());
        }

        appendPhase(new TornadoLoopCanonicalization());

        if (TornadoOptions.ENABLE_FMA) {
            appendPhase(new SPIRVFMAPhase());
        }

        if (TornadoOptions.MATH_OPTIMIZATIONS) {
            appendPhase(new InverseSquareRootPhase());
        }

        appendPhase(new InfinityReplacementPhase());

        // TODO Atomics Phase for SPIRV (this is the last thing to support)

        appendPhase(new SPIRVFieldCoopsAccessPhase());

        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS));

        if (TornadoOptions.FEATURE_EXTRACTION) {
            appendPhase(new TornadoFeatureExtraction(deviceContext));
        }

        if (TornadoOptions.DUMP_LOW_TIER_WITH_IGV) {
            appendPhase(new DumpLowTierGraph());
        }
    }

    private CanonicalizerPhase getCannonicalizer() {
        return CanonicalizerPhase.create();
    }
}
