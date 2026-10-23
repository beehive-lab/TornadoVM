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
package uk.ac.manchester.tornado.tc.frontend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import uk.ac.manchester.tornado.tc.ir.ApiTypes;
import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.BlockArg;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;

/**
 * The SSA variables of one method's translation, built on the fly as in Braun, Buchwald, Hack, Leissa,
 * Mallon and Zwinkau, "Simple and Efficient Construction of Static Single Assignment Form" (CC 2013):
 * a block is sealed once all its predecessors are filled; block arguments play the role of phi
 * functions; trivial block arguments are removed at the end. The Translator walks the bytecode and
 * says what each variable is in each block; this class finds every use's definition.
 */
final class SsaVariables {

    /**
     * An SSA variable of the bytecode: a local slot (keyed by the JVM kind that stored it), an
     * operand-stack slot live across a block boundary, or the current value of a vector parameter.
     */
    sealed interface Var permits Local, StackSlot, VectorParam {
    }

    record Local(int slot, char kind) implements Var {
    }

    record StackSlot(int depth) implements Var {
    }

    record VectorParam(int slot) implements Var {
    }

    private final Function function;
    /** The bytecode CFG's predecessors, owned by the Translator (pruned to reachable blocks before use). */
    private final Map<Block, List<Block>> preds;
    private final BiFunction<Var, Block, RuntimeException> undefinedRead;
    private final Map<Block, Map<Var, Value>> currentDef = new IdentityHashMap<>();
    private final Map<Block, LinkedHashMap<Var, BlockArg>> incomplete = new IdentityHashMap<>();
    private final Set<Block> sealed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Block> filled = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<BlockArg, Var> argumentKeys = new IdentityHashMap<>();

    /** {@code undefinedRead}: the error for a read of a variable no path defines. */
    SsaVariables(Function function, Map<Block, List<Block>> preds, BiFunction<Var, Block, RuntimeException> undefinedRead) {
        this.function = function;
        this.preds = preds;
        this.undefinedRead = undefinedRead;
    }

    /** {@code block} is translated: its definitions are final, its successors may be sealed. */
    void filled(Block block) {
        filled.add(block);
    }

    boolean isFilled(Block block) {
        return filled.contains(block);
    }

    boolean isSealed(Block block) {
        return sealed.contains(block);
    }

    /** The variable a block argument was made for, if it was made by {@link #read}. */
    Var keyOf(BlockArg arg) {
        return argumentKeys.get(arg);
    }

    void write(Var key, Block block, Value value) {
        currentDef.computeIfAbsent(block, _k -> new HashMap<>()).put(key, value);
    }

    Value read(Var key, Block block, Type type, String name) {
        var defs = currentDef.get(block);
        if (defs != null && defs.containsKey(key)) {
            return defs.get(key);
        }
        Value value;
        var blockPreds = preds.getOrDefault(block, List.of());
        if (!sealed.contains(block)) {
            // A reference read in a loop header not sealed yet must know whether it is an array, a local
            // array (pointer) or an object before its first use is translated.
            var reaching = ApiTypes.UNRESOLVED.equals(type) ? reachingDefinition(key, block, true, Collections.newSetFromMap(new IdentityHashMap<>())) : null;
            var known = reaching == null ? null : reaching.type();
            var arg = block.addArg(known != null ? known : type, name);
            argumentKeys.put(arg, key);
            incomplete.computeIfAbsent(block, _k -> new LinkedHashMap<>()).put(key, arg);
            value = arg;
        } else if (blockPreds.size() == 1) {
            value = read(key, blockPreds.get(0), type, name);
        } else if (blockPreds.isEmpty()) {
            throw undefinedRead.apply(key, block);
        } else {
            var arg = block.addArg(type, name);
            argumentKeys.put(arg, key);
            write(key, block, arg);
            addArgumentOperands(key, block, arg);
            value = arg;
        }
        write(key, block, value);
        return value;
    }

    /** Passes the value of {@code key} on every incoming edge of {@code block} to {@code arg}. */
    private void addArgumentOperands(Var key, Block block, BlockArg arg) {
        for (var pred : distinctPreds(block)) {
            var value = read(key, pred, arg.type(), arg.name());
            if (ApiTypes.UNRESOLVED.equals(arg.type())) {
                arg.setType(value.type());
            }
            var term = pred.terminator().orElseThrow();
            for (int i = 0; i < term.successors().size(); i++) {
                if (term.successors().get(i).target() == block) {
                    var args = new ArrayList<>(term.successors().get(i).args());
                    args.add(value);
                    term.setSuccessorArgs(i, args);
                }
            }
        }
    }

    void seal(Block block) {
        if (!sealed.add(block)) {
            return;
        }
        var pending = incomplete.remove(block);
        if (pending != null) {
            pending.forEach((key, arg) -> addArgumentOperands(key, block, arg));
        }
    }

    private List<Block> distinctPreds(Block block) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        var result = new ArrayList<Block>();
        for (var p : preds.get(block)) {
            if (seen.add(p)) {
                result.add(p);
            }
        }
        return result;
    }

    /** Removes block arguments whose incoming values are all the same value (or the argument itself). */
    void removeTrivialArguments() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var block : function.blocks()) {
                if (block == function.entry()) {
                    continue;
                }
                for (int index = block.args().size() - 1; index >= 0; index--) {
                    var arg = block.args().get(index);
                    Value same = null;
                    boolean trivial = true;
                    for (var incoming : incomingValues(block, index)) {
                        if (incoming == arg || incoming == same) {
                            continue;
                        }
                        if (same != null) {
                            trivial = false;
                            break;
                        }
                        same = incoming;
                    }
                    if (!trivial || same == null) {
                        continue;
                    }
                    arg.replaceAllUsesWith(same);
                    removeIncoming(block, index);
                    block.removeArg(arg);
                    changed = true;
                }
            }
        }
    }

    List<Value> incomingValues(Block block, int index) {
        var values = new ArrayList<Value>();
        for (var pred : function.blocks()) {
            pred.terminator().ifPresent(term -> {
                for (var s : term.successors()) {
                    if (s.target() == block) {
                        values.add(s.args().get(index));
                    }
                }
            });
        }
        return values;
    }

    private void removeIncoming(Block block, int index) {
        for (var pred : function.blocks()) {
            pred.terminator().ifPresent(term -> {
                for (int i = 0; i < term.successors().size(); i++) {
                    if (term.successors().get(i).target() == block) {
                        var args = new ArrayList<>(term.successors().get(i).args());
                        args.remove(index);
                        term.setSuccessorArgs(i, args);
                    }
                }
            });
        }
    }

    boolean definedSomewhere(Var key) {
        for (var defs : currentDef.values()) {
            if (defs.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The value of {@code key} at the end of {@code block}, if defined on a path into it, without
     * creating block arguments; {@code filledOnly} follows only predecessors already translated.
     */
    Value reachingDefinition(Var key, Block block, boolean filledOnly, Set<Block> seen) {
        if (!seen.add(block)) {
            return null;
        }
        var defs = currentDef.get(block);
        if (defs != null && defs.containsKey(key)) {
            return defs.get(key);
        }
        for (var pred : preds.getOrDefault(block, List.of())) {
            if (filledOnly && !filled.contains(pred)) {
                continue;
            }
            var v = reachingDefinition(key, pred, filledOnly, seen);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    static String describe(Var key) {
        // CHECKSTYLE.OFF: an arrow switch on record patterns, which checkstyle reads as falling through
        return switch (key) {
            case Local(var slot, var kind) -> "local slot " + slot;
            case StackSlot(var depth) -> "operand-stack slot " + depth;
            case VectorParam(var slot) -> "vector parameter in slot " + slot;
        };
        // CHECKSTYLE.ON
    }
}
