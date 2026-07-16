/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.fuzz.gen;

import java.util.ArrayList;
import java.util.List;

/**
 * Delta-debugging shrinker for a failing {@link Expr}. Greedily replaces the
 * whole tree (or a subtree) with a smaller variant — a child, or a bare leaf —
 * keeping any variant that still reproduces the failure, until no smaller variant
 * reproduces. Turns a deep generated expression into the minimal one that still
 * exposes the codegen bug.
 */
public final class Shrinker {

    /** Returns true if the candidate expression still reproduces the failure (mismatch or exception). */
    public interface Reproducer {
        boolean fails(Expr candidate);
    }

    private static final int CANDIDATE_CAP = 128;

    private Shrinker() {
    }

    /**
     * @param root   the failing expression
     * @param repro  re-runs a candidate on the CUDA device + JVM reference
     * @param budget maximum number of device re-runs to spend
     */
    public static Expr shrink(Expr root, Reproducer repro, int budget) {
        Expr best = root;
        int used = 0;
        boolean improved = true;
        while (improved && used < budget) {
            improved = false;
            for (Expr cand : candidates(best)) {
                if (cand.nodeCount() >= best.nodeCount()) {
                    continue;
                }
                if (used >= budget) {
                    break;
                }
                used++;
                if (repro.fails(cand)) {
                    best = cand;
                    improved = true;
                    break;
                }
            }
        }
        return best;
    }

    /** One-edit simplifications of {@code e}: collapse to a child, to a leaf, or simplify one child. */
    private static List<Expr> candidates(Expr e) {
        List<Expr> out = new ArrayList<>();
        // Collapse the whole node to one of its children.
        out.addAll(e.children());
        // Collapse the whole node to a bare leaf.
        out.add(Expr.leafA());
        out.add(Expr.leafB());
        out.add(Expr.constant(0));
        out.add(Expr.constant(1));
        // Deep edits: replace exactly one child with one of its own simplifications.
        List<Expr> kids = e.children();
        for (int i = 0; i < kids.size() && out.size() < CANDIDATE_CAP; i++) {
            for (Expr rc : candidates(kids.get(i))) {
                if (out.size() >= CANDIDATE_CAP) {
                    break;
                }
                List<Expr> newKids = new ArrayList<>(kids);
                newKids.set(i, rc);
                out.add(e.withChildren(newKids));
            }
        }
        return out;
    }
}
