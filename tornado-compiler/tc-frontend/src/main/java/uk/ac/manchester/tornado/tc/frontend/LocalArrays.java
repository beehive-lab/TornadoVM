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
import java.util.List;
import java.util.Optional;

import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;

/**
 * Local and shared arrays whose length is not a constant in the bytecode, such as
 * {@code context.allocateFloatLocalArray(blockSize)} with {@code blockSize} a task argument. The
 * frontend emits a pending allocation, the intrinsic {@code tornado.local_array.<space>.<element>(%n)};
 * after value specialisation and constant folding, {@link #run} turns each one whose length became a
 * constant into an {@code alloca}, and reports {@code TC-E1004} for the others (the GPU needs the size
 * of a local array at compile time).
 */
public final class LocalArrays {

    private static final String PREFIX = "tornado.local_array.";
    private static final List<Type> ELEMENTS = List.of(Type.I8, Type.I16, Type.I32, Type.I64, Type.F16, Type.F32, Type.F64);

    private LocalArrays() {
    }

    static String pendingName(AddressSpace space, Type element) {
        return PREFIX + space.text() + "." + element.text();
    }

    /** Whether {@code inst} is a pending allocation. */
    public static boolean isPending(Inst inst) {
        return inst.opcode() instanceof Opcode.Intrinsic(var name) && name.startsWith(PREFIX);
    }

    /** Whether {@code function} has a pending allocation (the stage runs only then). */
    public static boolean hasPending(Function function) {
        return function.blocks().stream().flatMap(b -> b.insts().stream()).anyMatch(LocalArrays::isPending);
    }

    /** The element type of an allocation, pending or not. */
    static Optional<Type> element(Inst inst) {
        if (inst.opcode() instanceof Opcode.Alloca(var space, var element, var count)) {
            return Optional.of(element);
        }
        if (inst.opcode() instanceof Opcode.Intrinsic(var name) && name.startsWith(PREFIX)) {
            var text = name.substring(name.lastIndexOf('.') + 1);
            return ELEMENTS.stream().filter(t -> t.text().equals(text)).findFirst();
        }
        return Optional.empty();
    }

    /** Materialises pending allocations with constant lengths; returns TC-E1004 for the rest. */
    public static List<Diagnostic> run(Function function) {
        var problems = new ArrayList<Diagnostic>();
        for (var block : function.blocks()) {
            for (var inst : List.copyOf(block.insts())) {
                if (!isPending(inst)) {
                    continue;
                }
                if (inst.operand(0) instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var count)) {
                    var name = ((Opcode.Intrinsic) inst.opcode()).name();
                    var space = AddressSpace.fromText(name.substring(PREFIX.length(), name.lastIndexOf('.')));
                    var alloca = new IrBuilder(block).before(inst).at(inst.loc()).alloca(space, element(inst).orElseThrow(), (int) count);
                    alloca.setName(inst.name());
                    inst.replaceAllUsesWith(alloca);
                    inst.erase();
                } else {
                    problems.add(new Diagnostic("TC-E1004", "local and shared arrays need a length known at compile time (a constant or a task argument)", "", inst.loc()));
                }
            }
        }
        return problems;
    }
}
