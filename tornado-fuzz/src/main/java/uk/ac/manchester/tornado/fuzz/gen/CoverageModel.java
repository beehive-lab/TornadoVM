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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Novelty model that turns the fuzzer's blind random generation into
 * coverage-guided generation (the cuFuzz idea, applied to the code generator
 * rather than the running kernel).
 *
 * <p>Each generated expression is decomposed into codegen-relevant "features":
 * the operator category at every node, and every parent&rarr;child operator
 * bigram (which maps closely to a distinct CUDA code-generation path, e.g. a
 * right-shift of a multiply lowers differently from a right-shift of a leaf).
 * The model counts how often each category has been produced so far and boosts
 * the generation weight of under-covered categories, so the grammar keeps
 * exploring rare op combinations instead of drowning in common ones. The set of
 * distinct bigrams is the coverage metric reported per run.
 */
public final class CoverageModel {

    /** Operator categories the grammar can emit, in the order the generator branches on them. */
    public static final String[] CATEGORIES = { "ARITH", "DIV", "SHIFT", "HIGHMUL", "UNARY" };

    /** Baseline weights preserving the fragile-op bias (div/shift/highmul favored over plain arith). */
    private static final Map<String, Integer> BASE = Map.of("ARITH", 30, "DIV", 25, "SHIFT", 20, "HIGHMUL", 13, "UNARY", 12);

    private final Map<String, Integer> catCounts = new HashMap<>();
    private final Set<String> bigrams = new HashSet<>();
    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Baseline-times-novelty weight for a category: rarer categories are boosted. */
    public int weightFor(String category) {
        int base = BASE.getOrDefault(category, 10);
        if (!enabled) {
            return base;
        }
        int count = catCounts.getOrDefault(category, 0);
        int max = 0;
        for (String c : CATEGORIES) {
            max = Math.max(max, catCounts.getOrDefault(c, 0));
        }
        // Novelty factor in [1, base]: an unseen category gets the full boost; the most-seen gets none.
        int novelty = 1 + (max - count);
        return base * novelty;
    }

    public int[] categoryWeights() {
        int[] w = new int[CATEGORIES.length];
        for (int i = 0; i < CATEGORIES.length; i++) {
            w[i] = weightFor(CATEGORIES[i]);
        }
        return w;
    }

    /** Record the features of a generated expression tree. */
    public void observe(Expr root) {
        walk(root, "ROOT");
    }

    private void walk(Expr node, String parentCat) {
        String cat = node.category();
        if (cat != null) {
            catCounts.merge(cat, 1, Integer::sum);
            bigrams.add(parentCat + ">" + cat);
        }
        String childParent = cat != null ? cat : parentCat;
        for (Expr child : node.children()) {
            walk(child, childParent);
        }
    }

    /** Number of distinct operator bigrams seen so far — the coverage metric. */
    public int coverage() {
        return bigrams.size();
    }

    public Map<String, Integer> categoryCounts() {
        return new HashMap<>(catCounts);
    }
}
