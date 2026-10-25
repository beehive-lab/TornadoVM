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
package uk.ac.manchester.tornado.tc.opt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.Analyses;

/**
 * Dominator-based global value numbering: a pure instruction that
 * computes the same operation on the same operands as one in a dominating position is replaced by
 * it. Operands of commutative integer operations are put in a canonical order first. Constants are
 * numbered too, so each distinct constant appears once per dominator scope.
 */
public final class GlobalValueNumbering implements Pass {

    private record Key(Opcode opcode, Type type, List<Object> operands, List<uk.ac.manchester.tornado.tc.ir.Attribute> attributes, uk.ac.manchester.tornado.tc.ir.Immediates immediates) {
    }

    @Override
    public String name() {
        return "gvn";
    }

    @Override
    public boolean run(Function function) {
        var tree = Analyses.dominators(function);
        var ordinal = new IdentityHashMap<Value, Integer>();
        for (var param : function.params()) {
            ordinal.put(param, ordinal.size());
        }
        boolean changed = false;
        // One table for the dominator-tree walk: a block's keys are removed when its subtree is done,
        // instead of each block copying its parent's table.
        var scope = new HashMap<Key, Inst>();
        final class Frame {
            final Block block;
            final List<Key> added = new ArrayList<>();
            boolean entered;

            Frame(Block block) {
                this.block = block;
            }
        }
        var stack = new ArrayDeque<Frame>();
        stack.push(new Frame(function.entry()));
        while (!stack.isEmpty()) {
            var frame = stack.peek();
            if (frame.entered) {
                stack.pop();
                frame.added.forEach(scope::remove);
                continue;
            }
            frame.entered = true;
            for (var arg : frame.block.args()) {
                ordinal.putIfAbsent(arg, ordinal.size());
            }
            for (var inst : List.copyOf(frame.block.insts())) {
                if (inst.producesValue() && inst.opcode().isPure()) {
                    var key = key(inst, ordinal);
                    var existing = scope.get(key);
                    if (existing != null) {
                        inst.replaceAllUsesWith(existing);
                        inst.erase();
                        changed = true;
                        continue;
                    }
                    scope.put(key, inst);
                    frame.added.add(key);
                }
                ordinal.putIfAbsent(inst, ordinal.size());
            }
            var children = new ArrayList<>(tree.children(frame.block));
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(new Frame(children.get(i)));
            }
        }
        return changed;
    }

    /**
     * Opcode, type, operands and attributes (an attribute can change meaning). An operand without an
     * ordinal is keyed by its own identity, never by a hash that two values could share.
     */
    private static Key key(Inst inst, Map<Value, Integer> ordinal) {
        var operands = new ArrayList<Object>();
        for (var op : inst.operands()) {
            var n = ordinal.get(op);
            operands.add(n != null ? n : new Identity(op));
        }
        if (inst.opcode() instanceof Opcode.Binary(var op) && commutative(op) && operands.get(0) instanceof Integer a && operands.get(1) instanceof Integer b && a > b) {
            operands.add(operands.remove(0));
        }
        return new Key(inst.opcode(), inst.type(), operands, List.copyOf(inst.attributes()), inst.immediates());
    }

    /** A value compared by identity in a key. */
    private record Identity(Value value) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Identity other && other.value == value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }
    }

    private static boolean commutative(BinaryOp op) {
        return switch (op) {
            case ADD, MUL, AND, OR, XOR -> true;
            default -> false;
        };
    }
}
