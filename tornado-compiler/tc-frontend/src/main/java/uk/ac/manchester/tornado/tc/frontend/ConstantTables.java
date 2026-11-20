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

    /**
     * Whether {@code owner}'s computed scalars may be taken as constant: the JDK's own math classes.
     * {@code Math.twoToTheDoubleScaleUp} and {@code twoToTheDoubleScaleDown} are {@code static} but not
     * {@code final}, so nothing in the class file proves they keep the value the initialiser gave them;
     * what makes them safe is that they are package-private to {@code java.lang} and assigned only
     * there, which is a fact about these classes rather than something TC can check of any class.
     * {@link #scalar} still checks that no other method of the class writes the field.
     */
    static boolean trustedScalar(String owner) {
        return trusted(owner) || owner.equals("java/lang/Math") || owner.equals("java/lang/StrictMath");
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

    /**
     * The value {@code field} holds once {@code classFile}'s initialiser has run, when that value can be
     * worked out from the bytecode alone.
     *
     * <p>
     * Only the classes {@link #trustedScalar} names, and only when no other method of the class writes
     * the field, so that the initialiser's value is the one a kernel reads. Beyond that the value has to
     * be got exactly right, so only initialisers this can evaluate are accepted and everything else
     * returns empty, leaving the field refused as before.
     * </p>
     *
     * <p>
     * Reading the field reflectively would be simpler and is what a source backed by the host's classes
     * tries first, but it does not work for the JDK's own: {@code java.base} does not open
     * {@code java.lang}, so {@code setAccessible} on, say, {@code Math.twoToTheDoubleScaleDown} throws.
     * That field is one {@code StrictMath}'s {@code sin}, {@code cos}, {@code tan} and {@code pow} reach
     * through {@code Math.scalb}, so without this Java-exact math cannot compile them.
     * </p>
     */
    public static Optional<Number> scalar(ClassFile classFile, String field) {
        var clinit = classFile.method("<clinit>", "()V").flatMap(ClassFile.Method::code);
        if (clinit.isEmpty() || !trustedScalar(classFile.name()) || writtenElsewhere(classFile, field)) {
            return Optional.empty();
        }
        Number found = null;
        var stack = new ArrayDeque<Object>();
        final Object unknown = new Object();
        for (var insn : Bytecode.decode(clinit.get().bytes())) {
            int op = insn.opcode();
            if (op == PUTSTATIC) {
                var ref = classFile.constants().member(insn.index());
                var value = stack.isEmpty() ? unknown : stack.pop();
                if (ref.owner().equals(classFile.name()) && ref.name().equals(field)) {
                    if (found != null || !(value instanceof Number n)) {
                        return Optional.empty(); // assigned twice, or from something this cannot follow
                    }
                    found = narrow(ref.descriptor(), n);
                    if (found == null) {
                        return Optional.empty();
                    }
                }
            } else if (op == Bytecode.GETSTATIC && classFile.constants().member(insn.index()).name().equals(field)
                    && classFile.constants().member(insn.index()).owner().equals(classFile.name())) {
                return Optional.empty(); // read back, so a later store could still change it
            } else if (op == Bytecode.INVOKESTATIC) {
                var ref = classFile.constants().member(insn.index());
                var args = new Number[arity(ref.descriptor())];
                boolean known = true;
                for (int i = args.length - 1; i >= 0; i--) {
                    var a = stack.isEmpty() ? unknown : stack.pop();
                    if (a instanceof Number n) {
                        args[i] = n;
                    } else {
                        known = false;
                    }
                }
                var result = known ? call(classFile, ref.owner(), ref.name(), ref.descriptor(), args, 0) : Optional.<Number> empty();
                stack.push(result.isPresent() ? result.get() : unknown);
            } else if (!push(classFile, insn, stack)) {
                // Anything else (a branch, a new object) ends what can be followed from here.
                stack.clear();
            }
        }
        return Optional.ofNullable(found);
    }

    /** Whether any method other than the class initialiser stores into {@code field}. */
    private static boolean writtenElsewhere(ClassFile classFile, String field) {
        for (var method : classFile.methods()) {
            if (method.name().equals("<clinit>") || method.code().isEmpty()) {
                continue;
            }
            for (var insn : Bytecode.decode(method.code().get().bytes())) {
                if (insn.opcode() == PUTSTATIC) {
                    var ref = classFile.constants().member(insn.index());
                    if (ref.owner().equals(classFile.name()) && ref.name().equals(field)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** {@code value} as the field's declared type, or null when it is not that kind of value. */
    private static Number narrow(String descriptor, Number value) {
        return switch (descriptor) {
            case "J" -> value.longValue();
            case "D" -> value.doubleValue();
            case "F" -> value.floatValue();
            case "I", "S", "C", "B", "Z" -> value.intValue();
            default -> null;
        };
    }

    private static int arity(String descriptor) {
        int count = 0;
        for (int i = 1; descriptor.charAt(i) != ')'; i++) {
            char c = descriptor.charAt(i);
            while (c == '[') {
                c = descriptor.charAt(++i);
            }
            if (c == 'L') {
                i = descriptor.indexOf(';', i);
            }
            count++;
        }
        return count;
    }

    /**
     * {@code ref} applied to {@code args}: the bit-level conversions in {@code java.lang}, and otherwise a
     * static method of the same class, evaluated from its own bytecode. The depth is bounded because a
     * class initialiser is not worth following far, and a cycle would not terminate.
     */
    private static Optional<Number> call(ClassFile classFile, String owner, String name, String descriptor, Number[] args, int depth) {
        if (depth > 4) {
            return Optional.empty();
        }
        if (args.length == 1) {
            var one = args[0];
            switch (owner + "." + name) {
                case "java/lang/Double.longBitsToDouble":
                    return Optional.of(Double.longBitsToDouble(one.longValue()));
                case "java/lang/Double.doubleToRawLongBits", "java/lang/Double.doubleToLongBits":
                    return Optional.of(Double.doubleToRawLongBits(one.doubleValue()));
                case "java/lang/Float.intBitsToFloat":
                    return Optional.of(Float.intBitsToFloat(one.intValue()));
                case "java/lang/Float.floatToRawIntBits", "java/lang/Float.floatToIntBits":
                    return Optional.of(Float.floatToRawIntBits(one.floatValue()));
                default:
                    break;
            }
        }
        if (!owner.equals(classFile.name())) {
            return Optional.empty();
        }
        var method = classFile.method(name, descriptor).flatMap(ClassFile.Method::code);
        return method.isEmpty() ? Optional.empty() : evaluate(classFile, method.get().bytes(), args, method.get().maxLocals(), depth);
    }

    /**
     * A static method over primitives, run on {@code args}. Only the instructions a computed constant
     * needs are implemented -- constants, loads, arithmetic, conversions, comparisons and calls -- and
     * anything else gives up. Assertions are taken as disabled, as they are in a kernel.
     */
    private static Optional<Number> evaluate(ClassFile classFile, byte[] code, Number[] args, int maxLocals, int depth) {
        var insns = Bytecode.decode(code);
        var at = new java.util.HashMap<Integer, Integer>();
        for (int i = 0; i < insns.size(); i++) {
            at.put(insns.get(i).bci(), i);
        }
        var locals = new Number[Math.max(maxLocals, args.length)];
        System.arraycopy(args, 0, locals, 0, args.length);
        var stack = new ArrayDeque<Object>();
        for (int pc = 0, steps = 0; pc < insns.size() && steps < 10_000; steps++) {
            var insn = insns.get(pc);
            int op = insn.opcode();
            Integer jump = null;
            if (op >= 21 && op <= 24) { // iload, lload, fload, dload
                stack.push(local(locals, insn.index()));
            } else if (op >= 26 && op <= 41) { // iload_0 .. dload_3
                stack.push(local(locals, (op - 26) % 4));
            } else if (op >= 172 && op <= 175) { // ireturn, lreturn, freturn, dreturn
                return stack.peek() instanceof Number n ? Optional.of(n) : Optional.empty();
            } else if (op == Bytecode.GETSTATIC) {
                var ref = classFile.constants().member(insn.index());
                if (!ref.name().equals("$assertionsDisabled")) {
                    return Optional.empty();
                }
                stack.push(1); // as a kernel sees them (Translator does the same)
            } else if (op == Bytecode.INVOKESTATIC) {
                var ref = classFile.constants().member(insn.index());
                var callee = new Number[arity(ref.descriptor())];
                for (int i = callee.length - 1; i >= 0; i--) {
                    if (!(stack.poll() instanceof Number n)) {
                        return Optional.empty();
                    }
                    callee[i] = n;
                }
                var result = call(classFile, ref.owner(), ref.name(), ref.descriptor(), callee, depth + 1);
                if (result.isEmpty()) {
                    return Optional.empty();
                }
                stack.push(result.get());
            } else if (op >= 153 && op <= 158) { // ifeq .. ifle
                if (!(stack.poll() instanceof Number n)) {
                    return Optional.empty();
                }
                jump = compare(op - 153, n.intValue(), 0) ? at.get(insn.targets()[0]) : null;
            } else if (op >= 159 && op <= 164) { // if_icmpeq .. if_icmple
                if (!(stack.poll() instanceof Number b) || !(stack.poll() instanceof Number a)) {
                    return Optional.empty();
                }
                jump = compare(op - 159, a.intValue(), b.intValue()) ? at.get(insn.targets()[0]) : null;
            } else if (op == Bytecode.GOTO) {
                jump = at.get(insn.targets()[0]);
            } else if (!push(classFile, insn, stack) && !arithmetic(op, stack)) {
                return Optional.empty();
            }
            if (jump != null) {
                pc = jump;
            } else if (op == Bytecode.GOTO) {
                return Optional.empty(); // a target this cannot resolve
            } else {
                pc++;
            }
        }
        return Optional.empty();
    }

    private static Number local(Number[] locals, int slot) {
        return slot >= 0 && slot < locals.length && locals[slot] != null ? locals[slot] : null;
    }

    private static boolean compare(int kind, int a, int b) {
        return switch (kind) {
            case 0 -> a == b;
            case 1 -> a != b;
            case 2 -> a < b;
            case 3 -> a >= b;
            case 4 -> a > b;
            default -> a <= b;
        };
    }

    /** The constant-pushing instructions, shared by the initialiser scan and the evaluator. */
    private static boolean push(ClassFile classFile, Bytecode.Insn insn, ArrayDeque<Object> stack) {
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
            Object value;
            try {
                value = classFile.constants().loadable(insn.index());
            } catch (RuntimeException e) {
                return false; // a class or method handle, say: not a number this can carry
            }
            if (!(value instanceof Number)) {
                return false;
            }
            stack.push(value);
        } else {
            return false;
        }
        return true;
    }

    /**
     * Arithmetic, bitwise and conversion instructions on constants; false for anything else, which ends
     * the evaluation rather than producing a value. The opcodes are listed one by one on purpose: the
     * JVM's numeric instructions run int/long/float/double in fours up to {@code dneg}, but the shifts
     * and the bitwise operations that follow come in twos, so arithmetic on the opcode gets them wrong.
     */
    private static boolean arithmetic(int op, ArrayDeque<Object> stack) {
        if (op >= 116 && op <= 119) { // ineg, lneg, fneg, dneg
            if (!(stack.poll() instanceof Number a)) {
                return false;
            }
            stack.push(switch (op) {
                case 116 -> -a.intValue();
                case 117 -> -a.longValue();
                case 118 -> -a.floatValue();
                default -> -a.doubleValue();
            });
            return true;
        }
        if (op >= 133 && op <= 147) { // i2l .. i2s
            if (!(stack.poll() instanceof Number a)) {
                return false;
            }
            stack.push(switch (op) {
                case 133 -> (long) a.intValue();
                case 134 -> (float) a.intValue();
                case 135 -> (double) a.intValue();
                case 136 -> (int) a.longValue();
                case 137 -> (float) a.longValue();
                case 138 -> (double) a.longValue();
                case 139 -> (int) a.floatValue();
                case 140 -> (long) a.floatValue();
                case 141 -> (double) a.floatValue();
                case 142 -> (int) a.doubleValue();
                case 143 -> (long) a.doubleValue();
                case 144 -> (float) a.doubleValue();
                case 145 -> (int) (byte) a.intValue();
                case 146 -> (int) (char) a.intValue();
                default -> (int) (short) a.intValue();
            });
            return true;
        }
        if (op < 96 || op > 131) {
            return false;
        }
        if (!(stack.poll() instanceof Number b) || !(stack.poll() instanceof Number a)) {
            return false;
        }
        Number result = switch (op) {
            case 96 -> a.intValue() + b.intValue();
            case 97 -> a.longValue() + b.longValue();
            case 98 -> a.floatValue() + b.floatValue();
            case 99 -> a.doubleValue() + b.doubleValue();
            case 100 -> a.intValue() - b.intValue();
            case 101 -> a.longValue() - b.longValue();
            case 102 -> a.floatValue() - b.floatValue();
            case 103 -> a.doubleValue() - b.doubleValue();
            case 104 -> a.intValue() * b.intValue();
            case 105 -> a.longValue() * b.longValue();
            case 106 -> a.floatValue() * b.floatValue();
            case 107 -> a.doubleValue() * b.doubleValue();
            case 108 -> b.intValue() == 0 ? null : a.intValue() / b.intValue();
            case 109 -> b.longValue() == 0 ? null : a.longValue() / b.longValue();
            case 110 -> a.floatValue() / b.floatValue();
            case 111 -> a.doubleValue() / b.doubleValue();
            case 112 -> b.intValue() == 0 ? null : a.intValue() % b.intValue();
            case 113 -> b.longValue() == 0 ? null : a.longValue() % b.longValue();
            case 114 -> a.floatValue() % b.floatValue();
            case 115 -> a.doubleValue() % b.doubleValue();
            case 120 -> a.intValue() << b.intValue();
            case 121 -> a.longValue() << b.intValue();
            case 122 -> a.intValue() >> b.intValue();
            case 123 -> a.longValue() >> b.intValue();
            case 124 -> a.intValue() >>> b.intValue();
            case 125 -> a.longValue() >>> b.intValue();
            case 126 -> a.intValue() & b.intValue();
            case 127 -> a.longValue() & b.longValue();
            case 128 -> a.intValue() | b.intValue();
            case 129 -> a.longValue() | b.longValue();
            case 130 -> a.intValue() ^ b.intValue();
            default -> a.longValue() ^ b.longValue();
        };
        if (result == null) {
            return false; // division by zero: not a constant this can give a value for
        }
        stack.push(result);
        return true;
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
