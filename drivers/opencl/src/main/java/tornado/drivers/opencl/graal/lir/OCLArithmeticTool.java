/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGenerator;
import jdk.vm.ci.meta.*;
import tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import tornado.drivers.opencl.graal.OCLLIRKindTool;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLTernaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import tornado.drivers.opencl.graal.compiler.OCLLIRGenerator;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.LoadStmt;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.StoreStmt;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.VectorLoadStmt;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.VectorStoreStmt;
import tornado.drivers.opencl.graal.lir.OCLUnary.MemoryAccess;
import tornado.drivers.opencl.graal.lir.OCLUnary.OCLAddressCast;
import tornado.drivers.opencl.graal.nodes.vector.VectorUtil;

import static tornado.common.exceptions.TornadoInternalError.*;
import static tornado.graal.compiler.TornadoCodeGenerator.trace;
import static tornado.graal.compiler.TornadoCodeGenerator.trace;
import static tornado.graal.compiler.TornadoCodeGenerator.trace;
import static tornado.graal.compiler.TornadoCodeGenerator.trace;

public class OCLArithmeticTool extends ArithmeticLIRGenerator {

    public OCLLIRGenerator getGen() {
        return (OCLLIRGenerator) getLIRGen();
    }

    public OCLLIROp genBinaryExpr(OCLBinaryOp op, LIRKind lirKind, Value x, Value y) {
        return new OCLBinary.Expr(op, lirKind, x, y);
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
        trace("emitAnd: %s = %s & %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.BITWISE_AND, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitDiv(Value x, Value y, LIRFrameState frameState) {
        trace("emitDiv: %s / %s", x, y);
        return emitBinaryAssign(OCLBinaryOp.DIV, LIRKind.combine(x, y), x, y);
    }

    @Override
    public Value emitFloatConvert(FloatConvert floatConvert, Value input) {
        unimplemented();
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
        unimplemented();
        return null;
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
        unimplemented();
        return null;
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
        getGen().append(new LoadStmt(result, cast, address));
    }

    public void emitVectorLoad(AllocatableValue result, OCLBinaryIntrinsic op, Value index, OCLAddressCast cast, MemoryAccess address) {
        trace("emitVectorLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        getGen().append(new VectorLoadStmt(result, op, index, cast, address));
    }

    @Override
    public Variable emitLoad(LIRKind lirKind, Value address, LIRFrameState state) {
        trace("emitLoad: %s <- %s\nstate:%s", lirKind, address, state);
        final Variable result = getGen().newVariable(lirKind);

        // final MemoryAccess memAccess = (MemoryAccess) address;
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
    public void emitStore(ValueKind<?> lirKind, Value address, Value input,
            LIRFrameState state) {
        trace("emitStore: kind=%s, address=%s, input=%s", lirKind, address, input);
        guarantee(lirKind.getPlatformKind() instanceof OCLKind, "invalid LIRKind: %s", lirKind);
        OCLKind oclKind = (OCLKind) lirKind.getPlatformKind();
        final MemoryAccess memAccess = (MemoryAccess) address;

        if (oclKind.isVector()) {
            OCLTernaryIntrinsic intrinsic = VectorUtil.resolveStoreIntrinsic(oclKind);
            OCLAddressCast cast = new OCLAddressCast(memAccess.getBase(), LIRKind.value(oclKind.getElementKind()));
            getGen().append(new VectorStoreStmt(intrinsic, new ConstantValue(LIRKind.value(OCLKind.INT), PrimitiveConstant.INT_0), cast, memAccess, input));
        } else {
            OCLAddressCast cast = new OCLAddressCast(memAccess.getBase(), LIRKind.value(oclKind));
            getGen().append(new StoreStmt(cast, memAccess, input));
        }
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
}
