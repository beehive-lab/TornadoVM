/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2022-2023, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Variable;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class SPIRVBuiltinTool {

    public Value genFloatACos(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: acos(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.ACOS, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatACosh(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: acosh(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.ACOSH, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatACospi(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: acospi(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.ACOSPI, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatCospi(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: cospi(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.COSPI, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatASin(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: asin(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.ASIN, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatASinh(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: asinh(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.ASINH, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatASinpi(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: asinpi(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.ASINPI, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatSinpi(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: sinpi(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.SINPI, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatATan(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: atan(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.ATAN, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatATanh(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: atanh(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.ATANH, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatATanpi(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: atanpi(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.ATANPI, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatCbrt(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: cbrt(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.CBRT, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatCeil(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: ceil(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.CEIL, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatCos(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: cos(%s)", input);
        SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic intrinsic = SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.COS;
        if (TornadoOptions.ENABLE_NATIVE_FUNCTION) {
            intrinsic = SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.NATIVE_COS;
        }
        return new SPIRVUnary.Intrinsic(intrinsic, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatCosh(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: cosh(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.COSH, LIRKind.value(input.getPlatformKind()), input);
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
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: exp(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.EXP, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value generateSign(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: sign(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.SIGN, LIRKind.value(input.getPlatformKind()), input);
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
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: floor(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.FLOOR, LIRKind.value(input.getPlatformKind()), input);
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
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: log(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.LOG, LIRKind.value(input.getPlatformKind()), input);
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
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: sin(%s)", input);
        SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic intrinsic = SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.SIN;
        if (TornadoOptions.ENABLE_NATIVE_FUNCTION) {
            intrinsic = SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.NATIVE_SIN;
        }
        return new SPIRVUnary.Intrinsic(intrinsic, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatSinh(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatTan(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: tan(%s)", input);
        SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic intrinsic = SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.TAN;
        if (TornadoOptions.ENABLE_NATIVE_FUNCTION) {
            intrinsic = SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.NATIVE_TAN;
        }
        return new SPIRVUnary.Intrinsic(intrinsic, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatTanh(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: tanh(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.TANH, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatRadians(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "genFloatRadians: radians(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.RADIANS, LIRKind.value(input.getPlatformKind()), input);
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
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "genFloatAtan2: atan(%s, %s)", x, y);
        return new SPIRVBinary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.ATAN2, LIRKind.combine(x, y), x, y);
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
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: max(%s,%s)", x, y);
        return new SPIRVBinary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.FMAX, LIRKind.combine(x, y), x, y);
    }

    public Value genFloatMin(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: min(%s,%s)", x, y);
        return new SPIRVBinary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.FMIN, LIRKind.combine(x, y), x, y);
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
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "genFloatPow: pow(%s,%s)", x, y);
        return new SPIRVBinary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.POW, LIRKind.combine(x, y), x, y);
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
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "genIntAbs: abs(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.SABS, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatSqrt(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: sqrt(%s)", input);
        SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic intrinsic = SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.SQRT;
        if (TornadoOptions.ENABLE_NATIVE_FUNCTION) {
            intrinsic = SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.NATIVE_SQRT;
        }
        return new SPIRVUnary.Intrinsic(intrinsic, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genIntMax(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "genMax: max(%s,%s)", x, y);
        return new SPIRVBinary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.SMAX, LIRKind.combine(x, y), x, y);
    }

    public Value genIntMin(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "genMin: min(%s,%s)", x, y);
        return new SPIRVBinary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.SMIN, LIRKind.combine(x, y), x, y);
    }

    public Value genIntClz(Value value) {
        unimplemented();
        return null;
    }

    public Value genIntPopcount(Value value) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "popcount: abs(%s)", value);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.POPCOUNT, LIRKind.value(value.getPlatformKind()), value);
    }

    public Value genIntClamp(Variable result, Value x, Value y, Value z) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "genIntClamp: clamp(%s, %s, %s)", x, y, z);
        return new SPIRVTernary.TernaryIntrinsic(result, SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.SCLAMP, LIRKind.combine(x, y, z), x, y, z);
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
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "gen: fabs(%s)", input);
        return new SPIRVUnary.Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.FABS, LIRKind.value(input.getPlatformKind()), input);
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
