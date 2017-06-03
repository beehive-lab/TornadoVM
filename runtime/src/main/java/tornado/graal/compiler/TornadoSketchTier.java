/* 
 * Copyright 2012 James Clarkson.
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
 */
package tornado.graal.compiler;

import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.common.IterativeConditionalEliminationPhase;
import com.oracle.graal.phases.common.inlining.InliningPhase;
import tornado.graal.phases.TornadoApiReplacement;
import tornado.graal.phases.TornadoDataflowAnalysis;
import tornado.graal.phases.TornadoInliningPolicy;
import tornado.graal.phases.TornadoSketchTierContext;

import static com.oracle.graal.compiler.common.GraalOptions.ConditionalElimination;
import static com.oracle.graal.compiler.common.GraalOptions.ImmutableCode;
import static com.oracle.graal.compiler.phases.HighTier.Options.Inline;
import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

public class TornadoSketchTier extends PhaseSuite<TornadoSketchTierContext> {

    protected final CustomCanonicalizer customCanonicalizer;

    public CustomCanonicalizer getCustomCanonicalizer() {
        return customCanonicalizer;
    }

    public TornadoSketchTier(CustomCanonicalizer customCanonicalizer) {
        this.customCanonicalizer = customCanonicalizer;

        final CanonicalizerPhase canonicalizer = new CanonicalizerPhase(customCanonicalizer);

        if (ImmutableCode.getValue()) {
            canonicalizer.disableReadCanonicalization();
        }
        appendPhase(canonicalizer);

        if (Inline.getValue()) {
            appendPhase(new InliningPhase(new TornadoInliningPolicy(), canonicalizer));
            appendPhase(new DeadCodeEliminationPhase(Optional));

            if (ConditionalElimination.getValue()) {
                appendPhase(canonicalizer);
                appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
            }
        }

        appendPhase(new TornadoApiReplacement());
        appendPhase(new TornadoDataflowAnalysis());
    }
}
