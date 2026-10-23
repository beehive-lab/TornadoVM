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
package uk.ac.manchester.tornado.tc.frontend.classfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the instructions of a {@code Code} attribute (JVMS section 6.5): opcode, operands and branch
 * targets, with {@code wide}, {@code tableswitch} and {@code lookupswitch} handled.
 */
public final class Bytecode {

    /**
     * One decoded instruction.
     *
     * @param bci     bytecode index
     * @param opcode  the opcode ({@code wide} is folded into the instruction it modifies)
     * @param index   local slot, constant-pool index or immediate value, depending on the opcode
     * @param value   second immediate ({@code iinc} increment, {@code newarray} type, dimensions)
     * @param targets branch targets (absolute bcis); for switches the cases, then the default
     * @param keys    switch keys, one per case target
     */
    public record Insn(int bci, int opcode, int index, int value, int[] targets, int[] keys) {
        public String mnemonic() {
            return NAMES[opcode];
        }
    }

    // CHECKSTYLE.OFF
    // The opcodes the frontend refers to by name (JVMS section 7).
    public static final int NOP = 0, ACONST_NULL = 1, ICONST_M1 = 2, ICONST_5 = 8, LCONST_0 = 9, LCONST_1 = 10, FCONST_0 = 11, FCONST_2 = 13, DCONST_0 = 14, DCONST_1 = 15,
            BIPUSH = 16, SIPUSH = 17, LDC = 18, LDC_W = 19, LDC2_W = 20, ILOAD = 21, LLOAD = 22, FLOAD = 23, DLOAD = 24, ALOAD = 25, ILOAD_0 = 26, ALOAD_3 = 45,
            IALOAD = 46, SALOAD = 53, ISTORE = 54, LSTORE = 55, FSTORE = 56, DSTORE = 57, ASTORE = 58, ISTORE_0 = 59, ASTORE_0 = 75, ASTORE_3 = 78, IASTORE = 79, SASTORE = 86,
            POP = 87, POP2 = 88, DUP = 89, DUP_X1 = 90, DUP_X2 = 91, DUP2 = 92, DUP2_X1 = 93, DUP2_X2 = 94, SWAP = 95, IADD = 96, DREM = 115, INEG = 116, DNEG = 119,
            ISHL = 120, LXOR = 131, IINC = 132, I2L = 133, I2S = 147, LCMP = 148, FCMPL = 149, FCMPG = 150, DCMPL = 151, DCMPG = 152, IFEQ = 153, IFNE = 154, IFLE = 158,
            IF_ICMPEQ = 159, IF_ICMPLE = 164, IF_ACMPEQ = 165, IF_ACMPNE = 166, GOTO = 167, JSR = 168, RET = 169, TABLESWITCH = 170, LOOKUPSWITCH = 171,
            IRETURN = 172, DRETURN = 175, ARETURN = 176, RETURN = 177, GETSTATIC = 178, PUTSTATIC = 179, GETFIELD = 180, PUTFIELD = 181, INVOKEVIRTUAL = 182,
            INVOKESPECIAL = 183, INVOKESTATIC = 184, INVOKEINTERFACE = 185, INVOKEDYNAMIC = 186, NEW = 187, NEWARRAY = 188, ANEWARRAY = 189,
            ARRAYLENGTH = 190, ATHROW = 191, CHECKCAST = 192, INSTANCEOF = 193, MONITORENTER = 194, MONITOREXIT = 195, WIDE = 196, MULTIANEWARRAY = 197,
            IFNULL = 198, IFNONNULL = 199, GOTO_W = 200, JSR_W = 201;
    // CHECKSTYLE.ON

    private static final String[] NAMES = ("nop aconst_null iconst_m1 iconst_0 iconst_1 iconst_2 iconst_3 iconst_4 iconst_5 lconst_0 lconst_1 fconst_0 fconst_1 fconst_2 "
            + "dconst_0 dconst_1 bipush sipush ldc ldc_w ldc2_w iload lload fload dload aload iload_0 iload_1 iload_2 iload_3 lload_0 lload_1 lload_2 lload_3 "
            + "fload_0 fload_1 fload_2 fload_3 dload_0 dload_1 dload_2 dload_3 aload_0 aload_1 aload_2 aload_3 iaload laload faload daload aaload baload caload "
            + "saload istore lstore fstore dstore astore istore_0 istore_1 istore_2 istore_3 lstore_0 lstore_1 lstore_2 lstore_3 fstore_0 fstore_1 fstore_2 "
            + "fstore_3 dstore_0 dstore_1 dstore_2 dstore_3 astore_0 astore_1 astore_2 astore_3 iastore lastore fastore dastore aastore bastore castore sastore "
            + "pop pop2 dup dup_x1 dup_x2 dup2 dup2_x1 dup2_x2 swap iadd ladd fadd dadd isub lsub fsub dsub imul lmul fmul dmul idiv ldiv fdiv ddiv irem lrem frem "
            + "drem ineg lneg fneg dneg ishl lshl ishr lshr iushr lushr iand land ior lor ixor lxor iinc i2l i2f i2d l2i l2f l2d f2i f2l f2d d2i d2l d2f i2b i2c "
            + "i2s lcmp fcmpl fcmpg dcmpl dcmpg ifeq ifne iflt ifge ifgt ifle if_icmpeq if_icmpne if_icmplt if_icmpge if_icmpgt if_icmple if_acmpeq if_acmpne "
            + "goto jsr ret tableswitch lookupswitch ireturn lreturn freturn dreturn areturn return getstatic putstatic getfield putfield invokevirtual "
            + "invokespecial invokestatic invokeinterface invokedynamic new newarray anewarray arraylength athrow checkcast instanceof monitorenter monitorexit "
            + "wide multianewarray ifnull ifnonnull goto_w jsr_w").split(" ");

    private Bytecode() {
    }

    public static String name(int opcode) {
        return opcode < NAMES.length ? NAMES[opcode] : "op" + opcode;
    }

    public static boolean isConditionalBranch(int op) {
        return (op >= IFEQ && op <= IF_ACMPNE) || op == IFNULL || op == IFNONNULL;
    }

    public static boolean isReturn(int op) {
        return op >= IRETURN && op <= RETURN;
    }

    /** Instructions after which control does not fall through. */
    public static boolean endsBlock(int op) {
        return op == GOTO || op == GOTO_W || op == TABLESWITCH || op == LOOKUPSWITCH || isReturn(op) || op == ATHROW || isConditionalBranch(op) || op == JSR || op == JSR_W || op == RET;
    }

    public static List<Insn> decode(byte[] code) {
        var insns = new ArrayList<Insn>();
        int pc = 0;
        while (pc < code.length) {
            int bci = pc;
            int op = u1(code, pc++);
            Insn insn;
            if (op == WIDE) {
                int inner = u1(code, pc++);
                int index = u2(code, pc);
                pc += 2;
                int increment = 0;
                if (inner == IINC) {
                    increment = s2(code, pc);
                    pc += 2;
                }
                insn = new Insn(bci, inner, index, increment, new int[0], new int[0]);
            } else if (op == TABLESWITCH) {
                pc = (pc + 3) & ~3;
                int deflt = bci + s4(code, pc);
                int low = s4(code, pc + 4);
                int high = s4(code, pc + 8);
                pc += 12;
                int n = high - low + 1;
                var targets = new int[n + 1];
                var keys = new int[n];
                for (int i = 0; i < n; i++) {
                    keys[i] = low + i;
                    targets[i] = bci + s4(code, pc);
                    pc += 4;
                }
                targets[n] = deflt;
                insn = new Insn(bci, op, 0, 0, targets, keys);
            } else if (op == LOOKUPSWITCH) {
                pc = (pc + 3) & ~3;
                int deflt = bci + s4(code, pc);
                int n = s4(code, pc + 4);
                pc += 8;
                var targets = new int[n + 1];
                var keys = new int[n];
                for (int i = 0; i < n; i++) {
                    keys[i] = s4(code, pc);
                    targets[i] = bci + s4(code, pc + 4);
                    pc += 8;
                }
                targets[n] = deflt;
                insn = new Insn(bci, op, 0, 0, targets, keys);
            } else {
                insn = switch (operandShape(op)) {
                    case NONE -> new Insn(bci, op, 0, 0, new int[0], new int[0]);
                    case LOCAL_U1, CP_U1 -> new Insn(bci, op, u1(code, pc++), 0, new int[0], new int[0]);
                    case IMM_S1 -> new Insn(bci, op, (byte) u1(code, pc++), 0, new int[0], new int[0]);
                    case IMM_S2 -> {
                        var v = s2(code, pc);
                        pc += 2;
                        yield new Insn(bci, op, v, 0, new int[0], new int[0]);
                    }
                    case CP_U2 -> {
                        var v = u2(code, pc);
                        pc += 2;
                        yield new Insn(bci, op, v, 0, new int[0], new int[0]);
                    }
                    case IINC_SHAPE -> {
                        var slot = u1(code, pc);
                        var inc = (byte) u1(code, pc + 1);
                        pc += 2;
                        yield new Insn(bci, op, slot, inc, new int[0], new int[0]);
                    }
                    case BRANCH_S2 -> {
                        var target = bci + s2(code, pc);
                        pc += 2;
                        yield new Insn(bci, op, 0, 0, new int[] { target }, new int[0]);
                    }
                    case BRANCH_S4 -> {
                        var target = bci + s4(code, pc);
                        pc += 4;
                        yield new Insn(bci, op, 0, 0, new int[] { target }, new int[0]);
                    }
                    case INTERFACE -> {
                        var v = u2(code, pc);
                        pc += 4;
                        yield new Insn(bci, op, v, 0, new int[0], new int[0]);
                    }
                    case DYNAMIC -> {
                        var v = u2(code, pc);
                        pc += 4;
                        yield new Insn(bci, op, v, 0, new int[0], new int[0]);
                    }
                    case NEWARRAY_SHAPE -> new Insn(bci, op, 0, u1(code, pc++), new int[0], new int[0]);
                    case MULTI -> {
                        var v = u2(code, pc);
                        var dims = u1(code, pc + 2);
                        pc += 3;
                        yield new Insn(bci, op, v, dims, new int[0], new int[0]);
                    }
                };
            }
            insns.add(insn);
        }
        return insns;
    }

    private enum Shape {
        NONE, LOCAL_U1, CP_U1, IMM_S1, IMM_S2, CP_U2, IINC_SHAPE, BRANCH_S2, BRANCH_S4, INTERFACE, DYNAMIC, NEWARRAY_SHAPE, MULTI
    }

    private static Shape operandShape(int op) {
        if (op == BIPUSH) {
            return Shape.IMM_S1;
        }
        if (op == SIPUSH) {
            return Shape.IMM_S2;
        }
        if (op == LDC) {
            return Shape.CP_U1;
        }
        if (op == LDC_W || op == LDC2_W || (op >= GETSTATIC && op <= INVOKESTATIC) || op == NEW || op == ANEWARRAY || op == CHECKCAST || op == INSTANCEOF) {
            return Shape.CP_U2;
        }
        if ((op >= ILOAD && op <= ALOAD) || (op >= ISTORE && op <= ASTORE) || op == RET) {
            return Shape.LOCAL_U1;
        }
        if (op == IINC) {
            return Shape.IINC_SHAPE;
        }
        if ((op >= IFEQ && op <= JSR) || op == IFNULL || op == IFNONNULL) {
            return Shape.BRANCH_S2;
        }
        if (op == GOTO_W || op == JSR_W) {
            return Shape.BRANCH_S4;
        }
        if (op == INVOKEINTERFACE) {
            return Shape.INTERFACE;
        }
        if (op == INVOKEDYNAMIC) {
            return Shape.DYNAMIC;
        }
        if (op == NEWARRAY) {
            return Shape.NEWARRAY_SHAPE;
        }
        if (op == MULTIANEWARRAY) {
            return Shape.MULTI;
        }
        return Shape.NONE;
    }

    private static int u1(byte[] b, int i) {
        return b[i] & 0xff;
    }

    private static int u2(byte[] b, int i) {
        return (u1(b, i) << 8) | u1(b, i + 1);
    }

    private static int s2(byte[] b, int i) {
        return (short) u2(b, i);
    }

    private static int s4(byte[] b, int i) {
        return (u1(b, i) << 24) | (u1(b, i + 1) << 16) | (u1(b, i + 2) << 8) | u1(b, i + 3);
    }
}
