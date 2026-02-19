/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.graal.compiler;

import static jdk.graal.compiler.core.common.GraalOptions.ConditionalElimination;
import static jdk.graal.compiler.core.phases.HighTier.Options.Inline;
import static jdk.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.inlining.InliningPhase;
import jdk.graal.compiler.phases.common.inlining.policy.InliningPolicy;

import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHalfFloatFixedGuardElimination;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoSketchTierContext;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoApiReplacement;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoAutoParalleliser;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoBatchFunctionAnalysis;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoDataflowAnalysis;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoFullInliningPolicy;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoKernelContextReplacement;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoNativeTypeElimination;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoNumericPromotionPhase;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoPanamaPrivateMemory;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoPartialInliningPolicy;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoReduceReplacement;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoStampResolver;

public class TornadoSketchTier extends PhaseSuite<TornadoSketchTierContext> {

    protected final CanonicalizerPhase.CustomSimplification customSimplification;

    public TornadoSketchTier(OptionValues options, CanonicalizerPhase.CustomSimplification customCanonicalizer) {
        this.customSimplification = customCanonicalizer;

        appendPhase(new TornadoNumericPromotionPhase());

        InliningPolicy inliningPolicy = (TornadoOptions.FULL_INLINING) ? new TornadoFullInliningPolicy() : new TornadoPartialInliningPolicy();

        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        appendPhase(canonicalizer);

        if (Inline.getValue(options)) {
            appendPhase(new InliningPhase(inliningPolicy, canonicalizer));
            appendPhase(new DeadCodeEliminationPhase(Optional));

            if (ConditionalElimination.getValue(options)) {
                appendPhase(canonicalizer);
                appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
            }
        }

        appendPhase(new TornadoStampResolver());
        appendPhase(new TornadoHalfFloatFixedGuardElimination());
        appendPhase(new TornadoNativeTypeElimination());
        appendPhase(new TornadoReduceReplacement());
        appendPhase(new TornadoApiReplacement());
        appendPhase(new TornadoKernelContextReplacement());
        appendPhase(new TornadoAutoParalleliser());
        appendPhase(new TornadoDataflowAnalysis());
        appendPhase(new TornadoPanamaPrivateMemory());
        appendPhase(new TornadoBatchFunctionAnalysis());
    }

}
