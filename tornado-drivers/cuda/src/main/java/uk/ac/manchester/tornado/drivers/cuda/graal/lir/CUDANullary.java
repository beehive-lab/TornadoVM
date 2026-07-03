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

import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.lir.Opcode;

import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDANullaryIntrinsic;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDANullaryOp;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDANullaryTemplate;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResultBuilder;

public class CUDANullary {

    /**
     * Abstract operation which consumes no inputs
     */
    protected static class NullaryConsumer extends CUDALIROp {

        @Opcode
        protected final CUDANullaryOp opcode;

        protected NullaryConsumer(CUDANullaryOp opcode, LIRKind lirKind) {
            super(lirKind);
            this.opcode = opcode;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            opcode.emit(crb);
        }

        @Override
        public String toString() {
            return String.format("%s", opcode.toString());
        }
    }

    public static class Expr extends NullaryConsumer {

        public Expr(CUDANullaryOp opcode, LIRKind lirKind) {
            super(opcode, lirKind);
        }

    }

    public static class Parameter extends NullaryConsumer {

        public Parameter(String name, LIRKind lirKind) {
            super(new CUDANullaryTemplate(name), lirKind);
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.emit(opcode.toString());
        }
    }

    public static class Intrinsic extends NullaryConsumer {

        public Intrinsic(CUDANullaryIntrinsic opcode, LIRKind lirKind) {
            super(opcode, lirKind);
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            opcode.emit(crb);
            asm.emit("()");
        }

    }

}
