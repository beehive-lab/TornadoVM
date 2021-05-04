package uk.ac.manchester.tornado.drivers.spirv.graal.compiler.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGenerator;

import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;

public class SPIRVArithmeticTool extends ArithmeticLIRGenerator {

    public SPIRVLIRGenerator getGen() {
        return (SPIRVLIRGenerator) getLIRGen();
    }

    // public Variable emitBinaryAssign(SPIRVBinaryOp op, LIRKind lirKind, Value x,
    // Value y) {
    // final Variable result = getGen().newVariable(lirKind);
    // getGen().append(new OCLLIRStmt.AssignStmt(result, genBinaryExpr(op, lirKind,
    // x, y)));
    // return result;
    // }

    @Override
    protected boolean isNumericInteger(PlatformKind kind) {
        return false;
    }

    @Override
    protected Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        SPIRVLogger.trace("[µInstructions] emitAdd: %s + %s", a, b);
        // return emitBinaryAssign(SPIRVBinaryOp.ADD, resultKind, a, b);
        return null;
    }

    @Override
    protected Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        return null;
    }

    @Override
    public Value emitNegate(Value input) {
        return null;
    }

    @Override
    public Value emitMul(Value a, Value b, boolean setFlags) {
        return null;
    }

    @Override
    public Value emitMulHigh(Value a, Value b) {
        return null;
    }

    @Override
    public Value emitUMulHigh(Value a, Value b) {
        return null;
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        return null;
    }

    @Override
    public Value emitRem(Value a, Value b, LIRFrameState state) {
        return null;
    }

    @Override
    public Value emitUDiv(Value a, Value b, LIRFrameState state) {
        return null;
    }

    @Override
    public Value emitURem(Value a, Value b, LIRFrameState state) {
        return null;
    }

    @Override
    public Value emitNot(Value input) {
        return null;
    }

    @Override
    public Value emitAnd(Value a, Value b) {
        return null;
    }

    @Override
    public Value emitOr(Value a, Value b) {
        return null;
    }

    @Override
    public Value emitXor(Value a, Value b) {
        return null;
    }

    @Override
    public Value emitShl(Value a, Value b) {
        return null;
    }

    @Override
    public Value emitShr(Value a, Value b) {
        return null;
    }

    @Override
    public Value emitUShr(Value a, Value b) {
        return null;
    }

    @Override
    public Value emitFloatConvert(FloatConvert op, Value inputVal) {
        return null;
    }

    @Override
    public Value emitReinterpret(LIRKind to, Value inputVal) {
        return null;
    }

    @Override
    public Value emitNarrow(Value inputVal, int bits) {
        return null;
    }

    @Override
    public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
        return null;
    }

    @Override
    public Value emitZeroExtend(Value inputVal, int fromBits, int toBits) {
        return null;
    }

    @Override
    public Value emitMathAbs(Value input) {
        return null;
    }

    @Override
    public Value emitMathSqrt(Value input) {
        return null;
    }

    @Override
    public Value emitBitCount(Value operand) {
        return null;
    }

    @Override
    public Value emitBitScanForward(Value operand) {
        return null;
    }

    @Override
    public Value emitBitScanReverse(Value operand) {
        return null;
    }

    @Override
    public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state) {
        return null;
    }

    @Override
    public Variable emitVolatileLoad(LIRKind kind, Value address, LIRFrameState state) {
        return null;
    }

    @Override
    public void emitStore(ValueKind<?> kind, Value address, Value input, LIRFrameState state) {

    }

    @Override
    public void emitVolatileStore(ValueKind<?> kind, Value address, Value input, LIRFrameState state) {

    }
}
