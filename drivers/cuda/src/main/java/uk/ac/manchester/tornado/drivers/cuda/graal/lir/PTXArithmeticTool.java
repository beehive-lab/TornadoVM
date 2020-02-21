package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGenerator;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXLIRKindTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXLIRGenerator;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

public class PTXArithmeticTool extends ArithmeticLIRGenerator {
    @Override
    protected boolean isNumericInteger(PlatformKind kind) {
        unimplemented();
        return false;
    }

    @Override
    protected Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        unimplemented();
        return null;
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
        unimplemented();
        return null;
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
        unimplemented();
        return null;
    }

    @Override
    public void emitStore(ValueKind<?> kind, Value address, Value input, LIRFrameState state) {
        unimplemented();
    }
}
