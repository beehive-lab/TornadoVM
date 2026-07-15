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

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.LIRInstruction.Use;
import jdk.graal.compiler.lir.Opcode;

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

        // CUDA built-in vector types (float2/3/4, int2/3/4, ...) have NO overloaded
        // arithmetic operators, so vector add/sub/mul/div must be emitted
        // componentwise via the make_<type>N(...) constructor.
        private static final String[] COMPONENTS = { "x", "y", "z", "w" };

        public Expr(CUDABinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            CUDAKind resultKind = getCUDAPlatformKind();
            if (resultKind == null || !resultKind.isVector()) {
                super.emit(crb, asm);
                return;
            }

            int length = resultKind.getVectorLength();
            if (length < 2 || length > 4) {
                throw new uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException(
                        "CUDA backend does not support arithmetic on vector width " + length + ".");
            }

            boolean xIsVector = x.getValueKind() != null && (x.getPlatformKind() instanceof CUDAKind) && ((CUDAKind) x.getPlatformKind()).isVector();
            boolean yIsVector = y.getValueKind() != null && (y.getPlatformKind() instanceof CUDAKind) && ((CUDAKind) y.getPlatformKind()).isVector();

            asm.beginStackPush();
            asm.emitValueOrOp(crb, x);
            final String xs = asm.getLastOp();
            asm.emitValueOrOp(crb, y);
            final String ys = asm.getLastOp();
            asm.endStackPush();

            String op = opcode.toString();

            if (resultKind.getElementKind() == CUDAKind.HALF && length == 2) {
                // cuda_fp16.h packed half2 intrinsics keep the pair in one 32-bit
                // register instead of decomposing into scalar half lanes.
                String intrinsic = switch (op) {
                    case "+" -> "__hadd2";
                    case "-" -> "__hsub2";
                    case "*" -> "__hmul2";
                    case "/" -> "__h2div";
                    default -> null;
                };
                if (intrinsic != null) {
                    String xe = xIsVector ? xs : "__half2half2(" + xs + ")";
                    String ye = yIsVector ? ys : "__half2half2(" + ys + ")";
                    asm.emit(intrinsic + "(" + xe + ", " + ye + ")");
                    return;
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("make_").append(resultKind.getElementKind().toString()).append(length).append("(");
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                String c = COMPONENTS[i];
                String xc = xIsVector ? ("(" + xs + ")." + c) : ("(" + xs + ")");
                String yc = yIsVector ? ("(" + ys + ")." + c) : ("(" + ys + ")");
                sb.append(xc).append(" ").append(op).append(" ").append(yc);
            }
            sb.append(")");
            asm.emit(sb.toString());
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
