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

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.lir.Variable;
import jdk.vm.ci.meta.Value;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;
import static tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryIntrinsic.*;
import static tornado.drivers.opencl.graal.asm.OCLAssembler.OCLTernaryIntrinsic.CLAMP;
import static tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryIntrinsic.*;
import static tornado.graal.compiler.TornadoCodeGenerator.trace;

public class OCLBuiltinTool {

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

    public Value genFloatCos(Value input) {
        unimplemented();
        return null;
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

    public Value genFloatExp(Value input) {
        trace("genExp: exp(%s)", input);
        return new OCLUnary.Intrinsic(EXP, LIRKind.value(input.getPlatformKind()), input);
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

    public Value genFloatFloor(Value input) {
        trace("genFloatFloor: floor(%s)", input);
        return new OCLUnary.Intrinsic(FLOAT_FLOOR, LIRKind.value(input.getPlatformKind()), input);
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
        trace("genLog: log(%s)", input);
        return new OCLUnary.Intrinsic(LOG, LIRKind.value(input.getPlatformKind()), input);
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

    public Value genFloatTrunc(Value input) {
        trace("genFloatTrunc: trunc(%s)", input);
        return new OCLUnary.Intrinsic(FLOAT_TRUNC, LIRKind.value(input.getPlatformKind()), input);
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
        trace("genFloatMax: max(%s,%s)", x, y);
        return new OCLBinary.Intrinsic(FLOAT_MAX, LIRKind.combine(x, y), x, y);
    }

    public Value genFloatMin(Value x, Value y) {
        trace("genFloatMin: min(%s,%s)", x, y);
        return new OCLBinary.Intrinsic(FLOAT_MIN, LIRKind.combine(x, y), x, y);
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
        trace("genFloatPow: pow(%s,%s)", x, y);
        return new OCLBinary.Intrinsic(FLOAT_POW, LIRKind.combine(x, y), x, y);
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
        trace("genIntAbs: abs(%s)", input);
        return new OCLUnary.Intrinsic(ABS, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatSqrt(Value input) {
        trace("genAbs: sqrt(%s)", input);
        return new OCLUnary.Intrinsic(SQRT, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genIntMax(Value x, Value y) {
        trace("genMax: max(%s,%s)", x, y);
        return new OCLBinary.Intrinsic(INT_MAX, LIRKind.combine(x, y), x, y);
    }

    public Value genIntMin(Value x, Value y) {
        trace("genMin: min(%s,%s)", x, y);
        return new OCLBinary.Intrinsic(INT_MIN, LIRKind.combine(x, y), x, y);
    }

    public Value genIntClz(Value value) {
        unimplemented();
        return null;
    }

    public Value genIntPopcount(Value value) {
        unimplemented();
        return null;
    }

    public Value genIntClamp(Value x, Value y, Value z) {
        trace("genIntClamp: clamp(%s, %s, %s)", x, y, z);
        return new OCLTernary.Intrinsic(CLAMP, LIRKind.combine(x, y, z), x, y, z);
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
        trace("genFloatAbs: abs(%s)", input);
        return new OCLUnary.Intrinsic(FLOAT_ABS, LIRKind.value(input.getPlatformKind()), input);
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

    public Value genGeometricDot(Value x, Value y) {
        trace("genDot: dot(%s,%s)", x, y);
        return new OCLBinary.Intrinsic(DOT, LIRKind.combine(x, y), x, y);
    }

    public Value genGeometricCross(Value x, Value y) {
        trace("genCross: cross(%s,%s)", x, y);
        return new OCLBinary.Intrinsic(CROSS, LIRKind.combine(x, y), x, y);
    }

}
