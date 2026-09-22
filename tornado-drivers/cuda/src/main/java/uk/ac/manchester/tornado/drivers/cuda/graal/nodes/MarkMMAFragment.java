/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
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
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import tornado.graal.compiler.nodes.PiNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.ValuePhiNode;
import tornado.graal.compiler.nodes.ValueProxyNode;

/**
 * Marker for nodes whose value is an {@code mma.sync} fragment: a per-lane tuple of
 * registers that the code generator emits as a C array, not a Java array in memory.
 *
 * <p>The API hands these back as {@code float[]}, {@code int[]}, {@code HalfFloat[]} or
 * {@code byte[]} because Java has no other way to name a tuple, but nothing behind the
 * reference is addressable: there is no base pointer to offset from. Lowering therefore
 * has to recognise a {@code frag[i]} access before it becomes a read of an address, which
 * is what {@link #accumulatorFragmentOf} exists for.
 *
 * <p>Only the accumulator (C/D) fragment has elements a kernel can name: the four f32/s32
 * values {@code mma.sync} produces per lane. The A and B fragments hold operand data packed
 * into b32 lanes, so their Java element type says nothing about what an index would select;
 * they report {@code false} from {@link #isAccumulatorFragment()} and an indexed access to
 * one is rejected.
 */
public interface MarkMMAFragment {

    /** Number of elements in an accumulator fragment (m16n8 tile, four values per lane). */
    int ACCUMULATOR_FRAGMENT_LENGTH = 4;

    /**
     * @return true if this node produces an accumulator (C/D) fragment, whose elements are
     *     addressable, rather than an A/B operand fragment.
     */
    boolean isAccumulatorFragment();

    /**
     * Resolves {@code value} to the node that produced the accumulator fragment behind it,
     * looking through the Pi, proxy and phi nodes a fragment picks up when it crosses a null
     * check, a loop exit or a loop back-edge - the usual GEMM shape, where the accumulator is
     * created before the K loop, replaced by {@code mma} inside it, and read after it.
     *
     * @return one of the producing nodes, or {@code null} if {@code value} is not an
     *     accumulator fragment - which includes A/B operand fragments and ordinary arrays.
     */
    static MarkMMAFragment accumulatorFragmentOf(ValueNode value) {
        MarkMMAFragment found = null;
        for (ValueNode node : sources(value)) {
            if (!(node instanceof MarkMMAFragment fragment) || !fragment.isAccumulatorFragment()) {
                // A phi is an accumulator fragment only if every one of its inputs is.
                return null;
            }
            found = fragment;
        }
        return found;
    }

    /** @return true if {@code value} is an {@code mma.sync} fragment of any kind. */
    static boolean isFragment(ValueNode value) {
        for (ValueNode node : sources(value)) {
            if (node instanceof MarkMMAFragment) {
                return true;
            }
        }
        return false;
    }

    /**
     * The value nodes {@code value} can actually come from, with Pi, proxy and phi nodes
     * walked through. Visited nodes are recorded rather than revisited, so a phi cycle on a
     * loop back-edge terminates.
     */
    private static Set<ValueNode> sources(ValueNode value) {
        Set<ValueNode> sources = new HashSet<>();
        Set<ValueNode> seen = new HashSet<>();
        Deque<ValueNode> pending = new ArrayDeque<>();
        if (value != null) {
            pending.add(value);
        }
        while (!pending.isEmpty()) {
            ValueNode current = pending.poll();
            if (!seen.add(current)) {
                continue;
            }
            if (current instanceof PiNode pi) {
                addIfPresent(pending, pi.object());
            } else if (current instanceof ValueProxyNode proxy) {
                addIfPresent(pending, proxy.value());
            } else if (current instanceof ValuePhiNode phi) {
                for (ValueNode input : phi.values()) {
                    addIfPresent(pending, input);
                }
            } else {
                sources.add(current);
            }
        }
        return sources;
    }

    private static void addIfPresent(Deque<ValueNode> pending, ValueNode node) {
        if (node != null) {
            pending.add(node);
        }
    }
}
