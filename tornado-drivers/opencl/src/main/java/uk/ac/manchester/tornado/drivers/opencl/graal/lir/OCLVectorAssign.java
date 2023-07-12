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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLOp3;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLOp4;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLOp8;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

public class OCLVectorAssign {

    /**
     * OpenCL vector assignment expression
     */
    public static class Assign2Expr extends OCLLIROp {

        @Opcode
        protected final OCLOp opcode;

        @Use
        protected Value s0;
        @Use
        protected Value s1;

        public Assign2Expr(OCLOp opcode, OCLKind oclKind, Value s0, Value s1) {
            super(LIRKind.value(oclKind));
            this.opcode = opcode;
            this.s0 = s0;
            this.s1 = s1;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit(opcode.toString());
            asm.emit("(");
            asm.emitValueOrOp(crb, s0);
            asm.emit(", ");
            asm.emitValueOrOp(crb, s1);
            asm.emit(")");
        }
    }

    /**
     * OpenCL vector assignment expression
     */
    public static class Assign3Expr extends Assign2Expr {

        @Use
        protected Value s2;

        public Assign3Expr(OCLOp3 opcode, OCLKind kind, Value s0, Value s1, Value s2) {
            super(opcode, kind, s0, s1);
            this.s2 = s2;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit(opcode.toString());
            asm.emit("(");
            asm.emitValueOrOp(crb, s0);
            asm.emit(", ");
            asm.emitValueOrOp(crb, s1);
            asm.emit(", ");
            asm.emitValueOrOp(crb, s2);
            asm.emit(")");
        }

    }

    /**
     * OpenCL vector assignment expression
     */
    public static class Assign4Expr extends Assign3Expr {

        @Use
        protected Value s3;

        public Assign4Expr(OCLOp4 opcode, OCLKind kind, Value s0, Value s1, Value s2, Value s3) {
            super(opcode, kind, s0, s1, s2);
            this.s3 = s3;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit(opcode.toString());
            asm.emit("(");
            asm.emitValueOrOp(crb, s0);
            asm.emit(", ");
            asm.emitValueOrOp(crb, s1);
            asm.emit(", ");
            asm.emitValueOrOp(crb, s2);
            asm.emit(", ");
            asm.emitValueOrOp(crb, s3);
            asm.emit(")");
        }

    }

    /**
     * OpenCL vector assignment expression
     */
    public static class Assign8Expr extends Assign4Expr {

        @Use
        protected Value s4;
        @Use
        protected Value s5;
        @Use
        protected Value s6;
        @Use
        protected Value s7;

        public Assign8Expr(OCLOp8 opcode, OCLKind kind, Value s0, Value s1, Value s2, Value s3, Value s4, Value s5, Value s6, Value s7) {
            super(opcode, kind, s0, s1, s2, s3);
            this.s4 = s4;
            this.s5 = s5;
            this.s6 = s6;
            this.s7 = s7;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit(opcode.toString());
            asm.emit("(");
            asm.emitValueOrOp(crb, s0);
            asm.emit(", ");
            asm.emitValueOrOp(crb, s1);
            asm.emit(", ");
            asm.emitValueOrOp(crb, s2);
            asm.emit(", ");
            asm.emitValueOrOp(crb, s3);
            asm.emit(", ");
            asm.emitValueOrOp(crb, s4);
            asm.emit(", ");
            asm.emitValueOrOp(crb, s5);
            asm.emit(", ");
            asm.emitValueOrOp(crb, s6);
            asm.emit(", ");
            asm.emitValueOrOp(crb, s7);
            asm.emit(")");
        }

    }

}
