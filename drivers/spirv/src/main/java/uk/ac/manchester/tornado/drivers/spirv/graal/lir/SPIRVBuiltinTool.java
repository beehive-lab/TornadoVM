package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Variable;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;

public class SPIRVBuiltinTool {

    public Value genFloatACos(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: acos(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.ACOS, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatACosh(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: acosh(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.ACOSH, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatACospi(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: acospi(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.ACOSPI, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatASin(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: asin(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.ASIN, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatASinh(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: asinh(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.ASIN, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatASinpi(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: asinpi(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.ASINPI, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatATan(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: atan(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.ATAN, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatATanh(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: atanh(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.ATANH, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatATanpi(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: atanpi(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.ATANPI, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatCbrt(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: cbrt(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.CBRT, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatCeil(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: ceil(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.CEIL, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatCos(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: cos(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.NATIVE_COS, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatCosh(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: atan(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.COSH, LIRKind.value(input.getPlatformKind()), input);
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
        SPIRVLogger.traceBuildLIR("genCos: exp(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.EXP, LIRKind.value(input.getPlatformKind()), input);
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
        SPIRVLogger.traceBuildLIR("gen: floor(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.FLOOR, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatILogb(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatLGamma(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatLog(Value input) {
        SPIRVLogger.traceBuildLIR("gen: log(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.LOG, LIRKind.value(input.getPlatformKind()), input);
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

    public Value genFloatSin(Value input) {
        SPIRVLogger.traceBuildLIR("gen: sin(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.SIN, LIRKind.value(input.getPlatformKind()), input);
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
        SPIRVLogger.traceBuildLIR("gen: tan(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.TAN, LIRKind.value(input.getPlatformKind()), input);
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

    public Value genFloatMax(Value x, Value y) {
        SPIRVLogger.traceBuildLIR("gen: min(%s,%s)", x, y);
        return new SPIRVBinary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.FMAX, LIRKind.combine(x, y), x, y);
    }

    public Value genFloatMin(Value x, Value y) {
        SPIRVLogger.traceBuildLIR("gen: min(%s,%s)", x, y);
        return new SPIRVBinary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.FMIN, LIRKind.combine(x, y), x, y);
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

    public Value genFloatPow(Value x, Value y) {
        SPIRVLogger.traceBuildLIR("genFloatPow: pow(%s,%s)", x, y);
        return new SPIRVBinary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.POW, LIRKind.combine(x, y), x, y);
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

    public Value genIntAbs(Value input) {
        SPIRVLogger.traceBuildLIR("genIntAbs: abs(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.SABS, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatSqrt(Value input) {
        SPIRVLogger.traceBuildLIR("gen: sqrt(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.SQRT, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genIntMax(Value x, Value y) {
        SPIRVLogger.traceBuildLIR("genMax: max(%s,%s)", x, y);
        return new SPIRVBinary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.SMAX, LIRKind.combine(x, y), x, y);
    }

    public Value genIntMin(Value x, Value y) {
        SPIRVLogger.traceBuildLIR("genMin: min(%s,%s)", x, y);
        return new SPIRVBinary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.SMIN, LIRKind.combine(x, y), x, y);
    }

    public Value genIntClz(Value value) {
        unimplemented();
        return null;
    }

    public Value genIntPopcount(Value value) {
        SPIRVLogger.traceBuildLIR("popcount: abs(%s)", value);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.POPCOPUNT, LIRKind.value(value.getPlatformKind()), value);
    }

    public Value genIntClamp(Value x, Value y, Value z) {
        SPIRVLogger.traceBuildLIR("genIntClamp: clamp(%s, %s, %s)", x, y, z);
        return new SPIRVTernary.TernaryIntrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.SCLAMP, LIRKind.combine(x, y, z), x, y, z);
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

    public Value genFloatAbs(Value input) {
        SPIRVLogger.traceBuildLIR("genCos: fabs(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic.FABS, LIRKind.value(input.getPlatformKind()), input);
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
