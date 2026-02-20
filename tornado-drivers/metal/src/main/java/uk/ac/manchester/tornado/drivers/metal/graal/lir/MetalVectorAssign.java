/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal.graal.lir;

import jdk.vm.ci.meta.Value;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp3;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp4;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp8;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompilationResultBuilder;

public class MetalVectorAssign {

    /**
     * Metal vector assignment expression
     */
    public static class Assign2Expr extends MetalLIROp {

        @Opcode
        protected final MetalOp opcode;

        @Use
        protected Value s0;
        @Use
        protected Value s1;

        public Assign2Expr(MetalOp opcode, MetalKind oclKind, Value s0, Value s1) {
            super(LIRKind.value(oclKind));
            this.opcode = opcode;
            this.s0 = s0;
            this.s1 = s1;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
              // Emit Metal MSL vector constructor for 2 elements
            asm.emit("float2(");
            asm.emitValue(crb, s0);
            asm.emit(", ");
            asm.emitValue(crb, s1);
            asm.emit(")");
        }
    }

    /**
     * Metal vector assignment expression
     */
    public static class Assign3Expr extends Assign2Expr {

        @Use
        protected Value s2;

        public Assign3Expr(MetalOp3 opcode, MetalKind kind, Value s0, Value s1, Value s2) {
            super(opcode, kind, s0, s1);
            this.s2 = s2;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
              // Emit Metal MSL vector constructor for 3 elements
            asm.emit("float3(");
            asm.emitValue(crb, s0);
            asm.emit(", ");
            asm.emitValue(crb, s1);
            asm.emit(", ");
            asm.emitValue(crb, s2);
            asm.emit(")");
        }

    }

    /**
     * Metal vector assignment expression
     */
    public static class Assign4Expr extends Assign3Expr {

        @Use
        protected Value s3;

        public Assign4Expr(MetalOp4 opcode, MetalKind kind, Value s0, Value s1, Value s2, Value s3) {
            super(opcode, kind, s0, s1, s2);
            this.s3 = s3;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
              // Emit Metal MSL vector constructor for 4 elements
            asm.emit("float4(");
            asm.emitValue(crb, s0);
            asm.emit(", ");
            asm.emitValue(crb, s1);
            asm.emit(", ");
            asm.emitValue(crb, s2);
            asm.emit(", ");
            asm.emitValue(crb, s3);
            asm.emit(")");
        }

    }

    /**
     * Metal vector assignment expression
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

        public Assign8Expr(MetalOp8 opcode, MetalKind kind, Value s0, Value s1, Value s2, Value s3, Value s4, Value s5, Value s6, Value s7) {
            super(opcode, kind, s0, s1, s2, s3);
            this.s4 = s4;
            this.s5 = s5;
            this.s6 = s6;
            this.s7 = s7;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
              // Emit Metal MSL vector constructor for 8 elements
            asm.emit("float8(");
            asm.emitValue(crb, s0);
            asm.emit(", ");
            asm.emitValue(crb, s1);
            asm.emit(", ");
            asm.emitValue(crb, s2);
            asm.emit(", ");
            asm.emitValue(crb, s3);
            asm.emit(", ");
            asm.emitValue(crb, s4);
            asm.emit(", ");
            asm.emitValue(crb, s5);
            asm.emit(", ");
            asm.emitValue(crb, s6);
            asm.emit(", ");
            asm.emitValue(crb, s7);
            asm.emit(")");
        }

    }

    /**
     * Metal vector assignment expression
     */
    public static class Assign16Expr extends Assign8Expr {

        @Use
        protected Value s8;
        @Use
        protected Value s9;
        @Use
        protected Value s10;
        @Use
        protected Value s11;
        @Use
        protected Value s12;
        @Use
        protected Value s13;
        @Use
        protected Value s14;
        @Use
        protected Value s15;

        public Assign16Expr(MetalOp8 opcode, MetalKind kind, Value s0, Value s1, Value s2, Value s3, Value s4, Value s5, Value s6, Value s7, Value s8, Value s9, Value s10, Value s11, Value s12, Value s13,
                Value s14, Value s15) {
            super(opcode, kind, s0, s1, s2, s3, s4, s5, s6, s7);
            this.s8 = s8;
            this.s9 = s9;
            this.s10 = s10;
            this.s11 = s11;
            this.s12 = s12;
            this.s13 = s13;
            this.s14 = s14;
            this.s15 = s15;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
              // Emit Metal MSL vector constructor for 16 elements
            asm.emit("float16(");
            asm.emitValue(crb, s0);
            asm.emit(", ");
            asm.emitValue(crb, s1);
            asm.emit(", ");
            asm.emitValue(crb, s2);
            asm.emit(", ");
            asm.emitValue(crb, s3);
            asm.emit(", ");
            asm.emitValue(crb, s4);
            asm.emit(", ");
            asm.emitValue(crb, s5);
            asm.emit(", ");
            asm.emitValue(crb, s6);
            asm.emit(", ");
            asm.emitValue(crb, s7);
            asm.emit(", ");
            asm.emitValue(crb, s8);
            asm.emit(", ");
            asm.emitValue(crb, s9);
            asm.emit(", ");
            asm.emitValue(crb, s10);
            asm.emit(", ");
            asm.emitValue(crb, s11);
            asm.emit(", ");
            asm.emitValue(crb, s12);
            asm.emit(", ");
            asm.emitValue(crb, s13);
            asm.emit(", ");
            asm.emitValue(crb, s14);
            asm.emit(", ");
            asm.emitValue(crb, s15);
            asm.emit(")");
        }

    }

}
