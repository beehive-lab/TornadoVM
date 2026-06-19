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
package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDABinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDATernaryIntrinsic;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAUnary.MemoryAccess;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAUnary.CUDAAddressCast;
import uk.ac.manchester.tornado.drivers.cuda.graal.meta.CUDAMemorySpace;

public class CUDALIRStmt {

    /** Provides unique local-variable suffixes for the inline atomicCAS multiply loop. */
    private static final java.util.concurrent.atomic.AtomicInteger ATOMIC_MUL_COUNTER = new java.util.concurrent.atomic.AtomicInteger();

    protected abstract static class AbstractInstruction extends LIRInstruction {

        protected AbstractInstruction(LIRInstructionClass<? extends AbstractInstruction> c) {
            super(c);
        }

        @Override
        public final void emitCode(CompilationResultBuilder crb) {
            emitCode((CUDACompilationResultBuilder) crb, (CUDAAssembler) crb.asm);
        }

        public abstract void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm);

    }

    @Opcode("MARK_RELOCATE")
    public static class MarkRelocateInstruction extends AbstractInstruction {

        public static final LIRInstructionClass<MarkRelocateInstruction> TYPE = LIRInstructionClass.create(MarkRelocateInstruction.class);

        public MarkRelocateInstruction() {
            super(TYPE);
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emitValue(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            if (rhs instanceof CUDALIROp) {
                ((CUDALIROp) rhs).emit(crb, asm);
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            // casts an 8-byte address to a 4-byte pointer
            // uint_var = *((__global uint *) ulong_address);
            asm.indent();
            asm.emitValue(crb, compressed);
            asm.space();
            asm.assign();
            asm.space();
            asm.emit("*((unsigned int *)");
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
        protected Value base;
        @Use
        protected Value compressed;

        public DecompressPointerStmt(Value decompressed, Value base, Value compressed) {
            super(TYPE);
            this.decompressed = decompressed;
            this.base = base;
            this.compressed = compressed;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            // emits instruction to decompress a 4-byte reference into an 8-byte address.
            // ulong_var = ul_0 + ((ulong) uint_var << 3);
            asm.indent();
            asm.emitValue(crb, decompressed);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, base);
            asm.space();
            asm.emit("+");
            asm.space();
            asm.emit("((unsigned long) ");
            asm.emitValue(crb, compressed);
            asm.space();
            asm.emit("<< 3)"); // this 3 is standard for the decompression - this code is generated only for coops
            asm.delimiter();
            asm.eol();
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emitValue(crb, floatValue);
            asm.space();
            asm.assign();
            asm.space();
            asm.emit("__half2float(");
            asm.emitValue(crb, halfValue);
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }

    }

    @Opcode("CONVERT_FLOAT_TO_HALF")
    public static class ConvertFloatToHalfStmt extends AbstractInstruction {

        public static final LIRInstructionClass<ConvertFloatToHalfStmt> TYPE = LIRInstructionClass.create(ConvertFloatToHalfStmt.class);

        @Use
        protected Value floatValue;
        @Def
        protected Value halfValue;

        public ConvertFloatToHalfStmt(Value floatValue, Value halfValue) {
            super(TYPE);
            this.floatValue = floatValue;
            this.halfValue = halfValue;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emitValue(crb, halfValue);
            asm.space();
            asm.assign();
            asm.space();
            asm.emit("__float2half(");
            asm.emitValue(crb, floatValue);
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            if (x instanceof CUDAVectorElementSelect) {
                CUDAVectorElementSelect selectX = (CUDAVectorElementSelect) x;
                selectX.emit(crb, asm);
            } else {
                asm.emitValue(crb, x);
            }
            asm.space();
            asm.emitSymbol("+");
            asm.space();
            if (y instanceof CUDAVectorElementSelect) {
                CUDAVectorElementSelect selectY = (CUDAVectorElementSelect) y;
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitHalfOperand(crb, x);
            asm.space();
            asm.emitSymbol("+");
            asm.space();
            asm.emitHalfOperand(crb, y);
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitHalfOperand(crb, x);
            asm.space();
            asm.emitSymbol("-");
            asm.space();
            asm.emitHalfOperand(crb, y);
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            if (x instanceof CUDAVectorElementSelect) {
                CUDAVectorElementSelect selectX = (CUDAVectorElementSelect) x;
                selectX.emit(crb, asm);
            } else {
                asm.emitValue(crb, x);
            }
            asm.space();
            asm.emitSymbol("-");
            asm.space();
            if (y instanceof CUDAVectorElementSelect) {
                CUDAVectorElementSelect selectY = (CUDAVectorElementSelect) y;
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitHalfOperand(crb, x);
            asm.space();
            asm.emitSymbol("*");
            asm.space();
            asm.emitHalfOperand(crb, y);
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            if (x instanceof CUDAVectorElementSelect) {
                CUDAVectorElementSelect selectX = (CUDAVectorElementSelect) x;
                selectX.emit(crb, asm);
            } else {
                asm.emitValue(crb, x);
            }
            asm.space();
            asm.emitSymbol("*");
            asm.space();
            if (y instanceof CUDAVectorElementSelect) {
                CUDAVectorElementSelect selectY = (CUDAVectorElementSelect) y;
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitHalfOperand(crb, x);
            asm.space();
            asm.emitSymbol("/");
            asm.space();
            asm.emitHalfOperand(crb, y);
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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
        protected CUDAAddressCast cast;
        @Use
        protected MemoryAccess address;
        @Use
        protected Value index;

        public LoadStmt(AllocatableValue lhs, CUDAAddressCast cast, MemoryAccess address) {
            super(TYPE);
            this.lhs = lhs;
            this.cast = cast;
            this.address = address;
        }

        public LoadStmt(AllocatableValue lhs, CUDAAddressCast cast, MemoryAccess address, Value index) {
            super(TYPE);
            this.lhs = lhs;
            this.cast = cast;
            this.address = address;
            this.index = index;
        }

        public void emitIntegerBasedIndexCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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

        public void emitPointerBaseIndexCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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
            return this.cast.getMemorySpace().getBase().getMemorySpace() == CUDAMemorySpace.LOCAL || this.cast.getMemorySpace().getBase().getMemorySpace() == CUDAMemorySpace.PRIVATE;
        }

        public AllocatableValue getResult() {
            return lhs;
        }

        public CUDAAddressCast getCast() {
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
        protected CUDAAddressCast cast;
        @Use
        protected MemoryAccess address;

        @Use
        protected Value index;

        protected CUDABinaryIntrinsic op;

        public VectorLoadStmt(AllocatableValue lhs, CUDABinaryIntrinsic op, Value index, CUDAAddressCast cast, MemoryAccess address) {
            super(TYPE);
            this.lhs = lhs;
            this.cast = cast;
            this.address = address;
            this.op = op;
            this.index = index;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            // OpenCL vloadN(i, p) reads N consecutive (unaligned) elements at p[i*N].
            // CUDA built-in vector types (e.g. float4) are __align__(16), so a
            // reinterpret cast ((float4*)p)[i] requires 16-byte alignment and faults
            // on TornadoVM's element-aligned buffers ("misaligned address"). Emit an
            // alignment-safe componentwise load through the ELEMENT pointer instead:
            //   make_floatN(((float*)p)[i*N+0], ((float*)p)[i*N+1], ...)
            CUDAKind vectorKind = (CUDAKind) lhs.getPlatformKind();
            int n = vectorKind.getVectorLength();
            String elem = vectorKind.getElementKind().toString();

            asm.beginStackPush();
            address.emit(crb, asm);
            final String addr = asm.getLastOp();
            asm.emitValue(crb, index);
            final String idx = asm.getLastOp();
            asm.endStackPush();

            asm.indent();
            asm.emitValue(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            StringBuilder sb = new StringBuilder();
            sb.append("make_").append(elem).append(n).append("(");
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("((").append(elem).append(" *)(").append(addr).append("))[(").append(idx).append(") * ").append(n).append(" + ").append(i).append("]");
            }
            sb.append(")");
            asm.emit(sb.toString());
            asm.delimiter();
            asm.eol();
        }

        public Value getResult() {
            return lhs;
        }

        public CUDAAddressCast getCast() {
            return cast;
        }

        public MemoryAccess getAddress() {
            return address;
        }

        public CUDABinaryIntrinsic getOp() {
            return op;
        }
    }

    @Opcode("STORE")
    public static class StoreStmt extends AbstractInstruction {

        public static final LIRInstructionClass<StoreStmt> TYPE = LIRInstructionClass.create(StoreStmt.class);

        @Use
        protected Value rhs;
        @Use
        protected CUDAAddressCast cast;
        @Use
        protected MemoryAccess address;
        @Use
        protected Value index;

        public StoreStmt(CUDAAddressCast cast, MemoryAccess address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.cast = cast;
            this.address = address;
        }

        public StoreStmt(CUDAAddressCast cast, MemoryAccess address, Value rhs, Value index) {
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
         *     CUDADriver Compilation Result Builder
         *
         * @param asm
         *     CUDADriver Assembler
         */
        public void emitLocalAndPrivateStore(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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
         * *((__global <type> *) ul_13) = <value>
         * </code>
         *
         * @param crb
         *     CUDADriver Compilation Result Builder
         *
         * @param asm
         *     CUDADriver Assembler
         */
        public void emitGlobalStore(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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
            return this.cast.getMemorySpace().getBase().getMemorySpace() == CUDAMemorySpace.LOCAL || this.cast.getMemorySpace().getBase().getMemorySpace() == CUDAMemorySpace.PRIVATE;
        }

        public Value getRhs() {
            return rhs;
        }

        public CUDAAddressCast getCast() {
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
        protected CUDAAddressCast cast;
        @Use
        protected Value left;
        @Use
        protected MemoryAccess address;

        public StoreAtomicAddStmt(CUDAAddressCast cast, MemoryAccess address, Value rhs) {
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

        private void emitAtomicAddStore(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            // CUDA has no atomicAdd overload for signed long; use the
            // unsigned long long int overload (cast both pointer and value).
            boolean isLong = ((CUDAKind) cast.getCUDAPlatformKind()) == CUDAKind.ATOMIC_ADD_LONG;
            asm.emit("atomicAdd( ");
            if (isLong) {
                asm.emit("(unsigned long long *)");
            }
            asm.emit("& (");
            asm.emit("*(");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")), ");
            asm.space();
            if (isLong) {
                asm.emit("(unsigned long long) (");
                asm.emitValue(crb, rhs);
                asm.emit(")");
            } else {
                asm.emitValue(crb, rhs);
            }
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }

        private void emitStore(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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

        private void emitScalarStore(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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

        public CUDAAddressCast getCast() {
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
        protected CUDAAddressCast cast;
        @Use
        protected Value left;
        @Use
        protected MemoryAccess address;

        public StoreAtomicAddFloatStmt(CUDAAddressCast cast, MemoryAccess address, Value rhs) {
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

        private void emitAtomicAddStore(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            // CUDA provides a native atomicAdd(float*, float) on all supported
            // architectures (and atomicAdd(double*, double) for compute >= 6.0).
            asm.emit("atomicAdd( &(");
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

        private void emitStore(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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

        private void emitScalarStore(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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

        public CUDAAddressCast getCast() {
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
        protected CUDAAddressCast cast;
        @Use
        protected Value left;
        @Use
        protected MemoryAccess address;

        public StoreAtomicSubStmt(CUDAAddressCast cast, MemoryAccess address, Value rhs) {
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

        private void emitAtomicSubStore(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.emit("atomicSub( & (");
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

        private void emitStore(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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

        private void emitScalarStore(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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

        public CUDAAddressCast getCast() {
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
        protected CUDAAddressCast cast;
        @Use
        protected Value left;
        @Use
        protected MemoryAccess address;

        public StoreAtomicMulStmt(CUDAAddressCast cast, MemoryAccess address, Value rhs) {
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

        private void emitAtomicMulStore(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            // CUDA has no native atomicMul. Emit an inline atomicCAS read-modify-write
            // loop on the int* target (matching OpenCL atomic multiply semantics).
            int id = ATOMIC_MUL_COUNTER.getAndIncrement();
            String ptr = "atm_p_" + id;
            String oldV = "atm_old_" + id;
            String assumedV = "atm_assumed_" + id;
            asm.beginStackPush();
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            final String addr = asm.getLastOp();
            asm.emitValue(crb, rhs);
            final String operand = asm.getLastOp();
            asm.endStackPush();
            // addr already includes the "( int *)" pointer cast emitted by cast.emit.
            asm.emit(String.format("int *%s = &(*(%s))", ptr, addr));
            asm.delimiter();
            asm.eol();
            asm.indent();
            asm.emit(String.format("int %s = *%s, %s", oldV, ptr, assumedV));
            asm.delimiter();
            asm.eol();
            asm.indent();
            asm.emit(String.format("do { %s = %s; %s = atomicCAS(%s, %s, %s * (%s)); } while (%s != %s)", assumedV, oldV, oldV, ptr, assumedV, assumedV, operand, assumedV, oldV));
            asm.delimiter();
            asm.eol();
        }

        private void emitStore(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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

        private void emitScalarStore(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
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

        public CUDAAddressCast getCast() {
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
        protected CUDAAddressCast cast;
        @Use
        protected MemoryAccess address;
        @Use
        protected Value index;

        protected CUDATernaryIntrinsic op;

        public VectorStoreStmt(CUDATernaryIntrinsic op, Value index, CUDAAddressCast cast, MemoryAccess address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.cast = cast;
            this.address = address;
            this.op = op;
            this.index = index;
        }

        private static final String[] COMPONENTS = { "x", "y", "z", "w" };

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            // OpenCL vstoreN(v, i, p) stores N (unaligned) elements at p[i*N]. As with
            // the vload, an aligned reinterpret store would fault on float4 buffers, so
            // store componentwise through the ELEMENT pointer:
            //   ((float*)p)[i*N+0] = v.x; ((float*)p)[i*N+1] = v.y; ...
            CUDAKind vectorKind = (CUDAKind) rhs.getPlatformKind();
            int n = vectorKind.getVectorLength();
            String elem = vectorKind.getElementKind().toString();
            String idx = CUDAAssembler.getAbsoluteIndexFromValue(index);

            asm.beginStackPush();
            address.emit(crb);
            final String addr = asm.getLastOp();
            asm.emitValueWithFormat(crb, rhs);
            final String v = asm.getLastOp();
            asm.endStackPush();

            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    asm.indent();
                }
                asm.emit(String.format("((%s *)(%s))[(%s) * %d + %d]", elem, addr, idx, n, i));
                asm.space();
                asm.assign();
                asm.space();
                asm.emit(String.format("(%s).%s", v, COMPONENTS[i]));
                asm.delimiter();
                asm.eol();
            }
        }

        public Value getRhs() {
            return rhs;
        }

        public CUDAAddressCast getCast() {
            return cast;
        }

        public MemoryAccess getAddress() {
            return address;
        }

        public Value getIndex() {
            return index;
        }

        public CUDATernaryIntrinsic getOp() {
            return op;
        }
    }

    @Opcode("EXPR")
    public static class ExprStmt extends AbstractInstruction {

        public static final LIRInstructionClass<ExprStmt> TYPE = LIRInstructionClass.create(ExprStmt.class);

        @Use
        protected Value expr;

        public ExprStmt(CUDALIROp expr) {
            super(TYPE);
            this.expr = expr;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            if (expr instanceof CUDALIROp) {
                ((CUDALIROp) expr).emit(crb, asm);
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

        public RelocatedExpressionStmt(CUDALIROp expr) {
            super(expr);
            this.expr = expr;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            if (expr instanceof CUDALIROp) {
                ((CUDALIROp) expr).emit(crb, asm);
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

        public PragmaExpr(CUDALIROp prg) {
            super(TYPE);
            this.prg = prg;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            if (prg instanceof CUDALIROp) {
                ((CUDALIROp) prg).emit(crb, asm);
            } else {
                asm.emitValue(crb, prg);
            }

        }
    }
}
