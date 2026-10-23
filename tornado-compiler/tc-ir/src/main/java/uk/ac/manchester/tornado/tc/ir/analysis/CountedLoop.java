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

import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalLong;

import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.BlockArg;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.IntPredicate;
import uk.ac.manchester.tornado.tc.ir.Value;

/**
 * A loop counting one induction variable: a single entry edge, a single back edge whose argument for
 * the variable is {@code iv + step} with a loop-invariant step, and, when the header tests it, the
 * exit compare. Every pass that needs a counted loop (parallel-loop lowering, reductions, full
 * unrolling, batch offsets) asks this analysis instead of matching the shape itself.
 *
 * @param loop      the loop
 * @param iv        the induction variable, a header argument
 * @param entry     the one edge into the header from outside the loop
 * @param backEdge  the one edge from the loop back to the header
 * @param increment {@code iv + step}, the back edge's argument for {@code iv}
 * @param step      the loop-invariant step
 * @param exit      the header's exit test on {@code iv}, if the header ends with one
 */
public record CountedLoop(LoopForest.Loop loop, BlockArg iv, Edge entry, Edge backEdge, Inst increment, Value step, Optional<Exit> exit) implements LoopShape {

    /** One control-flow edge: successor {@code successor} of {@code terminator}. */
    public record Edge(Inst terminator, int successor) {

        public Block target() {
            return terminator.successors().get(successor).target();
        }

        /** The value the edge passes for the target's argument {@code index}. */
        public Value argument(int index) {
            return terminator.successors().get(successor).args().get(index);
        }

        public void setArgument(int index, Value value) {
            var args = new ArrayList<>(terminator.successors().get(successor).args());
            args.set(index, value);
            terminator.setSuccessorArgs(successor, args);
        }
    }

    /**
     * The header's {@code cond_br (icmp predicate iv, bound)}; successor {@code stay} stays in the loop.
     */
    public record Exit(Inst branch, int stay, Inst compare, IntPredicate predicate, Value bound) {
    }

    /** The counted loop with induction variable {@code header.args().get(ivIndex)}, if the loop has that shape. */
    public static Optional<CountedLoop> of(LoopForest.Loop loop, int ivIndex) {
        return analyse(loop, ivIndex) instanceof CountedLoop c ? Optional.of(c) : Optional.empty();
    }

    /** As {@link #of(LoopForest.Loop, int)}, or the {@link Failure} that rules it out. */
    public static LoopShape analyse(LoopForest.Loop loop, int ivIndex) {
        var header = loop.header();
        if (ivIndex < 0 || ivIndex >= header.args().size()) {
            return new LoopShape.NotCounted(LoopShape.Failure.INCREMENT);
        }
        var iv = header.args().get(ivIndex);
        Edge entry = null;
        Edge back = null;
        for (var pred : header.function().predecessors().get(header)) {
            var t = pred.terminator().orElseThrow();
            for (int s = 0; s < t.successors().size(); s++) {
                if (t.successors().get(s).target() != header) {
                    continue;
                }
                if (loop.contains(pred)) {
                    if (back != null) {
                        return new LoopShape.NotCounted(LoopShape.Failure.EDGES);
                    }
                    back = new Edge(t, s);
                } else {
                    if (entry != null) {
                        return new LoopShape.NotCounted(LoopShape.Failure.EDGES);
                    }
                    entry = new Edge(t, s);
                }
            }
        }
        if (entry == null || back == null) {
            return new LoopShape.NotCounted(LoopShape.Failure.EDGES);
        }
        if (!(back.argument(ivIndex) instanceof Inst add) || !(add.opcode() instanceof Opcode.Binary(var op)) || op != Opcode.BinaryOp.ADD || (add.operand(0) != iv && add.operand(1) != iv)) {
            return new LoopShape.NotCounted(LoopShape.Failure.INCREMENT);
        }
        var step = add.operand(0) == iv ? add.operand(1) : add.operand(0);
        if (!isInvariant(step, loop)) {
            return new LoopShape.NotCounted(LoopShape.Failure.STEP);
        }
        return new CountedLoop(loop, iv, entry, back, add, step, exit(loop, iv));
    }

    /** The counted loop whose induction variable is the one the header's exit test compares. */
    public static Optional<CountedLoop> of(LoopForest.Loop loop) {
        var term = loop.header().terminator().orElse(null);
        if (term != null && term.opcode() instanceof Opcode.CondBranch && term.operand(0) instanceof Inst cmp && cmp.opcode() instanceof Opcode.ICmp
                && cmp.operand(0) instanceof BlockArg iv && iv.block() == loop.header()) {
            return of(loop, loop.header().args().indexOf(iv));
        }
        return Optional.empty();
    }

    private static Optional<Exit> exit(LoopForest.Loop loop, BlockArg iv) {
        var term = loop.header().terminator().orElse(null);
        if (term == null || !(term.opcode() instanceof Opcode.CondBranch) || !(term.operand(0) instanceof Inst cmp) || !(cmp.opcode() instanceof Opcode.ICmp(var predicate))
                || cmp.operand(0) != iv) {
            return Optional.empty();
        }
        boolean first = loop.contains(term.successors().get(0).target());
        boolean second = loop.contains(term.successors().get(1).target());
        if (first == second) {
            return Optional.empty();
        }
        return Optional.of(new Exit(term, first ? 0 : 1, cmp, predicate, cmp.operand(1)));
    }

    /** A value defined outside the loop, or a constant. */
    public static boolean isInvariant(Value v, LoopForest.Loop loop) {
        return switch (v) {
            case Inst i -> i.opcode() instanceof Opcode.ConstInt || i.block().map(b -> !loop.contains(b)).orElse(false);
            case BlockArg a -> !loop.contains(a.block());
        };
    }

    public Block header() {
        return loop.header();
    }

    public int ivIndex() {
        return header().args().indexOf(iv);
    }

    /** The initial value, passed on the entry edge. */
    public Value init() {
        return entry.argument(ivIndex());
    }

    public OptionalLong constantInit() {
        return Constants.integer(init());
    }

    public OptionalLong constantStep() {
        return Constants.integer(step);
    }

    public OptionalLong constantBound() {
        return exit.map(e -> Constants.integer(e.bound())).orElse(OptionalLong.empty());
    }

    /**
     * The exclusive upper bound of a loop javac compiled from {@code i < upper} or {@code i <= last}:
     * it tests the exit condition, {@code i >= upper} (or {@code i > last}, upper {@code last + 1}).
     */
    public OptionalLong exclusiveUpper() {
        var bound = constantBound();
        if (bound.isEmpty()) {
            return OptionalLong.empty();
        }
        return switch (exit.orElseThrow().predicate()) {
            case SGE, SLT -> bound;
            case SGT, SLE -> OptionalLong.of(bound.getAsLong() + 1);
            default -> OptionalLong.empty();
        };
    }

    /** Whether {@code inst} is the loop's own control: its exit compare or its increment. */
    public boolean controls(Inst inst) {
        return inst == increment || exit.map(e -> e.compare() == inst).orElse(false);
    }
}
