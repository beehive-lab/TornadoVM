/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.compiler;

import static org.graalvm.compiler.core.common.GraalOptions.ConditionalElimination;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.core.common.GraalOptions.OptConvertDeoptsToGuards;
import static org.graalvm.compiler.core.common.GraalOptions.PartialEscapeAnalysis;
import static org.graalvm.compiler.core.phases.HighTier.Options.Inline;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import org.graalvm.compiler.loop.DefaultLoopPolicies;
import org.graalvm.compiler.loop.LoopPolicies;
import org.graalvm.compiler.loop.phases.LoopFullUnrollPhase;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import org.graalvm.compiler.phases.common.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;

import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoParallelScheduler;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoTaskSpecialisation;
import uk.ac.manchester.tornado.graal.compiler.TornadoHighTier;
import uk.ac.manchester.tornado.graal.phases.ExceptionSuppression;
import uk.ac.manchester.tornado.graal.phases.TornadoInliningPolicy;
import uk.ac.manchester.tornado.graal.phases.TornadoShapeAnalysis;
import uk.ac.manchester.tornado.graal.phases.TornadoValueTypeCleanup;

public class OCLHighTier extends TornadoHighTier {

    public OCLHighTier(OptionValues options, CustomCanonicalizer customCanonicalizer) {
        super(customCanonicalizer);

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

        appendPhase(new TornadoTaskSpecialisation(canonicalizer));
//        appendPhase(new TornadoVectorResolver());
        appendPhase(canonicalizer);
        appendPhase(new DeadCodeEliminationPhase(Optional));

        appendPhase(canonicalizer);

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

        // possibly not needed - one schedule phase is required but not sure on its placement
        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST));

        LoopPolicies loopPolicies = new DefaultLoopPolicies();
        appendPhase(new LoopFullUnrollPhase(canonicalizer, loopPolicies));
        appendPhase(new RemoveValueProxyPhase());
        appendPhase(canonicalizer);
        appendPhase(new DeadCodeEliminationPhase(Optional));

        // possibly not needed - one schedule phase is required but not sure on its placement
        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST));
        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER));
        appendPhase(new ExceptionSuppression());
    }
}
