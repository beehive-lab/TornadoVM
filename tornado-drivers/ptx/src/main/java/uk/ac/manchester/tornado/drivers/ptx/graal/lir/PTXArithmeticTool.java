/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

package uk.ac.manchester.tornado.drivers.ptx.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXUnaryIntrinsic.RSQRT;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.memory.MemoryExtendKind;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGenerator;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import uk.ac.manchester.tornado.drivers.common.code.CodeUtil;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXLIRKindTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryOp;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXTernaryOp;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class PTXArithmeticTool extends ArithmeticLIRGenerator {
    @Override
    protected boolean isNumericInteger(PlatformKind kind) {
        guarantee(kind instanceof PTXKind, "invalid platform kind");
        return ((PTXKind) kind).isInteger();
    }

    @Override
    protected Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitAdd resultKind=%s a=%s b=%s setFlags=%b", resultKind, a, b, setFlags);
        return emitBinaryAssign(PTXBinaryOp.ADD, resultKind, a, b);
    }

    @Override
    protected Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitSub resultKind=%s a=%s b=%s setFlags=%b", resultKind, a, b, setFlags);
        return emitBinaryAssign(PTXBinaryOp.SUB, resultKind, a, b);
    }

    @Override
    public Value emitNegate(Value input, boolean setFlags) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitNegate input=%s", input);
        return emitUnaryAssign(PTXAssembler.PTXUnaryOp.NEGATE, LIRKind.value(input.getPlatformKind()), input);
    }

    @Override
    public Value emitMul(Value a, Value b, boolean setFlags) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitMul a=%s b=%s setFlags=%b", a, b, setFlags);
        LIRKind resultKind = LIRKind.combine(a, b);
        PTXBinaryOp op = ((PTXKind) resultKind.getPlatformKind()).isFloating() ? PTXBinaryOp.MUL : PTXBinaryOp.MUL_LO;
        return emitBinaryAssign(op, resultKind, a, b);
    }

    @Override
    public Value emitMulHigh(Value a, Value b) {
        return null;
    }

    @Override
    public Value emitUMulHigh(Value a, Value b) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitDiv a=%s b=%s", a, b);
        LIRKind resultKind = LIRKind.combine(a, b);
        PTXBinaryOp op = resultKind.getPlatformKind() == PTXKind.F32 ? PTXBinaryOp.DIV_FULL : PTXBinaryOp.DIV;
        return emitBinaryAssign(op, resultKind, a, b);
    }

    @Override
    public Value emitRem(Value a, Value b, LIRFrameState state) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitRem: %s %% %s", a, b);
        return emitBinaryAssign(PTXBinaryOp.REM, LIRKind.combine(a, b), a, b);
    }

    @Override
    public Value emitUDiv(Value a, Value b, LIRFrameState state) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitURem(Value a, Value b, LIRFrameState state) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitNot(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitNot input=%s", input);
        return emitUnaryAssign(PTXAssembler.PTXUnaryOp.NOT, LIRKind.value(input.getPlatformKind()), input);
    }

    @Override
    public Value emitAnd(Value a, Value b) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitAnd a=%s b=%s", a, b);
        return emitBinaryAssign(PTXBinaryOp.BITWISE_AND, LIRKind.combine(a, b), a, b);
    }

    @Override
    public Value emitOr(Value a, Value b) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitOr a=%s b=%s", a, b);
        return emitBinaryAssign(PTXBinaryOp.BITWISE_OR, LIRKind.combine(a, b), a, b);
    }

    @Override
    public Value emitXor(Value a, Value b) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitXor a=%s b=%s", a, b);
        return emitBinaryAssign(PTXBinaryOp.BITWISE_XOR, LIRKind.combine(a, b), a, b);
    }

    @Override
    public Value emitXorFP(Value a, Value b) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitShl(Value a, Value b) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitShl a=%s b=%s", a, b);
        return emitBinaryAssign(PTXBinaryOp.BITWISE_LEFT_SHIFT, LIRKind.combine(a, b), a, b);
    }

    @Override
    public Value emitShr(Value a, Value b) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitShr a=%s b=%s", a, b);
        return emitBinaryAssign(PTXBinaryOp.BITWISE_RIGHT_SHIFT, LIRKind.combine(a, b), a, b);
    }

    @Override
    public Value emitUShr(Value a, Value b) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitUShr a=%s b=%s", a, b);
        return emitShr(a, b);
    }

    @Override
    public Value emitFloatConvert(FloatConvert op, Value inputVal) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitReinterpret(LIRKind to, Value inputVal) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitNarrow(Value inputVal, int bits) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitNarrow inputVal=%s bits=%d", inputVal, bits);
        PTXLIRKindTool kindTool = getGen().getLIRKindTool();
        PTXKind kind = (PTXKind) inputVal.getPlatformKind();
        LIRKind toKind;
        if (kind.isInteger()) {
            toKind = kindTool.getIntegerKind(bits);
        } else if (kind.isFloating()) {
            toKind = kindTool.getFloatingKind(bits);
        } else {
            throw shouldNotReachHere();
        }

        Variable result = getGen().newVariable(toKind);

        getGen().emitMove(result, inputVal);
        return result;
    }

    @Override
    public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitSignExtend inputVal=%s fromBits=%d toBits=%d", inputVal, fromBits, toBits);
        PTXLIRKindTool kindTool = getGen().getLIRKindTool();
        PTXKind kind = (PTXKind) inputVal.getPlatformKind();
        LIRKind toKind;
        if (kind.isInteger()) {
            toKind = kindTool.getIntegerKind(toBits);
        } else if (kind.isFloating()) {
            toKind = kindTool.getFloatingKind(toBits);
        } else {
            throw shouldNotReachHere();
        }

        Variable result = getGen().newVariable(toKind);

        getGen().emitMove(result, inputVal);
        return result;

    }

    @Override
    public Value emitZeroExtend(Value inputVal, int fromBits, int toBits) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitZeroExtend inputVal=%s fromBits=%d toBits=%d", inputVal, fromBits, toBits);
        PTXLIRKindTool kindTool = getGen().getLIRKindTool();
        PTXKind kind = (PTXKind) inputVal.getPlatformKind();
        LIRKind toKind;
        if (kind.isInteger()) {
            toKind = kindTool.getUnsignedIntegerKind(toBits);
        } else if (kind.isFloating()) {
            toKind = kindTool.getFloatingKind(toBits);
        } else {
            throw shouldNotReachHere();
        }

        Variable signExtendedValue = getGen().newVariable(toKind);
        getGen().emitMove(signExtendedValue, inputVal);

        // Apply a bitwise mask in order to avoid sign extension and instead zero extend the value.
        ConstantValue mask = new ConstantValue(toKind, JavaConstant.forIntegerKind(CodeUtil.javaKindFromBitSize(toBits, kind.isFloating()), (1L << fromBits) - 1));
        Variable result = emitBinaryAssign(PTXBinaryOp.BITWISE_AND, toKind, signExtendedValue, mask);
        return result;
    }

    public PTXLIRGenerator getGen() {
        return (PTXLIRGenerator) getLIRGen();
    }

    @Override
    public Value emitMathAbs(Value input) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitMathSqrt(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitMathSqrt input=%s", input);
        PTXBuiltinTool builtinTool = getGen().getPtxBuiltinTool();
        PTXKind ptxKind = (PTXKind) input.getPlatformKind();
        Variable result = getGen().newVariable(input.getValueKind());
        if (ptxKind.isFloating()) {
            getGen().append(new PTXLIRStmt.AssignStmt(result, builtinTool.genFloatSqrt(input)));
        } else {
            shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitMathSignum(Value input) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitMathCopySign(Value magnitude, Value sign) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitMathCopySign magnitude=%s sign=%s", sign, magnitude);
        PTXBuiltinTool builtinTool = getGen().getPtxBuiltinTool();
        Variable result = getGen().newVariable(sign.getValueKind());

        /*
         * This method will reverse the two input parameters, as the PTX copySign
         * instruction follows the opposite order of parameters than the Java copySign
         * implementation.
         */
        getGen().append(new PTXLIRStmt.AssignStmt(result, builtinTool.genCopySign(sign, magnitude)));
        return result;
    }

    @Override
    public Value emitBitCount(Value operand) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitBitScanForward(Value operand) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitBitScanReverse(Value operand) {
        unimplemented();
        return null;
    }

    @Override
    public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state, MemoryOrderMode memoryOrder, MemoryExtendKind extendKind) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitLoad kind=%s address=%s", kind, address);
        guarantee(kind.getPlatformKind() instanceof PTXKind, "invalid LIRKind: %s", kind);
        PTXKind ptxKind = (PTXKind) kind.getPlatformKind();

        Variable dest = getGen().newVariable(kind);

        if (ptxKind.isVector()) {
            emitVectorLoad(dest, (PTXUnary.MemoryAccess) address);
        } else {
            getGen().append(new PTXLIRStmt.LoadStmt((PTXUnary.MemoryAccess) address, dest, PTXAssembler.PTXNullaryOp.LD));
        }
        return dest;
    }

    @Override
    public void emitStore(ValueKind<?> kind, Value address, Value input, LIRFrameState state, MemoryOrderMode memoryOrder) {
        assert address instanceof PTXUnary.MemoryAccess;
        assert kind.getPlatformKind() instanceof PTXKind;
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitStore: kind=%s, address=%s, input=%s", kind, address, input);
        PTXUnary.MemoryAccess access = (PTXUnary.MemoryAccess) address;
        PTXKind ptxKind = (PTXKind) input.getPlatformKind();
        if (ptxKind.isVector()) {
            assert input instanceof Variable;
            getGen().append(new PTXLIRStmt.VectorStoreStmt((Variable) input, access));
        } else {
            getGen().append(new PTXLIRStmt.StoreStmt(access, input));
        }
    }

    public void emitVectorLoad(Variable result, PTXUnary.MemoryAccess address) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitVectorLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        getGen().append(new PTXLIRStmt.VectorLoadStmt(result, address));
    }

    public Variable emitUnaryAssign(PTXAssembler.PTXUnaryOp op, LIRKind lirKind, Value x) {
        final Variable result = getGen().newVariable(lirKind);
        getGen().append(new PTXLIRStmt.AssignStmt(result, new PTXUnary.Expr(op, lirKind, x)));
        return result;
    }

    public Variable emitBinaryAssign(PTXBinaryOp op, LIRKind lirKind, Value x, Value y) {
        final Variable result = getGen().newVariable(lirKind);
        getGen().append(new PTXLIRStmt.AssignStmt(result, genBinaryExpr(op, lirKind, x, y)));
        return result;
    }

    public PTXLIROp genBinaryExpr(PTXBinaryOp op, LIRKind lirKind, Value x, Value y) {
        return new PTXBinary.Expr(op, lirKind, x, y);
    }

    public Value emitMultiplyAdd(Value op1, Value op2, Value op3) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitMultiplyAdd op1=%s op2=%s op3=%s", op1, op2, op3);
        LIRKind resultKind = LIRKind.combine(op1, op2, op3);
        Variable result = getGen().newVariable(resultKind);
        PTXTernaryOp op;
        if (TornadoOptions.FAST_MATH_OPTIMIZATIONS) {
            op = ((PTXKind) resultKind.getPlatformKind()).isFloating() ? PTXTernaryOp.MAD : PTXTernaryOp.MAD_LO;
        } else {
            op = PTXTernaryOp.FMA;
        }
        getGen().append(new PTXLIRStmt.AssignStmt(result, new PTXTernary.Expr(op, resultKind, op1, op2, op3)));
        return result;
    }

    public Value emitRSQRT(Value operand) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emit rsqrt op1=%s ", operand);
        LIRKind resultKind = LIRKind.value(operand.getPlatformKind());
        Variable result = getGen().newVariable(resultKind);
        getGen().append(new PTXLIRStmt.AssignStmt(result, new PTXUnary.Intrinsic(RSQRT, LIRKind.value(operand.getPlatformKind()), operand)));
        return result;
    }
}
