package uk.ac.manchester.tornado.drivers.spirv.graal.compiler.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGenerator;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler.SPIRVBinaryOp;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBinary;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIROp;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary.MemoryAccess;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary.SPIRVAddressCast;

public class SPIRVArithmeticTool extends ArithmeticLIRGenerator {

    public SPIRVLIRGenerator getGen() {
        return (SPIRVLIRGenerator) getLIRGen();
    }

    public SPIRVLIROp genBinaryExpr(SPIRVBinaryOp op, LIRKind lirKind, Value x, Value y) {
        return new SPIRVBinary.AddExpr(op, lirKind, x, y);
    }

    public Variable emitBinaryAssign(SPIRVBinaryOp op, LIRKind lirKind, Value x, Value y) {
        final Variable result = getGen().newVariable(lirKind);
        getGen().append(new SPIRVLIRStmt.AssignStmt(result, genBinaryExpr(op, lirKind, x, y)));
        return result;
    }

    @Override
    protected boolean isNumericInteger(PlatformKind platformKind) {
        if (!(platformKind instanceof SPIRVKind)) {
            throw new RuntimeException("Invalid Platform Kind");
        }
        return ((SPIRVKind) platformKind).isInteger();
    }

    @Override
    protected Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        SPIRVLogger.traceBuildLIR("[µInstructions] emitAdd: %s + %s", a, b);
        return emitBinaryAssign(SPIRVBinaryOp.ADD, resultKind, a, b);
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
        SPIRVLogger.traceBuildLIR("emitShl: %s << %s", a, b);
        LIRKind lirKind = LIRKind.combine(a, b);
        final Variable result = getGen().newVariable(lirKind);
        SPIRVBinary.ShiftLeft shiftLeft = new SPIRVBinary.ShiftLeft(SPIRVBinaryOp.BITWISE_LEFT_SHIFT, lirKind, a, b);
        getGen().append(new SPIRVLIRStmt.AssignStmt(result, shiftLeft));
        return result;
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
        SPIRVLogger.traceBuildLIR("signExtend: %s , from %s to %s", inputVal, fromBits, toBits);
        LIRKind lirKind = getGen().getLIRKindTool().getIntegerKind(toBits);
        final Variable result = getGen().newVariable(lirKind);
        SPIRVUnary.SignExtend signExtend = new SPIRVUnary.SignExtend(lirKind, inputVal, fromBits, toBits);
        getGen().append(new SPIRVLIRStmt.AssignStmt(result, signExtend));
        return result;
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

    private void emitLoad(AllocatableValue result, SPIRVAddressCast cast, MemoryAccess address) {
        SPIRVLogger.traceBuildLIR("emitLoad STMT: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        getGen().append(new SPIRVLIRStmt.LoadStmt(result, cast, address));
    }

    @Override
    public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state) {
        SPIRVLogger.traceBuildLIR("emitLoad: %s <- %s with state:%s", kind, address, state);
        final Variable result = getGen().newVariable(kind);
        if (!(kind.getPlatformKind() instanceof SPIRVKind)) {
            throw new RuntimeException("invalid LIRKind");
        }

        SPIRVKind spirvKind = (SPIRVKind) kind.getPlatformKind();
        SPIRVArchitecture.SPIRVMemoryBase base = ((MemoryAccess) (address)).getMemoryRegion();

        if (spirvKind.isVector()) {
            throw new RuntimeException("Vector Load not supported");
        } else {
            SPIRVAddressCast cast = new SPIRVAddressCast(address, base, kind);
            emitLoad(result, cast, (MemoryAccess) address);
        }

        return result;
    }

    @Override
    public Variable emitVolatileLoad(LIRKind kind, Value address, LIRFrameState state) {
        return null;
    }

    @Override
    public void emitStore(ValueKind<?> kind, Value address, Value input, LIRFrameState state) {
        SPIRVLogger.trace("emitStore: kind=%s, address=%s, input=%s", kind, address, input);
        guarantee(kind.getPlatformKind() instanceof SPIRVKind, "invalid LIRKind: %s", kind);
        SPIRVKind spirvKind = (SPIRVKind) kind.getPlatformKind();

        MemoryAccess memAccess = null;
        Value accumulator = null;

        if (address instanceof MemoryAccess) {
            memAccess = (MemoryAccess) address;
        } else {
            accumulator = address;
        }

        if (spirvKind.isVector()) {
            throw new RuntimeException("Vector types not supported yet");
        } else {
            if (memAccess != null) {
                System.out.println("IMPLEMENT SPIRVAddressCastNode ");
                SPIRVAddressCast cast = new SPIRVAddressCast(memAccess.getValue(), memAccess.getMemoryRegion(), LIRKind.value(spirvKind));
                if (memAccess.getIndex() == null) {
                    getGen().append(new SPIRVLIRStmt.StoreStmt(cast, memAccess, input));
                }

                // AllocatableValue valueHolder = memAccess.assignedTo();
                // if (valueHolder != null) {
                // getGen().append(new OCLLIRStmt.AssignStmt(valueHolder, input));
                // }
            }
        }
    }

    @Override
    public void emitVolatileStore(ValueKind<?> kind, Value address, Value input, LIRFrameState state) {

    }
}
