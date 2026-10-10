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
package uk.ac.manchester.tornado.tc.tornado;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.tc.ir.Attribute;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;

/**
 * {@code AtomicInteger}s in a kernel, as the TornadoVM runtime expects them: every counter is a slot
 * of the kernel's {@code _atomics} region. A counter created in the kernel ({@code new
 * AtomicInteger(v)}) gets a slot initialised to {@code v}; an {@code AtomicInteger} argument gets a
 * slot the runtime fills with the argument's value before the launch and copies back after it, and
 * the argument leaves the kernel signature. Each operation then names its slot as a constant.
 *
 * <p>The assignment is recorded as the function attribute {@code atomics(init=v0,v1,..;params=p:s,..)}
 * (see {@link #info}); the runtime registers it as for the Graal path.</p>
 */
public final class AtomicSlots {

    /** Initial value per slot (argument slots hold a placeholder); argument index -> slot. */
    public record Info(List<Integer> initial, Map<Integer, Integer> params) {
        public boolean isEmpty() {
            return initial.isEmpty();
        }
    }

    private static final int PLACEHOLDER = -1;

    private AtomicSlots() {
    }

    public static List<Diagnostic> run(Function function) {
        var initial = new ArrayList<Integer>();
        var params = new LinkedHashMap<Integer, Integer>();
        Map<Value, Integer> slots = new java.util.IdentityHashMap<>();
        for (int p = 0; p < function.params().size(); p++) {
            var param = function.params().get(p);
            if (param.type() instanceof Type.ObjectType o && o.className().equals("AtomicInteger") && param.hasUses()) {
                slots.put(param, initial.size());
                params.put(p, initial.size());
                initial.add(PLACEHOLDER);
            }
        }
        var operations = new ArrayList<Inst>();
        for (var block : function.blocks()) {
            for (var inst : block.insts()) {
                if (inst.opcode() instanceof Opcode.Intrinsic(var name) && name.startsWith("tornado.atomic_int.")) {
                    if (name.equals("tornado.atomic_int.new")) {
                        if (!(inst.operand(0) instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var v))) {
                            return List.of(new Diagnostic("TC-E3205", "an AtomicInteger created in a kernel needs a constant initial value", "", inst.loc()));
                        }
                        slots.put(inst, initial.size());
                        initial.add((int) v);
                    } else {
                        operations.add(inst);
                    }
                }
            }
        }
        if (slots.isEmpty()) {
            return List.of();
        }
        for (var op : operations) {
            var counter = slots.get(op.operand(0));
            if (counter == null) {
                return List.of(new Diagnostic("TC-E3205", "an AtomicInteger must be created in the kernel or passed to it (it cannot be selected at run time)", "", op.loc()));
            }
            var slot = new IrBuilder(op.block().orElseThrow()).before(op).at(op.loc()).constInt(Type.I32, counter);
            op.setOperand(0, slot);
        }
        for (var value : slots.keySet()) {
            if (value instanceof Inst created) {
                if (created.hasUses()) {
                    return List.of(new Diagnostic("TC-E3205", "an AtomicInteger is used other than through its methods", "", created.loc()));
                }
                created.erase();
            }
        }
        for (int s = 0; s < initial.size(); s++) {
            function.addAttribute(Attribute.of("atomic_slot", "slot", s, "init", initial.get(s)));
        }
        params.forEach((param, slot) -> function.addAttribute(Attribute.of("atomic_param", "param", param, "slot", slot)));
        return List.of();
    }

    /** The slot assignment recorded by {@link #run}; empty when the kernel has no AtomicInteger. */
    public static Info info(Function function) {
        var initial = new java.util.TreeMap<Integer, Integer>();
        var params = new LinkedHashMap<Integer, Integer>();
        for (var a : function.attributes()) {
            switch (a.name()) {
                case "atomic_slot" -> initial.put(a.integer("slot"), a.integer("init"));
                case "atomic_param" -> params.put(a.integer("param"), a.integer("slot"));
                default -> {
                }
            }
        }
        return new Info(List.copyOf(initial.values()), params);
    }
}
