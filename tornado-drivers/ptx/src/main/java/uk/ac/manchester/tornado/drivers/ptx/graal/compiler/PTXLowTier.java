/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2021, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import static jdk.graal.compiler.core.common.GraalOptions.ConditionalElimination;
import static jdk.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.AddressLoweringByNodePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.FixReadsPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.LowTierLoweringPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.analysis.TornadoFeatureExtraction;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.loops.TornadoLoopCanonicalization;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.utils.DumpLowTierGraph;
import uk.ac.manchester.tornado.drivers.ptx.graal.phases.InverseSquareRootPhase;
import uk.ac.manchester.tornado.drivers.ptx.graal.phases.InfinityReplacementPhase;
import uk.ac.manchester.tornado.drivers.ptx.graal.phases.PTXFMAPhase;
import uk.ac.manchester.tornado.drivers.ptx.graal.phases.PTXFieldCoopsAccessPhase;
import uk.ac.manchester.tornado.drivers.ptx.graal.phases.TornadoFixedArrayCopyPhase;
import uk.ac.manchester.tornado.drivers.ptx.graal.phases.TornadoHalfFloatVectorOffset;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoLowTier;

public class PTXLowTier extends TornadoLowTier {

    TornadoDeviceContext tornadoDeviceContext;

    public PTXLowTier(OptionValues options, TornadoDeviceContext tornadoDeviceContext, AddressLoweringByNodePhase.AddressLowering addressLowering) {
        this.tornadoDeviceContext = tornadoDeviceContext;
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();

        appendPhase(new LowTierLoweringPhase(canonicalizer));

        /*
         * Cleanup IsNull checks resulting from MID_TIER/LOW_TIER lowering and
         * ExpandLogic phase.
         */
        if (ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
        }

        appendPhase(new TornadoHalfFloatVectorOffset());

        appendPhase(new FixReadsPhase(true, new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS)));

        appendPhase(new TornadoFixedArrayCopyPhase());

        appendPhase(new AddressLoweringByNodePhase(addressLowering));

        appendPhase(new DeadCodeEliminationPhase(Required));

        appendPhase(new TornadoLoopCanonicalization());

        if (TornadoOptions.ENABLE_FMA) {
            appendPhase(new PTXFMAPhase());
        }

        if (TornadoOptions.MATH_OPTIMIZATIONS) {
            appendPhase(new InverseSquareRootPhase());
        }

        appendPhase(new InfinityReplacementPhase());

        appendPhase(new PTXFieldCoopsAccessPhase());

        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS));

        if (TornadoOptions.FEATURE_EXTRACTION) {
            appendPhase(new TornadoFeatureExtraction(tornadoDeviceContext));
        }

        if (TornadoOptions.DUMP_LOW_TIER_WITH_IGV) {
            appendPhase(new DumpLowTierGraph());
        }
    }
}
