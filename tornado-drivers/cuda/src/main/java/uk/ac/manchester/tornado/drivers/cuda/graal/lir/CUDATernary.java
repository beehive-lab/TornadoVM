/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssemblerConstants.CLOSE_PARENTHESIS;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssemblerConstants.OPEN_PARENTHESIS;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDATernaryOp;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDATernaryTemplate;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResultBuilder;

public class CUDATernary {

    /**
     * Abstract operation which consumes two inputs
     */
    protected static class TernaryConsumer extends CUDALIROp {

        @Opcode
        protected final CUDATernaryOp opcode;

        @Use
        protected Value x;
        @Use
        protected Value y;
        @Use
        protected Value z;

        protected TernaryConsumer(CUDATernaryOp opcode, LIRKind lirKind, Value x, Value y, Value z) {
            super(lirKind);
            this.opcode = opcode;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            opcode.emit(crb, x, y, z);
        }

        @Override
        public String toString() {
            return String.format("%s %s %s %s", opcode.toString(), x, y, z);
        }

    }

    public static class Expr extends TernaryConsumer {

        public Expr(CUDATernaryOp opcode, LIRKind lirKind, Value x, Value y, Value z) {
            super(opcode, lirKind, x, y, z);
        }
    }

    public static class Select extends TernaryConsumer {

        protected CUDALIROp condition;

        public Select(LIRKind lirKind, CUDALIROp condition, Value y, Value z) {
            super(CUDATernaryTemplate.SELECT, lirKind, null, y, z);
            this.condition = condition;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.emit(OPEN_PARENTHESIS);
            condition.emit(crb, asm);
            asm.emit(CLOSE_PARENTHESIS);
            asm.emit(" ? ");
            asm.emitValue(crb, y);
            asm.emit(" : ");
            asm.emitValue(crb, z);
        }
    }

    /**
     * CUDADriver intrinsic call which consumes three inputs
     */
    public static class Intrinsic extends TernaryConsumer {

        public Intrinsic(CUDATernaryOp opcode, LIRKind lirKind, Value x, Value y, Value z) {
            super(opcode, lirKind, x, y, z);
        }

        @Override
        public String toString() {
            return String.format("%s(%s, %s, %s)", opcode.toString(), x, y, z);
        }
    }

}
