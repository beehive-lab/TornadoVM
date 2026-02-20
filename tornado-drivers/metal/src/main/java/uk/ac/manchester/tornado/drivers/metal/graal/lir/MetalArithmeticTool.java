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
package uk.ac.manchester.tornado.drivers.metal.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.RSQRT;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.memory.MemoryExtendKind;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGenerator;

import uk.ac.manchester.tornado.drivers.common.code.CodeUtil;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalArchitecture.MetalMemoryBase;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalLIRKindTool;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryOp;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalTernaryIntrinsic;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryOp;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalLIRGenerator;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.LoadStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.StoreAtomicAddFloatStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.StoreAtomicAddStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.StoreAtomicMulStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.StoreAtomicSubStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.StoreStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.VectorLoadStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.VectorStoreStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalUnary.MemoryAccess;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalUnary.MetalAddressCast;
import uk.ac.manchester.tornado.drivers.metal.graal.meta.MetalMemorySpace;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.vector.VectorUtil;

public class MetalArithmeticTool extends ArithmeticLIRGenerator {

    public MetalLIRGenerator getGen() {
        return (MetalLIRGenerator) getLIRGen();
    }

    public MetalLIROp genBinaryExpr(MetalBinaryOp op, LIRKind lirKind, Value x, Value y) {
        return new MetalBinary.Expr(op, lirKind, x, y);
    }

    public MetalLIROp genTestBinaryExpr(MetalBinaryOp op, LIRKind lirKind, Value x, Value y) {
        return new MetalBinary.TestZeroExpression(op, lirKind, x, y);
    }

    public MetalLIROp genTestNegateBinaryExpr(MetalBinaryOp op, LIRKind lirKind, Value x, Value y) {
        return new MetalBinary.TestNegateZeroExpression(op, lirKind, x, y);
    }

    public MetalLIROp genBinaryIntrinsic(MetalBinaryIntrinsic op, LIRKind lirKind, Value x, Value y) {
        return new MetalBinary.Intrinsic(op, lirKind, x, y);
    }

    public Variable emitBinaryAssign(MetalBinaryOp op, LIRKind lirKind, Value x, Value y) {
        final Variable result = getGen().newVariable(lirKind);
        getGen().append(new AssignStmt(result, genBinaryExpr(op, lirKind, x, y)));
        return result;
    }

    public Variable emitBinaryAssign(MetalBinaryIntrinsic op, LIRKind lirKind, Value x, Value y) {
        final Variable result = getGen().newVariable(lirKind);
        getGen().append(new AssignStmt(result, genBinaryIntrinsic(op, lirKind, x, y)));
        return result;
    }

    public MetalLIROp genUnaryExpr(MetalUnaryOp op, LIRKind lirKind, Value value) {
        return new MetalUnary.Expr(op, lirKind, value);
    }

    public MetalLIROp genUnaryExpr(MetalUnaryIntrinsic op, LIRKind lirKind, Value value) {
        return new MetalUnary.Intrinsic(op, lirKind, value);
    }

    public Variable emitUnaryAssign(MetalUnaryOp op, LIRKind lirKind, Value value) {
        final Variable result = getGen().newVariable(lirKind);
        getGen().append(new AssignStmt(result, genUnaryExpr(op, lirKind, value)));
        return result;
    }

    public Variable emitUnaryAssign(MetalUnaryIntrinsic op, LIRKind lirKind, Value value) {
        final Variable result = getGen().newVariable(lirKind);
        getGen().append(new AssignStmt(result, genUnaryExpr(op, lirKind, value)));
        return result;
    }

    @Override
    public Variable emitAdd(LIRKind lirKind, Value x, Value y, boolean setFlags) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitAdd: %s + %s", x, y);
        return emitBinaryAssign(MetalBinaryOp.ADD, lirKind, x, y);
    }

    @Override
    public Value emitAnd(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitAnd: %s & %s", x, y);
        return emitBinaryAssign(MetalBinaryOp.BITWISE_AND, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitDiv(Value x, Value y, LIRFrameState frameState) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitDiv: %s / %s", x, y);
        return emitBinaryAssign(MetalBinaryOp.DIV, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitFloatConvert(FloatConvert floatConvert, Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitFloatConvert: (%s) %s", floatConvert, input);
        switch (floatConvert) {
            case I2D:
                return emitUnaryAssign(MetalUnaryOp.CAST_TO_DOUBLE, LIRKind.value(MetalKind.DOUBLE), input);
            default:
                unimplemented("float convert %s", floatConvert);
        }
        return null;

    }

    @Override
    public Value emitMul(Value x, Value y, boolean setFlags) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitMul: %s * %s", x, y);
        return emitBinaryAssign(MetalBinaryOp.MUL, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitMulHigh(Value x, Value y) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitNegate(Value x, boolean setFlags) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitNegate: - %s", x);
        return emitUnaryAssign(MetalUnaryOp.NEGATE, LIRKind.combine(x), x);
    }

    @Override
    public Value emitNot(Value x) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitNot: ~ %s", x);
        return emitUnaryAssign(MetalUnaryOp.BITWISE_NOT, LIRKind.combine(x), x);
    }

    @Override
    public Value emitOr(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitOr: %s | %s", x, y);
        return emitBinaryAssign(MetalBinaryOp.BITWISE_OR, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitReinterpret(LIRKind lirKind, Value x) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitRem(Value x, Value y, LIRFrameState frameState) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitRem: %s %% %s", x, y);
        return emitBinaryAssign(MetalBinaryOp.MOD, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitShl(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitShl: %s << %s", x, y);
        return emitBinaryAssign(MetalBinaryOp.BITWISE_LEFT_SHIFT, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitShr(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitShr: %s >> %s", x, y);
        return emitBinaryAssign(MetalBinaryOp.BITWISE_RIGHT_SHIFT, LIRKind.combine(x, y), x, y);
    }

    private MetalUnaryOp getSignExtendOp(int toBits) {
        switch (toBits) {
            case 8:
                return MetalUnaryOp.CAST_TO_BYTE;
            case 16:
                return MetalUnaryOp.CAST_TO_SHORT;
            case 32:
                return MetalUnaryOp.CAST_TO_INT;
            case 64:
                return MetalUnaryOp.CAST_TO_LONG;
            default:
                unimplemented();
        }
        return null;
    }

    @Override
    public Value emitNarrow(Value x, int toBits) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitNarrow: %s, %d", x, toBits);
        LIRKind lirKind = getGen().getLIRKindTool().getIntegerKind(toBits);
        return emitUnaryAssign(getSignExtendOp(toBits), lirKind, x);
    }

    @Override
    public Value emitSignExtend(Value x, int fromBits, int toBits) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitSignExtend: %s, %d, %d", x, fromBits, toBits);
        LIRKind lirKind = getGen().getLIRKindTool().getIntegerKind(toBits);
        return emitUnaryAssign(getSignExtendOp(toBits), lirKind, x);
    }

    @Override
    public Variable emitSub(LIRKind lirKind, Value x, Value y, boolean setFlags) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitSub: %s - %s", x, y);
        return emitBinaryAssign(MetalBinaryOp.SUB, lirKind, x, y);
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
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitURem: %s %% %s", x, y);
        return emitBinaryAssign(MetalBinaryOp.MOD, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitUShr(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitUShr: %s >>> %s", x, y);
        return emitBinaryAssign(MetalBinaryOp.BITWISE_RIGHT_SHIFT, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitXor(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitXor: %s ^ %s", x, y);
        return emitBinaryAssign(MetalBinaryOp.BITWISE_XOR, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitXorFP(Value a, Value b) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitZeroExtend(Value value, int fromBits, int toBits) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitZeroExtend: %s (from %d to %d)", value, fromBits, toBits);
        MetalLIRKindTool kindTool = getGen().getLIRKindTool();
        MetalKind kind = (MetalKind) value.getPlatformKind();
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
        Variable result = emitBinaryAssign(MetalBinaryOp.BITWISE_AND, toKind, value, mask);
        return result;
    }

    @Override
    protected boolean isNumericInteger(PlatformKind platformKind) {
        guarantee(platformKind instanceof MetalKind, "invalid platform kind");
        return ((MetalKind) platformKind).isInteger();
    }

    public void emitLoad(AllocatableValue result, MetalAddressCast cast, MemoryAccess address) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        if (shouldEmitIntegerIndexes(cast)) {
            getGen().append(new LoadStmt(result, cast, address, address.getIndex()));
        } else {
            getGen().append(new LoadStmt(result, cast, address));
        }
    }

    private boolean shouldEmitIntegerIndexes(MetalAddressCast cast) {
        return cast.getMemorySpace().name() == MetalAssemblerConstants.LOCAL_MEM_MODIFIER || cast.getMemorySpace().name() == MetalAssemblerConstants.PRIVATE_MEM_MODIFIER;
    }

    public void emitVectorLoad(AllocatableValue result, MetalBinaryIntrinsic op, Value index, MetalAddressCast cast, MemoryAccess address) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitVectorLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
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
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitLoad: %s <- %s\nstate:%s", kind, address, state);
        final Variable result = getGen().newVariable(kind);

        guarantee(kind.getPlatformKind() instanceof MetalKind, "invalid LIRKind: %s", kind);
        MetalKind oclKind = (MetalKind) kind.getPlatformKind();
        MetalMemoryBase base = ((MemoryAccess) address).getBase();

        if (oclKind.isVector()) {
            MetalBinaryIntrinsic intrinsic = VectorUtil.resolveLoadIntrinsic(oclKind);
            MetalAddressCast cast = new MetalAddressCast(base, LIRKind.value(oclKind.getElementKind()));
            emitVectorLoad(result, intrinsic, getOffsetValue(oclKind, (MemoryAccess) address), cast, (MemoryAccess) address);
        } else {
            MetalAddressCast cast = new MetalAddressCast(base, kind);
            emitLoad(result, cast, (MemoryAccess) address);
        }

        return result;
    }

    @Override
    public void emitStore(ValueKind<?> kind, Value address, Value input, LIRFrameState state, MemoryOrderMode memoryOrder) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitStore: kind=%s, address=%s, input=%s", kind, address, input);
        guarantee(kind.getPlatformKind() instanceof MetalKind, "invalid LIRKind: %s", kind);
        MetalKind oclKind = (MetalKind) kind.getPlatformKind();

        MemoryAccess memAccess = null;
        Value accumulator = null;
        if (address instanceof MemoryAccess) {
            memAccess = (MemoryAccess) address;
        } else {
            accumulator = address;
        }

        if (oclKind.isVector()) {
            MetalTernaryIntrinsic intrinsic = VectorUtil.resolveStoreIntrinsic(oclKind);
            assert memAccess != null;
            MetalAddressCast cast = new MetalAddressCast(memAccess.getBase(), LIRKind.value(oclKind.getElementKind()));
            getGen().append(new VectorStoreStmt(intrinsic, getOffsetValue(oclKind, memAccess), cast, memAccess, input));
        } else {

            /*
             * Handling atomic operations introduced during lowering.
             */
            if (oclKind == MetalKind.ATOMIC_ADD_INT || oclKind == MetalKind.ATOMIC_ADD_LONG) {
                if (memAccess != null) {
                    MetalAddressCast cast = new MetalAddressCast(memAccess.getBase(), LIRKind.value(oclKind));
                    getGen().append(new StoreAtomicAddStmt(cast, memAccess, input));
                } else {
                    getGen().append(new StoreAtomicAddStmt(accumulator, input));
                }
            } else if (oclKind == MetalKind.ATOMIC_SUB_INT) {
                if (memAccess != null) {
                    MetalAddressCast cast = new MetalAddressCast(memAccess.getBase(), LIRKind.value(oclKind));
                    getGen().append(new StoreAtomicSubStmt(cast, memAccess, input));
                } else {
                    getGen().append(new StoreAtomicSubStmt(accumulator, input));
                }
            } else if (oclKind == MetalKind.ATOMIC_MUL_INT) {
                if (memAccess != null) {
                    MetalAddressCast cast = new MetalAddressCast(memAccess.getBase(), LIRKind.value(oclKind));
                    getGen().append(new StoreAtomicMulStmt(cast, memAccess, input));
                } else {
                    getGen().append(new StoreAtomicMulStmt(accumulator, input));
                }
            } else if (oclKind == MetalKind.ATOMIC_ADD_FLOAT) {
                if (memAccess != null) {
                    MetalAddressCast cast = new MetalAddressCast(memAccess.getBase(), LIRKind.value(oclKind));
                    getGen().append(new StoreAtomicAddFloatStmt(cast, memAccess, input));
                } else {
                    getGen().append(new StoreAtomicAddFloatStmt(accumulator, input));
                }
            } else {
                if (memAccess != null) {
                    MetalAddressCast cast = new MetalAddressCast(memAccess.getBase(), LIRKind.value(oclKind));
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
        MetalBuiltinTool builtinTool = getGen().getMetalBuiltinTool();
        MetalKind oclKind = (MetalKind) input.getPlatformKind();
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
        MetalBuiltinTool builtinTool = getGen().getMetalBuiltinTool();
        MetalKind oclKind = (MetalKind) input.getPlatformKind();
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
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "emitMathSignum: input=%s", input);
        MetalBuiltinTool builtinTool = getGen().getMetalBuiltinTool();
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
    private Value getPrivateOffsetValue(MetalKind oclKind, MemoryAccess memoryAccess) {
        if (memoryAccess == null) {
            return null;
        }
        if (memoryAccess.getIndex() instanceof ConstantValue) {
            ConstantValue constantValue = (ConstantValue) memoryAccess.getIndex();
            int parsedIntegerIndex = Integer.parseInt(constantValue.getConstant().toValueString());
            int index = parsedIntegerIndex / oclKind.getVectorLength();
            return new ConstantValue(LIRKind.value(MetalKind.INT), JavaConstant.forInt(index));
        }
        int index = Integer.parseInt(MetalAssembler.getAbsoluteIndexFromValue(memoryAccess.getIndex())) / oclKind.getVectorLength();
        return new ConstantValue(LIRKind.value(MetalKind.INT), JavaConstant.forInt(index));
    }

    private Value getOffsetValue(MetalKind oclKind, MemoryAccess memoryAccess) {
        if (memoryAccess.getBase().getMemorySpace() == MetalMemorySpace.GLOBAL.getBase().getMemorySpace()) {
            return new ConstantValue(LIRKind.value(MetalKind.INT), PrimitiveConstant.INT_0);
        } else {
            return getPrivateOffsetValue(oclKind, memoryAccess);
        }
    }

    public Value emitFMAInstruction(Value op1, Value op2, Value op3) {
        LIRKind resultKind = LIRKind.combine(op1, op2, op3);
        Variable result = getGen().newVariable(resultKind);
        MetalAssembler.MetalTernaryOp operation = MetalTernaryIntrinsic.FMA;
        getGen().append(new MetalLIRStmt.AssignStmt(result, new MetalTernary.Expr(operation, resultKind, op1, op2, op3)));
        return result;
    }

    public Value emitRSQRT(Value op) {
        LIRKind resultKind = LIRKind.value(op.getPlatformKind());
        Variable result = getGen().newVariable(resultKind);
        getGen().append(new MetalLIRStmt.AssignStmt(result, new MetalUnary.Intrinsic(RSQRT, LIRKind.value(op.getPlatformKind()), op)));
        return result;
    }

}
