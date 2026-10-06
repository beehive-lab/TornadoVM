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
package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import tornado.graal.compiler.graph.Node;
import tornado.graal.compiler.nodes.loop.DefaultLoopPolicies;
import tornado.graal.compiler.nodes.loop.LoopEx;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileNode;

/**
 * Loop policies of the CUDA backend: a loop that contains a CUDA Tile operation is never fully
 * unrolled.
 *
 * <p>
 * Task specialisation turns the k-loop of a tile kernel ({@code for k: acc = mma(load A, load B, acc)})
 * into a loop with a constant trip count, which Graal fully unrolls whenever it fits the node budget
 * (up to 64 steps for a GEMM body). The CUDA Tile compiler pipelines the loads of a rolled loop itself;
 * the unrolled straight-line form takes tens of seconds to compile (past the tile compiler timeout
 * for 128x128 tiles at n = 2048) and runs 2-15x slower on sm_120.
 * </p>
 */
public class CUDATileLoopPolicies extends DefaultLoopPolicies {

    public static boolean isTileOperation(Node node) {
        return node instanceof CUDATileNode;
    }

    public static boolean containsTileOperation(LoopEx loop) {
        for (Node node : loop.inside().nodes()) {
            if (isTileOperation(node)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean shouldFullUnroll(LoopEx loop) {
        return !containsTileOperation(loop) && super.shouldFullUnroll(loop);
    }
}
