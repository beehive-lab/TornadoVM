/*
 * Copyright (c) 2020, 2024, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.compiler;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.loop.phases.LoopFullUnrollPhase;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.inlining.InliningPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.analysis.TornadoShapeAnalysis;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.guards.ExceptionSuppression;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.guards.TornadoValueTypeCleanup;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.memalloc.TornadoFieldAccessFixup;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.memalloc.TornadoLocalMemoryAllocation;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.memalloc.TornadoNewArrayDevirtualizationReplacement;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.memalloc.TornadoPrivateArrayPiRemoval;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoBatchGlobalIndexOffset;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoHalfFloatReplacement;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoOpenCLIntrinsicsReplacements;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoParallelScheduler;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoTaskSpecialisation;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoHighTier;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoInliningPolicy;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoFullInliningPolicy;
import uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoPartialInliningPolicy;

import static jdk.graal.compiler.core.common.GraalOptions.ConditionalElimination;
import static jdk.graal.compiler.core.common.GraalOptions.OptConvertDeoptsToGuards;
import static jdk.graal.compiler.core.common.GraalOptions.PartialEscapeAnalysis;
import static jdk.graal.compiler.core.phases.HighTier.Options.Inline;
import static jdk.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

public class OCLHighTier extends TornadoHighTier {

    public OCLHighTier(OptionValues options, TornadoDeviceContext deviceContext, CanonicalizerPhase.CustomSimplification customCanonicalizer, MetaAccessProvider metaAccessProvider) {
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
        appendPhase(new TornadoFieldAccessFixup());
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

        if (!deviceContext.isPlatformFPGA()) {
            LoopPolicies loopPolicies = new DefaultLoopPolicies();
            appendPhase(new LoopFullUnrollPhase(canonicalizer, loopPolicies));
        }

        appendPhase(canonicalizer);
        appendPhase(new DeadCodeEliminationPhase(Optional));

        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST));

        appendPhase(new HighTierLoweringPhase(canonicalizer));

        // After the first Lowering, TornadoVM replaces reductions with snippets
        // that contains method calls to barriers.

        appendPhase(new TornadoOpenCLIntrinsicsReplacements(metaAccessProvider));

        appendPhase(new TornadoLocalMemoryAllocation());

        appendPhase(new ExceptionSuppression());
    }

    private CanonicalizerPhase createCanonicalizerPhase(OptionValues options, CanonicalizerPhase.CustomSimplification customCanonicalizer) {
        CanonicalizerPhase canonicalizerPhase = CanonicalizerPhase.create();
        return canonicalizerPhase.copyWithCustomSimplification(customCanonicalizer);

    }
}
