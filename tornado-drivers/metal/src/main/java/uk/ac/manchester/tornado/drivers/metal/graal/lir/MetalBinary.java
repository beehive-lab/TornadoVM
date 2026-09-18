/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.graal.lir;

import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.lir.LIRInstruction.Use;
import tornado.graal.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryOp;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompilationResultBuilder;

public class MetalBinary {

    /**
     * Abstract operation which consumes two inputs
     */
    protected static class BinaryConsumer extends MetalLIROp {

        @Opcode
        protected final MetalBinaryOp opcode;

        @Use
        protected Value x;
        @Use
        protected Value y;

        protected BinaryConsumer(MetalBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(lirKind);
            this.opcode = opcode;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            if (wrapsOnOverflow()) {
                emitWrapping(crb, asm);
            } else {
                opcode.emit(crb, x, y);
            }
        }

        /**
         * Whether this operation has to be emitted with wrap-around semantics. Java defines
         * {@code +}, {@code -}, {@code *} and {@code <<} on {@code int} and {@code long} to wrap;
         * C++ leaves signed overflow undefined and the device compiler acts on it. Only the signed
         * integer kinds are affected: unsigned arithmetic already wraps, float overflow is defined.
         */
        private boolean wrapsOnOverflow() {
            if (opcode != MetalAssembler.MetalBinaryOp.ADD && opcode != MetalAssembler.MetalBinaryOp.SUB //
                    && opcode != MetalAssembler.MetalBinaryOp.MUL && opcode != MetalAssembler.MetalBinaryOp.BITWISE_LEFT_SHIFT) {
                return false;
            }
            MetalKind kind = getMetalPlatformKind();
            return kind == MetalKind.INT || kind == MetalKind.LONG;
        }

        /** Emits {@code (int) ((uint) x OP y)}; the left cast makes the whole expression unsigned. */
        private void emitWrapping(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            MetalKind kind = getMetalPlatformKind();
            String unsigned = (kind == MetalKind.INT ? MetalKind.UINT : MetalKind.ULONG).toString();
            asm.emit("(" + kind + ") ((" + unsigned + ") ");
            asm.emitValueOrOp(crb, x);
            asm.space();
            asm.emit(opcode.toString());
            asm.space();
            if (opcode == MetalAssembler.MetalBinaryOp.BITWISE_LEFT_SHIFT) {
                // Java masks shift distances; an out-of-range C shift is undefined.
                asm.emit("(");
                asm.emitValueOrOp(crb, y);
                asm.emit(kind == MetalKind.INT ? " & 31)" : " & 63)");
            } else {
                asm.emitValueOrOp(crb, y);
            }
            asm.emit(")");
        }

        public MetalBinaryOp getOpcode() {
            return opcode;
        }

        public Value getX() {
            return x;
        }

        public Value getY() {
            return y;
        }

        @Override
        public String toString() {
            return String.format("%s %s %s", opcode.toString(), x, y);
        }

    }

    public static class Expr extends BinaryConsumer {

        public Expr(MetalBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }
    }

    public static class TestZeroExpression extends BinaryConsumer {

        public TestZeroExpression(MetalBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.emit("(");
            asm.emitValue(crb, x);
            asm.emit(" ");
            asm.emit(opcode.toString());
            asm.emit(" ");
            asm.emitValue(crb, y);
            asm.emit(")");
            asm.emit(" == 0");
        }
    }

    public static class TestNegateZeroExpression extends BinaryConsumer {

        public TestNegateZeroExpression(MetalBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.emit("!((");
            asm.emitValue(crb, x);
            asm.emit(" ");
            asm.emit(opcode.toString());
            asm.emit(" ");
            asm.emitValue(crb, y);
            asm.emit(")");
            asm.emit(" == 0)");
        }
    }

    /**
     * Metal intrinsic call which consumes two inputs
     */
    public static class Intrinsic extends BinaryConsumer {

        public Intrinsic(MetalBinaryIntrinsic opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public String toString() {
            return String.format("%s(%s, %s)", opcode.toString(), x, y);
        }
    }

    public static class Selector extends Expr {

        public Selector(MetalBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.emitValue(crb, x);
            asm.emit(opcode.toString());
            asm.emitValue(crb, y);
        }

        @Override
        public String toString() {
            return String.format("%s.%s", opcode.toString(), x, y);
        }

    }
}
