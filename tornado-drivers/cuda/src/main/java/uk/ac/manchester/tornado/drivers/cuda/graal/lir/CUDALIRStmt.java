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

import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDABinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDATernaryIntrinsic;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIROp;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAUnary.MemoryAccess;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAUnary.CUDAAddressCast;
import uk.ac.manchester.tornado.drivers.cuda.graal.meta.CUDAMemorySpace;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;

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
            // Fragment-carrying phi copy: when RHS is an MMA fragment kind, LHS is
            // either a fragment (naturally) or a Variable that was retyped to a
            // C array by CUDABackend.emitVariableDefs (loop-carried phi case).
            // Either way, the C-level type of both sides is `T[N]`, so a whole-array
            // assignment is illegal in C, expand into N per-lane element copies.
            CUDAKind rhsKind = fragmentKindOf(rhs);
            if (rhsKind != null) {
                int lanes = rhsKind.getVectorLength();
                for (int i = 0; i < lanes; i++) {
                    asm.indent();
                    asm.emitValue(crb, lhs);
                    asm.emit("[%d]", i);
                    asm.space();
                    asm.assign();
                    asm.space();
                    if (rhs instanceof CUDALIROp) {
                        // Extremely unlikely to occur for MMA fragment RHS in practice,
                        // but preserved for symmetry with the scalar path below.
                        ((CUDALIROp) rhs).emit(crb, asm);
                    } else {
                        asm.emitValue(crb, rhs);
                        asm.emit("[%d]", i);
                    }
                    asm.delimiter();
                    asm.eol();
                }
                return;
            }

            // Scalar path.
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

        /**
         * Returns the fragment CUDAKind carried by {@code v}, or null if {@code v}
         * isn't a Variable/AllocatableValue with an MMA fragment PlatformKind.
         */
        private static CUDAKind fragmentKindOf(Value v) {
            if (v == null || !(v.getValueKind() instanceof LIRKind)) {
                return null;
            }
            Object pk = ((LIRKind) v.getValueKind()).getPlatformKind();
            if (!(pk instanceof CUDAKind)) {
                return null;
            }
            CUDAKind k = (CUDAKind) pk;
            return k.isMMAFragment() ? k : null;
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

    /**
     * Emits an inline {@code dp4a.s32.s32} PTX dot-product-accumulate over two
     * packed-int8 operands loaded from memory. Loads four signed bytes from each
     * operand (reinterpreting them as a 32-bit word) and accumulates into {@code c}:
     *
     * <pre>{@code asm("dp4a.s32.s32 %0, %1, %2, %3;"
     *       : "=r"(result)
     *       : "r"(*((int *)(baseA + offsetA + HEADER))),
     *         "r"(*((int *)(baseB + offsetB [+ HEADER]))),
     *         "r"(c));}</pre>
     *
     * When operand B comes from a local (shared-memory) array it has no array header, so
     * {@code bHasHeader} is false and only the element offset is applied.
     */
    @Opcode("DP4A")
    public static class Dp4aStmt extends AbstractInstruction {

        public static final LIRInstructionClass<Dp4aStmt> TYPE = LIRInstructionClass.create(Dp4aStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value baseA;
        @Use
        protected Value offsetA;
        @Use
        protected Value baseB;
        @Use
        protected Value offsetB;
        @Use
        protected Value accumulator;

        private final boolean bHasHeader;
        private final long headerSize;

        public Dp4aStmt(Value result, Value baseA, Value offsetA, Value baseB, Value offsetB, Value accumulator, boolean bHasHeader, long headerSize) {
            super(TYPE);
            this.result = result;
            this.baseA = baseA;
            this.offsetA = offsetA;
            this.baseB = baseB;
            this.offsetB = offsetB;
            this.accumulator = accumulator;
            this.bHasHeader = bHasHeader;
            this.headerSize = headerSize;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            // Emit the dp4a.s32.s32 PTX instruction inline. The asm operand
            // constraints accept arbitrary C expressions, so the two int8 words and
            // the accumulator are passed directly with no helper function (and hence
            // no kernel preamble).
            asm.indent();
            asm.emit("asm(\"dp4a.s32.s32 %0, %1, %2, %3;\" : \"=r\"(");
            asm.emitValue(crb, result);
            asm.emit(") : \"r\"(*(( int *) (");
            asm.emitValue(crb, baseA);
            asm.emit(" + ");
            asm.emitValue(crb, offsetA);
            asm.emit(" + " + headerSize + "L))), \"r\"(*(( int *) (");
            asm.emitValue(crb, baseB);
            asm.emit(" + ");
            asm.emitValue(crb, offsetB);
            if (bHasHeader) {
                asm.emit(" + " + headerSize + "L");
            }
            asm.emit("))), \"r\"(");
            asm.emitValue(crb, accumulator);
            asm.emit("))");
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * Emits an inline {@code dp4a.s32.s32} PTX instruction over two already-packed
     * 32-bit operands, accumulating into {@code c}.
     */
    @Opcode("DP4A_PACKED")
    public static class Dp4aPackedStmt extends AbstractInstruction {

        public static final LIRInstructionClass<Dp4aPackedStmt> TYPE = LIRInstructionClass.create(Dp4aPackedStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value a;
        @Use
        protected Value b;
        @Use
        protected Value accumulator;

        public Dp4aPackedStmt(Value result, Value a, Value b, Value accumulator) {
            super(TYPE);
            this.result = result;
            this.a = a;
            this.b = b;
            this.accumulator = accumulator;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            // Inline dp4a.s32.s32 over two already-packed operands; no helper/preamble.
            asm.indent();
            asm.emit("asm(\"dp4a.s32.s32 %0, %1, %2, %3;\" : \"=r\"(");
            asm.emitValue(crb, result);
            asm.emit(") : \"r\"(");
            asm.emitValue(crb, a);
            asm.emit("), \"r\"(");
            asm.emitValue(crb, b);
            asm.emit("), \"r\"(");
            asm.emitValue(crb, accumulator);
            asm.emit("))");
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

    @Opcode("SHUFFLE_SYNC")
    public static class ShuffleSyncStmt extends AbstractInstruction {

        public static final LIRInstructionClass<ShuffleSyncStmt> TYPE = LIRInstructionClass.create(ShuffleSyncStmt.class);

        /**
         * Warp-shuffle mode mapping to a CUDA {@code __shfl_*_sync} intrinsic.
         *
         * <ul>
         *   <li>{@code DOWN} -> {@code __shfl_down_sync(mask, var, delta)}: each lane
         *       receives {@code var} from the lane {@code delta} positions ahead.</li>
         *   <li>{@code IDX}  -> {@code __shfl_sync(mask, var, srcLane)}: each lane
         *       receives {@code var} from {@code srcLane} (broadcast when srcLane=0).</li>
         *   <li>{@code XOR}  -> {@code __shfl_xor_sync(mask, var, laneMask)}: butterfly
         *       exchange, used to build an all-lanes reduction.</li>
         * </ul>
         */
        public enum Mode {
            DOWN("__shfl_down_sync"),
            IDX("__shfl_sync"),
            XOR("__shfl_xor_sync");

            private final String intrinsic;

            Mode(String intrinsic) {
                this.intrinsic = intrinsic;
            }
        }

        // All active lanes participate (CUDA 9+ requires an explicit member mask).
        private static final String FULL_MASK = "0xffffffff";

        private final Mode mode;
        @Def
        protected Value result;
        @Use
        protected Value source;
        @Use
        protected Value operand;

        public ShuffleSyncStmt(Mode mode, Value result, Value source, Value operand) {
            super(TYPE);
            this.mode = mode;
            this.result = result;
            this.source = source;
            this.operand = operand;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emitValue(crb, result);
            asm.space();
            asm.assign();
            asm.space();
            asm.emit(mode.intrinsic);
            asm.emit("(");
            asm.emit(FULL_MASK);
            asm.emit(", ");
            asm.emitValue(crb, source);
            asm.emit(", ");
            asm.emitValue(crb, operand);
            asm.emit(")");
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
        @Use( { COMPOSITE })
        protected CUDAAddressCast cast;
        @Use( { COMPOSITE })
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
         * This method is used to check if emiting a load to a local or private memory space.
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
        @Use( { COMPOSITE })
        protected CUDAAddressCast cast;
        @Use( { COMPOSITE })
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
        @Use( { COMPOSITE })
        protected CUDAAddressCast cast;
        @Use( { COMPOSITE })
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
         * This method is used to check if emitting a store to a local or private memory space.
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
        @Use( { COMPOSITE })
        protected CUDAAddressCast cast;
        @Use
        protected Value left;
        @Use( { COMPOSITE })
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
        @Use( { COMPOSITE })
        protected CUDAAddressCast cast;
        @Use
        protected Value left;
        @Use( { COMPOSITE })
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
        @Use( { COMPOSITE })
        protected CUDAAddressCast cast;
        @Use
        protected Value left;
        @Use( { COMPOSITE })
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
        @Use( { COMPOSITE })
        protected CUDAAddressCast cast;
        @Use
        protected Value left;
        @Use( { COMPOSITE })
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
        @Use( { COMPOSITE })
        protected CUDAAddressCast cast;
        @Use( { COMPOSITE })
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

    private static void frag(CUDACompilationResultBuilder crb, CUDAAssembler asm, Value f, int i) {
        asm.emitValue(crb, f);   // emits the variable's C name
        asm.emit("[" + i + "]");
    }

    @Opcode("MMA_FRAGMENT")
    public static class MMAFragmentStmt extends AbstractInstruction {
        public static final LIRInstructionClass<MMAFragmentStmt> TYPE =
                LIRInstructionClass.create(MMAFragmentStmt.class);

        @Def protected Value result;
        @Use protected Value initValue;
        private final int fragmentSize;
        private final boolean isInt8;

        public MMAFragmentStmt(Value result, Value initValue, int fragmentSize, boolean isInt8) {
            super(TYPE);
            this.result = result; this.initValue = initValue;
            this.fragmentSize = fragmentSize; this.isInt8 = isInt8;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            String cType = isInt8 ? "int" : "float";
            // float result[4];
            asm.indent();
            asm.emit(cType + " ");
            asm.emitValue(crb, result);
            asm.emit("[" + fragmentSize + "]");
            asm.delimiter();
            asm.eol();
            // result[i] = init;
            for (int i = 0; i < fragmentSize; i++) {
                asm.indent();
                frag(crb, asm, result, i);
                asm.emit(" = ");
                asm.emitValue(crb, initValue);
                asm.delimiter();
                asm.eol();
            }
        }
    }

    @Opcode("LDMATRIX")
    public static class LdmatrixStmt extends AbstractInstruction {

        public enum Variant {
            X4(4, false, false),                          // A: row-major
            X2_TRANS(2, true, false),                     // B: transposed (col-major)
            X2_TRANS_SWIZZLE_FP16_STRIDE32(2, true, true);// B: + XOR swizzle
            final int numRegs; final boolean trans; final boolean swizzle;
            Variant(int n, boolean t, boolean s) { numRegs = n; trans = t; swizzle = s; }
        }

        public static final LIRInstructionClass<LdmatrixStmt> TYPE =
                LIRInstructionClass.create(LdmatrixStmt.class);

        @Def protected Value result;
        @Use protected Value tile;
        @Use({OperandFlag.REG, OperandFlag.ILLEGAL}) protected Value byteOffset;
        private final Variant variant;
        private final int rowStride;

        public LdmatrixStmt(Variant v, Value result, Value tile, int rowStride, Value byteOffset) {
            super(TYPE);
            this.variant = v; this.result = result; this.tile = tile;
            this.rowStride = rowStride;
            this.byteOffset = (byteOffset == null) ? Value.ILLEGAL : byteOffset;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            int n = variant.numRegs;
            int strideShift = Integer.numberOfTrailingZeros(rowStride);
            String frag = asm.getStringValue(crb, result);   // base name once
            String tileName = asm.getStringValue(crb, tile);

            // unsigned frag[4];
            asm.indent();
            asm.emit("unsigned " + frag + "[" + n + "]");
            asm.delimiter();
            asm.eol();

            // Per-lane byte offset, computed in C (mirrors the PTX address arithmetic).
            // Use a unique block so the temporaries don't collide across statements.
            asm.indent();
            asm.emit("{");
            asm.eol();
            asm.pushIndent();
            asm.indent();
            asm.emit("unsigned __lane = threadIdx.x & 31u");
            asm.delimiter();
            asm.eol();
            asm.indent();
            asm.emit("unsigned __rit  = __lane & 7u");
            asm.delimiter();
            asm.eol();
            asm.indent();
            asm.emit("unsigned __grp  = __lane >> 3");
            asm.delimiter();
            asm.eol();
            asm.indent();
            asm.emit("unsigned __bo");
            asm.delimiter();
            asm.eol();

            if (variant == Variant.X4) {
                // rowOff = (grp&1)<<3 ; colOff = (grp>>1)<<4 ; row = rowOff+rit
                asm.indent();
                asm.emit("unsigned __row = ((__grp & 1u) << 3) + __rit");
                asm.delimiter();
                asm.eol();
                asm.indent();
                asm.emit("unsigned __col = (__grp >> 1) << 4");
                asm.delimiter();
                asm.eol();
                asm.indent();
                asm.emit("__bo = (__row << " + strideShift + ") + __col");
                asm.delimiter();
                asm.eol();
            } else {
                // colOff = (grp&1)<<7 ; bo = rit*stride + colOff
                asm.indent();
                asm.emit("unsigned __col = (__grp & 1u) << 7");
                asm.delimiter();
                asm.eol();
                asm.indent();
                asm.emit("__bo = (__rit << " + strideShift + ") + __col");
                asm.delimiter();
                asm.eol();
            }
            if (variant.swizzle) {
                // bo ^= ((bo >> 7) & 0b111) << 4   (matches swizzleStoreFp16Stride32: S=7,M=7,T=4)
                asm.indent();
                asm.emit("__bo ^= (((__bo >> 7) & 7u) << 4)");
                asm.delimiter();
                asm.eol();
            }
            if (byteOffset != null && !byteOffset.equals(Value.ILLEGAL)) {
                asm.indent();
                asm.emit("__bo += ");
                asm.emitValue(crb, byteOffset);
                asm.delimiter();
                asm.eol();
            }

            // smem address (shared state space, 32-bit) via explicit cvta.to.shared.
            asm.indent();
            asm.emit("unsigned __smem");
            asm.delimiter();
            asm.eol();
            asm.indent();
            asm.emit("asm volatile(\"{ .reg .u64 u; cvta.to.shared.u64 u, %1; cvt.u32.u64 %0, u; }\" "
                    + ": \"=r\"(__smem) : \"l\"((const char *) " + tileName + " + __bo))");
            asm.delimiter();
            asm.eol();

            String mnem = (variant == Variant.X4)
                    ? "ldmatrix.sync.aligned.m8n8.x4.shared.b16"
                    : "ldmatrix.sync.aligned.m8n8.x2.trans.shared.b16";
            asm.indent();
            StringBuilder ph = new StringBuilder();
            for (int i = 0; i < n; i++) { if (i > 0) ph.append(","); ph.append("%").append(i); }
            asm.emit("asm volatile(\"" + mnem + " {" + ph + "}, [%" + n + "];\" : ");
            for (int i = 0; i < n; i++) {
                if (i > 0) asm.emit(", ");
                asm.emit("\"=r\"(" + frag + "[" + i + "])");
            }
            asm.emit(" : \"r\"(__smem))");
            asm.delimiter();
            asm.eol();

            asm.popIndent();
            asm.indent();
            asm.emit("}");
            asm.eol();
        }
    }

    @Opcode("MMA_COMPUTE")
    public static class MMAComputeStmt extends AbstractInstruction {
        public static final LIRInstructionClass<MMAComputeStmt> TYPE =
                LIRInstructionClass.create(MMAComputeStmt.class);

        @Def protected Value result;
        @Use protected Value fragA, fragB, fragC;
        private final MMAShape shape;

        public MMAComputeStmt(Value result, Value a, Value b, Value c, MMAShape shape) {
            super(TYPE);
            this.result = result; this.fragA = a; this.fragB = b; this.fragC = c; this.shape = shape;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            boolean i8 = (shape == MMAShape.M16N8K32);
            String accType  = i8 ? "int"  : "float";
            String accCons  = i8 ? "r"    : "f";        // s32 -> "r", f32 -> "f"
            String suffix   = i8 ? ".row.col.s32.s8.s8.s32" : ".row.col.f32.f16.f16.f32";
            String d = asm.getStringValue(crb, result);
            String a = asm.getStringValue(crb, fragA);
            String b = asm.getStringValue(crb, fragB);
            String c = asm.getStringValue(crb, fragC);

            // accType result[4];
            asm.indent();
            asm.emit(accType + " " + d + "[4]");
            asm.delimiter();
            asm.eol();

            // asm volatile("mma.sync.aligned.<shape>.<suffix> {d0..d3},{a0..a3},{b0,b1},{c0..c3};"
            //              : "=<acc>"(d[0..3]) : "r"(a0..3),"r"(b0,1),"<acc>"(c0..3));
            asm.indent();
            asm.emit("asm volatile(\"mma.sync.aligned." + shape.getPtxName() + suffix
                    + " {%0,%1,%2,%3}, {%4,%5,%6,%7}, {%8,%9}, {%10,%11,%12,%13};\" : ");
            for (int i = 0; i < 4; i++) {
                if (i > 0) asm.emit(", ");
                asm.emit("\"=" + accCons + "\"(" + d + "[" + i + "])");
            }
            asm.emit(" : ");
            for (int i = 0; i < 4; i++) asm.emit((i > 0 ? ", " : "") + "\"r\"(" + a + "[" + i + "])");
            for (int i = 0; i < 2; i++) asm.emit(", \"r\"(" + b + "[" + i + "])");
            for (int i = 0; i < 4; i++) asm.emit(", \"" + accCons + "\"(" + c + "[" + i + "])");
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }
    }

    @Opcode("MMA_STORE")
    public static class MMAStoreStmt extends AbstractInstruction {
        public static final LIRInstructionClass<MMAStoreStmt> TYPE =
                LIRInstructionClass.create(MMAStoreStmt.class);

        @Use protected Value fragD, target, tileRow, tileCol, dimN;
        private final int headerElements;
        private final boolean isInt8;

        public MMAStoreStmt(Value fragD, Value target, Value tileRow, Value tileCol,
                            Value dimN, int headerElements, boolean isInt8) {
            super(TYPE);
            this.fragD = fragD; this.target = target; this.tileRow = tileRow;
            this.tileCol = tileCol; this.dimN = dimN;
            this.headerElements = headerElements; this.isInt8 = isInt8;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            String cType = isInt8 ? "int" : "float";
            int elemSize = 4;                    // both int32 and float32
            int headerBytes = headerElements * elemSize;
            String d   = asm.getStringValue(crb, fragD);
            String out = asm.getStringValue(crb, target);
            String row = asm.getStringValue(crb, tileRow);
            String col = asm.getStringValue(crb, tileCol);
            String ld  = asm.getStringValue(crb, dimN);

            asm.indent();
            asm.emit("{");
            asm.eol();
            asm.pushIndent();
            asm.indent();
            asm.emit("unsigned __lane = threadIdx.x & 31u");
            asm.delimiter();
            asm.eol();
            for (int i = 0; i < 4; i++) {
                int rowSel = (i < 2) ? 0 : 8;
                int colSel = i % 2;
                asm.indent();
                asm.emit("unsigned __rit" + i + " = (__lane >> 2)" + (rowSel != 0 ? " + 8u" : ""));
                asm.delimiter(); asm.eol();
                asm.indent();
                asm.emit("unsigned __cit" + i + " = ((__lane & 3u) << 1)" + (colSel != 0 ? " + 1u" : ""));
                asm.delimiter(); asm.eol();
                // Element index — no header here; header is on the byte pointer below.
                asm.indent();
                asm.emit("unsigned __idx" + i + " = (" + row + " + __rit" + i + ") * " + ld
                        + " + (" + col + " + __cit" + i + ")");
                asm.delimiter(); asm.eol();
                // ((cType *) ((char *) out + headerBytes))[__idx] = d[i];
                asm.indent();
                asm.emit("((" + cType + " *) ((char *) " + out + " + " + headerBytes + "u))[__idx" + i
                        + "] = " + d + "[" + i + "]");
                asm.delimiter();
                asm.eol();
            }
            asm.popIndent();
            asm.indent();
            asm.emit("}");
            asm.eol();
        }
    }

    @Opcode("SWIZZLED_STORE_FP16_STRIDE_32")
    public static class SwizzledStoreFP16Stride32Stmt extends AbstractInstruction {
        public static final LIRInstructionClass<SwizzledStoreFP16Stride32Stmt> TYPE =
                LIRInstructionClass.create(SwizzledStoreFP16Stride32Stmt.class);

        @Use protected Value localArray;
        @Use protected Value row;
        @Use protected Value column;
        @Use protected Value stride;
        @Use protected Value value;
        @Use({OperandFlag.REG, OperandFlag.ILLEGAL}) protected Value byteOffset;

        /** Single-warp / no sub-tile offset. */
        public SwizzledStoreFP16Stride32Stmt(Value localArray, Value row, Value column,
                                             Value stride, Value value) {
            this(localArray, row, column, stride, value, Value.ILLEGAL);
        }

        /** Offset-aware: {@code byteOffset} selects the sub-tile region (in bytes). */
        public SwizzledStoreFP16Stride32Stmt(Value localArray, Value row, Value column,
                                             Value stride, Value value, Value byteOffset) {
            super(TYPE);
            this.localArray = localArray;
            this.row = row;
            this.column = column;
            this.stride = stride;
            this.value = value;
            this.byteOffset = (byteOffset == null) ? Value.ILLEGAL : byteOffset;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            //   lin     = row * stride + column          (logical element index)
            //   byteOff = lin << 1                        (fp16 = 2 bytes)
            //   byteOff ^= (((byteOff >> 7) & 0b111) << 4)    (apply swizzle)
            //   [byteOff += byteOffset]                   (sub-tile base, in bytes)
            //   tile[byteOff >> 1] = value                (st.shared.b16 equivalent)
            String arr = asm.getStringValue(crb, localArray);

            asm.indent();
            asm.emit("{");
            asm.eol();
            asm.pushIndent();

            asm.indent();
            asm.emit("unsigned __lin = ");
            asm.emitValue(crb, row);
            asm.emit(" * ");
            asm.emitValue(crb, stride);
            asm.emit(" + ");
            asm.emitValue(crb, column);
            asm.delimiter(); asm.eol();

            asm.indent();
            asm.emit("unsigned __bo = __lin << 1");
            asm.delimiter();
            asm.eol();
            asm.indent();
            asm.emit("__bo ^= (((__bo >> 7) & 7u) << 4)");
            asm.delimiter();
            asm.eol();

            if (byteOffset != null && !byteOffset.equals(Value.ILLEGAL)) {
                asm.indent();
                asm.emit("__bo += ");
                asm.emitValue(crb, byteOffset);
                asm.delimiter(); asm.eol();
            }

            // half element write into shared memory (local arrays carry no header).
            asm.indent();
            asm.emit("((half *) " + arr + ")[__bo >> 1] = ");
            asm.emitValue(crb, value);
            asm.delimiter();
            asm.eol();

            asm.popIndent();
            asm.indent();
            asm.emit("}");
            asm.eol();
        }
    }

    @Opcode("HALF_BITS_TO_INT")
    public static class HalfBitsToIntStmt extends AbstractInstruction {
        public static final LIRInstructionClass<HalfBitsToIntStmt> TYPE =
                LIRInstructionClass.create(HalfBitsToIntStmt.class);

        @Def protected Value result;
        @Use protected Value halfValue;

        public HalfBitsToIntStmt(Value result, Value halfValue) {
            super(TYPE);
            this.result = result;
            this.halfValue = halfValue;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            // u32_result = (unsigned) __half_as_ushort(half_value);
            asm.indent();
            asm.emitValue(crb, result);
            asm.emit(" = (unsigned) __half_as_ushort(");
            asm.emitValue(crb, halfValue);
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }
    }

    @Opcode("SWIZZLED_LOAD_FP16_STRIDE_32")
    public static class SwizzledLoadFP16Stride32Stmt extends AbstractInstruction {
        public static final LIRInstructionClass<SwizzledLoadFP16Stride32Stmt> TYPE =
                LIRInstructionClass.create(SwizzledLoadFP16Stride32Stmt.class);

        @Def protected Value result;
        @Use protected Value localArray;
        @Use protected Value row;
        @Use protected Value column;
        @Use protected Value stride;

        public SwizzledLoadFP16Stride32Stmt(Value result, Value localArray, Value row,
                                            Value column, Value stride) {
            super(TYPE);
            this.result = result;
            this.localArray = localArray;
            this.row = row;
            this.column = column;
            this.stride = stride;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            //   lin     = row * stride + column
            //   byteOff = lin << 1
            //   byteOff ^= (((byteOff >> 7) & 0b111) << 4)
            //   result = ((half *) tile)[byteOff >> 1]
            String arr = asm.getStringValue(crb, localArray);

            asm.indent();
            asm.emit("{");
            asm.eol();
            asm.pushIndent();

            asm.indent();
            asm.emit("unsigned __lin = ");
            asm.emitValue(crb, row);
            asm.emit(" * ");
            asm.emitValue(crb, stride);
            asm.emit(" + ");
            asm.emitValue(crb, column);
            asm.delimiter();
            asm.eol();

            asm.indent();
            asm.emit("unsigned __bo = __lin << 1");
            asm.delimiter();
            asm.eol();

            asm.indent();
            asm.emit("__bo ^= (((__bo >> 7) & 7u) << 4)");
            asm.delimiter();
            asm.eol();

            // result = ((half *) tile)[byteOff >> 1]
            asm.indent();
            asm.emitValue(crb, result);
            asm.emit(" = ((half *) " + arr + ")[__bo >> 1]");
            asm.delimiter();
            asm.eol();

            asm.popIndent();
            asm.indent();
            asm.emit("}");
            asm.eol();
        }
    }

}
