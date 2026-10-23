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
package uk.ac.manchester.tornado.tc.ir;

import java.util.List;

/**
 * What an instruction does. Op kinds are immutable records; the mutable part of an instruction
 * (operands, successors, position) lives in {@link Inst}. Passes dispatch with exhaustive pattern
 * {@code switch}es over this sealed hierarchy. Semantics follow the Java Language Specification
 * unless stated; see {@code ir-specification.md} section 1.3.
 */
public sealed interface Opcode {

    /** Mnemonic used in the text form. */
    String mnemonic();

    default boolean isTerminator() {
        return false;
    }

    /** True when the op has no side effects and its result depends only on its operands. */
    default boolean isPure() {
        return false;
    }

    // ---------------------------------------------------------------- constants

    /** Integer constant; the value is sign-extended into a {@code long} and truncated to the type. */
    record ConstInt(long value) implements Opcode {
        @Override
        public String mnemonic() {
            return "const";
        }

        @Override
        public boolean isPure() {
            return true;
        }
    }

    /** Floating-point constant; NaN, infinities and -0.0 are preserved. */
    record ConstFloat(double value) implements Opcode {
        @Override
        public String mnemonic() {
            return "const";
        }

        @Override
        public boolean isPure() {
            return true;
        }
    }

    // ---------------------------------------------------------------- arithmetic and logic

    enum BinaryOp {
        // Integer ops (Java semantics: wrap-around; div/rem truncate toward zero; shift counts masked).
        ADD("add", false), SUB("sub", false), MUL("mul", false), DIV("div", false), REM("rem", false),
        AND("and", false), OR("or", false), XOR("xor", false), SHL("shl", false), SHR("shr", false), USHR("ushr", false),
        // Floating-point ops (strict IEEE 754; the `contract` attribute permits FMA formation).
        FADD("fadd", true), FSUB("fsub", true), FMUL("fmul", true), FDIV("fdiv", true), FREM("frem", true);

        public final String text;
        public final boolean isFloat;

        BinaryOp(String text, boolean isFloat) {
            this.text = text;
            this.isFloat = isFloat;
        }

        /** Division by zero traps in Java, so only these are free of side effects. */
        public boolean canTrap() {
            return this == DIV || this == REM;
        }
    }

    record Binary(BinaryOp op) implements Opcode {
        @Override
        public String mnemonic() {
            return op.text;
        }

        @Override
        public boolean isPure() {
            return !op.canTrap();
        }
    }

    enum UnaryOp {
        NEG("neg", false), NOT("not", false), FNEG("fneg", true);

        public final String text;
        public final boolean isFloat;

        UnaryOp(String text, boolean isFloat) {
            this.text = text;
            this.isFloat = isFloat;
        }
    }

    record Unary(UnaryOp op) implements Opcode {
        @Override
        public String mnemonic() {
            return op.text;
        }

        @Override
        public boolean isPure() {
            return true;
        }
    }

    enum IntPredicate {
        EQ, NE, SLT, SLE, SGT, SGE, ULT, ULE, UGT, UGE;

        public String text() {
            return name().toLowerCase();
        }
    }

    record ICmp(IntPredicate predicate) implements Opcode {
        @Override
        public String mnemonic() {
            return "icmp." + predicate.text();
        }

        @Override
        public boolean isPure() {
            return true;
        }
    }

    /** Ordered predicates are false when either operand is NaN; unordered ones are true. */
    enum FloatPredicate {
        OEQ, ONE, OLT, OLE, OGT, OGE, UEQ, UNE, ULT, ULE, UGT, UGE, ORD, UNO;

        public String text() {
            return name().toLowerCase();
        }
    }

    record FCmp(FloatPredicate predicate) implements Opcode {
        @Override
        public String mnemonic() {
            return "fcmp." + predicate.text();
        }

        @Override
        public boolean isPure() {
            return true;
        }
    }

    record Select() implements Opcode {
        @Override
        public String mnemonic() {
            return "select";
        }

        @Override
        public boolean isPure() {
            return true;
        }
    }

    enum ConvertOp {
        SEXT("sext"), ZEXT("zext"), TRUNC("trunc"),
        /** Float to integer with Java semantics: saturating, NaN becomes 0. */
        F2I_SAT("f2i.sat"),
        /** Signed integer to float, round to nearest. */
        I2F("i2f"),
        FEXT("fext"), FTRUNC("ftrunc"), BITCAST("bitcast");

        public final String text;

        ConvertOp(String text) {
            this.text = text;
        }
    }

    record Convert(ConvertOp op) implements Opcode {
        @Override
        public String mnemonic() {
            return op.text;
        }

        @Override
        public boolean isPure() {
            return true;
        }
    }

    // ---------------------------------------------------------------- Java-level memory (pre-ABI)

    /** {@code array.load %array[%index]}. */
    record ArrayLoad() implements Opcode {
        @Override
        public String mnemonic() {
            return "array.load";
        }
    }

    /** {@code array.store %array[%index], %value}. */
    record ArrayStore() implements Opcode {
        @Override
        public String mnemonic() {
            return "array.store";
        }
    }

    record ArrayLength() implements Opcode {
        @Override
        public String mnemonic() {
            return "array.length";
        }

        @Override
        public boolean isPure() {
            return true;
        }
    }

    record FieldLoad(String field) implements Opcode {
        @Override
        public String mnemonic() {
            return "field.load";
        }
    }

    record FieldStore(String field) implements Opcode {
        @Override
        public String mnemonic() {
            return "field.store";
        }
    }

    // ---------------------------------------------------------------- pointer memory (post-ABI)

    /** {@code ptr.offset %pointer, %bytes}: byte offset within the same address space. */
    record PointerOffset() implements Opcode {
        @Override
        public String mnemonic() {
            return "ptr.offset";
        }

        @Override
        public boolean isPure() {
            return true;
        }
    }

    record Load() implements Opcode {
        @Override
        public String mnemonic() {
            return "load";
        }
    }

    record Store() implements Opcode {
        @Override
        public String mnemonic() {
            return "store";
        }
    }

    /** A fixed-size private or shared array; the result is a pointer into {@code space}. */
    record Alloca(AddressSpace space, Type element, int count) implements Opcode {
        @Override
        public String mnemonic() {
            return "alloca." + space.text();
        }
    }

    // ---------------------------------------------------------------- calls and intrinsics

    record Call(String callee) implements Opcode {
        @Override
        public String mnemonic() {
            return "call";
        }
    }

    /**
     * A TornadoVM, math, vector or target intrinsic, for example {@code tornado.thread_id.global<dim = 0>} or
     * {@code math.sqrt}. Semantics and per-dialect spellings come from the intrinsic registry.
     */
    record Intrinsic(String name) implements Opcode {
        @Override
        public String mnemonic() {
            return name;
        }

        /** Pure when the intrinsic registry says it has no effects; an unregistered intrinsic is not pure. */
        @Override
        public boolean isPure() {
            return IntrinsicRegistry.lookup(name).map(IntrinsicRegistry.Def::isPure).orElse(false);
        }
    }

    // ---------------------------------------------------------------- terminators

    record Branch() implements Opcode {
        @Override
        public String mnemonic() {
            return "br";
        }

        @Override
        public boolean isTerminator() {
            return true;
        }
    }

    record CondBranch() implements Opcode {
        @Override
        public String mnemonic() {
            return "cond_br";
        }

        @Override
        public boolean isTerminator() {
            return true;
        }
    }

    /** Multi-way branch; successor {@code i} is taken for {@code keys.get(i)}, the last one is the default. */
    record Switch(List<Long> keys) implements Opcode {
        public Switch {
            keys = List.copyOf(keys);
        }

        @Override
        public String mnemonic() {
            return "switch";
        }

        @Override
        public boolean isTerminator() {
            return true;
        }
    }

    record Return() implements Opcode {
        @Override
        public String mnemonic() {
            return "ret";
        }

        @Override
        public boolean isTerminator() {
            return true;
        }
    }

    record Unreachable() implements Opcode {
        @Override
        public String mnemonic() {
            return "unreachable";
        }

        @Override
        public boolean isTerminator() {
            return true;
        }
    }
}
