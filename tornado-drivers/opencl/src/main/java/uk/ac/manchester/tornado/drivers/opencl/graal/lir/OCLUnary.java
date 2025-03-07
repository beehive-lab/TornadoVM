/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2022, 2024-2025, APT Group, Department of Computer Science,
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

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryTemplate;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;
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

        UnaryConsumer(OCLUnaryOp opcode, LIRKind lirKind, Value value) {
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

    @Opcode("AtomAdd")
    public static class AtomOperation extends UnaryConsumer {

        @Use
        OCLUnary.MemoryAccess address;

        @Use
        OCLAssembler.OCLUnaryIntrinsic atomicOp;

        @Use
        Value inc;

        LIRKind destLirKind;

        public AtomOperation(OCLUnaryOp opcode, LIRKind lirKind, Value value, OCLUnary.MemoryAccess address, OCLAssembler.OCLUnaryIntrinsic atomicOp, Value inc) {
            super(opcode, lirKind, value);

            this.address = address;
            this.atomicOp = atomicOp;
            this.inc = inc;
            this.destLirKind = lirKind;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            atomicOp.emit(crb);
            asm.emitSymbol(OCLAssemblerConstants.OPEN_PARENTHESIS);
            asm.emitSymbol(OCLAssemblerConstants.OPEN_PARENTHESIS);
            asm.emitSymbol(OCLAssemblerConstants.VOLATILE);
            asm.space();
            asm.emitSymbol(address.getBase().getMemorySpace().name());
            asm.space();
            asm.emit(destLirKind.getPlatformKind().toString().toLowerCase());
            asm.space();
            asm.emitSymbol(OCLAssemblerConstants.MULT);
            asm.emitSymbol(OCLAssemblerConstants.CLOSE_PARENTHESIS);
            asm.space();
            address.emit(crb, asm);
            asm.emitSymbol(OCLAssemblerConstants.EXPR_DELIMITER);
            asm.space();
            asm.emitValue(crb, inc);
            asm.emitSymbol(OCLAssemblerConstants.CLOSE_PARENTHESIS);
        }
    }

    public static class IntrinsicAtomicFetch extends UnaryConsumer {

        public IntrinsicAtomicFetch(OCLUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit(toString());
        }

        @Override
        public String toString() {
            return String.format("%s(&%s, 1, memory_order_relaxed)", opcode.toString(), value.toString());
        }
    }

    public enum AtomicOperator {
        INCREMENT_AND_GET, GET_AND_INCREMENT, DECREMENT_AND_GET, GET_AND_DECREMENT
    }

    public static class IntrinsicAtomicOperator extends UnaryConsumer {

        private static final String arrayName = OCLArchitecture.atomicSpace.getName();
        private int index;
        private final AtomicOperator atomicOperator;

        public IntrinsicAtomicOperator(OCLUnaryOp opcode, LIRKind lirKind, Value value, int index, AtomicOperator atomicOperator) {
            super(opcode, lirKind, value);
            this.index = index;
            this.atomicOperator = atomicOperator;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit(toString());
        }

        @Override
        public String toString() {
            switch (atomicOperator) {
                case INCREMENT_AND_GET -> {
                    return String.format("%s(&%s[%s]) + %d", opcode.toString(), arrayName, index, 1);
                }
                case DECREMENT_AND_GET -> {
                    return String.format("%s(&%s[%s]) - %d", opcode.toString(), arrayName, index, 1);
                }
                case GET_AND_INCREMENT -> {
                    return String.format("%s(&%s[%s])", opcode.toString(), arrayName, index);
                }
                case GET_AND_DECREMENT -> {
                    return String.format("%s(&%s[%s])", opcode.toString(), arrayName, index);
                }
            }
            return "";
        }
    }

    public static class IntrinsicAtomicGet extends UnaryConsumer {

        private static final String arrayName = OCLArchitecture.atomicSpace.getName();
        private int index;

        public IntrinsicAtomicGet(OCLUnaryOp opcode, LIRKind lirKind, Value value, int index) {
            super(opcode, lirKind, value);
            this.index = index;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit(toString());
        }

        @Override
        public String toString() {
            return String.format("%s[%s]", arrayName, index);
        }
    }

    public static class IntrinsicAtomicDeclaration extends UnaryConsumer {

        AllocatableValue lhs;

        /*
         * The opcode is the initializer intrinsic to use
         */
        public IntrinsicAtomicDeclaration(OCLUnaryOp opcode, AllocatableValue lhs, Value initialValue) {
            super(opcode, LIRKind.Illegal, initialValue);
            this.lhs = lhs;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            StringBuffer lineGlobalScope = new StringBuffer();
            lineGlobalScope.append("__global atomic_int ");
            lineGlobalScope.append(asm.getStringValue(crb, lhs));
            lineGlobalScope.append(OCLAssemblerConstants.ASSIGN);
            lineGlobalScope.append(opcode.toString());
            lineGlobalScope.append(OCLAssemblerConstants.OPEN_PARENTHESIS);
            lineGlobalScope.append(asm.getStringValue(crb, value));
            lineGlobalScope.append(OCLAssemblerConstants.CLOSE_PARENTHESIS);
            lineGlobalScope.append(OCLAssemblerConstants.STMT_DELIMITER);
            lineGlobalScope.append("\n");
            asm.emitLineGlobal(lineGlobalScope.toString());
        }
    }

    public static class LoadOCLKernelContext extends UnaryConsumer {

        public LoadOCLKernelContext(OCLUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit(opcode.toString());
            asm.emit("[");
            asm.emitValueOrOp(crb, value);
            asm.emit("]");
        }

        @Override
        public String toString() {
            return String.format("%s[%s] ", opcode.toString(), value);
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
        private Value index;

        MemoryAccess(OCLMemoryBase base, Value value) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
        }

        MemoryAccess(OCLMemoryBase base, Value value, Value index) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
            this.index = index;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emitValue(crb, value);
        }

        public OCLMemoryBase getBase() {
            return base;
        }

        public Value getIndex() {
            return index;
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
            OCLKind oclKind = getOCLPlatformKind();
            asm.emit(((OCLUnaryTemplate) opcode).getTemplate(), base.getMemorySpace().name() + " " + oclKind.toString());
        }

        OCLMemorySpace getMemorySpace() {
            return base.getMemorySpace();
        }

    }

}
