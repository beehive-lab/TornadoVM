/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
 */
package uk.ac.manchester.tornado.tc.frontend;

import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.BIPUSH;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.DCONST_0;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.DCONST_1;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.DUP;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.FCONST_0;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.FCONST_2;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.IASTORE;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.ICONST_5;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.ICONST_M1;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.LCONST_0;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.LCONST_1;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.LDC;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.LDC2_W;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.LDC_W;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.NEWARRAY;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.PUTSTATIC;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.SASTORE;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.SIPUSH;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;

/**
 * Constant tables: {@code static final} primitive arrays a kernel only reads, such as FdLibm's
 * {@code two_over_pi} or {@code atanhi}. Their contents are recovered from the class initialiser's
 * bytecode, without loading the class: javac compiles {@code static final double[] T = {a, b}} to
 * {@code newarray}, then per element {@code dup}, an index constant, a value constant and a store,
 * then {@code putstatic}. That is the only shape accepted; a table built any other way (a loop, a
 * computed element, a second write to the field) is not a constant table and stays refused.
 */
public final class ConstantTables {

    /**
     * A table: its element type, as the {@code newarray} code (6 float, 7 double, 10 int, 11 long,
     * 4/8/9/5 boolean/byte/short/char), and its values.
     */
    public record Table(int newarrayType, List<Number> values) {
    }

    private ConstantTables() {
    }

    /**
     * Whether {@code owner}'s tables may be taken as constant: the JDK's FdLibm port, whose tables
     * are private and never written after initialisation.
     */
    static boolean trusted(String owner) {
        return owner.equals("java/lang/FdLibm") || owner.startsWith("java/lang/FdLibm$");
    }

    /** The table {@code field} holds once {@code classFile}'s initialiser has run, if it is one. */
    public static Optional<Table> read(ClassFile classFile, String field) {
        var clinit = classFile.methods().stream().filter(m -> m.name().equals("<clinit>") && m.code().isPresent()).findFirst();
        if (clinit.isEmpty()) {
            return Optional.empty();
        }
        Table found = null;
        // A value on the simulated stack: a constant, an array under construction, or something else.
        var stack = new ArrayDeque<Object>();
        final Object unknown = new Object();
        for (var insn : Bytecode.decode(clinit.get().code().get().bytes())) {
            int op = insn.opcode();
            if (op >= ICONST_M1 && op <= ICONST_5) {
                stack.push(op - ICONST_M1 - 1);
            } else if (op == LCONST_0 || op == LCONST_1) {
                stack.push((long) (op - LCONST_0));
            } else if (op >= FCONST_0 && op <= FCONST_2) {
                stack.push((float) (op - FCONST_0));
            } else if (op == DCONST_0 || op == DCONST_1) {
                stack.push((double) (op - DCONST_0));
            } else if (op == BIPUSH || op == SIPUSH) {
                stack.push(insn.index());
            } else if (op == LDC || op == LDC_W || op == LDC2_W) {
                var value = classFile.constants().loadable(insn.index());
                stack.push(value instanceof Number ? value : unknown);
            } else if (op == NEWARRAY) {
                var length = stack.isEmpty() ? unknown : stack.pop();
                stack.push(length instanceof Integer n && n >= 0 ? new Building(insn.value(), new Number[n]) : unknown);
            } else if (op == DUP) {
                stack.push(stack.isEmpty() ? unknown : stack.peek());
            } else if (op >= IASTORE && op <= SASTORE) {
                var value = stack.isEmpty() ? unknown : stack.pop();
                var index = stack.isEmpty() ? unknown : stack.pop();
                var array = stack.isEmpty() ? unknown : stack.pop();
                if (array instanceof Building b && index instanceof Integer i && i >= 0 && i < b.values.length && value instanceof Number n) {
                    b.values[i] = n;
                } else if (array instanceof Building b) {
                    b.poisoned = true;
                }
            } else if (op == PUTSTATIC) {
                var ref = classFile.constants().member(insn.index());
                var value = stack.isEmpty() ? unknown : stack.pop();
                if (ref.owner().equals(classFile.name()) && ref.name().equals(field)) {
                    if (found != null || !(value instanceof Building b) || b.poisoned || Arrays.asList(b.values).contains(null)) {
                        return Optional.empty(); // written twice, or not a fully constant array
                    }
                    found = new Table(b.type, List.copyOf(new ArrayList<>(Arrays.asList(b.values))));
                }
            } else if (op == Bytecode.GETSTATIC && classFile.constants().member(insn.index()).name().equals(field)
                    && classFile.constants().member(insn.index()).owner().equals(classFile.name())) {
                // The initialiser reads the field back, which is how it would change the contents after
                // the putstatic: not a table this can vouch for.
                return Optional.empty();
            } else {
                // Anything else (a call, arithmetic, a branch) ends what can be followed: an array
                // still being built becomes unknown, so a table assigned after it is not taken.
                stack.clear();
            }
        }
        return Optional.ofNullable(found);
    }

    /** An array between its {@code newarray} and its {@code putstatic}. */
    private static final class Building {
        final int type;
        final Number[] values;
        boolean poisoned;

        Building(int type, Number[] values) {
            this.type = type;
            this.values = values;
        }
    }
}
