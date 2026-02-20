/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic.ATAN2;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic.CROSS;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic.DOT;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic.FLOAT_MAX;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic.FLOAT_MIN;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic.FLOAT_POW;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic.INT_MAX;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic.INT_MIN;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalTernaryIntrinsic.CLAMP;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.ABS;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.ACOS;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.ACOSH;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.ASIN;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.ASINH;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.ATAN;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.CEIL;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.COS;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.COSPI;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.EXP;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.FLOAT_ABS;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.FLOAT_FLOOR;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.FLOAT_TRUNC;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.LOG;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.NATIVE_COS;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.NATIVE_SIN;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.NATIVE_SQRT;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.NATIVE_TAN;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.POPCOUNT;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.RADIANS;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.SIGN;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.SIN;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.SINPI;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.SQRT;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.TAN;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic.TANH;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Variable;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

// FIXME <REFACTOR> Common between the 3 backends
public class MetalBuiltinTool {

    public Value genFloatACos(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genAcos: acos(%s)", input);
        return new MetalUnary.Intrinsic(ACOS, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatACosh(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genAcosh: acosh(%s)", input);
        return new MetalUnary.Intrinsic(ACOSH, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatACospi(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatASin(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genAsin: asin(%s)", input);
        return new MetalUnary.Intrinsic(ASIN, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatASinh(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genAsinh: asinh(%s)", input);
        return new MetalUnary.Intrinsic(ASINH, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatASinpi(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatATan(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genAtan: atan(%s)", input);
        return new MetalUnary.Intrinsic(ATAN, LIRKind.value(input.getPlatformKind()), input);
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
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genCeil: ceil(%s)", input);
        return new MetalUnary.Intrinsic(CEIL, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatCos(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genCos: cos(%s)", input);
        if (TornadoOptions.ENABLE_NATIVE_FUNCTION) {
            return new MetalUnary.Intrinsic(NATIVE_COS, LIRKind.value(input.getPlatformKind()), input);
        }
        return new MetalUnary.Intrinsic(COS, LIRKind.value(input.getPlatformKind()), input);
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
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genExp: exp(%s)", input);
        return new MetalUnary.Intrinsic(EXP, LIRKind.value(input.getPlatformKind()), input);
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
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genFloatFloor: floor(%s)", input);
        return new MetalUnary.Intrinsic(FLOAT_FLOOR, LIRKind.value(input.getPlatformKind()), input);
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
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genLog: log(%s)", input);
        return new MetalUnary.Intrinsic(LOG, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatRadians(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genFloatRadians: radians(%s)", input);
        return new MetalUnary.Intrinsic(RADIANS, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatCosPI(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genFloatCosPI: cospi(%s)", input);
        return new MetalUnary.Intrinsic(COSPI, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatSinPI(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genFloatCosPI: sinpi(%s)", input);
        return new MetalUnary.Intrinsic(SINPI, LIRKind.value(input.getPlatformKind()), input);
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

    public Value genFloatSign(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genSign: sign(%s)", input);
        return new MetalUnary.Intrinsic(SIGN, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatSin(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genSin: sin(%s)", input);
        if (TornadoOptions.ENABLE_NATIVE_FUNCTION) {
            return new MetalUnary.Intrinsic(NATIVE_SIN, LIRKind.value(input.getPlatformKind()), input);
        }
        return new MetalUnary.Intrinsic(SIN, LIRKind.value(input.getPlatformKind()), input);
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
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genTan: tan(%s)", input);
        if (TornadoOptions.ENABLE_NATIVE_FUNCTION) {
            return new MetalUnary.Intrinsic(NATIVE_TAN, LIRKind.value(input.getPlatformKind()), input);
        }
        return new MetalUnary.Intrinsic(TAN, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatTanh(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genTanh: tanh(%s)", input);
        return new MetalUnary.Intrinsic(TANH, LIRKind.value(input.getPlatformKind()), input);
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
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genFloatTrunc: trunc(%s)", input);
        return new MetalUnary.Intrinsic(FLOAT_TRUNC, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatATan2(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genFloatAtan2: atan(%s, %s)", x, y);
        return new MetalBinary.Intrinsic(ATAN2, LIRKind.combine(x, y), x, y);
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
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genFloatMax: max(%s,%s)", x, y);
        return new MetalBinary.Intrinsic(FLOAT_MAX, LIRKind.combine(x, y), x, y);
    }

    public Value genFloatMin(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genFloatMin: min(%s,%s)", x, y);
        return new MetalBinary.Intrinsic(FLOAT_MIN, LIRKind.combine(x, y), x, y);
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
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genFloatPow: pow(%s,%s)", x, y);
        return new MetalBinary.Intrinsic(FLOAT_POW, LIRKind.combine(x, y), x, y);
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
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genIntAbs: abs(%s)", input);
        return new MetalUnary.Intrinsic(ABS, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatSqrt(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genAbs: sqrt(%s)", input);
        if (TornadoOptions.ENABLE_NATIVE_FUNCTION) {
            return new MetalUnary.Intrinsic(NATIVE_SQRT, LIRKind.value(input.getPlatformKind()), input);
        }
        return new MetalUnary.Intrinsic(SQRT, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genIntMax(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genMax: max(%s,%s)", x, y);
        return new MetalBinary.Intrinsic(INT_MAX, LIRKind.combine(x, y), x, y);
    }

    public Value genIntMin(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genMin: min(%s,%s)", x, y);
        return new MetalBinary.Intrinsic(INT_MIN, LIRKind.combine(x, y), x, y);
    }

    public Value genIntClz(Value value) {
        unimplemented();
        return null;
    }

    public Value genIntPopcount(Value value) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genBitCount: bitcount(%s)", value);
        return new MetalUnary.Intrinsic(POPCOUNT, LIRKind.value(value.getPlatformKind()), value);
    }

    public Value genIntClamp(Value x, Value y, Value z) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genIntClamp: clamp(%s, %s, %s)", x, y, z);
        return new MetalTernary.Intrinsic(CLAMP, LIRKind.combine(x, y, z), x, y, z);
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
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genFloatAbs: abs(%s)", input);
        return new MetalUnary.Intrinsic(FLOAT_ABS, LIRKind.value(input.getPlatformKind()), input);
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
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genDot: dot(%s,%s)", x, y);
        return new MetalBinary.Intrinsic(DOT, LIRKind.combine(x, y), x, y);
    }

    public Value genGeometricCross(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.Metal, "genCross: cross(%s,%s)", x, y);
        return new MetalBinary.Intrinsic(CROSS, LIRKind.combine(x, y), x, y);
    }

}
