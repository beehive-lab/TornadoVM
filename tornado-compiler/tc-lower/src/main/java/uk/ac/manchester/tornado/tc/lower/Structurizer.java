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
package uk.ac.manchester.tornado.tc.lower;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.BlockArg;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrPrinter;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Successor;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.DominatorTree;

/**
 * Translates TC-SSA into TC-S: out-of-SSA translation plus control-flow structuring.
 *
 * <p><b>Structuring</b> follows the dominator-tree method of N. Ramsey, "Beyond Relooper: Recursive
 * Translation of Unstructured Control Flow to Structured Control Flow" (ICFP 2022). A block is
 * emitted inside the code of its immediate dominator. A child with two or more forward in-edges (a
 * merge node) is emitted after a {@link S.Labelled} block, and edges to it become breaks out of that
 * block; edges back to a loop header become continues. TC refinement: the children of a loop header
 * that lie outside its natural loop (loop exits) are placed after the {@link S.Loop}, so leaving a
 * loop is a plain break. Irreducible control flow is rejected with {@code TC-E3001}.</p>
 *
 * <p><b>Out of SSA</b>: block arguments become variables, assigned on each incoming edge as a
 * parallel copy (sequenced through temporaries when the copies interfere). Constants, allocation
 * addresses and pure single-use values in the same block are folded into expression trees; every
 * other value gets a variable assigned at its definition.</p>
 */
public final class Structurizer {

    /** Thrown when the control flow cannot be structured. */
    public static final class StructuringException extends RuntimeException {
        private final String code;

        StructuringException(String code, String message) {
            super(code + ": " + message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final Function function;
    private final DominatorTree dominators;
    private final IrPrinter names = new IrPrinter(false);
    private final Map<Block, Integer> rpo = new IdentityHashMap<>();
    private final Map<Block, Integer> forwardInEdges = new IdentityHashMap<>();
    private final Map<Block, Set<Block>> loops = new IdentityHashMap<>();
    private final Map<Value, S.Var> vars = new IdentityHashMap<>();
    private final Set<String> usedNames = new HashSet<>();
    private final List<S.Var> locals = new ArrayList<>();
    private final List<S.Alloca> allocas = new ArrayList<>();
    private final Map<Inst, S.Alloca> allocaByInst = new IdentityHashMap<>();
    private int temporaries;

    private Structurizer(Function function) {
        this.function = function;
        this.dominators = new DominatorTree(function);
        names.printFunction(function);
    }

    public static S.Kernel structure(Function function) {
        return new Structurizer(function).run();
    }

    private S.Kernel run() {
        var order = dominators.reversePostorder();
        for (int i = 0; i < order.size(); i++) {
            rpo.put(order.get(i), i);
        }
        analyseEdges(order);
        var params = new ArrayList<S.Var>();
        for (var param : function.params()) {
            params.add(variable(param));
        }
        for (var block : order) {
            for (var arg : block.args()) {
                if (block != function.entry()) {
                    locals.add(variable(arg));
                }
            }
            for (var inst : block.insts()) {
                if (inst.opcode() instanceof Opcode.Alloca) {
                    var alloca = new S.Alloca(inst, unique(cName(names.name(inst))));
                    allocas.add(alloca);
                    allocaByInst.put(inst, alloca);
                } else if (inst.producesValue() && !isInline(inst)) {
                    locals.add(variable(inst));
                }
            }
        }
        var body = doTree(function.entry());
        var simplified = Simplifier.simplify(body);
        return new S.Kernel(function, params, locals, allocas, simplified);
    }

    // ---------------------------------------------------------------- CFG facts

    private void analyseEdges(List<Block> order) {
        for (var block : order) {
            forwardInEdges.put(block, 0);
        }
        for (var block : order) {
            for (var target : block.successors()) {
                if (dominators.dominates(target, block)) {
                    loops.computeIfAbsent(target, _k -> Collections.newSetFromMap(new IdentityHashMap<>()));
                } else if (rpo.get(target) <= rpo.get(block)) {
                    throw new StructuringException("TC-E3001", "irreducible control flow in @" + function.name() + ": edge ^" + names.blockName(block) + " -> ^"
                            + names.blockName(target) + " re-enters a loop without passing its header");
                } else {
                    forwardInEdges.merge(target, 1, Integer::sum);
                }
            }
        }
        var preds = function.predecessors();
        for (var header : loops.keySet()) {
            var body = loops.get(header);
            body.add(header);
            var work = new ArrayDeque<Block>();
            for (var pred : preds.get(header)) {
                if (dominators.dominates(header, pred) && body.add(pred)) {
                    work.push(pred);
                }
            }
            while (!work.isEmpty()) {
                for (var pred : preds.get(work.pop())) {
                    if (dominators.isReachable(pred) && body.add(pred)) {
                        work.push(pred);
                    }
                }
            }
        }
    }

    private boolean isLoopHeader(Block block) {
        return loops.containsKey(block);
    }

    private boolean inLoop(Block header, Block block) {
        return loops.get(header).contains(block);
    }

    /** A block emitted after a labelled block (its edges become breaks) rather than inline. */
    private boolean isPlaced(Block block) {
        var parent = dominators.immediateDominator(block);
        if (parent.isEmpty()) {
            return false;
        }
        return forwardInEdges.get(block) >= 2 || (isLoopHeader(parent.get()) && !inLoop(parent.get(), block));
    }

    // ---------------------------------------------------------------- structuring

    private List<S.Stmt> doTree(Block block) {
        var placed = new ArrayList<Block>();
        for (var child : dominators.children(block)) {
            if (isPlaced(child)) {
                placed.add(child);
            }
        }
        placed.sort(Comparator.comparing((Block b) -> rpo.get(b)).reversed());
        if (isLoopHeader(block)) {
            var inside = placed.stream().filter(c -> inLoop(block, c)).toList();
            var outside = placed.stream().filter(c -> !inLoop(block, c)).toList();
            List<S.Stmt> loop = List.of(new S.Loop(label(block), nodeWithin(block, inside)));
            return wrap(outside, loop);
        }
        return nodeWithin(block, placed);
    }

    /** {@code following} sorted by descending reverse-postorder number. */
    private List<S.Stmt> nodeWithin(Block block, List<Block> following) {
        if (following.isEmpty()) {
            var code = new ArrayList<S.Stmt>(blockCode(block));
            code.addAll(terminator(block));
            return code;
        }
        var first = following.get(0);
        var result = new ArrayList<S.Stmt>();
        result.add(new S.Labelled(label(first), nodeWithin(block, following.subList(1, following.size()))));
        result.addAll(doTree(first));
        return result;
    }

    private List<S.Stmt> wrap(List<Block> following, List<S.Stmt> inner) {
        if (following.isEmpty()) {
            return inner;
        }
        var first = following.get(0);
        var result = new ArrayList<S.Stmt>();
        result.add(new S.Labelled(label(first), wrap(following.subList(1, following.size()), inner)));
        result.addAll(doTree(first));
        return result;
    }

    private List<S.Stmt> terminator(Block block) {
        var term = block.terminator().orElseThrow(() -> new StructuringException("TC-E3002", "block ^" + names.blockName(block) + " has no terminator"));
        return switch (term.opcode()) {
            case Opcode.Branch _b -> edge(block, term.successors().get(0));
            case Opcode.CondBranch _c -> List.of(new S.If(expr(term.operand(0)), edge(block, term.successors().get(0)), edge(block, term.successors().get(1))));
            case Opcode.Switch(var keys) -> {
                var cases = new ArrayList<S.Case>();
                for (int i = 0; i < keys.size(); i++) {
                    cases.add(new S.Case(keys.get(i), edge(block, term.successors().get(i))));
                }
                yield List.of(new S.Switch(expr(term.operand(0)), cases, edge(block, term.successors().get(keys.size()))));
            }
            case Opcode.Return _r -> List.of(new S.Return(term.operandCount() == 0 ? Optional.empty() : Optional.of(expr(term.operand(0)))));
            case Opcode.Unreachable _u -> List.of(new S.Unreachable());
            default -> throw new StructuringException("TC-E3002", "unexpected terminator " + term.opcode().mnemonic());
        };
    }

    /** Edge copies for the target's arguments, then the jump itself. */
    private List<S.Stmt> edge(Block from, Successor successor) {
        var target = successor.target();
        var code = new ArrayList<S.Stmt>(parallelCopy(target.args(), successor.args()));
        if (dominators.dominates(target, from)) {
            code.add(new S.Continue(label(target)));
        } else if (isPlaced(target)) {
            code.add(new S.Break(label(target)));
        } else {
            code.addAll(doTree(target));
        }
        return code;
    }

    /**
     * Sequences the copies of one edge so that no copy overwrites a variable another pending copy
     * still reads; a cycle (for example a swap) is broken by saving one variable in a temporary.
     */
    private List<S.Stmt> parallelCopy(List<BlockArg> targets, List<Value> sources) {
        record Copy(S.Var target, S.Expr source) {
        }
        var pending = new ArrayList<Copy>();
        for (int i = 0; i < targets.size(); i++) {
            var target = variable(targets.get(i));
            var source = expr(sources.get(i));
            if (!(source instanceof S.VarRef(var v) && v == target)) {
                pending.add(new Copy(target, source));
            }
        }
        var code = new ArrayList<S.Stmt>();
        while (!pending.isEmpty()) {
            Copy ready = null;
            for (var candidate : pending) {
                var readByOthers = pending.stream().anyMatch(other -> other != candidate && reads(other.source, candidate.target));
                if (!readByOthers) {
                    ready = candidate;
                    break;
                }
            }
            if (ready != null) {
                code.add(new S.Assign(ready.target, ready.source, Loc.UNKNOWN));
                pending.remove(ready);
                continue;
            }
            // Every pending target is still read elsewhere: save one in a temporary.
            var saved = pending.get(0).target;
            var temp = new S.Var(unique("tmp" + temporaries++), saved.type());
            locals.add(temp);
            code.add(new S.Assign(temp, new S.VarRef(saved), Loc.UNKNOWN));
            for (int i = 0; i < pending.size(); i++) {
                var copy = pending.get(i);
                pending.set(i, new Copy(copy.target, substitute(copy.source, saved, temp)));
            }
        }
        return code;
    }

    private static boolean reads(S.Expr expr, S.Var var) {
        var found = new HashSet<S.Var>();
        collectVars(expr, found);
        return found.contains(var);
    }

    private static S.Expr substitute(S.Expr expr, S.Var from, S.Var to) {
        return switch (expr) {
            case S.VarRef(var v) -> v == from ? new S.VarRef(to) : expr;
            case S.Op(var inst, var operands) -> new S.Op(inst, operands.stream().map(o -> substitute(o, from, to)).toList());
            case S.Const _c -> expr;
            case S.AllocaRef _a -> expr;
        };
    }

    private static void collectVars(S.Expr expr, Set<S.Var> into) {
        switch (expr) {
            case S.VarRef(var v) -> into.add(v);
            case S.Op(var op, var operands) -> operands.forEach(o -> collectVars(o, into));
            case S.Const _c -> {
            }
            case S.AllocaRef _a -> {
            }
        }
    }

    // ---------------------------------------------------------------- straight-line code

    private List<S.Stmt> blockCode(Block block) {
        var code = new ArrayList<S.Stmt>();
        for (var inst : block.insts()) {
            if (inst.isTerminator() || isInline(inst) || inst.opcode() instanceof Opcode.Alloca) {
                continue;
            }
            var op = new S.Op(inst, inst.operands().stream().map(this::expr).toList());
            if (inst.producesValue()) {
                code.add(new S.Assign(vars.get(inst), op, inst.loc()));
            } else {
                code.add(new S.Effect(op, inst.loc()));
            }
        }
        return code;
    }

    /** Values printed in place instead of through a variable. */
    private boolean isInline(Inst inst) {
        var opcode = inst.opcode();
        if (opcode instanceof Opcode.ConstInt || opcode instanceof Opcode.ConstFloat || opcode instanceof Opcode.Alloca) {
            return true;
        }
        if (!opcode.isPure() || inst.users().size() != 1) {
            return false;
        }
        var user = inst.users().get(0);
        // C spells these float comparisons with each operand twice: fold only variables into them.
        if (user.opcode() instanceof Opcode.FCmp(var p) && (p == Opcode.FloatPredicate.ONE || p == Opcode.FloatPredicate.UEQ || p == Opcode.FloatPredicate.ORD || p == Opcode.FloatPredicate.UNO)) {
            return false;
        }
        return user.block().isPresent() && user.block().equals(inst.block());
    }

    private S.Expr expr(Value value) {
        if (value instanceof Inst inst) {
            if (inst.opcode() instanceof Opcode.ConstInt || inst.opcode() instanceof Opcode.ConstFloat) {
                return new S.Const(inst);
            }
            if (inst.opcode() instanceof Opcode.Alloca) {
                return new S.AllocaRef(allocaByInst.get(inst));
            }
            if (isInline(inst)) {
                return new S.Op(inst, inst.operands().stream().map(this::expr).toList());
            }
        }
        return new S.VarRef(variable(value));
    }

    private S.Var variable(Value value) {
        return vars.computeIfAbsent(value, v -> new S.Var(unique(cName(names.name(v))), v.type()));
    }

    private String label(Block block) {
        return "L_" + cName(names.blockName(block));
    }

    private String unique(String base) {
        var candidate = base;
        for (int k = 1; !usedNames.add(candidate); k++) {
            candidate = base + "_" + k;
        }
        return candidate;
    }

    /** A C identifier for a TC name: dots and dollars become underscores, leading digits get a prefix. */
    static String cName(String name) {
        var c = name.replace('.', '_').replace('$', '_');
        return c.isEmpty() || Character.isDigit(c.charAt(0)) ? "v" + c : c;
    }
}
