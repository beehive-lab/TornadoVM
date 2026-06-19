/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDABinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDABinaryOp;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResultBuilder;

public class CUDABinary {

    /**
     * Abstract operation which consumes two inputs
     */
    protected static class BinaryConsumer extends CUDALIROp {

        @Opcode
        protected final CUDABinaryOp opcode;

        @Use
        protected Value x;
        @Use
        protected Value y;

        protected BinaryConsumer(CUDABinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(lirKind);
            this.opcode = opcode;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            opcode.emit(crb, x, y);
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

        public Expr(CUDABinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }
    }

    public static class TestZeroExpression extends BinaryConsumer {

        public TestZeroExpression(CUDABinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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

        public TestNegateZeroExpression(CUDABinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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
     * CUDADriver intrinsic call which consumes two inputs
     */
    public static class Intrinsic extends BinaryConsumer {

        public Intrinsic(CUDABinaryIntrinsic opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public String toString() {
            return String.format("%s(%s, %s)", opcode.toString(), x, y);
        }
    }

    public static class Selector extends Expr {

        public Selector(CUDABinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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
