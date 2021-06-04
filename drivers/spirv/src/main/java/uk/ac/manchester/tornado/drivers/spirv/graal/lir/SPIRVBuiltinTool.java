package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Variable;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;

public class SPIRVBuiltinTool {

    public Value genFloatACos(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatACosh(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatACospi(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatASin(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatASinh(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatASinpi(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatATan(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatATanh(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatATanpi(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatCbrt(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatCeil(Value input) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genFloatCos(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: cos(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.NATIVE_COS, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatCosh(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatCospi(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatErfc(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatErf(Value input) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genFloatExp(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatExp2(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatExp10(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatExpm1(Value input) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genFloatFloor(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatILogb(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatLGamma(Value input) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genFloatLog(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatLog2(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatLog10(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatLog1p(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatLogb(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatNan(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatRint(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatRound(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatRSqrt(Value input) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genFloatSin(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatSinh(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatSinpi(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatTan(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatTanh(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatTanpi(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatTGamma(Value input) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genFloatTrunc(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatATan2(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatATan2pi(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatCopySign(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatDim(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatFma(Value x, Value y) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genFloatMax(Value x, Value y) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genFloatMin(Value x, Value y) {
        SPIRVLogger.traceBuildLIR("genFloatMin: min(%s,%s)", x, y);
        // return new SPIRVBinary.Intrinsic("min", LIRKind.combine(x, y), x, y);
        unimplemented();
        return null;
    }

    public Value genFloatMod(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatFract(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatFrexp(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatHypot(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatLdexp(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatMad(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatMaxmag(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatMinmag(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatModf(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatNextAfter(Value x, Value y) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genFloatPow(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatPown(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatPowr(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatRemainder(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatRootn(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genFloatSincos(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Variable genBitCount(Value input) {
        unimplemented();
        return null;
    }

    public Variable genBitScanForward(Value input) {
        unimplemented();
        return null;
    }

    public Variable genBitScanReverse(Value input) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genIntAbs(Value input) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genFloatSqrt(Value input) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genIntMax(Value x, Value y) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genIntMin(Value x, Value y) {
        unimplemented();
        return null;
    }

    public Value genIntClz(Value value) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genIntPopcount(Value value) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genIntClamp(Value x, Value y, Value z) {
        unimplemented();
        return null;
    }

    public Value genIntMad24(Value x, Value y, Value z) {
        unimplemented();
        return null;
    }

    public Value genIntMadHi(Value x, Value y, Value z) {
        unimplemented();
        return null;
    }

    public Value genIntMadSat(Value x, Value y, Value z) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genFloatAbs(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatFMA(Value x, Value y, Value z) {
        unimplemented();
        return null;
    }

    public Value genFloatMAD(Value x, Value y, Value z) {
        unimplemented();
        return null;
    }

    public Value genFloatRemquo(Value x, Value y, Value z) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genGeometricDot(Value x, Value y) {
        unimplemented();
        return null;
    }

    // FIXME: REVISIT
    public Value genGeometricCross(Value x, Value y) {
        unimplemented();
        return null;
    }
}
