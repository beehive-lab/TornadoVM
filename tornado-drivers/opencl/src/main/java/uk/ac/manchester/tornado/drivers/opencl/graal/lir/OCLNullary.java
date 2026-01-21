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
import jdk.graal.compiler.lir.Opcode;

import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryIntrinsic;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryTemplate;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

public class OCLNullary {

    /**
     * Abstract operation which consumes no inputs
     */
    protected static class NullaryConsumer extends OCLLIROp {

        @Opcode
        protected final OCLNullaryOp opcode;

        protected NullaryConsumer(OCLNullaryOp opcode, LIRKind lirKind) {
            super(lirKind);
            this.opcode = opcode;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            opcode.emit(crb);
        }

        @Override
        public String toString() {
            return String.format("%s", opcode.toString());
        }
    }

    public static class Expr extends NullaryConsumer {

        public Expr(OCLNullaryOp opcode, LIRKind lirKind) {
            super(opcode, lirKind);
        }

    }

    public static class Parameter extends NullaryConsumer {

        public Parameter(String name, LIRKind lirKind) {
            super(new OCLNullaryTemplate(name), lirKind);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit(opcode.toString());
        }
    }

    public static class Intrinsic extends NullaryConsumer {

        public Intrinsic(OCLNullaryIntrinsic opcode, LIRKind lirKind) {
            super(opcode, lirKind);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            opcode.emit(crb);
            asm.emit("()");
        }

    }

}
