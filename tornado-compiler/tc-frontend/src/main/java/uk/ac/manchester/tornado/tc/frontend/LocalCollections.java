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

import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.ConvertOp;
import uk.ac.manchester.tornado.tc.ir.Type;

/**
 * Vector collections allocated in a kernel ({@code new VectorFloat2(n)}): the frontend makes them
 * {@code tornado.local_collection(storage, n, width)}, with {@code storage} a private array of
 * {@code n * width} elements. Once the collection's methods are inlined, their reads of the
 * {@code storage} and {@code numElements} fields become that array and {@code n}, and the
 * collection object disappears (scalar replacement, as on the Graal path).
 *
 * <p>Error code: {@code TC-E1011} a collection used other than through its methods (passed on, stored).</p>
 */
public final class LocalCollections {

    public static final String NAME = "tornado.local_collection";

    private LocalCollections() {
    }

    public static List<Diagnostic> run(Function function) {
        var collections = new ArrayList<Inst>();
        for (var block : function.blocks()) {
            for (var inst : block.insts()) {
                if (inst.opcode() instanceof Opcode.Intrinsic(var name) && name.equals(NAME)) {
                    collections.add(inst);
                }
            }
        }
        var problems = new ArrayList<Diagnostic>();
        for (var collection : collections) {
            var storage = collection.operand(0);
            // The array is the collection's storage: it takes the Java local's name (`float vectorFloat2[32]`).
            if ((storage.name().isEmpty() || storage.name().chars().allMatch(Character::isDigit)) && !collection.name().isEmpty() && !collection.name().chars().allMatch(Character::isDigit)) {
                storage.setName(collection.name());
            }
            for (var use : List.copyOf(collection.users())) {
                if (use.opcode() instanceof Opcode.FieldLoad(var field) && use.operand(0) == collection) {
                    switch (field) {
                        case "storage" -> {
                            // The storage's own length (FloatArray.getSize) is n * width.
                            for (var lengthRead : List.copyOf(use.users())) {
                                if (lengthRead.opcode() instanceof Opcode.ArrayLength) {
                                    var b = IrBuilder.insertingBefore(lengthRead);
                                    var total = b.binary(BinaryOp.MUL, collection.operand(1), collection.operand(2));
                                    lengthRead.replaceAllUsesWith(lengthRead.type().equals(Type.I64) ? b.convert(ConvertOp.SEXT, total, Type.I64) : total);
                                    lengthRead.erase();
                                }
                            }
                            use.replaceAllUsesWith(storage);
                            use.erase();
                        }
                        case "numElements" -> {
                            use.replaceAllUsesWith(collection.operand(1));
                            use.erase();
                        }
                        default -> {
                        }
                    }
                }
            }
            if (collection.users().isEmpty()) {
                collection.erase();
            } else {
                problems.add(new Diagnostic("TC-E1011", "a vector collection allocated in the kernel can only be used through its own methods (get, set, getLength)", "@" + function.name(),
                        collection.loc()));
            }
        }
        return problems;
    }
}
