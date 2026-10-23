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
package uk.ac.manchester.tornado.tc.tornado;

import java.util.IdentityHashMap;
import java.util.Set;

import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.DominatorTree;
import uk.ac.manchester.tornado.tc.ir.analysis.LoopForest;

/**
 * How a kernel uses each of its parameters: read, written or both. The runtime decides data
 * movement from it (a read argument is copied to the device, a written one back), so it is computed
 * on the Java-level IR after inlining, before any lowering. The rules follow the TornadoVM contract:
 * only object parameters (arrays, objects, vectors) have an access; an access through a field or a
 * nested reference counts for the parameter it was reached from; a field write also reads the
 * object (its device copy is written back whole); reading an array's length reads its header;
 * atomics read and write; and a store that runs only
 * on some paths through the kernel also reads, because the elements it does not overwrite must keep
 * their values (the copy-in). Results are bit sets: {@link #READ} | {@link #WRITE}.
 */
public final class AccessAnalysis {

    public static final int READ = 0b01;
    public static final int WRITE = 0b10;

    private AccessAnalysis() {
    }

    /** One bit set per parameter of {@code function}, in parameter order. */
    public static int[] run(Function function) {
        var params = function.params();
        var result = new int[params.size()];
        var loops = new LoopForest(function);
        for (int p = 0; p < params.size(); p++) {
            var param = params.get(p);
            if (param.type() instanceof Type.ObjectType || param.type() instanceof Type.ArrayType || param.type() instanceof Type.VectorType) {
                result[p] = accessOf(function, param, loops);
            }
        }
        return result;
    }

    private static int accessOf(Function function, Value param, LoopForest loops) {
        var derived = reachedFrom(function, param);
        int bits = 0;
        boolean conditionalStore = false;
        for (var block : function.blocks()) {
            for (var inst : block.insts()) {
                switch (inst.opcode()) {
                    case Opcode.ArrayLoad l when derived.contains(inst.operand(0)) -> bits |= READ;
                    // The length is in the array's header: reading it reads the device copy.
                    case Opcode.ArrayLength l when derived.contains(inst.operand(0)) -> bits |= READ;
                    case Opcode.ArrayStore s when derived.contains(inst.operand(0)) -> {
                        bits |= WRITE;
                        conditionalStore |= isConditional(function, block, loops);
                    }
                    case Opcode.FieldLoad f when derived.contains(inst.operand(0)) -> bits |= READ;
                    case Opcode.FieldStore f when derived.contains(inst.operand(0)) -> bits |= READ | WRITE;
                    case Opcode.Intrinsic i when isView(inst) -> {
                        // Making a view of an array (tile.view, tile.partition) is not an access.
                    }
                    case Opcode.Intrinsic(var name) when inst.operands().stream().anyMatch(derived::contains) -> {
                        int use = intrinsicAccess(name);
                        bits |= use;
                        if ((use & WRITE) != 0) {
                            conditionalStore |= isConditional(function, block, loops);
                        }
                    }
                    // A call left after inlining (or a store of the reference elsewhere): assume both.
                    case Opcode.Call c when inst.operands().stream().anyMatch(derived::contains) -> bits |= READ | WRITE;
                    case Opcode.Store s when inst.operandCount() > 1 && derived.contains(inst.operand(1)) -> bits |= READ | WRITE;
                    default -> {
                    }
                }
            }
        }
        if (conditionalStore) {
            bits |= READ;
        }
        return bits;
    }

    /** What an intrinsic does to an array or object among its operands. */
    private static int intrinsicAccess(String name) {
        if (name.contains("atomic") || name.equals("tornado.device_launch")) {
            return READ | WRITE;
        }
        if (name.contains("store")) {
            return WRITE;
        }
        return READ;
    }

    /** The parameter and every reference reached from it: fields of reference type, and block arguments it flows into. */
    private static Set<Value> reachedFrom(Function function, Value param) {
        Set<Value> derived = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        derived.add(param);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var block : function.blocks()) {
                for (var inst : block.insts()) {
                    if (inst.opcode() instanceof Opcode.FieldLoad && derived.contains(inst.operand(0)) && isReference(inst.type()) && derived.add(inst)) {
                        changed = true;
                    }
                    if (inst.opcode() instanceof Opcode.Select && isReference(inst.type()) && inst.operands().stream().skip(1).anyMatch(derived::contains) && derived.add(inst)) {
                        changed = true;
                    }
                    if (isView(inst) && inst.operands().stream().anyMatch(derived::contains) && derived.add(inst)) {
                        changed = true;
                    }
                    for (var successor : inst.successors()) {
                        var args = successor.args();
                        var targets = successor.target().args();
                        for (int k = 0; k < args.size() && k < targets.size(); k++) {
                            if (derived.contains(args.get(k)) && derived.add(targets.get(k))) {
                                changed = true;
                            }
                        }
                    }
                }
            }
        }
        return derived;
    }

    /** An intrinsic that returns a view of memory (a tile TensorView or PartitionView), not a value. */
    private static boolean isView(uk.ac.manchester.tornado.tc.ir.Inst inst) {
        return inst.opcode() instanceof Opcode.Intrinsic && inst.type() instanceof Type.ObjectType(var className) && className.endsWith("View");
    }

    private static boolean isReference(Type type) {
        return type instanceof Type.ObjectType || type instanceof Type.ArrayType;
    }

    /**
     * Whether a store in {@code block} may leave elements unwritten, so the array must also be read
     * (copied in). As conservative as the TornadoVM sketch, never less: the store is conditional when
     * its {@code @Parallel} loop steps by anything but 1 (indices are skipped), or when a branch
     * dominates it inside the loop body (an if, or an inner loop's test); outside a parallel loop,
     * when any branch dominates it. The parallel loop's own bound test is not a condition.
     */
    private static boolean isConditional(Function function, Block block, LoopForest loops) {
        LoopForest.Loop parallel = null;
        for (var loop : loops.loops()) {
            if (loop.contains(block) && loop.header().hasAttribute("parallel") && (parallel == null || loop.depth() > parallel.depth())) {
                parallel = loop;
            }
        }
        if (parallel != null && !unitStep(parallel)) {
            return true;
        }
        var dominators = new DominatorTree(function);
        var stop = parallel == null ? null : parallel.header();
        for (var b = dominators.immediateDominator(block).orElse(null); b != null && b != stop; b = dominators.immediateDominator(b).orElse(null)) {
            if (b.terminator().map(t -> t.opcode() instanceof Opcode.CondBranch || t.opcode() instanceof Opcode.Switch).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the parallel loop's induction variable steps by exactly 1 on its back edge. */
    private static boolean unitStep(LoopForest.Loop loop) {
        var header = loop.header();
        int iv = header.attributes().stream().filter(a -> a.name().equals("parallel")).findFirst().map(a -> a.integer("iv")).orElse(0);
        if (iv >= header.args().size() || loop.latches().size() != 1) {
            return false;
        }
        var ivArg = header.args().get(iv);
        var branch = loop.latches().get(0).terminator().orElse(null);
        if (branch == null) {
            return false;
        }
        for (var successor : branch.successors()) {
            if (successor.target() == header && iv < successor.args().size() && successor.args().get(iv) instanceof Inst next && next.opcode() instanceof Opcode.Binary(var op)
                    && op == Opcode.BinaryOp.ADD) {
                for (int side = 0; side < 2; side++) {
                    if (next.operand(side) == ivArg && next.operand(1 - side) instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var step)) {
                        return step == 1;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether a loop index is written as data: a loop's induction variable flows, through
     * arithmetic and conversions, into the value of an array or field store. In batched execution
     * such a kernel is recompiled per batch, the index offset by the batch start.
     */
    public static boolean writesLoopIndex(Function function) {
        var loops = new LoopForest(function);
        java.util.Set<uk.ac.manchester.tornado.tc.ir.Value> fromIndex = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (var loop : loops.loops()) {
            fromIndex.addAll(loop.header().args());
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var block : function.blocks()) {
                for (var inst : block.insts()) {
                    boolean arithmetic = inst.opcode() instanceof Opcode.Binary || inst.opcode() instanceof Opcode.Convert || inst.opcode() instanceof Opcode.Unary
                            || inst.opcode() instanceof Opcode.Select;
                    if (arithmetic && inst.operands().stream().anyMatch(fromIndex::contains) && fromIndex.add(inst)) {
                        changed = true;
                    }
                }
            }
        }
        for (var block : function.blocks()) {
            for (var inst : block.insts()) {
                if (inst.opcode() instanceof Opcode.ArrayStore && fromIndex.contains(inst.operand(2))
                        || inst.opcode() instanceof Opcode.FieldStore && fromIndex.contains(inst.operand(1))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** For tests and dumps: {@code R}, {@code W}, {@code RW} or {@code -}. */
    public static String text(int bits) {
        return switch (bits) {
            case READ -> "R";
            case WRITE -> "W";
            case READ | WRITE -> "RW";
            default -> "-";
        };
    }

}
