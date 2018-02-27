/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import static uk.ac.manchester.tornado.common.Tornado.OPENCL_USE_RELATIVE_ADDRESSES;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.ADDRESS_OF;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.SQUARE_BRACKETS_CLOSE;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.SQUARE_BRACKETS_OPEN;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryTemplate;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.meta.OCLMemorySpace;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLBarrierNode.OCLMemFenceFlags;

public class OCLUnary {

    /**
     * Abstract operation which consumes one inputs
     */
    protected static class UnaryConsumer extends OCLLIROp {

        @Opcode
        protected final OCLUnaryOp opcode;

        @Use
        protected Value value;

        protected UnaryConsumer(OCLUnaryOp opcode, LIRKind lirKind, Value value) {
            super(lirKind);
            this.opcode = opcode;
            this.value = value;
        }

        public Value getValue() {
            return value;
        }

        public OCLUnaryOp getOpcode() {
            return opcode;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            opcode.emit(crb, value);
        }

        @Override
        public String toString() {
            return String.format("%s %s", opcode.toString(), value);
        }

    }

    public static class Expr extends UnaryConsumer {

        public Expr(OCLUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

    }

    public static class Intrinsic extends UnaryConsumer {

        public Intrinsic(OCLUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", opcode.toString(), value);
        }

    }

    public static class Barrier extends UnaryConsumer {

        OCLMemFenceFlags flags;

        public Barrier(OCLUnaryOp opcode, OCLMemFenceFlags flags) {
            super(opcode, LIRKind.Illegal, null);
            this.flags = flags;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit(toString());
        }

        @Override
        public String toString() {
            return String.format("%s(CLK_%s_MEM_FENCE)", opcode.toString(), flags.toString().toUpperCase());
        }

    }

    public static class FloatCast extends UnaryConsumer {

        public FloatCast(OCLUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit("isnan(");
            asm.emitValueOrOp(crb, value);
            asm.emit(")? 0 : ");
            opcode.emit(crb, value);
        }

        @Override
        public String toString() {
            return String.format("isnan(%s) ? 0 : %s %s", value, opcode.toString(), value);
        }
    }

    public static class MemoryAccess extends UnaryConsumer {

        private final OCLMemoryBase base;
        private final boolean needsBase;

        public MemoryAccess(OCLMemoryBase base, Value value, boolean needsBase) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
            this.needsBase = needsBase;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {

            if (needsBase || OPENCL_USE_RELATIVE_ADDRESSES) {
                asm.emitSymbol(ADDRESS_OF);
                asm.emit(base.name);
                asm.emitSymbol(SQUARE_BRACKETS_OPEN);
                asm.emitValue(crb, value);
                asm.emitSymbol(SQUARE_BRACKETS_CLOSE);
            } else {
                asm.emitValue(crb, value);
            }
        }

        public OCLMemoryBase getBase() {
            return base;
        }

        @Override
        public String toString() {
            return String.format("%s", value);
        }
    }

    public static class OCLAddressCast extends UnaryConsumer {

        private final OCLMemoryBase base;

        public OCLAddressCast(OCLMemoryBase base, LIRKind lirKind) {
            super(OCLUnaryTemplate.CAST_TO_POINTER, lirKind, null);
            this.base = base;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            OCLKind oclKind = (OCLKind) getPlatformKind();
            asm.emit(((OCLUnaryTemplate) opcode).getTemplate(), base.memorySpace.name() + " " + oclKind.toString());
        }

        public OCLMemorySpace getMemorySpace() {
            return base.memorySpace;
        }

    }

}
