/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, 2024, 2025, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.drivers.ptx.graal.lir;

import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil.getFPURoundingMode;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryOp.ADD;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryOp.DIV_APPROX;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryOp.MUL;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryOp.MUL_LO;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryOp.SUB;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.ASSIGN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.COMMA;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.CONVERT;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.CONVERT_RN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.CURLY_BRACKETS_CLOSE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.CURLY_BRACKETS_OPEN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.DOT;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.GLOBAL_MEM_MODIFIER;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.MOVE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.NEGATION;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.OP_GUARD;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.SPACE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.SQUARE_BRACKETS_CLOSE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.SQUARE_BRACKETS_OPEN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.STMT_DELIMITER;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.TAB;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.VECTOR;

import java.nio.charset.StandardCharsets;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ValueKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXNullaryOp;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.ptx.graal.meta.PTXMemorySpace;

public class PTXLIRStmt {

    protected abstract static class AbstractInstruction extends LIRInstruction {
        protected AbstractInstruction(LIRInstructionClass<? extends AbstractInstruction> c) {
            super(c);
        }

        @Override
        public final void emitCode(CompilationResultBuilder crb) {
            emitCode((PTXCompilationResultBuilder) crb, (PTXAssembler) crb.asm);
        }

        public abstract void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm);
    }

    @Opcode("DIVHALF")
    public static class DivideHalfFloatStmt extends AbstractInstruction {

        public static final LIRInstructionClass<DivideHalfFloatStmt> TYPE = LIRInstructionClass.create(DivideHalfFloatStmt.class);

        @Use
        protected Value dividend;
        @Use
        protected Value divisor;
        @Def
        protected Value dividendFloat;
        @Def
        protected Value divisorFloat;
        @Def
        protected Value floatResult;
        @Def
        protected Value halfFloatResult;

        public DivideHalfFloatStmt(Value dividend, Value divisor, Value dividendFloat, Value divisorFloat, Value floatResult, Value halfFloatResult) {
            super(TYPE);
            this.dividend = dividend;
            this.divisor = divisor;
            this.dividendFloat = dividendFloat;
            this.divisorFloat = divisorFloat;
            this.floatResult = floatResult;
            this.halfFloatResult = halfFloatResult;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            // convert divident from half-float to float
            asm.emitSymbol(TAB);
            asm.emit(CONVERT + DOT + dividendFloat.getPlatformKind() + DOT + dividend.getPlatformKind());
            asm.emitSymbol(SPACE);
            asm.emitValue(dividendFloat);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(dividend);
            asm.delimiter();
            asm.eol();
            //convert divisor from half-float to float
            asm.emitSymbol(TAB);
            asm.emit(CONVERT + DOT + divisorFloat.getPlatformKind() + DOT + divisor.getPlatformKind());
            asm.emitSymbol(SPACE);
            asm.emitValue(divisorFloat);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(divisor);
            asm.delimiter();
            asm.eol();
            // divide the two float values
            asm.emitSymbol(TAB);
            asm.emit(DIV_APPROX + DOT + floatResult.getPlatformKind());
            asm.emitSymbol(SPACE);
            asm.emitValue(floatResult);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(dividendFloat);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(divisorFloat);
            asm.delimiter();
            asm.eol();
            //convert the result from float to half-float
            asm.emitSymbol(TAB);
            asm.emit(CONVERT_RN + DOT + halfFloatResult.getPlatformKind() + DOT + floatResult.getPlatformKind());
            asm.emitSymbol(SPACE);
            asm.emitValue(halfFloatResult);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(floatResult);
            asm.delimiter();
            asm.eol();
        }

    }

    @Opcode("CONVERTHALF")
    public static class ConvertHalfFloatStmt extends AbstractInstruction {

        public static final LIRInstructionClass<ConvertHalfFloatStmt> TYPE = LIRInstructionClass.create(ConvertHalfFloatStmt.class);

        @Def
        protected Value halfFloatVariable;
        @Use
        protected Value input;
        @Def
        protected Value intermediate;

        public ConvertHalfFloatStmt(Value halfFloatVariable, Value input, Value intermediate) {
            super(TYPE);
            this.halfFloatVariable = halfFloatVariable;
            this.input = input;
            this.intermediate = intermediate;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            // move value to a float variable
            asm.emitSymbol(TAB);
            asm.emit(MOVE + DOT + intermediate.getPlatformKind());
            asm.emitSymbol(SPACE);
            asm.emitValue(intermediate);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(input);
            asm.delimiter();
            asm.eol();

            // convert float to half float
            asm.emitSymbol(TAB);
            asm.emit(CONVERT_RN + DOT + halfFloatVariable.getPlatformKind() + DOT + intermediate.getPlatformKind());
            asm.emitSymbol(SPACE);
            asm.emitValue(halfFloatVariable);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(intermediate);
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
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emitSymbol(TAB);
            asm.emit(ADD + DOT + "rn.f16");
            asm.emitSymbol(SPACE);
            asm.emitValue(result);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(x);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(y);
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
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emitSymbol(TAB);
            asm.emit(ADD + DOT + "rn.f16");
            asm.emitSymbol(SPACE);
            asm.emitValue(result);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(x);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(y);
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
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emitSymbol(TAB);
            asm.emit(SUB + DOT + "rn.f16");
            asm.emitSymbol(SPACE);
            asm.emitValue(result);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(x);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(y);
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
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emitSymbol(TAB);
            asm.emit(SUB + DOT + "rn.f16");
            asm.emitSymbol(SPACE);
            asm.emitValue(result);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(x);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(y);
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
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emitSymbol(TAB);
            asm.emit(MUL + DOT + "rn.f16");
            asm.emitSymbol(SPACE);
            asm.emitValue(result);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(x);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(y);
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
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emitSymbol(TAB);
            asm.emit(MUL + DOT + "rn.f16");
            asm.emitSymbol(SPACE);
            asm.emitValue(result);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(x);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(y);
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
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emitSymbol(TAB);
            asm.emit(CONVERT + DOT + "f32" + DOT + "f16");
            asm.emitSymbol(SPACE);
            asm.emitValue(floatValue);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(halfValue);
            asm.delimiter();
            asm.eol();
        }

    }

    @Opcode("LOCAL_MEMORY_ACCESS")
    public static class LocalMemoryAccessStmt extends AbstractInstruction {

        public static final LIRInstructionClass<LocalMemoryAccessStmt> TYPE = LIRInstructionClass.create(LocalMemoryAccessStmt.class);
        private final PTXKind lhsKind;
        private final PTXKind rhsKind;
        @Def
        protected Value lhs;
        @Use
        protected Value rhs;

        public LocalMemoryAccessStmt(Value lhs, Value rhs) {
            this(lhs, (PTXKind) lhs.getPlatformKind(), rhs, (PTXKind) rhs.getPlatformKind());
        }

        public LocalMemoryAccessStmt(Value lhs, PTXKind lhsKind, Value rhs, PTXKind rhsKind) {
            super(TYPE);
            this.lhs = lhs;
            this.rhs = rhs;
            this.lhsKind = lhsKind;
            this.rhsKind = rhsKind;
        }

        // local memory base is always u32 regardless of the FixedArray type
        public static boolean arrayBaseConversion(PTXKind lhsKind, PTXKind rhsKind) {
            if ((rhsKind.isF32() || rhsKind.isS32() || rhsKind.isF64() || rhsKind.isS64()) && lhsKind.isU32()) {
                return true;
            }
            return lhsKind == rhsKind && !lhsKind.is8Bit();
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emitSymbol(TAB);
            if (arrayBaseConversion(lhsKind, rhsKind)) {
                asm.emit(MOVE + DOT + lhsKind.toString());
            } else {
                asm.emit(CONVERT + DOT);
                if ((lhsKind.isFloating() || rhsKind.isFloating()) && getFPURoundingMode(lhsKind, rhsKind) != null) {
                    asm.emit(getFPURoundingMode(lhsKind, rhsKind));
                    asm.emitSymbol(DOT);
                }
                asm.emit(lhsKind.toString());
                asm.emitSymbol(DOT);
                asm.emit(rhsKind.toString());
            }
            asm.emitSymbol(TAB);
            asm.emitValue(lhs);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(rhs);
            asm.delimiter();
            asm.eol();
        }
    }

    @Opcode("ASSIGN")
    public static class AssignStmt extends AbstractInstruction {

        public static final LIRInstructionClass<AssignStmt> TYPE = LIRInstructionClass.create(AssignStmt.class);
        private final PTXKind lhsKind;
        private final PTXKind rhsKind;
        @Def
        protected Value lhs;
        @Use
        protected Value rhs;

        public AssignStmt(Value lhs, Value rhs) {
            this(lhs, (PTXKind) lhs.getPlatformKind(), rhs, (PTXKind) rhs.getPlatformKind());
        }

        public AssignStmt(Value lhs, PTXKind lhsKind, Value rhs, PTXKind rhsKind) {
            super(TYPE);
            this.lhs = lhs;
            this.rhs = rhs;
            this.lhsKind = lhsKind;
            this.rhsKind = rhsKind;
        }

        public static boolean shouldEmitMove(PTXKind lhsKind, PTXKind rhsKind) {
            if (rhsKind.isF16() && lhsKind.isB16() || rhsKind.isB16() && lhsKind.isF16()) {
                return true;
            }
            return lhsKind == rhsKind && !lhsKind.is8Bit();
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            if (rhs instanceof PTXLIROp) {
                ((PTXLIROp) rhs).emit(crb, asm, (Variable) lhs);
            } else if (lhsKind.isVector() && rhsKind.isVector()) {
                Variable rhsVar = (Variable) rhs;
                Variable lhsVar = (Variable) lhs;
                PTXVectorSplit rhsVectorSplit = new PTXVectorSplit(rhsVar);
                PTXVectorSplit lhsVectorSplit = new PTXVectorSplit(lhsVar);
                PTXVectorAssign.doVectorToVectorAssign(asm, lhsVectorSplit, rhsVectorSplit);
            } else if (rhs instanceof PTXArchitecture.PTXBuiltInRegister) {
                asm.emitSymbol(TAB);
                if (shouldEmitMove(lhsKind, rhsKind)) {
                    asm.emit(MOVE + DOT + lhsKind.toString());
                } else {
                    asm.emit(CONVERT + DOT);
                    if ((lhsKind.isFloating() || rhsKind.isFloating()) && getFPURoundingMode(lhsKind, rhsKind) != null) {
                        asm.emit(getFPURoundingMode(lhsKind, rhsKind));
                        asm.emitSymbol(DOT);
                    }
                    asm.emit(lhsKind.toString());
                    asm.emitSymbol(DOT);
                    asm.emit(rhsKind.toString());
                }
                asm.emitSymbol(TAB);
                asm.emitValue(lhs);
                asm.emitSymbol(COMMA + SPACE);
                asm.emitBuiltIn((PTXArchitecture.PTXBuiltInRegister) rhs);

            } else {
                asm.emitSymbol(TAB);
                if (shouldEmitMove(lhsKind, rhsKind)) {
                    if (lhsKind.isF16()) {
                        // here
                        asm.emit(MOVE + DOT + "b16");
                    } else {
                        asm.emit(MOVE + DOT + lhsKind.toString());
                    }
                } else {
                    asm.emit(CONVERT + DOT);
                    if ((lhsKind.isFloating() || rhsKind.isFloating()) && getFPURoundingMode(lhsKind, rhsKind) != null) {
                        asm.emit(getFPURoundingMode(lhsKind, rhsKind));
                        asm.emitSymbol(DOT);
                    }
                    asm.emit(lhsKind.toString());
                    asm.emitSymbol(DOT);
                    asm.emit(rhsKind.toString());
                }
                asm.emitSymbol(TAB);
                asm.emitValue(lhs);
                asm.emitSymbol(COMMA + SPACE);
                asm.emitValue(rhs);
            }
            asm.delimiter();
            asm.eol();
        }

        public Value getResult() {
            return lhs;
        }

        public Value getExpr() {
            return rhs;
        }
    }

    @Opcode("CONVERT_ADDRESS")
    public static class ConvertAddressStmt extends AbstractInstruction {
        public static final LIRInstructionClass<ConvertAddressStmt> TYPE = LIRInstructionClass.create(ConvertAddressStmt.class);

        @Use
        private final Value src;
        @Use
        private final Value dest;
        @Use
        private final PTXMemorySpace srcMemorySpace;

        public ConvertAddressStmt(Value dest, Value src, PTXMemorySpace srcMemorySpace) {
            super(TYPE);
            this.src = src;
            this.dest = dest;
            this.srcMemorySpace = srcMemorySpace;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            PTXKind destKind = (PTXKind) dest.getPlatformKind();

            PTXNullaryOp.CVTA.emit(crb, null);
            asm.emitSymbol(DOT);
            asm.emitSymbol(srcMemorySpace.getName());
            asm.emitSymbol(DOT);
            asm.emitSymbol(destKind.is64Bit() ? PTXKind.U64.toString() : PTXKind.U32.toString());
            asm.emitSymbol(SPACE);
            asm.emitValue(dest);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(src);
            asm.emitSymbol(STMT_DELIMITER);
            asm.eol();
        }
    }

    @Opcode("EXPR")
    public static class ExprStmt extends AbstractInstruction {
        public static final LIRInstructionClass<ExprStmt> TYPE = LIRInstructionClass.create(ExprStmt.class);

        @Use
        protected Value expr;

        public ExprStmt(PTXLIROp expr) {
            super(TYPE);
            this.expr = expr;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            if (expr instanceof PTXLIROp) {
                ((PTXLIROp) expr).emit(crb, asm, null);
            } else {
                asm.emitValue(expr);
            }
            asm.delimiter();
            asm.eol();
        }

        public Value getExpr() {
            return expr;
        }
    }

    @Opcode("LOAD")
    public static class LoadStmt extends AbstractInstruction {
        public static final LIRInstructionClass<LoadStmt> TYPE = LIRInstructionClass.create(LoadStmt.class);

        @Use
        protected Variable dest;

        @Use
        PTXUnary.MemoryAccess address;

        @Use
        PTXNullaryOp loadOp;

        public LoadStmt(PTXUnary.MemoryAccess address, Variable dest, PTXNullaryOp op) {
            super(TYPE);

            this.dest = dest;
            this.loadOp = op;
            this.address = address;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            // ld.u64 %rd9, [%rd8];
            loadOp.emit(crb, null);
            asm.emitSymbol(DOT);
            asm.emit(address.getBase().memorySpace.getName());
            asm.emitSymbol(DOT);
            asm.emit(dest.getPlatformKind().toString());
            asm.emitSymbol(TAB);

            asm.emitValue(dest);
            asm.emitSymbol(COMMA);
            asm.space();
            address.emit(crb, asm, null);
            asm.delimiter();
            asm.eol();
        }
    }

    @Opcode("HALFLOAD")
    public static class HalfFloatLoadStmt extends AbstractInstruction {
        public static final LIRInstructionClass<HalfFloatLoadStmt> TYPE = LIRInstructionClass.create(HalfFloatLoadStmt.class);

        @Use
        protected Variable dest;

        @Use
        PTXUnary.MemoryAccess address;

        @Use
        PTXNullaryOp loadOp;

        public HalfFloatLoadStmt(PTXUnary.MemoryAccess address, Variable dest, PTXNullaryOp op) {
            super(TYPE);
            this.dest = dest;
            this.loadOp = op;
            this.address = address;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            loadOp.emit(crb, null);
            asm.emitSymbol(DOT);
            asm.emit(address.getBase().memorySpace.getName());
            asm.emit(DOT + PTXKind.B16);
            asm.emitSymbol(TAB);

            asm.emitValue(dest);
            asm.emitSymbol(COMMA);
            asm.space();
            address.emit(crb, asm, null);
            asm.delimiter();
            asm.eol();
        }
    }

    @Opcode("VLOAD")
    public static class VectorLoadStmt extends AbstractInstruction {

        public static final LIRInstructionClass<VectorLoadStmt> TYPE = LIRInstructionClass.create(VectorLoadStmt.class);

        @Def
        protected Variable dest;
        @Use
        protected PTXUnary.MemoryAccess address;

        public VectorLoadStmt(Variable dest, PTXUnary.MemoryAccess address) {
            super(TYPE);
            this.dest = dest;
            this.address = address;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            PTXVectorSplit vectorSplitData = new PTXVectorSplit(dest);

            for (int i = 0; i < vectorSplitData.vectorNames.length; i++) {
                PTXNullaryOp.LD.emit(crb, null);
                asm.emitSymbol(DOT);
                asm.emit(address.getBase().memorySpace.getName());
                if (!vectorSplitData.fullUnwrapVector) {
                    asm.emitSymbol(DOT);
                    asm.emit(VECTOR + vectorSplitData.newKind.getVectorLength());
                }
                asm.emitSymbol(DOT);
                asm.emit(vectorSplitData.fullUnwrapVector ? vectorSplitData.newKind.toString() : vectorSplitData.newKind.getElementKind().toString());
                asm.emitSymbol(TAB);

                asm.emitSymbol(vectorSplitData.vectorNames[i]);
                asm.emitSymbol(COMMA);
                asm.space();
                address.emit(asm, i * vectorSplitData.newKind.getSizeInBytes());
                asm.delimiter();
                asm.eol();
            }
        }

        public Value getResult() {
            return dest;
        }

        public PTXUnary.MemoryAccess getAddress() {
            return address;
        }
    }

    @Opcode("STORE")
    public static class StoreStmt extends AbstractInstruction {

        public static final LIRInstructionClass<StoreStmt> TYPE = LIRInstructionClass.create(StoreStmt.class);

        @Use
        protected Value rhs;
        @Use
        protected PTXUnary.MemoryAccess address;

        public StoreStmt(PTXUnary.MemoryAccess address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.address = address;
        }

        public void emitNormalCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            // st.global.u32 [%rd19], %r10;
            PTXNullaryOp.ST.emit(crb, null);
            asm.emitSymbol(DOT);
            asm.emit(address.getBase().memorySpace.getName());
            asm.emitSymbol(DOT);
            asm.emit(rhs.getPlatformKind().toString());
            asm.emitSymbol(TAB);

            address.emit(crb, asm, null);
            asm.emitSymbol(COMMA);
            asm.space();

            asm.emitValueOrOp(crb, rhs, null);
            asm.delimiter();
            asm.eol();
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            emitNormalCode(crb, asm);
        }

        public Value getRhs() {
            return rhs;
        }

        public PTXUnary.MemoryAccess getAddress() {
            return address;
        }
    }

    @Opcode("STOREHALF")
    public static class HalfFloatStoreStmt extends AbstractInstruction {

        public static final LIRInstructionClass<HalfFloatStoreStmt> TYPE = LIRInstructionClass.create(HalfFloatStoreStmt.class);

        @Use
        protected Value rhs;
        @Use
        protected PTXUnary.MemoryAccess address;

        public HalfFloatStoreStmt(PTXUnary.MemoryAccess address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.address = address;
        }

        public void emitNormalCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            PTXNullaryOp.ST.emit(crb, null);
            asm.emitSymbol(DOT);
            asm.emit(address.getBase().memorySpace.getName());
            asm.emit(DOT + PTXKind.B16);
            asm.emitSymbol(TAB);

            address.emit(crb, asm, null);
            asm.emitSymbol(COMMA);
            asm.space();

            asm.emitValueOrOp(crb, rhs, null);
            asm.delimiter();
            asm.eol();
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            emitNormalCode(crb, asm);
        }
    }

    @Opcode("VSTORE")
    public static class VectorStoreStmt extends AbstractInstruction {

        public static final LIRInstructionClass<VectorStoreStmt> TYPE = LIRInstructionClass.create(VectorStoreStmt.class);

        @Def
        protected Variable source;
        @Use
        protected PTXUnary.MemoryAccess address;

        public VectorStoreStmt(Variable source, PTXUnary.MemoryAccess address) {
            super(TYPE);
            this.source = source;
            this.address = address;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            PTXVectorSplit vectorSplitData = new PTXVectorSplit(source);

            for (int i = 0; i < vectorSplitData.vectorNames.length; i++) {
                PTXNullaryOp.ST.emit(crb, null);
                asm.emitSymbol(DOT);
                asm.emit(address.getBase().memorySpace.getName());
                if (!vectorSplitData.fullUnwrapVector) {
                    asm.emitSymbol(DOT);
                    asm.emit(VECTOR + vectorSplitData.newKind.getVectorLength());
                }
                asm.emitSymbol(DOT);
                asm.emit(vectorSplitData.fullUnwrapVector ? vectorSplitData.newKind.toString() : vectorSplitData.newKind.getElementKind().toString());
                asm.emitSymbol(TAB);

                address.emit(asm, i * vectorSplitData.newKind.getSizeInBytes());
                asm.emitSymbol(COMMA);
                asm.space();
                asm.emitSymbol(vectorSplitData.vectorNames[i]);
                asm.delimiter();
                asm.eol();
            }
        }
    }

    @Opcode("AtomAdd")
    public static class AtomOperation extends AbstractInstruction {
        public static final LIRInstructionClass<AtomOperation> TYPE = LIRInstructionClass.create(AtomOperation.class);

        @Use
        protected Variable dest;

        @Use
        PTXUnary.MemoryAccess address;

        @Use
        PTXNullaryOp atomicOp;

        @Use
        PTXAssembler.PTXBinaryOp arithmeticOp;

        @Use
        Value inc;

        public AtomOperation(PTXUnary.MemoryAccess address, Variable dest, PTXNullaryOp atomicOp, PTXAssembler.PTXBinaryOp arithmeticOp, Value inc) {
            super(TYPE);

            this.address = address;
            this.dest = dest;
            this.atomicOp = atomicOp;
            this.arithmeticOp = arithmeticOp;
            this.inc = inc;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            atomicOp.emit(crb, null);
            asm.emitSymbol(DOT);
            asm.emit(address.getBase().memorySpace.getName());
            asm.emitSymbol(DOT);
            asm.emit(arithmeticOp.toString());
            asm.emitSymbol(DOT);
            asm.emit(resolvePTXTypeFromValueKind(dest.getValueKind()));
            asm.emitSymbol(TAB);

            asm.emitValue(dest);
            asm.emitSymbol(COMMA);
            asm.space();
            address.emit(crb, asm, null);
            asm.emitSymbol(COMMA);
            asm.space();
            asm.emitValue(inc);
            asm.delimiter();
            asm.eol();
        }
    }

    /*
     * This method helps to resolve the PTX type for the atom.add operation. The valid types for instruction 'atom'
     * .u32 or .s32 or .u64 or .f64 or f16 or f16x2 or .f32 or .bf16 or .bf16x2.
     * Hence, we need to return 'u64' as the type for Java 'long' types.
     */
    private static String resolvePTXTypeFromValueKind(ValueKind valueKind) {
        switch (valueKind.toString().toLowerCase()) {
            case "s64" -> {
                return "u64";
            }
            default -> {
                return valueKind.toString().toLowerCase();
            }
        }
    }

    @Opcode("GUARDED_STMT")
    public static class ConditionalStatement extends AbstractInstruction {
        public static final LIRInstructionClass<ConditionalStatement> TYPE = LIRInstructionClass.create(ConditionalStatement.class);

        @Use
        private final AbstractInstruction instruction;

        @Use
        private final Variable guard;

        @Use
        private final boolean isNegated;

        public ConditionalStatement(AbstractInstruction instr, Variable guard, boolean isNegated) {
            super(TYPE);
            this.instruction = instr;
            this.guard = guard;
            this.isNegated = isNegated;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emitSymbol(TAB);
            asm.emitSymbol(OP_GUARD);
            if (isNegated)
                asm.emitSymbol(NEGATION);
            asm.emitValue(guard);

            asm.convertNextTabToSpace();
            instruction.emitCode(crb, asm);
        }
    }

    @Opcode("PRINTF_STRING_STMT")
    public static class PrintfStringDeclarationStmt extends AbstractInstruction {

        public static final LIRInstructionClass<PrintfStringDeclarationStmt> TYPE = LIRInstructionClass.create(PrintfStringDeclarationStmt.class);

        @Use
        private final Value stringValue;

        @Use
        private final Value dest;

        public PrintfStringDeclarationStmt(Value dest, Value stringValue) {
            super(TYPE);
            this.dest = dest;
            this.stringValue = stringValue;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            String string = PTXAssembler.formatConstant((ConstantValue) stringValue);
            string = "tornado[%u, %u, %u]> " + string;
            byte[] asciiBytes = string.getBytes(StandardCharsets.US_ASCII);
            {
                // Replace "\n" (0x5C 0x6E) with NL (0xA) and NULL (0x0) characters
                byte first = asciiBytes[asciiBytes.length - 2];
                byte second = asciiBytes[asciiBytes.length - 1];
                if (first == 0x5C && second == 0x6E) {
                    asciiBytes[asciiBytes.length - 2] = 0xA;
                    asciiBytes[asciiBytes.length - 1] = 0x0;
                }
            }

            asm.emitSymbol(TAB);
            asm.emitSymbol(DOT + GLOBAL_MEM_MODIFIER + SPACE);
            asm.emitSymbol(DOT + dest.getPlatformKind().toString());
            asm.emitSymbol(SPACE);
            asm.emitValue(dest);
            asm.emitSymbol(SQUARE_BRACKETS_OPEN + asciiBytes.length + SQUARE_BRACKETS_CLOSE);
            asm.emitSymbol(SPACE + ASSIGN + SPACE);
            asm.emitSymbol(CURLY_BRACKETS_OPEN);
            for (int i = 0; i < asciiBytes.length - 1; i++) {
                asm.emitSymbol(asciiBytes[i] + COMMA + SPACE);
            }
            asm.emitSymbol(asciiBytes[asciiBytes.length - 1] + CURLY_BRACKETS_CLOSE);
            asm.emitSymbol(STMT_DELIMITER);
            asm.eol();
        }
    }

    @Opcode("PRIVATE_ARRAY_COPY")
    public static class PrivateArrayCopyStmt extends AbstractInstruction {

        public static final LIRInstructionClass<PrivateArrayCopyStmt> TYPE = LIRInstructionClass.create(PrivateArrayCopyStmt.class);
        @Use
        protected Value index;
        @Use
        protected Value arrayToBeCopied;
        @Use
        protected Value offsetedIndex;
        @Use
        protected Value baseAddress;
        @Use
        protected Value offset;

        public PrivateArrayCopyStmt(Value index, Value arrayToBeCopied, Value offsetedIndex, Value addResult, Value offset) {
            super(TYPE);
            this.index = index;
            this.arrayToBeCopied = arrayToBeCopied;
            this.offsetedIndex = offsetedIndex;
            this.baseAddress = addResult;
            this.offset = offset;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            // mult index with offset
            // since the index is always an int, s32 is hardcoded
            asm.emitSymbol(TAB);
            asm.emit(MUL_LO + DOT + "s32");
            asm.emitSymbol(SPACE);
            asm.emitValue(offsetedIndex);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(index);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(offset);
            asm.delimiter();
            asm.eol();
            // add base with offset
            // since local memory base is always an unsigned int, u32 is hardcoded
            asm.emitSymbol(TAB);
            asm.emit(ADD + DOT + "u32");
            asm.emitSymbol(SPACE);
            asm.emitValue(baseAddress);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(arrayToBeCopied);
            asm.emitSymbol(COMMA + SPACE);
            asm.emitValue(offsetedIndex);
            asm.delimiter();
            asm.eol();
        }

    }
}
