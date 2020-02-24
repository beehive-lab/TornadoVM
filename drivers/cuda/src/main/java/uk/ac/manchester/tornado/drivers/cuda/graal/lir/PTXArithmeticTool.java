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
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXBinaryOp;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXLIRGenerator;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.*;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

public class PTXArithmeticTool extends ArithmeticLIRGenerator {
    @Override
    protected boolean isNumericInteger(PlatformKind kind) {
        guarantee(kind instanceof PTXKind, "invalid platform kind");
        return ((PTXKind) kind).isInteger();
    }

    @Override
    protected Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        trace("emitAdd: %s + %s", a, b);
        return emitBinaryAssign(PTXBinaryOp.ADD, resultKind, a, b);
    }

    @Override
    protected Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitNegate(Value input) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitMul(Value a, Value b, boolean setFlags) {
        unimplemented();
        return null;
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
        unimplemented();
        return null;
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
        unimplemented();
        return null;
    }

    @Override
    public Value emitAnd(Value a, Value b) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitOr(Value a, Value b) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitXor(Value a, Value b) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitShl(Value a, Value b) {
        trace("emitShl: %s << %s", a, b);
        return emitBinaryAssign(PTXBinaryOp.BITWISE_LEFT_SHIFT, LIRKind.combine(a, b), a, b);
    }

    @Override
    public Value emitShr(Value a, Value b) {
        unimplemented();
        return null;
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
        unimplemented();
        return null;
    }

    @Override
    public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitZeroExtend(Value inputVal, int fromBits, int toBits) {
        trace("emitZeroExtend: %s (from %d to %d)", inputVal, fromBits, toBits);
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

    private PTXLIRGenerator getGen() {
        return (PTXLIRGenerator) getLIRGen();
    }

    @Override
    public Value emitMathAbs(Value input) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitMathSqrt(Value input) {
        unimplemented();
        return null;
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
        getGen().append(new PTXLIRStmt.LoadStmt((PTXUnary.MemoryAccess) address, dest));
        return dest;
    }

    @Override
    public void emitStore(ValueKind<?> kind, Value address, Value input, LIRFrameState state) {
        //TODO: This is probably not great
        getGen().append(new PTXLIRStmt.StoreStmt((PTXUnary.MemoryAccess) address, input));
    }

    public Variable emitBinaryAssign(PTXBinaryOp op, LIRKind lirKind, Value x, Value y) {
        final Variable result = getGen().newVariable(lirKind);
        getGen().append(new PTXLIRStmt.AssignStmt(result, genBinaryExpr(op, lirKind, x, y)));
        return result;
    }

    public PTXLIROp genBinaryExpr(PTXBinaryOp op, LIRKind lirKind, Value x, Value y) {
        return new PTXBinary.Expr(op, lirKind, x, y);
    }
}
