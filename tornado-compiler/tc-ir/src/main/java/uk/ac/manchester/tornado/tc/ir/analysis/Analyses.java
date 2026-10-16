/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
