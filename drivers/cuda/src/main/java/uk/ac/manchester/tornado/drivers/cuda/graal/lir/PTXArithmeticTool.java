package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGenerator;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXLIRKindTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXBinaryOp;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXTernaryOp;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXLIRGenerator;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.*;

public class PTXArithmeticTool extends ArithmeticLIRGenerator {
    @Override
    protected boolean isNumericInteger(PlatformKind kind) {
        guarantee(kind instanceof PTXKind, "invalid platform kind");
        return ((PTXKind) kind).isInteger();
    }

    @Override
    protected Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        return emitBinaryAssign(PTXBinaryOp.ADD, resultKind, a, b);
    }

    @Override
    protected Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        return emitBinaryAssign(PTXBinaryOp.SUB, resultKind, a, b);
    }

    @Override
    public Value emitNegate(Value input) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitMul(Value a, Value b, boolean setFlags) {
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
        LIRKind resultKind = LIRKind.combine(a, b);
        PTXBinaryOp op = resultKind.getPlatformKind() == PTXKind.F32 ? PTXBinaryOp.DIV_APPROX : PTXBinaryOp.DIV;
        return emitBinaryAssign(op, resultKind, a, b);
    }

    @Override
    public Value emitRem(Value a, Value b, LIRFrameState state) {
        unimplemented();
        return null;
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
        return emitUnaryAssign(PTXAssembler.PTXUnaryOp.NOT, LIRKind.value(input.getPlatformKind()), input);
    }

    @Override
    public Value emitAnd(Value a, Value b) {
        return emitBinaryAssign(PTXBinaryOp.BITWISE_AND, LIRKind.combine(a, b), a, b);
    }

    @Override
    public Value emitOr(Value a, Value b) {
        return emitBinaryAssign(PTXBinaryOp.BITWISE_OR, LIRKind.combine(a, b), a, b);
    }

    @Override
    public Value emitXor(Value a, Value b) {
        return emitBinaryAssign(PTXBinaryOp.BITWISE_XOR, LIRKind.combine(a, b), a, b);
    }

    @Override
    public Value emitShl(Value a, Value b) {
        return emitBinaryAssign(PTXBinaryOp.BITWISE_LEFT_SHIFT, LIRKind.combine(a, b), a, b);
    }

    @Override
    public Value emitShr(Value a, Value b) {
        return emitBinaryAssign(PTXBinaryOp.BITWISE_RIGHT_SHIFT, LIRKind.combine(a, b), a, b);
    }

    @Override
    public Value emitUShr(Value a, Value b) {
        unimplemented();
        return null;
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
        return emitZeroExtend(inputVal, fromBits, toBits);
    }

    @Override
    public Value emitZeroExtend(Value inputVal, int fromBits, int toBits) {
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
    public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state) {
        Variable dest = getGen().newVariable(kind);
        getGen().append(new PTXLIRStmt.LoadStmt(
                (PTXUnary.MemoryAccess) address,
                dest,
                PTXAssembler.PTXNullaryOp.LD
        ));
        return dest;
    }

    @Override
    public void emitStore(ValueKind<?> kind, Value address, Value input, LIRFrameState state) {
        PTXUnary.MemoryAccess access = (PTXUnary.MemoryAccess) address;
        getGen().append(new PTXLIRStmt.StoreStmt(access, input));

        // Store back to register if it was loaded to a register first
        Variable valueHolder = access.assignedTo();
        if (valueHolder != null) {
            getGen().append(new PTXLIRStmt.AssignStmt(valueHolder, input));
        }
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
        LIRKind resultKind = LIRKind.combine(op1, op2);
        Variable result = getGen().newVariable(resultKind);
        PTXTernaryOp op = ((PTXKind)resultKind.getPlatformKind()).isFloating() ? PTXTernaryOp.MAD : PTXTernaryOp.MAD_LO;
        getGen().append(new PTXLIRStmt.AssignStmt(result, new PTXTernary.Expr(op, resultKind, op1, op2, op3)));
        return result;
    }
}
