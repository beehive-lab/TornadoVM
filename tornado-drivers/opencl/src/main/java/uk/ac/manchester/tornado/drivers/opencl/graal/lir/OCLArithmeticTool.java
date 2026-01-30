/*
 * Copyright (c) 2018, 2020, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryIntrinsic.RSQRT;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGenerator;

import uk.ac.manchester.tornado.drivers.common.code.CodeUtil;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLLIRKindTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLTernaryIntrinsic;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryIntrinsic;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLLIRGenerator;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.LoadStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.StoreAtomicAddFloatStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.StoreAtomicAddStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.StoreAtomicMulStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.StoreAtomicSubStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.StoreStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.VectorLoadStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.VectorStoreStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLUnary.MemoryAccess;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLUnary.OCLAddressCast;
import uk.ac.manchester.tornado.drivers.opencl.graal.meta.OCLMemorySpace;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorUtil;

public class OCLArithmeticTool extends ArithmeticLIRGenerator {

    public OCLLIRGenerator getGen() {
        return (OCLLIRGenerator) getLIRGen();
    }

    public OCLLIROp genBinaryExpr(OCLBinaryOp op, LIRKind lirKind, Value x, Value y) {
        return new OCLBinary.Expr(op, lirKind, x, y);
    }

    public OCLLIROp genTestBinaryExpr(OCLBinaryOp op, LIRKind lirKind, Value x, Value y) {
        return new OCLBinary.TestZeroExpression(op, lirKind, x, y);
    }

    public OCLLIROp genTestNegateBinaryExpr(OCLBinaryOp op, LIRKind lirKind, Value x, Value y) {
        return new OCLBinary.TestNegateZeroExpression(op, lirKind, x, y);
    }

    public OCLLIROp genBinaryIntrinsic(OCLBinaryIntrinsic op, LIRKind lirKind, Value x, Value y) {
        return new OCLBinary.Intrinsic(op, lirKind, x, y);
    }

    public Variable emitBinaryAssign(OCLBinaryOp op, LIRKind lirKind, Value x, Value y) {
        final Variable result = getGen().newVariable(lirKind);
        getGen().append(new AssignStmt(result, genBinaryExpr(op, lirKind, x, y)));
        return result;
    }

    public Variable emitBinaryAssign(OCLBinaryIntrinsic op, LIRKind lirKind, Value x, Value y) {
        final Variable result = getGen().newVariable(lirKind);
        getGen().append(new AssignStmt(result, genBinaryIntrinsic(op, lirKind, x, y)));
        return result;
    }

    public OCLLIROp genUnaryExpr(OCLUnaryOp op, LIRKind lirKind, Value value) {
        return new OCLUnary.Expr(op, lirKind, value);
    }

    public OCLLIROp genUnaryExpr(OCLUnaryIntrinsic op, LIRKind lirKind, Value value) {
        return new OCLUnary.Intrinsic(op, lirKind, value);
    }

    public Variable emitUnaryAssign(OCLUnaryOp op, LIRKind lirKind, Value value) {
        final Variable result = getGen().newVariable(lirKind);
        getGen().append(new AssignStmt(result, genUnaryExpr(op, lirKind, value)));
        return result;
    }

    public Variable emitUnaryAssign(OCLUnaryIntrinsic op, LIRKind lirKind, Value value) {
        final Variable result = getGen().newVariable(lirKind);
        getGen().append(new AssignStmt(result, genUnaryExpr(op, lirKind, value)));
        return result;
    }

    @Override
    public Variable emitAdd(LIRKind lirKind, Value x, Value y, boolean setFlags) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitAdd: %s + %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.ADD, lirKind, x, y);
    }

    @Override
    public Value emitAnd(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitAnd: %s & %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.BITWISE_AND, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitDiv(Value x, Value y, LIRFrameState frameState) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitDiv: %s / %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.DIV, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitFloatConvert(FloatConvert floatConvert, Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitFloatConvert: (%s) %s", floatConvert, input);
        switch (floatConvert) {
            case I2D:
                return emitUnaryAssign(OCLUnaryOp.CAST_TO_DOUBLE, LIRKind.value(OCLKind.DOUBLE), input);
            default:
                unimplemented("float convert %s", floatConvert);
        }
        return null;

    }

    @Override
    public Value emitMul(Value x, Value y, boolean setFlags) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitMul: %s * %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.MUL, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitMulHigh(Value x, Value y) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitNegate(Value x, boolean setFlags) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitNegate: - %s", x);
        return emitUnaryAssign(OCLUnaryOp.NEGATE, LIRKind.combine(x), x);
    }

    @Override
    public Value emitNot(Value x) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitNot: ~ %s", x);
        return emitUnaryAssign(OCLUnaryOp.BITWISE_NOT, LIRKind.combine(x), x);
    }

    @Override
    public Value emitOr(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitOr: %s | %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.BITWISE_OR, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitReinterpret(LIRKind lirKind, Value x) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitRem(Value x, Value y, LIRFrameState frameState) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitRem: %s %% %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.MOD, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitShl(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitShl: %s << %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.BITWISE_LEFT_SHIFT, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitShr(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitShr: %s >> %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.BITWISE_RIGHT_SHIFT, LIRKind.combine(x, y), x, y);
    }


    private OCLUnaryOp getSignExtendOp(int toBits) {
        return switch (toBits) {
            case 8 -> OCLUnaryOp.CAST_TO_BYTE;
            case 16 -> OCLUnaryOp.CAST_TO_SHORT;
            case 32 -> OCLUnaryOp.CAST_TO_INT;
            case 64 -> OCLUnaryOp.CAST_TO_LONG;
            default -> throw new UnsupportedOperationException("Unimplemented case for toBits: " + toBits);
        };
    }

    @Override
    public Value emitNarrow(Value x, int toBits) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitNarrow: %s, %d", x, toBits);
        LIRKind lirKind = getGen().getLIRKindTool().getIntegerKind(toBits);
        return emitUnaryAssign(getSignExtendOp(toBits), lirKind, x);
    }

    @Override
    public Value emitSignExtend(Value x, int fromBits, int toBits) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitSignExtend: %s, %d, %d", x, fromBits, toBits);
        LIRKind lirKind = getGen().getLIRKindTool().getIntegerKind(toBits);
        return emitUnaryAssign(getSignExtendOp(toBits), lirKind, x);
    }

    @Override
    public Variable emitSub(LIRKind lirKind, Value x, Value y, boolean setFlags) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitSub: %s - %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.SUB, lirKind, x, y);
    }

    @Override
    public Value emitUDiv(Value x, Value y, LIRFrameState frameState) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitUMulHigh(Value x, Value y) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitURem(Value x, Value y, LIRFrameState frameState) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitURem: %s %% %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.MOD, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitUShr(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitUShr: %s >>> %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.BITWISE_RIGHT_SHIFT, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitXor(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitXor: %s ^ %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.BITWISE_XOR, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitXorFP(Value a, Value b) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitZeroExtend(Value value, int fromBits, int toBits) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitZeroExtend: %s (from %d to %d)", value, fromBits, toBits);
        OCLLIRKindTool kindTool = getGen().getLIRKindTool();
        OCLKind kind = (OCLKind) value.getPlatformKind();
        LIRKind toKind;
        if (kind.isInteger()) {
            toKind = kindTool.getUnsignedIntegerKind(toBits);
        } else if (kind.isFloating()) {
            toKind = kindTool.getFloatingKind(toBits);
        } else {
            throw shouldNotReachHere();
        }

        // Apply a bitwise mask in order to avoid sign extension and instead zero extend the value.
        ConstantValue mask = new ConstantValue(toKind, JavaConstant.forIntegerKind(CodeUtil.javaKindFromBitSize(toBits, kind.isFloating()), (1L << fromBits) - 1));
        Variable result = emitBinaryAssign(OCLBinaryOp.BITWISE_AND, toKind, value, mask);
        return result;
    }

    @Override
    protected boolean isNumericInteger(PlatformKind platformKind) {
        guarantee(platformKind instanceof OCLKind, "invalid platform kind");
        return ((OCLKind) platformKind).isInteger();
    }

    public void emitLoad(AllocatableValue result, OCLAddressCast cast, MemoryAccess address) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        if (shouldEmitIntegerIndexes(cast)) {
            getGen().append(new LoadStmt(result, cast, address, address.getIndex()));
        } else {
            getGen().append(new LoadStmt(result, cast, address));
        }
    }

    private boolean shouldEmitIntegerIndexes(OCLAddressCast cast) {
        return cast.getMemorySpace().name() == OCLAssemblerConstants.LOCAL_MEM_MODIFIER || cast.getMemorySpace().name() == OCLAssemblerConstants.PRIVATE_MEM_MODIFIER;
    }

    public void emitVectorLoad(AllocatableValue result, OCLBinaryIntrinsic op, Value index, OCLAddressCast cast, MemoryAccess address) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitVectorLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        getGen().append(new VectorLoadStmt(result, op, index, cast, address));
    }

    @Override
    public Variable emitBitCount(Value input) {
        unimplemented();
        return null;
    }

    @Override
    public Variable emitBitScanForward(Value input) {
        unimplemented();
        return null;
    }

    @Override
    public Variable emitBitScanReverse(Value input) {
        unimplemented();
        return null;
    }

    @Override
    public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state, MemoryOrderMode memoryOrder, MemoryExtendKind extendKind) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitLoad: %s <- %s\nstate:%s", kind, address, state);
        final Variable result = getGen().newVariable(kind);

        guarantee(kind.getPlatformKind() instanceof OCLKind, "invalid LIRKind: %s", kind);
        OCLKind oclKind = (OCLKind) kind.getPlatformKind();
        OCLMemoryBase base = ((MemoryAccess) address).getBase();

        if (oclKind.isVector()) {
            OCLBinaryIntrinsic intrinsic = VectorUtil.resolveLoadIntrinsic(oclKind);
            OCLAddressCast cast = new OCLAddressCast(base, LIRKind.value(oclKind.getElementKind()));
            emitVectorLoad(result, intrinsic, getOffsetValue(oclKind, (MemoryAccess) address), cast, (MemoryAccess) address);
        } else {
            OCLAddressCast cast = new OCLAddressCast(base, kind);
            emitLoad(result, cast, (MemoryAccess) address);
        }

        return result;
    }

    @Override
    public void emitStore(ValueKind<?> kind, Value address, Value input, LIRFrameState state, MemoryOrderMode memoryOrder) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitStore: kind=%s, address=%s, input=%s", kind, address, input);
        guarantee(kind.getPlatformKind() instanceof OCLKind, "invalid LIRKind: %s", kind);
        OCLKind oclKind = (OCLKind) kind.getPlatformKind();

        MemoryAccess memAccess = null;
        Value accumulator = null;
        if (address instanceof MemoryAccess) {
            memAccess = (MemoryAccess) address;
        } else {
            accumulator = address;
        }

        if (oclKind.isVector()) {
            OCLTernaryIntrinsic intrinsic = VectorUtil.resolveStoreIntrinsic(oclKind);
            assert memAccess != null;
            OCLAddressCast cast = new OCLAddressCast(memAccess.getBase(), LIRKind.value(oclKind.getElementKind()));
            getGen().append(new VectorStoreStmt(intrinsic, getOffsetValue(oclKind, memAccess), cast, memAccess, input));
        } else {

            /*
             * Handling atomic operations introduced during lowering.
             */
            if (oclKind == OCLKind.ATOMIC_ADD_INT || oclKind == OCLKind.ATOMIC_ADD_LONG) {
                if (memAccess != null) {
                    OCLAddressCast cast = new OCLAddressCast(memAccess.getBase(), LIRKind.value(oclKind));
                    getGen().append(new StoreAtomicAddStmt(cast, memAccess, input));
                } else {
                    getGen().append(new StoreAtomicAddStmt(accumulator, input));
                }
            } else if (oclKind == OCLKind.ATOMIC_SUB_INT) {
                if (memAccess != null) {
                    OCLAddressCast cast = new OCLAddressCast(memAccess.getBase(), LIRKind.value(oclKind));
                    getGen().append(new StoreAtomicSubStmt(cast, memAccess, input));
                } else {
                    getGen().append(new StoreAtomicSubStmt(accumulator, input));
                }
            } else if (oclKind == OCLKind.ATOMIC_MUL_INT) {
                if (memAccess != null) {
                    OCLAddressCast cast = new OCLAddressCast(memAccess.getBase(), LIRKind.value(oclKind));
                    getGen().append(new StoreAtomicMulStmt(cast, memAccess, input));
                } else {
                    getGen().append(new StoreAtomicMulStmt(accumulator, input));
                }
            } else if (oclKind == OCLKind.ATOMIC_ADD_FLOAT) {
                if (memAccess != null) {
                    OCLAddressCast cast = new OCLAddressCast(memAccess.getBase(), LIRKind.value(oclKind));
                    getGen().append(new StoreAtomicAddFloatStmt(cast, memAccess, input));
                } else {
                    getGen().append(new StoreAtomicAddFloatStmt(accumulator, input));
                }
            } else {
                if (memAccess != null) {
                    OCLAddressCast cast = new OCLAddressCast(memAccess.getBase(), LIRKind.value(oclKind));
                    if (memAccess.getIndex() == null) {
                        getGen().append(new StoreStmt(cast, memAccess, input));
                    } else {
                        getGen().append(new StoreStmt(cast, memAccess, input, memAccess.getIndex()));
                    }
                } else {
                    getGen().append(new StoreAtomicAddStmt(accumulator, input));
                }
            }
        }
    }

    @Override
    public Value emitMathAbs(Value input) {
        OCLBuiltinTool builtinTool = getGen().getOCLBuiltinTool();
        OCLKind oclKind = (OCLKind) input.getPlatformKind();
        Variable result = getGen().newVariable(input.getValueKind());
        if (oclKind.isFloating()) {
            getGen().append(new AssignStmt(result, builtinTool.genFloatAbs(input)));
        } else {
            shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitMathSqrt(Value input) {
        OCLBuiltinTool builtinTool = getGen().getOCLBuiltinTool();
        OCLKind oclKind = (OCLKind) input.getPlatformKind();
        Variable result = getGen().newVariable(input.getValueKind());
        if (oclKind.isFloating()) {
            getGen().append(new AssignStmt(result, builtinTool.genFloatSqrt(input)));
        } else {
            shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitMathSignum(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitMathSignum: input=%s", input);
        OCLBuiltinTool builtinTool = getGen().getOCLBuiltinTool();
        Variable result = getGen().newVariable(input.getValueKind());
        getGen().append(new AssignStmt(result, builtinTool.genFloatSign(input)));
        return result;
    }

    @Override
    public Value emitMathCopySign(Value magnitude, Value sign) {
        unimplemented();
        return null;
    }

    /**
     * It calculates and returns the offset for vstore/vload operations as a Value
     * object.
     *
     * @param oclKind
     *     the kind for getting the size of the element type in a vector
     * @param memoryAccess
     *     the object that holds the index of an element in a vector
     * @return {@link Value }
     */
    private Value getPrivateOffsetValue(OCLKind oclKind, MemoryAccess memoryAccess) {
        if (memoryAccess == null) {
            return null;
        }
        if (memoryAccess.getIndex() instanceof ConstantValue) {
            ConstantValue constantValue = (ConstantValue) memoryAccess.getIndex();
            int parsedIntegerIndex = Integer.parseInt(constantValue.getConstant().toValueString());
            int index = parsedIntegerIndex / oclKind.getVectorLength();
            return new ConstantValue(LIRKind.value(OCLKind.INT), JavaConstant.forInt(index));
        }
        int index = Integer.parseInt(OCLAssembler.getAbsoluteIndexFromValue(memoryAccess.getIndex())) / oclKind.getVectorLength();
        return new ConstantValue(LIRKind.value(OCLKind.INT), JavaConstant.forInt(index));
    }

    private Value getOffsetValue(OCLKind oclKind, MemoryAccess memoryAccess) {
        if (memoryAccess.getBase().getMemorySpace() == OCLMemorySpace.GLOBAL.getBase().getMemorySpace()) {
            return new ConstantValue(LIRKind.value(OCLKind.INT), PrimitiveConstant.INT_0);
        } else {
            return getPrivateOffsetValue(oclKind, memoryAccess);
        }
    }

    public Value emitFMAInstruction(Value op1, Value op2, Value op3) {
        LIRKind resultKind = LIRKind.combine(op1, op2, op3);
        Variable result = getGen().newVariable(resultKind);
        OCLAssembler.OCLTernaryOp operation = OCLTernaryIntrinsic.FMA;
        getGen().append(new OCLLIRStmt.AssignStmt(result, new OCLTernary.Expr(operation, resultKind, op1, op2, op3)));
        return result;
    }

    public Value emitRSQRT(Value op) {
        LIRKind resultKind = LIRKind.value(op.getPlatformKind());
        Variable result = getGen().newVariable(resultKind);
        getGen().append(new OCLLIRStmt.AssignStmt(result, new OCLUnary.Intrinsic(RSQRT, LIRKind.value(op.getPlatformKind()), op)));
        return result;
    }

}
