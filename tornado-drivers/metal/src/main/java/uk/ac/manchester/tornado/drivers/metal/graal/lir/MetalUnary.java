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
package uk.ac.manchester.tornado.drivers.metal.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalArchitecture;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalArchitecture.MetalMemoryBase;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryOp;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryTemplate;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalUnary.AtomicOperator;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalUnary.UnaryConsumer;
import uk.ac.manchester.tornado.drivers.metal.graal.meta.MetalMemorySpace;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalBarrierNode.MetalMemFenceFlags;

public class MetalUnary {

    /**
     * Abstract operation which consumes one inputs
     */
    protected static class UnaryConsumer extends MetalLIROp {

        @Opcode
        protected final MetalUnaryOp opcode;

        @Use
        protected Value value;

        UnaryConsumer(MetalUnaryOp opcode, LIRKind lirKind, Value value) {
            super(lirKind);
            this.opcode = opcode;
            this.value = value;
        }

        public Value getValue() {
            return value;
        }

        public MetalUnaryOp getOpcode() {
            return opcode;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            opcode.emit(crb, value);
        }

        @Override
        public String toString() {
            return String.format("%s %s", opcode.toString(), value);
        }

    }

    public static class Expr extends UnaryConsumer {

        public Expr(MetalUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

    }

    public static class Intrinsic extends UnaryConsumer {

        public Intrinsic(MetalUnaryOp opcode, LIRKind lirKind, Value value) {
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
        MetalUnary.MemoryAccess address;
        @Use
        Value inc;

        public AtomOperation(MetalUnaryOp opcode, LIRKind lirKind, Value value, MetalUnary.MemoryAccess address, MetalAssembler.MetalUnaryIntrinsic atomicOp, Value inc) {
            super(opcode, lirKind, value);
            this.address = address;
            this.inc = inc;
            // If atomicOp is needed, declare and assign it as well
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            // Use Metal MSL atomic_add for integer atomics
            asm.emit("atomic_add(");
            address.emit(crb, asm);
            asm.emit(", ");
            asm.emitValue(crb, inc);
            asm.emit(")");
            // For float atomics, Metal MSL does not natively support atomic float add.
            // Custom logic may be required (e.g., bitwise conversion or loop with atomic_compare_exchange).
        }
    }

    public enum AtomicOperator {
        INCREMENT_AND_GET, GET_AND_INCREMENT, DECREMENT_AND_GET, GET_AND_DECREMENT
    }

    public static class IntrinsicAtomicOperator extends UnaryConsumer {

        private static final String arrayName = MetalArchitecture.atomicSpace.getName();
        private int index;
        private final AtomicOperator atomicOperator;

        public IntrinsicAtomicOperator(MetalUnaryOp opcode, LIRKind lirKind, Value value, int index, AtomicOperator atomicOperator) {
            super(opcode, lirKind, value);
            this.index = index;
            this.atomicOperator = atomicOperator;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
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

        private static final String arrayName = MetalArchitecture.atomicSpace.getName();
        private int index;

        public IntrinsicAtomicGet(MetalUnaryOp opcode, LIRKind lirKind, Value value, int index) {
            super(opcode, lirKind, value);
            this.index = index;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.emit(toString());
        }

        @Override
        public String toString() {
            return String.format("%s[%s]", arrayName, index);
        }
    }

    public static class IntrinsicAtomicFetch extends UnaryConsumer {

        public IntrinsicAtomicFetch(MetalUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            throw TornadoInternalError.unimplementedMetal();
        }

        @Override
        public String toString() {
            throw TornadoInternalError.unimplementedMetal();
        }
    }

    public static class IntrinsicAtomicDeclaration extends UnaryConsumer {

        AllocatableValue lhs;

        /*
         * The opcode is the initializer intrinsic to use
         */
        public IntrinsicAtomicDeclaration(MetalUnaryOp opcode, AllocatableValue lhs, Value initialValue) {
            super(opcode, LIRKind.Illegal, initialValue);
            this.lhs = lhs;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            StringBuffer lineGlobalScope = new StringBuffer();
            // Use centralized global memory modifier constant instead of a hard-coded literal
            lineGlobalScope.append(MetalAssemblerConstants.GLOBAL_MEM_MODIFIER + " atomic_int ");
            lineGlobalScope.append(asm.getStringValue(crb, lhs));
            lineGlobalScope.append(MetalAssemblerConstants.ASSIGN);
            lineGlobalScope.append(opcode.toString());
            lineGlobalScope.append(MetalAssemblerConstants.OPEN_PARENTHESIS);
            lineGlobalScope.append(asm.getStringValue(crb, value));
            lineGlobalScope.append(MetalAssemblerConstants.CLOSE_PARENTHESIS);
            lineGlobalScope.append(MetalAssemblerConstants.STMT_DELIMITER);
            lineGlobalScope.append("\n");
            asm.emitLineGlobal(lineGlobalScope.toString());
        }
    }

    public static class LoadMetalKernelContext extends UnaryConsumer {

        public LoadMetalKernelContext(MetalUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
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

        MetalMemFenceFlags flags;

        public Barrier(MetalUnaryOp opcode, MetalMemFenceFlags flags) {
            super(opcode, LIRKind.Illegal, null);
            this.flags = flags;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.emit(toString());
        }

        @Override
        public String toString() {
            String metalFlags = (flags == MetalMemFenceFlags.LOCAL) ? "mem_flags::mem_threadgroup" : "mem_flags::mem_device";
            return String.format("threadgroup_barrier(%s)", metalFlags);
        }

    }

    public static class FloatCast extends UnaryConsumer {

        public FloatCast(MetalUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
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

        private final MetalMemoryBase base;
        @Use
        private Value index;

        MemoryAccess(MetalMemoryBase base, Value value) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
        }

        MemoryAccess(MetalMemoryBase base, Value value, Value index) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
            this.index = index;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.emitValue(crb, value);
        }

        public MetalMemoryBase getBase() {
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

    public static class MetalAddressCast extends UnaryConsumer {

        private final MetalMemoryBase base;

        public MetalAddressCast(MetalMemoryBase base, LIRKind lirKind) {
            super(MetalUnaryTemplate.CAST_TO_POINTER, lirKind, null);
            this.base = base;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            MetalKind oclKind = getMetalPlatformKind();
            asm.emit(((MetalUnaryTemplate) opcode).getTemplate(), base.getMemorySpace().name() + " " + oclKind.toString());
        }

        MetalMemorySpace getMemorySpace() {
            return base.getMemorySpace();
        }

    }

}
