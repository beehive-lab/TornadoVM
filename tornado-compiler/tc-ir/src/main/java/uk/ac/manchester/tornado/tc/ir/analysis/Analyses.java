/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.tc.ir.analysis;

import uk.ac.manchester.tornado.tc.ir.Function;

/**
 * The analyses of a function's control-flow graph, computed once and reused until the CFG changes
 * ({@link Function#cfgAnalysis}): passes, the verifier and the structurizer ask here instead of
 * rebuilding dominators and loops themselves.
 */
public final class Analyses {

    private Analyses() {
    }

    public static DominatorTree dominators(Function function) {
        return function.cfgAnalysis(DominatorTree.class, DominatorTree::new);
    }

    public static LoopForest loops(Function function) {
        return function.cfgAnalysis(LoopForest.class, f -> new LoopForest(f, dominators(f)));
    }
}
