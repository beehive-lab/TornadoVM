/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, 2024, APT Group, Department of Computer Science,
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
import static jdk.graal.compiler.core.common.GraalOptions.OptConvertDeoptsToGuards;
import static jdk.graal.compiler.core.common.GraalOptions.PartialEscapeAnalysis;
import static jdk.graal.compiler.core.phases.HighTier.Options.Inline;
import static jdk.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.loop.phases.LoopFullUnrollPhase;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.RemoveValueProxyPhase;
import jdk.graal.compiler.phases.common.inlining.InliningPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;

import jdk.vm.ci.meta.MetaAccessProvider;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.analysis.TornadoShapeAnalysis;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.guards.ExceptionSuppression;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.guards.TornadoValueTypeCleanup;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.memalloc.TornadoLocalMemoryAllocation;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.memalloc.TornadoNewArrayDevirtualizationReplacement;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.memalloc.TornadoPrivateArrayPiRemoval;
import uk.ac.manchester.tornado.drivers.ptx.graal.phases.TornadoBatchGlobalIndexOffset;
import uk.ac.manchester.tornado.drivers.ptx.graal.phases.TornadoHalfFloatReplacement;
import uk.ac.manchester.tornado.drivers.ptx.graal.phases.TornadoPTXIntrinsicsReplacements;
import uk.ac.manchester.tornado.drivers.ptx.graal.phases.TornadoParallelScheduler;
import uk.ac.manchester.tornado.drivers.ptx.graal.phases.TornadoTaskSpecialisation;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoHighTier;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoInliningPolicy;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoFullInliningPolicy;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoPartialInliningPolicy;

public class PTXHighTier extends TornadoHighTier {

    public PTXHighTier(OptionValues options, CanonicalizerPhase.CustomSimplification customCanonicalizer, MetaAccessProvider metaAccessProvider) {
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
        appendPhase(new TornadoBatchGlobalIndexOffset());
        appendPhase(canonicalizer);
        appendPhase(new DeadCodeEliminationPhase(Optional));

        appendPhase(canonicalizer);

        appendPhase(new TornadoNewArrayDevirtualizationReplacement());

        appendPhase(new TornadoHalfFloatReplacement());

        if (PartialEscapeAnalysis.getValue(options)) {
            appendPhase(new PartialEscapePhase(true, canonicalizer, options));
        }

        appendPhase(new TornadoPrivateArrayPiRemoval());

        appendPhase(new TornadoValueTypeCleanup());

        if (OptConvertDeoptsToGuards.getValue(options)) {
            appendPhase(new ConvertDeoptimizeToGuardPhase(canonicalizer));
        }

        appendPhase(new TornadoShapeAnalysis());
        appendPhase(canonicalizer);
        appendPhase(new TornadoParallelScheduler());
        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST));
        appendPhase(new DisableOverflownCountedLoopsPhase());


        LoopPolicies loopPolicies = new DefaultLoopPolicies();
        appendPhase(new LoopFullUnrollPhase(canonicalizer, loopPolicies));

        appendPhase(canonicalizer);
        appendPhase(new RemoveValueProxyPhase(canonicalizer));
        appendPhase(canonicalizer);
        appendPhase(new DeadCodeEliminationPhase(Optional));

        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST));
        appendPhase(new HighTierLoweringPhase(canonicalizer));

        // After the first Lowering, Tornado replaces reductions with snippets
        // that contains method calls to barriers.
        appendPhase(new TornadoPTXIntrinsicsReplacements(metaAccessProvider));

        appendPhase(new TornadoLocalMemoryAllocation());

        appendPhase(new ExceptionSuppression());
    }

    private CanonicalizerPhase createCanonicalizerPhase(OptionValues options, CanonicalizerPhase.CustomSimplification customCanonicalizer) {
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        return canonicalizer.copyWithCustomSimplification(customCanonicalizer);
    }
}
