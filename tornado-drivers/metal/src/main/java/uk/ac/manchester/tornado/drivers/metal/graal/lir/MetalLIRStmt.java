/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalTernaryIntrinsic;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalUnary.MemoryAccess;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalUnary.MetalAddressCast;
import uk.ac.manchester.tornado.drivers.metal.graal.meta.MetalMemorySpace;

public class MetalLIRStmt {

    protected abstract static class AbstractInstruction extends LIRInstruction {

        protected AbstractInstruction(LIRInstructionClass<? extends AbstractInstruction> c) {
            super(c);
        }

        @Override
        public final void emitCode(CompilationResultBuilder crb) {
            emitCode((MetalCompilationResultBuilder) crb, (MetalAssembler) crb.asm);
        }

        public abstract void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm);

    }

    @Opcode("MARK_RELOCATE")
    public static class MarkRelocateInstruction extends AbstractInstruction {

        public static final LIRInstructionClass<MarkRelocateInstruction> TYPE = LIRInstructionClass.create(MarkRelocateInstruction.class);

        public MarkRelocateInstruction() {
            super(TYPE);
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            // No code is generated
        }
    }

    @Opcode("ASSIGN")
    public static class AssignStmt extends AbstractInstruction {

        public static final LIRInstructionClass<AssignStmt> TYPE = LIRInstructionClass.create(AssignStmt.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected Value rhs;

        public AssignStmt(AllocatableValue lhs, Value rhs) {
            super(TYPE);
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            if (rhs instanceof MetalLIROp) {
                ((MetalLIROp) rhs).emit(crb, asm);
            } else {
                asm.emitValue(crb, rhs);
            }
            asm.delimiter();
            asm.eol();
        }

        public AllocatableValue getResult() {
            return lhs;
        }

        public Value getExpr() {
            return rhs;
        }
    }

    @Opcode("CONVERT_HALF")
    public static class ConvertHalfToFloatStmt extends AbstractInstruction {

        public static final LIRInstructionClass<ConvertHalfToFloatStmt> TYPE = LIRInstructionClass.create(ConvertHalfToFloatStmt.class);

        @Def
        protected Value floatValue;
        @Use
        protected Value halfValue;

        public ConvertHalfToFloatStmt(Value floatValue, Value halfValue) {
            super(TYPE);
            this.floatValue = floatValue;
            this.halfValue = halfValue;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, floatValue);
            asm.space();
            asm.assign();
            asm.space();
            asm.emit("(float)(");
            asm.emitValue(crb, halfValue);
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }

    }

    @Opcode("VADD_HALF")
    public static class VectorAddHalfStmt extends AbstractInstruction {

        public static final LIRInstructionClass<VectorAddHalfStmt> TYPE = LIRInstructionClass.create(VectorAddHalfStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value x;
        @Use
        protected Value y;

        public VectorAddHalfStmt(Value result, Value x, Value y) {
            super(TYPE);
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            if (x instanceof MetalVectorElementSelect) {
                MetalVectorElementSelect selectX = (MetalVectorElementSelect) x;
                selectX.emit(crb, asm);
            } else {
                asm.emitValue(crb, x);
            }
            asm.space();
            asm.emitSymbol("+");
            asm.space();
            if (y instanceof MetalVectorElementSelect) {
                MetalVectorElementSelect selectY = (MetalVectorElementSelect) y;
                selectY.emit(crb, asm);
            } else {
                asm.emitValue(crb, y);
            }
            asm.delimiter();
            asm.eol();
        }

    }

    @Opcode("ADD_HALF")
    public static class AddHalfStmt extends AbstractInstruction {

        public static final LIRInstructionClass<AddHalfStmt> TYPE = LIRInstructionClass.create(AddHalfStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value x;
        @Use
        protected Value y;

        public AddHalfStmt(Value result, Value x, Value y) {
            super(TYPE);
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, x);
            asm.space();
            asm.emitSymbol("+");
            asm.space();
            asm.emitValue(crb, y);
            asm.delimiter();
            asm.eol();
        }

    }

    @Opcode("SUB_HALF")
    public static class SubHalfStmt extends AbstractInstruction {

        public static final LIRInstructionClass<SubHalfStmt> TYPE = LIRInstructionClass.create(SubHalfStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value x;
        @Use
        protected Value y;

        public SubHalfStmt(Value result, Value x, Value y) {
            super(TYPE);
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, x);
            asm.space();
            asm.emitSymbol("-");
            asm.space();
            asm.emitValue(crb, y);
            asm.delimiter();
            asm.eol();
        }

    }

    @Opcode("VSUB_HALF")
    public static class VectorSubHalfStmt extends AbstractInstruction {

        public static final LIRInstructionClass<VectorSubHalfStmt> TYPE = LIRInstructionClass.create(VectorSubHalfStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value x;
        @Use
        protected Value y;

        public VectorSubHalfStmt(Value result, Value x, Value y) {
            super(TYPE);
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            if (x instanceof MetalVectorElementSelect) {
                MetalVectorElementSelect selectX = (MetalVectorElementSelect) x;
                selectX.emit(crb, asm);
            } else {
                asm.emitValue(crb, x);
            }
            asm.space();
            asm.emitSymbol("-");
            asm.space();
            if (y instanceof MetalVectorElementSelect) {
                MetalVectorElementSelect selectY = (MetalVectorElementSelect) y;
                selectY.emit(crb, asm);
            } else {
                asm.emitValue(crb, y);
            }
            asm.delimiter();
            asm.eol();
        }

    }

    @Opcode("MULT_HALF")
    public static class MultHalfStmt extends AbstractInstruction {

        public static final LIRInstructionClass<MultHalfStmt> TYPE = LIRInstructionClass.create(MultHalfStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value x;
        @Use
        protected Value y;

        public MultHalfStmt(Value result, Value x, Value y) {
            super(TYPE);
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, x);
            asm.space();
            asm.emitSymbol("*");
            asm.space();
            asm.emitValue(crb, y);
            asm.delimiter();
            asm.eol();
        }

    }

    @Opcode("VMULT_HALF")
    public static class VectorMultHalfStmt extends AbstractInstruction {

        public static final LIRInstructionClass<VectorMultHalfStmt> TYPE = LIRInstructionClass.create(VectorMultHalfStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value x;
        @Use
        protected Value y;

        public VectorMultHalfStmt(Value result, Value x, Value y) {
            super(TYPE);
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            if (x instanceof MetalVectorElementSelect) {
                MetalVectorElementSelect selectX = (MetalVectorElementSelect) x;
                selectX.emit(crb, asm);
            } else {
                asm.emitValue(crb, x);
            }
            asm.space();
            asm.emitSymbol("*");
            asm.space();
            if (y instanceof MetalVectorElementSelect) {
                MetalVectorElementSelect selectY = (MetalVectorElementSelect) y;
                selectY.emit(crb, asm);
            } else {
                asm.emitValue(crb, y);
            }
            asm.delimiter();
            asm.eol();
        }

    }

    @Opcode("DIV_HALF")
    public static class DivHalfStmt extends AbstractInstruction {

        public static final LIRInstructionClass<DivHalfStmt> TYPE = LIRInstructionClass.create(DivHalfStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value x;
        @Use
        protected Value y;

        public DivHalfStmt(Value result, Value x, Value y) {
            super(TYPE);
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, x);
            asm.space();
            asm.emitSymbol("/");
            asm.space();
            asm.emitValue(crb, y);
            asm.delimiter();
            asm.eol();
        }

    }

    @Opcode("MOVE")
    public static class MoveStmt extends AbstractInstruction {

        public static final LIRInstructionClass<MoveStmt> TYPE = LIRInstructionClass.create(MoveStmt.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected Value rhs;

        public MoveStmt(AllocatableValue lhs, Value rhs) {
            super(TYPE);
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, rhs);
            asm.delimiter();
            asm.eol();
        }

        public AllocatableValue getResult() {
            return lhs;
        }

        public Value getExpr() {
            return rhs;
        }
    }

    @Opcode("LOAD")
    public static class LoadStmt extends AbstractInstruction {

        public static final LIRInstructionClass<LoadStmt> TYPE = LIRInstructionClass.create(LoadStmt.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected MetalAddressCast cast;
        @Use
        protected MemoryAccess address;
        @Use
        protected Value index;

        public LoadStmt(AllocatableValue lhs, MetalAddressCast cast, MemoryAccess address) {
            super(TYPE);
            this.lhs = lhs;
            this.cast = cast;
            this.address = address;
        }

        public LoadStmt(AllocatableValue lhs, MetalAddressCast cast, MemoryAccess address, Value index) {
            super(TYPE);
            this.lhs = lhs;
            this.cast = cast;
            this.address = address;
            this.index = index;
        }

        public void emitIntegerBasedIndexCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.emitValue(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            address.emit(crb, asm);
            asm.emit("[");
            asm.emitValue(crb, index);
            asm.emit("]");
            asm.delimiter();
            asm.eol();
        }

        public void emitPointerBaseIndexCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.emitValue(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            asm.emit("*(");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            if (isLocalOrPrivateLoad()) {
                emitIntegerBasedIndexCode(crb, asm);
            } else {
                emitPointerBaseIndexCode(crb, asm);
            }
        }

        /**
         * This method is used to check if emiting a load to a local or private memory
         * space.
         *
         * @return boolean This returns if the memory base is private or local.
         */
        private boolean isLocalOrPrivateLoad() {
            return this.cast.getMemorySpace().getBase().getMemorySpace() == MetalMemorySpace.LOCAL || this.cast.getMemorySpace().getBase().getMemorySpace() == MetalMemorySpace.PRIVATE;
        }

        public AllocatableValue getResult() {
            return lhs;
        }

        public MetalAddressCast getCast() {
            return cast;
        }

        public MemoryAccess getAddress() {
            return address;
        }
    }

    @Opcode("VLOAD")
    public static class VectorLoadStmt extends AbstractInstruction {

        public static final LIRInstructionClass<VectorLoadStmt> TYPE = LIRInstructionClass.create(VectorLoadStmt.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected MetalAddressCast cast;
        @Use
        protected MemoryAccess address;

        @Use
        protected Value index;

        protected MetalBinaryIntrinsic op;

        public VectorLoadStmt(AllocatableValue lhs, MetalBinaryIntrinsic op, Value index, MetalAddressCast cast, MemoryAccess address) {
            super(TYPE);
            this.lhs = lhs;
            this.cast = cast;
            this.address = address;
            this.op = op;
            this.index = index;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            asm.emit(op.toString());
            asm.emit("(");
            asm.emitValue(crb, index);
            asm.emit(", ");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }

        public Value getResult() {
            return lhs;
        }

        public MetalAddressCast getCast() {
            return cast;
        }

        public MemoryAccess getAddress() {
            return address;
        }

        public MetalBinaryIntrinsic getOp() {
            return op;
        }
    }

    @Opcode("STORE")
    public static class StoreStmt extends AbstractInstruction {

        public static final LIRInstructionClass<StoreStmt> TYPE = LIRInstructionClass.create(StoreStmt.class);

        @Use
        protected Value rhs;
        @Use
        protected MetalAddressCast cast;
        @Use
        protected MemoryAccess address;
        @Use
        protected Value index;

        public StoreStmt(MetalAddressCast cast, MemoryAccess address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.cast = cast;
            this.address = address;
        }

        public StoreStmt(MetalAddressCast cast, MemoryAccess address, Value rhs, Value index) {
            super(TYPE);
            this.rhs = rhs;
            this.cast = cast;
            this.address = address;
            this.index = index;
        }

        /**
         * It emits code in the form:
         *
         * <code>
         * ul_12[index] = value;
         * </code>
         *
         * @param crb
         *     Metal Compilation Result Builder
         *
         * @param asm
         *     Metal Assembler
         */
        public void emitLocalAndPrivateStore(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            address.emit(crb, asm);
            asm.emit("[");
            asm.emitValue(crb, index);
            asm.emit("]");
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValueOrOp(crb, rhs);
            asm.delimiter();
            asm.eol();
        }

        /**
         * It emits code in the form:
         *
         * <code>
         * *((device <type> *) ul_13) = <value>
         * </code>
         *
         * @param crb
         *     Metal Compilation Result Builder
         *
         * @param asm
         *     Metal Assembler
         */
        public void emitGlobalStore(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.emit("*(");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")");
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValueOrOp(crb, rhs);
            asm.delimiter();
            asm.eol();
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            if (isLocalOrPrivateStore()) {
                emitLocalAndPrivateStore(crb, asm);
            } else {
                emitGlobalStore(crb, asm);
            }
        }

        /**
         * This method is used to check if emitting a store to a local or private memory
         * space.
         *
         * @return It returns true if the memory base is private or local.
         */
        private boolean isLocalOrPrivateStore() {
            return this.cast.getMemorySpace().getBase().getMemorySpace() == MetalMemorySpace.LOCAL || this.cast.getMemorySpace().getBase().getMemorySpace() == MetalMemorySpace.PRIVATE;
        }

        public Value getRhs() {
            return rhs;
        }

        public MetalAddressCast getCast() {
            return cast;
        }

        public MemoryAccess getAddress() {
            return address;
        }
    }

    @Opcode("ATOMIC_ADD_STORE")
    public static class StoreAtomicAddStmt extends AbstractInstruction {

        public static final LIRInstructionClass<StoreStmt> TYPE = LIRInstructionClass.create(StoreStmt.class);

        public static final boolean GENERATE_ATOMIC = true;

        @Use
        protected Value rhs;
        @Use
        protected MetalAddressCast cast;
        @Use
        protected Value left;
        @Use
        protected MemoryAccess address;

        public StoreAtomicAddStmt(MetalAddressCast cast, MemoryAccess address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.cast = cast;
            this.address = address;
        }

        public StoreAtomicAddStmt(Value left, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.left = left;
        }

        private void emitAtomicAddStore(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emit("atomic_fetch_add_explicit((device atomic_int*) ");
            address.emit(crb, asm);
            asm.emit(", ");
            asm.emitValue(crb, rhs);
            asm.emit(", memory_order_relaxed)");
            asm.delimiter();
            asm.eol();
        }

        private void emitStore(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emit("*(");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")");
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, rhs);
            asm.delimiter();
            asm.eol();
        }

        private void emitScalarStore(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, left);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, rhs);
            asm.delimiter();
            asm.eol();
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            if (left == null) {
                if (GENERATE_ATOMIC) {
                    emitAtomicAddStore(crb, asm);
                } else {
                    emitStore(crb, asm);
                }
            }
        }

        public Value getRhs() {
            return rhs;
        }

        public Value getLeft() {
            return left;
        }

        public MetalAddressCast getCast() {
            return cast;
        }

        public MemoryAccess getAddress() {
            return address;
        }
    }

    @Opcode("ATOMIC_ADD_FLOAT_STORE")
    public static class StoreAtomicAddFloatStmt extends AbstractInstruction {

        public static final LIRInstructionClass<StoreStmt> TYPE = LIRInstructionClass.create(StoreStmt.class);

        public static final boolean GENERATE_ATOMIC = true;

        @Use
        protected Value rhs;
        @Use
        protected MetalAddressCast cast;
        @Use
        protected Value left;
        @Use
        protected MemoryAccess address;

        public StoreAtomicAddFloatStmt(MetalAddressCast cast, MemoryAccess address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.cast = cast;
            this.address = address;
        }

        public StoreAtomicAddFloatStmt(Value left, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.left = left;
        }

        private void emitAtomicAddStore(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            // Use CAS-loop helper (portable, no atomic_float needed)
            asm.emit("tornado_atomic_add_float((device atomic_uint*) ");
            address.emit(crb, asm);
            asm.emit(", ");
            asm.emitValue(crb, rhs);
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }

        private void emitStore(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emit("*(");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")");
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, rhs);
            asm.delimiter();
            asm.eol();
        }

        private void emitScalarStore(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, left);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, rhs);
            asm.delimiter();
            asm.eol();
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            if (left == null) {
                if (GENERATE_ATOMIC) {
                    emitAtomicAddStore(crb, asm);
                } else {
                    emitStore(crb, asm);
                }
            }
        }

        public Value getRhs() {
            return rhs;
        }

        public Value getLeft() {
            return left;
        }

        public MetalAddressCast getCast() {
            return cast;
        }

        public MemoryAccess getAddress() {
            return address;
        }
    }

    @Opcode("ATOMIC_SUB_STORE")
    public static class StoreAtomicSubStmt extends AbstractInstruction {

        public static final LIRInstructionClass<StoreStmt> TYPE = LIRInstructionClass.create(StoreStmt.class);

        public static final boolean GENERATE_ATOMIC = true;

        @Use
        protected Value rhs;
        @Use
        protected MetalAddressCast cast;
        @Use
        protected Value left;
        @Use
        protected MemoryAccess address;

        public StoreAtomicSubStmt(MetalAddressCast cast, MemoryAccess address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.cast = cast;
            this.address = address;
        }

        public StoreAtomicSubStmt(Value left, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.left = left;
        }

        private void emitAtomicSubStore(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emit("atomic_fetch_sub_explicit((device atomic_int*) ");
            address.emit(crb, asm);
            asm.emit(", ");
            asm.emitValue(crb, rhs);
            asm.emit(", memory_order_relaxed)");
            asm.delimiter();
            asm.eol();
        }

        private void emitStore(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emit("*(");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")");
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, rhs);
            asm.delimiter();
            asm.eol();
        }

        private void emitScalarStore(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, left);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, rhs);
            asm.delimiter();
            asm.eol();
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            if (left == null) {
                if (GENERATE_ATOMIC) {
                    emitAtomicSubStore(crb, asm);
                } else {
                    emitStore(crb, asm);
                }
            }
        }

        public Value getRhs() {
            return rhs;
        }

        public Value getLeft() {
            return left;
        }

        public MetalAddressCast getCast() {
            return cast;
        }

        public MemoryAccess getAddress() {
            return address;
        }
    }

    @Opcode("ATOMIC_MUL_STORE")
    public static class StoreAtomicMulStmt extends AbstractInstruction {

        public static final LIRInstructionClass<StoreStmt> TYPE = LIRInstructionClass.create(StoreStmt.class);

        public static final boolean GENERATE_ATOMIC = true;

        @Use
        protected Value rhs;
        @Use
        protected MetalAddressCast cast;
        @Use
        protected Value left;
        @Use
        protected MemoryAccess address;

        public StoreAtomicMulStmt(MetalAddressCast cast, MemoryAccess address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.cast = cast;
            this.address = address;
        }

        public StoreAtomicMulStmt(Value left, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.left = left;
        }

        private void emitAtomicMulStore(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emit("atomicMul_Tornado_Int((device atomic_int*) ");
            address.emit(crb, asm);
            asm.emit(", ");
            asm.emitValue(crb, rhs);
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }

        private void emitStore(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emit("*(");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")");
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, rhs);
            asm.delimiter();
            asm.eol();
        }

        private void emitScalarStore(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, left);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, rhs);
            asm.delimiter();
            asm.eol();
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            if (left == null) {
                if (GENERATE_ATOMIC) {
                    emitAtomicMulStore(crb, asm);
                } else {
                    emitStore(crb, asm);
                }
            }
        }

        public Value getRhs() {
            return rhs;
        }

        public Value getLeft() {
            return left;
        }

        public MetalAddressCast getCast() {
            return cast;
        }

        public MemoryAccess getAddress() {
            return address;
        }
    }

    @Opcode("VSTORE")
    public static class VectorStoreStmt extends AbstractInstruction {

        public static final LIRInstructionClass<VectorStoreStmt> TYPE = LIRInstructionClass.create(VectorStoreStmt.class);

        @Use
        protected Value rhs;
        @Use
        protected MetalAddressCast cast;
        @Use
        protected MemoryAccess address;
        @Use
        protected Value index;

        protected MetalTernaryIntrinsic op;

        public VectorStoreStmt(MetalTernaryIntrinsic op, Value index, MetalAddressCast cast, MemoryAccess address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.cast = cast;
            this.address = address;
            this.op = op;
            this.index = index;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emit(op.toString());
            asm.emit("(");
            asm.emitValueWithFormat(crb, rhs);
            asm.emit(", ");
            asm.emit(MetalAssembler.getAbsoluteIndexFromValue(index));
            asm.emit(", ");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb);
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }

        public Value getRhs() {
            return rhs;
        }

        public MetalAddressCast getCast() {
            return cast;
        }

        public MemoryAccess getAddress() {
            return address;
        }

        public Value getIndex() {
            return index;
        }

        public MetalTernaryIntrinsic getOp() {
            return op;
        }
    }

    @Opcode("EXPR")
    public static class ExprStmt extends AbstractInstruction {

        public static final LIRInstructionClass<ExprStmt> TYPE = LIRInstructionClass.create(ExprStmt.class);

        @Use
        protected Value expr;

        public ExprStmt(MetalLIROp expr) {
            super(TYPE);
            this.expr = expr;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            if (expr instanceof MetalLIROp) {
                ((MetalLIROp) expr).emit(crb, asm);
            } else {
                asm.emitValue(crb, expr);
            }
            asm.delimiter();
            asm.eol();
        }

        public Value getExpr() {
            return expr;
        }
    }

    @Opcode("RELOCATED_EXPR")
    public static class RelocatedExpressionStmt extends ExprStmt {

        public static final LIRInstructionClass<RelocatedExpressionStmt> TYPE = LIRInstructionClass.create(RelocatedExpressionStmt.class);

        @Use
        protected Value expr;

        public RelocatedExpressionStmt(MetalLIROp expr) {
            super(expr);
            this.expr = expr;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            if (expr instanceof MetalLIROp) {
                ((MetalLIROp) expr).emit(crb, asm);
            } else {
                asm.emitValue(crb, expr);
            }
            asm.eol();
        }

        public Value getExpr() {
            return expr;
        }
    }

    @Opcode("Pragma")
    public static class PragmaExpr extends AbstractInstruction {

        public static final LIRInstructionClass<PragmaExpr> TYPE = LIRInstructionClass.create(PragmaExpr.class);

        @Use
        protected Value prg;

        public PragmaExpr(MetalLIROp prg) {
            super(TYPE);
            this.prg = prg;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            if (prg instanceof MetalLIROp) {
                ((MetalLIROp) prg).emit(crb, asm);
            } else {
                asm.emitValue(crb, prg);
            }

        }
    }

    @Opcode("CAST_COMPRESSED")
    public static class CastCompressedStmt extends AbstractInstruction {

        public static final LIRInstructionClass<CastCompressedStmt> TYPE = LIRInstructionClass.create(CastCompressedStmt.class);

        @Def
        protected Value compressed;
        @Use
        protected Value address;

        public CastCompressedStmt(Value compressed, Value address) {
            super(TYPE);
            this.compressed = compressed;
            this.address = address;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            // reads a 4-byte compressed OOP from the field address
            // uint_var = *((device uint *) ulong_address);
            asm.indent();
            asm.emitValue(crb, compressed);
            asm.space();
            asm.assign();
            asm.space();
            asm.emit("*((device uint *)");
            asm.space();
            asm.emitValue(crb, address);
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }
    }

    @Opcode("DECOMPRESS_POINTER")
    public static class DecompressPointerStmt extends AbstractInstruction {

        public static final LIRInstructionClass<DecompressPointerStmt> TYPE = LIRInstructionClass.create(DecompressPointerStmt.class);

        @Def
        protected Value decompressed;
        @Use
        protected Value compressed;

        public DecompressPointerStmt(Value decompressed, Value compressed) {
            super(TYPE);
            this.decompressed = decompressed;
            this.compressed = compressed;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            // decompresses a 4-byte compressed OOP into a 64-bit relative offset
            // The kernel will then compute: base + decompressed to get the absolute address
            // ulong_var = ((ulong) uint_var) << 3;
            asm.indent();
            asm.emitValue(crb, decompressed);
            asm.space();
            asm.assign();
            asm.space();
            asm.emit("((ulong)");
            asm.space();
            asm.emitValue(crb, compressed);
            asm.emit(")");
            asm.space();
            asm.emit("<< 3");
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * dp4a where b is in threadgroup (local) memory — no ARRAY_HEADER, no device cast for b.
     * Result = c + a[header+offsetA+i] * b[offsetB+i] for i in 0..3
     */
    @Opcode("DP4A_LOCAL")
    public static class Dp4aLocalMemStmt extends AbstractInstruction {

        public static final LIRInstructionClass<Dp4aLocalMemStmt> TYPE = LIRInstructionClass.create(Dp4aLocalMemStmt.class);

        private static final int ARRAY_HEADER = 16;

        @Def
        protected AllocatableValue result;
        @Use
        protected Value a;
        @Use
        protected Value offsetA;
        @Use
        protected Value b;
        @Use
        protected Value offsetB;
        @Use
        protected Value c;

        public Dp4aLocalMemStmt(AllocatableValue result, Value a, Value offsetA, Value b, Value offsetB, Value c) {
            super(TYPE);
            this.result = result;
            this.a = a;
            this.offsetA = offsetA;
            this.b = b;
            this.offsetB = offsetB;
            this.c = c;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, c);
            for (int i = 0; i < 4; i++) {
                asm.emit(" + (int)((device char*)");
                asm.emitValue(crb, a);
                asm.emit(")[" + ARRAY_HEADER + " + ");
                asm.emitValue(crb, offsetA);
                asm.emit(" + " + i + "] * (int)");
                asm.emitValue(crb, b);
                asm.emit("[");
                asm.emitValue(crb, offsetB);
                asm.emit(" + " + i + "]");
            }
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * Emits a 4-element signed-byte dot product (dp4a) for the Metal backend.
     * Metal has no native dp4a instruction, so this unrolls the 4-way multiply-add.
     * Result = c + a[header+offsetA+0]*b[header+offsetB+0] + ... (3 more elements)
     */
    @Opcode("DP4A")
    public static class Dp4aStmt extends AbstractInstruction {

        public static final LIRInstructionClass<Dp4aStmt> TYPE = LIRInstructionClass.create(Dp4aStmt.class);

        /** Array header size in bytes (TornadoNativeArray.ARRAY_HEADER = 16). */
        private static final int ARRAY_HEADER = 16;

        @Def
        protected AllocatableValue result;
        @Use
        protected Value a;
        @Use
        protected Value offsetA;
        @Use
        protected Value b;
        @Use
        protected Value offsetB;
        @Use
        protected Value c;

        public Dp4aStmt(AllocatableValue result, Value a, Value offsetA, Value b, Value offsetB, Value c) {
            super(TYPE);
            this.result = result;
            this.a = a;
            this.offsetA = offsetA;
            this.b = b;
            this.offsetB = offsetB;
            this.c = c;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            // result = c + sum_{i=0}^{3} ((int)((device char*)a)[header+offsetA+i] * (int)((device char*)b)[header+offsetB+i])
            asm.emitValue(crb, c);
            for (int i = 0; i < 4; i++) {
                asm.emit(" + (int)((device char*)");
                asm.emitValue(crb, a);
                asm.emit(")[" + ARRAY_HEADER + " + ");
                asm.emitValue(crb, offsetA);
                asm.emit(" + " + i + "] * (int)((device char*)");
                asm.emitValue(crb, b);
                asm.emit(")[" + ARRAY_HEADER + " + ");
                asm.emitValue(crb, offsetB);
                asm.emit(" + " + i + "]");
            }
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * Emits a full threadgroup-tiled single-precision GEMM ({@code C = A x B}) built on
     * Apple's {@code simdgroup_float8x8} hardware matrix units. Each threadgroup computes
     * a 32x32 output tile; its four SIMD groups cooperatively stage a 32x8 block of A and
     * an 8x32 block of B in {@code threadgroup} memory once per K-step and reuse them to
     * accumulate a 4x4 grid of 8x8 register fragments. This amortises the device-memory
     * traffic that a single-tile simdgroup matmul pays per 8x8 tile.
     *
     * <p>The whole kernel body is this single statement, so the {@code threadgroup} arrays
     * and barriers sit at the kernel's top scope and every thread participates. Dispatch is
     * a 1-D grid of {@code (m/32)*(n/32)*128} threads, local size 128 (four SIMD groups).
     * {@code m}/{@code n} must be multiples of 32 and {@code k} a multiple of 8.
     *
     * <p>Pure side-effect statement (writes to C): declares only {@code @Use} operands.
     */
    @Opcode("SIMDGROUP_TILED_MATMUL")
    public static class SimdgroupTiledMatmulStmt extends AbstractInstruction {

        public static final LIRInstructionClass<SimdgroupTiledMatmulStmt> TYPE = LIRInstructionClass.create(SimdgroupTiledMatmulStmt.class);

        /** FloatArray header in float elements (TornadoNativeArray.ARRAY_HEADER = 16 bytes). */
        private static final int FLOAT_HEADER = 4;

        @Use
        protected Value a;
        @Use
        protected Value b;
        @Use
        protected Value c;
        @Use
        protected Value m;
        @Use
        protected Value n;
        @Use
        protected Value k;

        public SimdgroupTiledMatmulStmt(Value a, Value b, Value c, Value m, Value n, Value k) {
            super(TYPE);
            this.a = a;
            this.b = b;
            this.c = c;
            this.m = m;
            this.n = n;
            this.k = k;
        }

        private static void line(MetalAssembler asm, String text) {
            asm.indent();
            asm.emit(text);
            asm.eol();
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            // Device base pointers past the FloatArray header. Emitted at the kernel's top
            // scope (no wrapping braces) so the threadgroup arrays below are function-scoped.
            asm.indent();
            asm.emit("const device float* _tmA = (const device float*)((device float*)");
            asm.emitValue(crb, a);
            asm.emit(" + " + FLOAT_HEADER + ");");
            asm.eol();

            asm.indent();
            asm.emit("const device float* _tmB = (const device float*)((device float*)");
            asm.emitValue(crb, b);
            asm.emit(" + " + FLOAT_HEADER + ");");
            asm.eol();

            asm.indent();
            asm.emit("device float* _tmC = (device float*)((device float*)");
            asm.emitValue(crb, c);
            asm.emit(" + " + FLOAT_HEADER + ");");
            asm.eol();

            asm.indent();
            asm.emit("const int _tmN = ");
            asm.emitValue(crb, n);
            asm.emit(";");
            asm.eol();

            asm.indent();
            asm.emit("const int _tmK = ");
            asm.emitValue(crb, k);
            asm.emit(";");
            asm.eol();

            // 32x32 output tile per threadgroup; four SIMD groups laid out 2x2, each owning
            // a 2x2 grid of 8x8 fragments. BK = 8 (one fragment deep along K per step).
            line(asm, "const int _tmTilesPerRow = _tmN / 32;");
            line(asm, "const int _tmGid = (int) _threadgroup_position_in_grid.x;");
            line(asm, "const int _tmRowBase = (_tmGid / _tmTilesPerRow) * 32;");
            line(asm, "const int _tmColBase = (_tmGid % _tmTilesPerRow) * 32;");
            line(asm, "const int _tmTid = (int) _thread_position_in_threadgroup.x;");
            line(asm, "const int _tmSg = _tmTid / 32;");
            line(asm, "const int _tmSgRow = _tmSg / 2;");
            line(asm, "const int _tmSgCol = _tmSg % 2;");

            // Threadgroup staging buffers: As[32][8] and Bs[8][32] (256 floats each).
            line(asm, "threadgroup float _tmAs[256];");
            line(asm, "threadgroup float _tmBs[256];");

            // Register accumulator fragments, zeroed.
            line(asm, "simdgroup_float8x8 _tmAcc00 = make_filled_simdgroup_matrix<float, 8, 8>(0.0f);");
            line(asm, "simdgroup_float8x8 _tmAcc01 = make_filled_simdgroup_matrix<float, 8, 8>(0.0f);");
            line(asm, "simdgroup_float8x8 _tmAcc10 = make_filled_simdgroup_matrix<float, 8, 8>(0.0f);");
            line(asm, "simdgroup_float8x8 _tmAcc11 = make_filled_simdgroup_matrix<float, 8, 8>(0.0f);");

            line(asm, "for (int _tmKb = 0; _tmKb < _tmK; _tmKb += 8) {");
            // Cooperative load of the 32x8 A block (256 elems / 128 threads = 2 each).
            line(asm, "  for (int _tmE = _tmTid; _tmE < 256; _tmE += 128) {");
            line(asm, "    _tmAs[_tmE] = _tmA[(_tmRowBase + (_tmE / 8)) * _tmK + (_tmKb + (_tmE % 8))];");
            line(asm, "  }");
            // Cooperative load of the 8x32 B block.
            line(asm, "  for (int _tmE = _tmTid; _tmE < 256; _tmE += 128) {");
            line(asm, "    _tmBs[_tmE] = _tmB[(_tmKb + (_tmE / 32)) * _tmN + (_tmColBase + (_tmE % 32))];");
            line(asm, "  }");
            line(asm, "  threadgroup_barrier(mem_flags::mem_threadgroup);");
            // Each SIMD group loads its two A and two B fragments and does four MMAs.
            line(asm, "  simdgroup_float8x8 _tmA0, _tmA1, _tmB0, _tmB1;");
            line(asm, "  simdgroup_load(_tmA0, _tmAs + (_tmSgRow * 2 + 0) * 64, 8);");
            line(asm, "  simdgroup_load(_tmA1, _tmAs + (_tmSgRow * 2 + 1) * 64, 8);");
            line(asm, "  simdgroup_load(_tmB0, _tmBs + (_tmSgCol * 2 + 0) * 8, 32);");
            line(asm, "  simdgroup_load(_tmB1, _tmBs + (_tmSgCol * 2 + 1) * 8, 32);");
            line(asm, "  simdgroup_multiply_accumulate(_tmAcc00, _tmA0, _tmB0, _tmAcc00);");
            line(asm, "  simdgroup_multiply_accumulate(_tmAcc01, _tmA0, _tmB1, _tmAcc01);");
            line(asm, "  simdgroup_multiply_accumulate(_tmAcc10, _tmA1, _tmB0, _tmAcc10);");
            line(asm, "  simdgroup_multiply_accumulate(_tmAcc11, _tmA1, _tmB1, _tmAcc11);");
            line(asm, "  threadgroup_barrier(mem_flags::mem_threadgroup);");
            line(asm, "}");

            // Store the four accumulated fragments to C.
            line(asm, "const int _tmCr0 = _tmRowBase + (_tmSgRow * 2 + 0) * 8;");
            line(asm, "const int _tmCr1 = _tmRowBase + (_tmSgRow * 2 + 1) * 8;");
            line(asm, "const int _tmCc0 = _tmColBase + (_tmSgCol * 2 + 0) * 8;");
            line(asm, "const int _tmCc1 = _tmColBase + (_tmSgCol * 2 + 1) * 8;");
            line(asm, "simdgroup_store(_tmAcc00, _tmC + _tmCr0 * _tmN + _tmCc0, _tmN);");
            line(asm, "simdgroup_store(_tmAcc01, _tmC + _tmCr0 * _tmN + _tmCc1, _tmN);");
            line(asm, "simdgroup_store(_tmAcc10, _tmC + _tmCr1 * _tmN + _tmCc0, _tmN);");
            line(asm, "simdgroup_store(_tmAcc11, _tmC + _tmCr1 * _tmN + _tmCc1, _tmN);");
        }
    }

    /** FloatArray header in float elements (TornadoNativeArray.ARRAY_HEADER = 16 bytes). */
    private static final int SIMDGROUP_FLOAT_HEADER = 4;

    /** {@code <result> = make_filled_simdgroup_matrix<float,8,8>(0.0f);} */
    @Opcode("SIMDGROUP_MATRIX_ZERO")
    public static class SimdgroupMatrixZeroStmt extends AbstractInstruction {

        public static final LIRInstructionClass<SimdgroupMatrixZeroStmt> TYPE = LIRInstructionClass.create(SimdgroupMatrixZeroStmt.class);

        @Def
        protected AllocatableValue result;

        public SimdgroupMatrixZeroStmt(AllocatableValue result) {
            super(TYPE);
            this.result = result;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.emit(" = make_filled_simdgroup_matrix<float, 8, 8>(0.0f);");
            asm.eol();
        }
    }

    /** {@code simdgroup_load(<result>, (const device float*)((device float*)<array> + 4 + <base>), (ulong)<stride>);} */
    @Opcode("SIMDGROUP_MATRIX_LOAD")
    public static class SimdgroupMatrixLoadStmt extends AbstractInstruction {

        public static final LIRInstructionClass<SimdgroupMatrixLoadStmt> TYPE = LIRInstructionClass.create(SimdgroupMatrixLoadStmt.class);

        @Def
        protected AllocatableValue result;
        @Use
        protected Value array;
        @Use
        protected Value base;
        @Use
        protected Value stride;
        private final boolean local;

        public SimdgroupMatrixLoadStmt(AllocatableValue result, Value array, Value base, Value stride, boolean local) {
            super(TYPE);
            this.result = result;
            this.array = array;
            this.base = base;
            this.stride = stride;
            this.local = local;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emit("simdgroup_load(");
            asm.emitValue(crb, result);
            asm.emit(", ");
            if (local) {
                // Threadgroup array: no header, no device cast; the array decays to a pointer.
                asm.emit("(const threadgroup float*)(");
                asm.emitValue(crb, array);
                asm.emit(" + ");
                asm.emitValue(crb, base);
                asm.emit(")");
            } else {
                asm.emit("(const device float*)((device float*)");
                asm.emitValue(crb, array);
                asm.emit(" + " + SIMDGROUP_FLOAT_HEADER + " + ");
                asm.emitValue(crb, base);
                asm.emit(")");
            }
            asm.emit(", (ulong)");
            asm.emitValue(crb, stride);
            asm.emit(");");
            asm.eol();
        }
    }

    /** {@code simdgroup_multiply_accumulate(<result>, <a>, <b>, <c>);} */
    @Opcode("SIMDGROUP_MATRIX_MMA")
    public static class SimdgroupMatrixMmaStmt extends AbstractInstruction {

        public static final LIRInstructionClass<SimdgroupMatrixMmaStmt> TYPE = LIRInstructionClass.create(SimdgroupMatrixMmaStmt.class);

        @Def
        protected AllocatableValue result;
        @Use
        protected Value a;
        @Use
        protected Value b;
        @Use
        protected Value c;

        public SimdgroupMatrixMmaStmt(AllocatableValue result, Value a, Value b, Value c) {
            super(TYPE);
            this.result = result;
            this.a = a;
            this.b = b;
            this.c = c;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emit("simdgroup_multiply_accumulate(");
            asm.emitValue(crb, result);
            asm.emit(", ");
            asm.emitValue(crb, a);
            asm.emit(", ");
            asm.emitValue(crb, b);
            asm.emit(", ");
            asm.emitValue(crb, c);
            asm.emit(");");
            asm.eol();
        }
    }

    /** {@code simdgroup_store(<m>, (device float*)((device float*)<array> + 4 + <base>), (ulong)<stride>);} */
    @Opcode("SIMDGROUP_MATRIX_STORE")
    public static class SimdgroupMatrixStoreStmt extends AbstractInstruction {

        public static final LIRInstructionClass<SimdgroupMatrixStoreStmt> TYPE = LIRInstructionClass.create(SimdgroupMatrixStoreStmt.class);

        @Use
        protected Value m;
        @Use
        protected Value array;
        @Use
        protected Value base;
        @Use
        protected Value stride;

        public SimdgroupMatrixStoreStmt(Value m, Value array, Value base, Value stride) {
            super(TYPE);
            this.m = m;
            this.array = array;
            this.base = base;
            this.stride = stride;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emit("simdgroup_store(");
            asm.emitValue(crb, m);
            asm.emit(", (device float*)((device float*)");
            asm.emitValue(crb, array);
            asm.emit(" + " + SIMDGROUP_FLOAT_HEADER + " + ");
            asm.emitValue(crb, base);
            asm.emit("), (ulong)");
            asm.emitValue(crb, stride);
            asm.emit(");");
            asm.eol();
        }
    }
}
