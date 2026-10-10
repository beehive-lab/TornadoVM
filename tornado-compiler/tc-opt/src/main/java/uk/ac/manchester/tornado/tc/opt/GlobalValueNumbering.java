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
import uk.ac.manchester.tornado.tc.ir.analysis.DominatorTree;

/**
 * Dominator-based global value numbering (optimisation list item O6): a pure instruction that
 * computes the same operation on the same operands as one in a dominating position is replaced by
 * it. Operands of commutative integer operations are put in a canonical order first. Constants are
 * numbered too, so each distinct constant appears once per dominator scope.
 */
public final class GlobalValueNumbering implements Pass {

    private record Key(Opcode opcode, Type type, List<Object> operands, List<uk.ac.manchester.tornado.tc.ir.Attribute> attributes) {
    }

    @Override
    public String name() {
        return "gvn";
    }

    @Override
    public boolean run(Function function) {
        var tree = new DominatorTree(function);
        var ordinal = new IdentityHashMap<Value, Integer>();
        for (var param : function.params()) {
            ordinal.put(param, ordinal.size());
        }
        boolean changed = false;
        record Frame(Block block, Map<Key, Inst> scope) {
        }
        var stack = new ArrayDeque<Frame>();
        stack.push(new Frame(function.entry(), new HashMap<>()));
        while (!stack.isEmpty()) {
            var frame = stack.pop();
            var scope = new HashMap<>(frame.scope());
            for (var arg : frame.block().args()) {
                ordinal.putIfAbsent(arg, ordinal.size());
            }
            for (var inst : List.copyOf(frame.block().insts())) {
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
                }
                ordinal.putIfAbsent(inst, ordinal.size());
            }
            var children = new ArrayList<>(tree.children(frame.block()));
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(new Frame(children.get(i), scope));
            }
        }
        return changed;
    }

    /**
     * Opcode, type, operands and attributes (an attribute can change meaning). An operand without an
     * ordinal is keyed by its own identity, never by a hash that two values could share (IR-5).
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
        return new Key(inst.opcode(), inst.type(), operands, List.copyOf(inst.attributes()));
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
