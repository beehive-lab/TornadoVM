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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.Function;

/**
 * The natural loops of a function and their nesting (reducible control flow). A loop is identified by
 * its header: a block that dominates the source of one of its incoming edges (a back edge). Its body
 * is the header plus every block that reaches a back-edge source without passing through the header.
 */
public final class LoopForest {

    /** One natural loop. {@code depth} is 1 for an outermost loop. */
    public static final class Loop {
        private final Block header;
        private final Set<Block> body = Collections.newSetFromMap(new IdentityHashMap<>());
        private final List<Block> latches = new ArrayList<>();
        private Loop parent;

        Loop(Block header) {
            this.header = header;
        }

        public Block header() {
            return header;
        }

        public boolean contains(Block block) {
            return body.contains(block);
        }

        public Set<Block> body() {
            return Collections.unmodifiableSet(body);
        }

        /** Blocks inside the loop with an edge back to the header. */
        public List<Block> latches() {
            return Collections.unmodifiableList(latches);
        }

        public Optional<Loop> parent() {
            return Optional.ofNullable(parent);
        }

        public int depth() {
            return parent == null ? 1 : parent.depth() + 1;
        }
    }

    private final Map<Block, Loop> byHeader = new IdentityHashMap<>();

    public LoopForest(Function function) {
        this(function, new DominatorTree(function));
    }

    public LoopForest(Function function, DominatorTree dominators) {
        var preds = function.predecessors();
        for (var block : dominators.reversePostorder()) {
            for (var successor : block.successors()) {
                if (dominators.dominates(successor, block)) {
                    var loop = byHeader.computeIfAbsent(successor, Loop::new);
                    if (!loop.latches.contains(block)) {
                        loop.latches.add(block);
                    }
                }
            }
        }
        for (var loop : byHeader.values()) {
            loop.body.add(loop.header);
            var work = new ArrayDeque<Block>();
            for (var latch : loop.latches) {
                if (loop.body.add(latch)) {
                    work.push(latch);
                }
            }
            while (!work.isEmpty()) {
                for (var pred : preds.get(work.pop())) {
                    if (dominators.isReachable(pred) && loop.body.add(pred)) {
                        work.push(pred);
                    }
                }
            }
        }
        // The parent of a loop is the smallest other loop whose body contains its header.
        for (var loop : byHeader.values()) {
            for (var other : byHeader.values()) {
                if (other != loop && other.body.contains(loop.header) && (loop.parent == null || loop.parent.body.size() > other.body.size())) {
                    loop.parent = other;
                }
            }
        }
    }

    public Optional<Loop> loopWithHeader(Block header) {
        return Optional.ofNullable(byHeader.get(header));
    }

    public List<Loop> loops() {
        return List.copyOf(byHeader.values());
    }
}
