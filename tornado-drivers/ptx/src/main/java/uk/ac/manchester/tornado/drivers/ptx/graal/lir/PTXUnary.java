/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */

package uk.ac.manchester.tornado.drivers.ptx.graal.lir;

import static jdk.graal.compiler.lir.LIRInstruction.Use;
import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture.paramSpace;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXUnaryOp;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.COMMA;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.SPACE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.SQUARE_BRACKETS_CLOSE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.SQUARE_BRACKETS_OPEN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.TAB;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.Variable;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture.PTXMemoryBase;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.ptx.graal.meta.PTXMemorySpace;

public class PTXUnary {

    /**
     * Abstract operation which consumes one input
     */
    protected static class UnaryConsumer extends PTXLIROp {
        @Use
        protected Value value;

        @Opcode
        protected PTXUnaryOp opcode;

        UnaryConsumer(PTXUnaryOp opcode, LIRKind lirKind, Value value) {
            super(lirKind);
            this.value = value;
            this.opcode = opcode;
        }

        public Value getValue() {
            return value;
        }

        public PTXUnaryOp getOpcode() {
            return opcode;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
            opcode.emit(crb, value, dest);
        }
    }

    public static class Expr extends UnaryConsumer {
        public Expr(PTXUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }
    }

    public static class Intrinsic extends UnaryConsumer {
        public Intrinsic(PTXUnaryOp opCode, LIRKind lirKind, Value value) {
            super(opCode, lirKind, value);
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", opcode.toString(), value);
        }
    }

    public static class Barrier extends PTXUnary.UnaryConsumer {

        private final int ctaInstance;
        private final int numberOfThreads;

        public Barrier(PTXAssembler.PTXUnaryOp opcode, int ctaInstance, int numberOfThreads) {
            super(opcode, LIRKind.Illegal, null);
            this.ctaInstance = ctaInstance;
            this.numberOfThreads = numberOfThreads;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
            opcode.emitOpcode(asm);
            asm.emitSymbol(TAB);
            asm.emitSymbol(String.valueOf(ctaInstance));
            if (numberOfThreads != -1) {
                asm.emitSymbol(COMMA);
                asm.emitSymbol(SPACE);
                asm.emitInt(numberOfThreads);
            }
        }

    }

    public static class MemoryAccess extends UnaryConsumer {

        private final PTXMemoryBase base;
        private Value index;
        private String name;

        MemoryAccess(PTXMemoryBase base, Value value) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
        }

        public MemoryAccess(PTXMemoryBase base, Value value, Value index) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
            this.index = index;
        }

        public MemoryAccess(String name) {
            super(null, LIRKind.Illegal, null);
            this.base = paramSpace;
            this.name = name;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
            if (isSharedMemoryAccess()) {
                asm.emitValue(value);
                asm.emitSymbol(SQUARE_BRACKETS_OPEN);
                asm.emitValue(index);
                asm.emitSymbol(SQUARE_BRACKETS_CLOSE);
            } else {
                asm.emitSymbol(SQUARE_BRACKETS_OPEN);
                if (name != null) {
                    asm.emit(name);
                }
                if (value != null) {
                    asm.emitValue(value);
                }
                if (index != null && ((ConstantValue) index).getJavaConstant().asInt() != 0) {
                    asm.emitConstant((ConstantValue) index);
                }
                asm.emitSymbol(SQUARE_BRACKETS_CLOSE);
            }
        }

        public void emit(PTXAssembler asm, int index) {
            if (isSharedMemoryAccess()) {
                if (value != null) {
                    asm.emitValue(value);
                }
                asm.emitSymbol(SQUARE_BRACKETS_OPEN);
                asm.emitConstant(index);
            } else {
                asm.emitSymbol(SQUARE_BRACKETS_OPEN);
                if (name != null) {
                    asm.emit(name);
                }
                if (value != null) {
                    asm.emitValue(value);
                }
                if (index != 0) {
                    asm.emitConstant(index);
                }
            }
            asm.emitSymbol(SQUARE_BRACKETS_CLOSE);
        }

        private boolean isSharedMemoryAccess() {
            return base.memorySpace.index() == PTXMemorySpace.SHARED.index();
        }

        public PTXMemoryBase getBase() {
            return base;
        }

        @Override
        public String toString() {
            return String.format("%s", value);
        }
    }

}
