/*
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGenerator;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
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
        trace("emitAdd: %s + %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.ADD, lirKind, x, y);
    }

    @Override
    public Value emitAnd(Value x, Value y) {
        trace("emitAnd: %s & %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.BITWISE_AND, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitDiv(Value x, Value y, LIRFrameState frameState) {
        trace("emitDiv: %s / %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.DIV, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitFloatConvert(FloatConvert floatConvert, Value input) {
        trace("emitFloatConvert: (%s) %s", floatConvert, input);
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
        trace("emitMul: %s * %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.MUL, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitMulHigh(Value x, Value y) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitNegate(Value x) {
        trace("emitNegate:  - %s", x);
        return emitUnaryAssign(OCLUnaryOp.NEGATE, LIRKind.combine(x), x);
    }

    @Override
    public Value emitNot(Value x) {
        // TODO check that this is LOGICAL_NOT and not BITWISE_NOT
        trace("emitNegate:  - %s", x);
        return emitUnaryAssign(OCLUnaryOp.LOGICAL_NOT, LIRKind.combine(x), x);
    }

    @Override
    public Value emitOr(Value x, Value y) {
        trace("emitOr: %s | %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.BITWISE_OR, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitReinterpret(LIRKind lirKind, Value x) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitRem(Value x, Value y, LIRFrameState frameState) {
        trace("emitRem: %s %% %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.MOD, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitShl(Value x, Value y) {
        trace("emitShl: %s << %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.BITWISE_LEFT_SHIFT, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitShr(Value x, Value y) {
        trace("emitShr: %s >> %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.BITWISE_RIGHT_SHIFT, LIRKind.combine(x, y), x, y);
    }

    private OCLUnaryOp getSignExtendOp(int toBits) {
        switch (toBits) {
            case 8:
                return OCLUnaryOp.CAST_TO_BYTE;
            case 16:
                return OCLUnaryOp.CAST_TO_SHORT;
            case 32:
                return OCLUnaryOp.CAST_TO_INT;
            case 64:
                return OCLUnaryOp.CAST_TO_LONG;
            default:
                unimplemented();
        }
        return null;
    }

    @Override
    public Value emitNarrow(Value x, int toBits) {
        trace("emitNarrow: %s, %d", x, toBits);
        LIRKind lirKind = getGen().getLIRKindTool().getIntegerKind(toBits);
        return emitUnaryAssign(getSignExtendOp(toBits), lirKind, x);
    }

    @Override
    public Value emitSignExtend(Value x, int fromBits, int toBits) {
        trace("emitSignExtend: %s, %d, %d", x, fromBits, toBits);
        LIRKind lirKind = getGen().getLIRKindTool().getIntegerKind(toBits);
        return emitUnaryAssign(getSignExtendOp(toBits), lirKind, x);
    }

    @Override
    public Variable emitSub(LIRKind lirKind, Value x, Value y, boolean setFlags) {
        trace("emitSub: %s - %s", x, y);
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
        trace("emitURem: %s %% %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.MOD, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitUShr(Value x, Value y) {
        trace("emitUShr: %s >>> %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.BITWISE_RIGHT_SHIFT, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitXor(Value x, Value y) {
        trace("emitXor: %s ^ %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.BITWISE_XOR, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitZeroExtend(Value value, int fromBits, int toBits) {
        trace("emitZeroExtend: %s (from %d to %d)", value, fromBits, toBits);
        OCLLIRKindTool kindTool = getGen().getLIRKindTool();
        OCLKind kind = (OCLKind) value.getPlatformKind();
        LIRKind toKind;
        if (kind.isInteger()) {
            toKind = kindTool.getIntegerKind(toBits);
        } else if (kind.isFloating()) {
            toKind = kindTool.getFloatingKind(toBits);
        } else {
            throw shouldNotReachHere();
        }

        Variable result = getGen().newVariable(toKind);

        getGen().emitMove(result, value);
        return result;
    }

    @Override
    protected boolean isNumericInteger(PlatformKind platformKind) {
        guarantee(platformKind instanceof OCLKind, "invalid platform kind");
        return ((OCLKind) platformKind).isInteger();
    }

    public void emitLoad(AllocatableValue result, OCLAddressCast cast, MemoryAccess address) {
        trace("emitLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
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
        trace("emitVectorLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        getGen().append(new VectorLoadStmt(result, op, index, cast, address));
    }

    @Override
    public Variable emitLoad(LIRKind lirKind, Value address, LIRFrameState state) {
        trace("emitLoad: %s <- %s\nstate:%s", lirKind, address, state);
        final Variable result = getGen().newVariable(lirKind);

        guarantee(lirKind.getPlatformKind() instanceof OCLKind, "invalid LIRKind: %s", lirKind);
        OCLKind oclKind = (OCLKind) lirKind.getPlatformKind();
        OCLMemoryBase base = ((MemoryAccess) address).getBase();

        if (oclKind.isVector()) {
            OCLBinaryIntrinsic intrinsic = VectorUtil.resolveLoadIntrinsic(oclKind);
            OCLAddressCast cast = new OCLAddressCast(base, LIRKind.value(oclKind.getElementKind()));
            emitVectorLoad(result, intrinsic, new ConstantValue(LIRKind.value(OCLKind.INT), PrimitiveConstant.INT_0), cast, (MemoryAccess) address);
        } else {
            OCLAddressCast cast = new OCLAddressCast(base, lirKind);
            emitLoad(result, cast, (MemoryAccess) address);
        }

        return result;
    }

    @Override
    public Variable emitVolatileLoad(LIRKind kind, Value address, LIRFrameState state) {
        unimplemented();
        return null;
    }

    @Override
    public void emitStore(ValueKind<?> lirKind, Value address, Value input, LIRFrameState state) {
        trace("emitStore: kind=%s, address=%s, input=%s", lirKind, address, input);
        guarantee(lirKind.getPlatformKind() instanceof OCLKind, "invalid LIRKind: %s", lirKind);
        OCLKind oclKind = (OCLKind) lirKind.getPlatformKind();

        MemoryAccess memAccess = null;
        Value accumulator = null;
        if (address instanceof MemoryAccess) {
            memAccess = (MemoryAccess) address;
        } else {
            accumulator = address;
        }

        if (oclKind.isVector()) {
            OCLTernaryIntrinsic intrinsic = VectorUtil.resolveStoreIntrinsic(oclKind);
            OCLAddressCast cast = new OCLAddressCast(memAccess.getBase(), LIRKind.value(oclKind.getElementKind()));
            getGen().append(new VectorStoreStmt(intrinsic, new ConstantValue(LIRKind.value(OCLKind.INT), PrimitiveConstant.INT_0), cast, memAccess, input));
        } else {

            /**
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

                    // Store back to register if it was loaded to a register first
                    AllocatableValue valueHolder = memAccess.assignedTo();
                    if (valueHolder != null) {
                        getGen().append(new OCLLIRStmt.AssignStmt(valueHolder, input));
                    }
                } else {
                    getGen().append(new StoreAtomicAddStmt(accumulator, input));
                }
            }
        }
    }

    @Override
    public void emitVolatileStore(ValueKind<?> kind, Value address, Value input, LIRFrameState state) {
        unimplemented();
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

    public Value emitFMAInstruction(Value op1, Value op2, Value op3) {
        LIRKind resultKind = LIRKind.combine(op1, op2, op3);
        Variable result = getGen().newVariable(resultKind);
        OCLAssembler.OCLTernaryOp operation = OCLTernaryIntrinsic.FMA;
        getGen().append(new OCLLIRStmt.AssignStmt(result, new OCLTernary.Expr(operation, resultKind, op1, op2, op3)));
        return result;
    }

}
