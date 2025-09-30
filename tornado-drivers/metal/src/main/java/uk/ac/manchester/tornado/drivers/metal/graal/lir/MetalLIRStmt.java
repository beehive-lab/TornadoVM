/*
 * Copyright (c) 2018, 2020-2022, 2024, 2025, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
            asm.emit("convert_float((float) ");
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
            asm.emit("atomic_add( & (");
            asm.emit("*(");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")), ");
            asm.space();
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
            asm.emit("atomicAdd_Tornado_Floats( &("); // Calling to the
                                                     // intrinsic for Floats
            asm.emit("*(");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")), ");
            asm.space();
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
            asm.emit("atomic_add( & (");
            asm.emit("*(");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")), ");
            asm.space();
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
            asm.emit("atomicMul_Tornado_Int( &(");
            asm.emit("*(");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")), ");
            asm.space();
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
}
