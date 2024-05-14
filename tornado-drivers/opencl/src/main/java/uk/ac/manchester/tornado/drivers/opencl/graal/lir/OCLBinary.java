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
package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.LIRInstruction.Use;
import jdk.graal.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

public class OCLBinary {

    /**
     * Abstract operation which consumes two inputs
     */
    protected static class BinaryConsumer extends OCLLIROp {

        @Opcode
        protected final OCLBinaryOp opcode;

        @Use
        protected Value x;
        @Use
        protected Value y;

        protected BinaryConsumer(OCLBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(lirKind);
            this.opcode = opcode;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
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

        public Expr(OCLBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }
    }

    public static class TestZeroExpression extends BinaryConsumer {

        public TestZeroExpression(OCLBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
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

        public TestNegateZeroExpression(OCLBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
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
     * OpenCL intrinsic call which consumes two inputs
     */
    public static class Intrinsic extends BinaryConsumer {

        public Intrinsic(OCLBinaryIntrinsic opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public String toString() {
            return String.format("%s(%s, %s)", opcode.toString(), x, y);
        }
    }

    public static class Selector extends Expr {

        public Selector(OCLBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
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
