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
package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.lir.LIRInstruction.Use;
import tornado.graal.compiler.lir.Opcode;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture.CUDAMemoryBase;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAUnaryOp;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAUnaryTemplate;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssemblerConstants;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.meta.CUDAMemorySpace;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDABarrierNode.CUDAMemFenceFlags;

public class CUDAUnary {

    /**
     * Abstract operation which consumes one inputs
     */
    protected static class UnaryConsumer extends CUDALIROp {

        @Opcode
        protected final CUDAUnaryOp opcode;

        @Use
        protected Value value;

        UnaryConsumer(CUDAUnaryOp opcode, LIRKind lirKind, Value value) {
            super(lirKind);
            this.opcode = opcode;
            this.value = value;
        }

        public Value getValue() {
            return value;
        }

        public CUDAUnaryOp getOpcode() {
            return opcode;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            opcode.emit(crb, value);
        }

        @Override
        public String toString() {
            return String.format("%s %s", opcode.toString(), value);
        }

    }

    public static class Expr extends UnaryConsumer {

        public Expr(CUDAUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

    }

    public static class Intrinsic extends UnaryConsumer {

        public Intrinsic(CUDAUnaryOp opcode, LIRKind lirKind, Value value) {
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
        CUDAUnary.MemoryAccess address;

        @Use
        CUDAAssembler.CUDAUnaryIntrinsic atomicOp;

        @Use
        Value inc;

        LIRKind destLirKind;

        public AtomOperation(CUDAUnaryOp opcode, LIRKind lirKind, Value value, CUDAUnary.MemoryAccess address, CUDAAssembler.CUDAUnaryIntrinsic atomicOp, Value inc) {
            super(opcode, lirKind, value);

            this.address = address;
            this.atomicOp = atomicOp;
            this.inc = inc;
            this.destLirKind = lirKind;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            // Native CUDA atomic: atomicAdd((<type> *) addr, inc). No volatile and no
            // address-space qualifier on the pointer cast.
            // CUDA has no atomicAdd overload for signed/unsigned 64-bit integers other
            // than the 'unsigned long long int' one, so cast pointer and value to it.
            CUDAKind kind = (CUDAKind) destLirKind.getPlatformKind();
            boolean is64BitInt = (kind == CUDAKind.LONG || kind == CUDAKind.ULONG);
            String pointerType = is64BitInt ? "unsigned long long" : kind.toString();
            atomicOp.emit(crb);
            asm.emitSymbol(CUDAAssemblerConstants.OPEN_PARENTHESIS);
            asm.emitSymbol(CUDAAssemblerConstants.OPEN_PARENTHESIS);
            asm.emit(pointerType);
            asm.space();
            asm.emitSymbol(CUDAAssemblerConstants.MULT);
            asm.emitSymbol(CUDAAssemblerConstants.CLOSE_PARENTHESIS);
            asm.space();
            address.emit(crb, asm);
            asm.emitSymbol(CUDAAssemblerConstants.EXPR_DELIMITER);
            asm.space();
            if (is64BitInt) {
                asm.emit("(unsigned long long) (");
                asm.emitValue(crb, inc);
                asm.emit(")");
            } else {
                asm.emitValue(crb, inc);
            }
            asm.emitSymbol(CUDAAssemblerConstants.CLOSE_PARENTHESIS);
        }
    }

    public static class IntrinsicAtomicFetch extends UnaryConsumer {

        public IntrinsicAtomicFetch(CUDAUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.emit(toString());
        }

        @Override
        public String toString() {
            // OpenCL atomic_fetch_add_explicit(&v, 1, order) -> CUDA atomicAdd(&v, 1),
            // which atomically adds and returns the previous value.
            return String.format("atomicAdd(&%s, 1)", value.toString());
        }
    }

    public enum AtomicOperator {
        INCREMENT_AND_GET, GET_AND_INCREMENT, DECREMENT_AND_GET, GET_AND_DECREMENT
    }

    public static class IntrinsicAtomicOperator extends UnaryConsumer {

        private static final String arrayName = CUDAArchitecture.atomicSpace.getName();
        private int index;
        private final AtomicOperator atomicOperator;

        public IntrinsicAtomicOperator(CUDAUnaryOp opcode, LIRKind lirKind, Value value, int index, AtomicOperator atomicOperator) {
            super(opcode, lirKind, value);
            this.index = index;
            this.atomicOperator = atomicOperator;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.emit(toString());
        }

        @Override
        public String toString() {
            // CUDA atomicAdd/atomicSub atomically apply the operation and return the
            // PREVIOUS value. atomic_inc(p) == atomicAdd(p, 1); atomic_dec(p) ==
            // atomicSub(p, 1). For the *_AND_GET variants we adjust by +/-1 to obtain
            // the new value from the returned old value.
            switch (atomicOperator) {
                case INCREMENT_AND_GET -> {
                    return String.format("atomicAdd(&%s[%s], 1) + 1", arrayName, index);
                }
                case DECREMENT_AND_GET -> {
                    return String.format("atomicSub(&%s[%s], 1) - 1", arrayName, index);
                }
                case GET_AND_INCREMENT -> {
                    return String.format("atomicAdd(&%s[%s], 1)", arrayName, index);
                }
                case GET_AND_DECREMENT -> {
                    return String.format("atomicSub(&%s[%s], 1)", arrayName, index);
                }
            }
            return "";
        }
    }

    public static class IntrinsicAtomicGet extends UnaryConsumer {

        private static final String arrayName = CUDAArchitecture.atomicSpace.getName();
        private int index;

        public IntrinsicAtomicGet(CUDAUnaryOp opcode, LIRKind lirKind, Value value, int index) {
            super(opcode, lirKind, value);
            this.index = index;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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
        public IntrinsicAtomicDeclaration(CUDAUnaryOp opcode, AllocatableValue lhs, Value initialValue) {
            super(opcode, LIRKind.Illegal, initialValue);
            this.lhs = lhs;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            StringBuffer lineGlobalScope = new StringBuffer();
            lineGlobalScope.append("int ");
            lineGlobalScope.append(asm.getStringValue(crb, lhs));
            lineGlobalScope.append(CUDAAssemblerConstants.ASSIGN);
            lineGlobalScope.append(opcode.toString());
            lineGlobalScope.append(CUDAAssemblerConstants.OPEN_PARENTHESIS);
            lineGlobalScope.append(asm.getStringValue(crb, value));
            lineGlobalScope.append(CUDAAssemblerConstants.CLOSE_PARENTHESIS);
            lineGlobalScope.append(CUDAAssemblerConstants.STMT_DELIMITER);
            lineGlobalScope.append("\n");
            asm.emitLineGlobal(lineGlobalScope.toString());
        }
    }

    public static class LoadCUDAKernelContext extends UnaryConsumer {

        public LoadCUDAKernelContext(CUDAUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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

        CUDAMemFenceFlags flags;

        public Barrier(CUDAUnaryOp opcode, CUDAMemFenceFlags flags) {
            super(opcode, LIRKind.Illegal, null);
            this.flags = flags;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.emit(toString());
        }

        @Override
        public String toString() {
            // CUDA C has no OpenCL barrier(...) built-in; all fences map to __syncthreads().
            return "__syncthreads()";
        }

    }

    public static class FloatCast extends UnaryConsumer {

        public FloatCast(CUDAUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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

        private final CUDAMemoryBase base;
        private Value index;

        MemoryAccess(CUDAMemoryBase base, Value value) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
        }

        MemoryAccess(CUDAMemoryBase base, Value value, Value index) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
            this.index = index;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.emitValue(crb, value);
        }

        public CUDAMemoryBase getBase() {
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

    public static class CUDAAddressCast extends UnaryConsumer {

        private final CUDAMemoryBase base;

        public CUDAAddressCast(CUDAMemoryBase base, LIRKind lirKind) {
            super(CUDAUnaryTemplate.CAST_TO_POINTER, lirKind, null);
            this.base = base;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            CUDAKind oclKind = getCUDAPlatformKind();
            asm.emit(((CUDAUnaryTemplate) opcode).getTemplate(), base.getMemorySpace().name() + " " + oclKind.toString());
        }

        CUDAMemorySpace getMemorySpace() {
            return base.getMemorySpace();
        }

    }

}
