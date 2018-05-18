/*
 * Copyright (c) 2018, APT Group, School of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.graal.compiler;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;

import uk.ac.manchester.tornado.graal.phases.*;

import static org.graalvm.compiler.core.common.GraalOptions.ConditionalElimination;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.core.phases.HighTier.Options.Inline;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

public class TornadoSketchTier extends PhaseSuite<TornadoSketchTierContext> {

    protected final CustomCanonicalizer customCanonicalizer;

    public CustomCanonicalizer getCustomCanonicalizer() {
        return customCanonicalizer;
    }

    public TornadoSketchTier(OptionValues options, CustomCanonicalizer customCanonicalizer) {
        this.customCanonicalizer = customCanonicalizer;

        final CanonicalizerPhase canonicalizer = new CanonicalizerPhase(customCanonicalizer);

        if (ImmutableCode.getValue(options)) {
            canonicalizer.disableReadCanonicalization();
        }
        appendPhase(canonicalizer);

        if (Inline.getValue(options)) {
            appendPhase(new InliningPhase(new TornadoInliningPolicy(), canonicalizer));
            appendPhase(new DeadCodeEliminationPhase(Optional));

            if (ConditionalElimination.getValue(options)) {
                appendPhase(canonicalizer);
                appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
            }
        }

        appendPhase(new TornadoStampResolver());

        appendPhase(new TornadoReduceReplacement());

        appendPhase(new TornadoApiReplacement());
        appendPhase(new TornadoAutoParalleliser());
        appendPhase(new TornadoDataflowAnalysis());
    }
}
