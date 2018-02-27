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
import static org.graalvm.compiler.core.common.GraalOptions.OptFloatingReads;
import static org.graalvm.compiler.core.common.GraalOptions.ReassociateInvariants;

import org.graalvm.compiler.loop.phases.ReassociateInvariantPhase;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.FrameStateAssignmentPhase;
import org.graalvm.compiler.phases.common.GuardLoweringPhase;
import org.graalvm.compiler.phases.common.IncrementalCanonicalizerPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;

import uk.ac.manchester.tornado.graal.compiler.TornadoMidTier;
import uk.ac.manchester.tornado.graal.phases.ExceptionCheckingElimination;
import uk.ac.manchester.tornado.graal.phases.TornadoMemoryPhiElimination;

public class OCLMidTier extends TornadoMidTier {

    public OCLMidTier(OptionValues options) {

        appendPhase(new ExceptionCheckingElimination());

        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        if (ImmutableCode.getValue(options)) {
            canonicalizer.disableReadCanonicalization();
        }

//        if (OptCanonicalizer.getValue()) {
        appendPhase(canonicalizer);
//        }

        //if(!OpenCLTornadoBackend.ENABLE_EXCEPTIONS)
        appendPhase(new ExceptionCheckingElimination());

        // appendPhase(new LockEliminationPhase());
        if (OptFloatingReads.getValue(options)) {
            appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new FloatingReadPhase()));
        }

//        if (OptReadElimination.getValue(options)) {
//            appendPhase(new EarlyReadEliminationPhase(canonicalizer));
//        }
        appendPhase(new TornadoMemoryPhiElimination());
        appendPhase(new RemoveValueProxyPhase());

//        if (OptCanonicalizer.getValue()) {
        appendPhase(canonicalizer);
//        }

        if (ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, true));
        }

        appendPhase(new GuardLoweringPhase());

//        if (OptCanonicalizer.getValue()) {
        appendPhase(canonicalizer);
//        }

//TODO disable as it introduces loop limit checks
//        appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new LoopSafepointEliminationPhase()));
        //appendPhase(new LoopSafepointInsertionPhase());
//        appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new GuardLoweringPhase()));
        //if (VerifyHeapAtReturn.getValue()) {
        //    appendPhase(new VerifyHeapAtReturnPhase());
        //}
        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.MID_TIER));

        appendPhase(new FrameStateAssignmentPhase());

        if (ReassociateInvariants.getValue(options)) {
            appendPhase(new ReassociateInvariantPhase());
        }

        //appendPhase(new TornadoIfCanonicalization());
        appendPhase(canonicalizer);
    }
}
