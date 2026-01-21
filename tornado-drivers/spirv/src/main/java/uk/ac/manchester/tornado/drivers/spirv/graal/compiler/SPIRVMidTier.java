/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
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
import static jdk.graal.compiler.core.common.GraalOptions.OptFloatingReads;
import static jdk.graal.compiler.core.common.GraalOptions.ReassociateExpressions;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.FrameStateAssignmentPhase;
import jdk.graal.compiler.phases.common.GuardLoweringPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.MidTierLoweringPhase;
import jdk.graal.compiler.phases.common.ReassociationPhase;
import jdk.graal.compiler.phases.common.RemoveValueProxyPhase;

import uk.ac.manchester.tornado.drivers.common.compiler.phases.guards.BoundCheckEliminationPhase;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.guards.ExceptionCheckingElimination;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.loops.TornadoPartialLoopUnrollPhase;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.memalloc.TornadoPanamaSegmentsHeaderPhase;
import uk.ac.manchester.tornado.drivers.spirv.graal.phases.TornadoFloatingReadReplacement;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoMidTier;

/**
 * SPIR-V backend reuses from the OCL the following phases:
 *
 * - TornadoPanamaSegmentsHeaderPhase
 *
 * - BoundCheckEliminationPhase
 *
 * - TornadoFloatingReadReplacement
 *
 * - TornadoPartialLoopUnroll
 */
public class SPIRVMidTier extends TornadoMidTier {

    public SPIRVMidTier(OptionValues options) {

        appendPhase(new TornadoPanamaSegmentsHeaderPhase());

        appendPhase(new ExceptionCheckingElimination());

        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();

        appendPhase(canonicalizer);

        appendPhase((new BoundCheckEliminationPhase()));
        appendPhase(new ExceptionCheckingElimination());

        if (OptFloatingReads.getValue(options)) {
            appendPhase(new TornadoFloatingReadReplacement(canonicalizer));
        }

        appendPhase(canonicalizer);

        if (ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, true));
        }

        appendPhase(new RemoveValueProxyPhase(canonicalizer));

        appendPhase(new GuardLoweringPhase());

        appendPhase(canonicalizer);

        if (TornadoOptions.isPartialUnrollEnabled()) {
            appendPhase(new TornadoPartialLoopUnrollPhase());
        }

        appendPhase(new MidTierLoweringPhase(canonicalizer));

        appendPhase(new FrameStateAssignmentPhase());

        if (ReassociateExpressions.getValue(options)) {
            appendPhase(new ReassociationPhase(canonicalizer));
        }

        appendPhase(canonicalizer);
    }
}
