/*
 * Copyright (c) 2021, 2022-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.ptx.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryIntrinsic.COPY_SIGN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryIntrinsic.FLOAT_MAX;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryIntrinsic.FLOAT_MIN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryIntrinsic.INT_MAX;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryIntrinsic.INT_MIN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryIntrinsic.RADIANS;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXUnaryIntrinsic.ABS;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXUnaryIntrinsic.CEIL;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXUnaryIntrinsic.COS;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXUnaryIntrinsic.EXP2;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXUnaryIntrinsic.FLOAT_FLOOR;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXUnaryIntrinsic.LOG2;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXUnaryIntrinsic.POPCOUNT;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXUnaryIntrinsic.SIN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXUnaryIntrinsic.SQRT;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXUnaryIntrinsic.TANH;

import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.lir.ConstantValue;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;

public class PTXBuiltinTool {

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
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genCeil: ceil(%s)", input);
        return new PTXUnary.Intrinsic(CEIL, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatCos(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genCos: cos(%s)", input);
        return new PTXUnary.Intrinsic(COS, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatCosPI(Value input, Value piConstant, Value resultMult, LIRGeneratorTool gen) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "cospi: cos(%s * PI)", input);
        Value multResult = gen.append(new PTXLIRStmt.AssignStmt(resultMult, new PTXBinary.Expr(PTXAssembler.PTXBinaryOp.MUL, LIRKind.value(PTXKind.F32), input, piConstant))).getResult();
        return new PTXUnary.Intrinsic(COS, LIRKind.value(input.getPlatformKind()), multResult);
    }

    public Value genFloatSinPI(Value input, Value piConstant, Value resultMult, LIRGeneratorTool gen) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "sinpi: sin(%s * PI)", input);
        Value multResult = gen.append(new PTXLIRStmt.AssignStmt(resultMult, new PTXBinary.Expr(PTXAssembler.PTXBinaryOp.MUL, LIRKind.value(PTXKind.F32), input, piConstant))).getResult();
        return new PTXUnary.Intrinsic(SIN, LIRKind.value(input.getPlatformKind()), multResult);
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

    public Value genFloatExp2(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genExp: exp(%s)", input);
        return new PTXUnary.Intrinsic(EXP2, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatFloor(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genFloatFloor: floor(%s)", input);
        return new PTXUnary.Intrinsic(FLOAT_FLOOR, LIRKind.value(input.getPlatformKind()), input);
    }

    /**
     * The radians operation is implemented as: (pi / 180) * degrees. In PTX, the
     * first argument of the multiplication is a constant value x
     * {@value uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants#DEGREES_TO_RADIANS}
     * while the second argument that corresponds to the degrees is value y.
     *
     * @param x
     *     the constant value of (pi/180)
     * @param y
     *     the angle value measured in degrees
     * @return Value: the approximately equivalent angle measured in radians
     */
    public Value genFloatRadians(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genFloatRadians: radians corresponds to PTX instruction mul.rn.f32(%s, %s)", x, y);
        return new PTXBinary.Intrinsic(RADIANS, LIRKind.combine(x, y), x, y);
    }

    public Value genFloatILogb(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatLGamma(Value input) {
        unimplemented();
        return null;
    }

    public Value genFloatLog2(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genFloatLog2: input=%s", input);
        return new PTXUnary.Intrinsic(LOG2, LIRKind.value(input.getPlatformKind()), input);
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
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genSin: sin(%s)", input);
        return new PTXUnary.Intrinsic(SIN, LIRKind.value(input.getPlatformKind()), input);
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
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genTanh: tanh(%s)", input);
        return new PTXUnary.Intrinsic(TANH, LIRKind.value(input.getPlatformKind()), input);
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

    public Value genCopySign(Value x, Value y) {
        return new PTXBinary.Intrinsic(COPY_SIGN, LIRKind.combine(x, y), x, y);
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
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genFloatMax: max(%s,%s)", x, y);
        return new PTXBinary.Intrinsic(FLOAT_MAX, LIRKind.combine(x, y), x, y);
    }

    public Value genFloatMin(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genFloatMin: min(%s,%s)", x, y);
        return new PTXBinary.Intrinsic(FLOAT_MIN, LIRKind.combine(x, y), x, y);
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

    public Value genIntAbs(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genIntAbs: abs(%s)", input);
        return new PTXUnary.Intrinsic(ABS, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genFloatSqrt(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genAbs: sqrt(%s)", input);
        return new PTXUnary.Intrinsic(SQRT, LIRKind.value(input.getPlatformKind()), input);
    }

    public Value genIntMax(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genMax: max(%s,%s)", x, y);
        return new PTXBinary.Intrinsic(INT_MAX, LIRKind.combine(x, y), x, y);
    }

    public Value genIntMin(Value x, Value y) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genMin: min(%s,%s)", x, y);
        return new PTXBinary.Intrinsic(INT_MIN, LIRKind.combine(x, y), x, y);
    }

    public Value genIntClz(Value value) {
        unimplemented();
        return null;
    }

    public Value genIntPopcount(Value value) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genBitCount: bitcount(%s)", value);
        return new PTXUnary.Intrinsic(POPCOUNT, LIRKind.value(value.getPlatformKind()), value);
    }

    public Value genIntClamp(Value x, Value y, Value z) {
        TornadoInternalError.unimplemented();
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

    public Value genFloatAbs(Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "genFloatAbs: abs(%s)", input);
        return new PTXUnary.Intrinsic(ABS, LIRKind.value(input.getPlatformKind()), input);
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
        unimplemented();
        return null;
    }

    public Value genGeometricCross(Value x, Value y) {
        unimplemented();
        return null;
    }

    /**
     * Coefficients of a 5-term Hastings minimax polynomial approximating atan(z) for z in [0, 1] (max absolute error
     * ~3e-5 radians): atan(z) ~= z * (c0 + s*(c1 + s*(c2 + s*(c3 + s*c4)))), where s = z*z.
     */
    private static final double[] ATAN_POLYNOMIAL = { 0.9998660, -0.3302995, 0.1801410, -0.0851330, 0.0208351 };

    /**
     * Creates a floating-point constant matching the precision (F32 or F64) of the surrounding computation.
     */
    public ConstantValue genFloatConstant(PTXKind kind, double value) {
        if (kind.isF32()) {
            return new ConstantValue(LIRKind.value(PTXKind.F32), JavaConstant.forFloat((float) value));
        }
        return new ConstantValue(LIRKind.value(PTXKind.F64), JavaConstant.forDouble(value));
    }

    /**
     * Materializes an expression into a fresh variable so it can be used as an operand in subsequent instructions.
     */
    public Value genMaterialize(NodeLIRBuilderTool builder, Value expression, LIRKind kind) {
        Variable variable = builder.getLIRGeneratorTool().newVariable(kind);
        return builder.getLIRGeneratorTool().append(new PTXLIRStmt.AssignStmt(variable, expression)).getResult();
    }

    /**
     * Emits a floating-point comparison ({@code setp.<cmp>}) producing a predicate register.
     */
    public Variable genFloatCompare(NodeLIRBuilderTool builder, PTXAssembler.PTXBinaryOp comparison, Value a, Value b) {
        LIRKind compareKind = LIRKind.value(a.getPlatformKind());
        Variable predicate = builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.PRED));
        builder.getLIRGeneratorTool().append(new PTXLIRStmt.AssignStmt(predicate, new PTXBinary.Expr(comparison, compareKind, a, b)));
        return predicate;
    }

    /**
     * Emits a branchless select ({@code selp}): {@code predicate ? valueIfTrue : valueIfFalse}.
     */
    public Value genFloatSelect(NodeLIRBuilderTool builder, Value predicate, Value valueIfTrue, Value valueIfFalse) {
        LIRKind kind = LIRKind.value(valueIfTrue.getPlatformKind());
        Variable result = builder.getLIRGeneratorTool().newVariable(kind);
        builder.getLIRGeneratorTool().append(new PTXLIRStmt.AssignStmt(result, new PTXTernary.Expr(PTXAssembler.PTXTernaryOp.SELP, kind, valueIfTrue, valueIfFalse, predicate)));
        return result;
    }

    /**
     * Approximates atan(z) for a reduced, non-negative argument z in [0, 1] using Horner evaluation of
     * {@link #ATAN_POLYNOMIAL}.
     */
    public Value genAtanReduced(PTXArithmeticTool lirGen, Value z) {
        PTXKind kind = (PTXKind) z.getPlatformKind();
        Value s = lirGen.emitMul(z, z, false);
        Value polynomial = genFloatConstant(kind, ATAN_POLYNOMIAL[ATAN_POLYNOMIAL.length - 1]);
        for (int i = ATAN_POLYNOMIAL.length - 2; i >= 0; i--) {
            polynomial = lirGen.emitAdd(lirGen.emitMul(s, polynomial, false), genFloatConstant(kind, ATAN_POLYNOMIAL[i]), false);
        }
        return lirGen.emitMul(z, polynomial, false);
    }

    /**
     * Computes atan(x) over the full domain, returning a materialized value.
     *
     * The argument is range-reduced to [0, 1] via the identity atan(t) = pi/2 - atan(1/t) for t &gt; 1, the reduced
     * argument is approximated with {@link #genAtanReduced}, and the sign of the input is restored (atan is odd).
     */
    public Value genAtan(NodeLIRBuilderTool builder, PTXArithmeticTool lirGen, Value x) {
        PTXKind kind = (PTXKind) x.getPlatformKind();
        LIRKind lirKind = LIRKind.value(kind);
        Value one = genFloatConstant(kind, 1.0);
        Value halfPi = genFloatConstant(kind, Math.PI / 2.0);

        Value absX = genMaterialize(builder, genFloatAbs(x), lirKind);
        Value isLarge = genFloatCompare(builder, PTXAssembler.PTXBinaryOp.SETP_GT, absX, one);
        Value reciprocal = lirGen.emitDiv(one, absX, null);
        Value z = genFloatSelect(builder, isLarge, reciprocal, absX);

        Value atanZ = genMaterialize(builder, genAtanReduced(lirGen, z), lirKind);
        Value complement = lirGen.emitSub(halfPi, atanZ, false);
        Value magnitude = genFloatSelect(builder, isLarge, complement, atanZ);

        return genMaterialize(builder, lirGen.emitMathCopySign(magnitude, x), lirKind);
    }

    /**
     * Computes atan2(numerator, denominator) over the full domain, returning a materialized value.
     *
     * The angle is derived branchlessly from the reduced ratio min(|x|,|y|)/max(|x|,|y|) in [0, 1] and corrected for
     * the quadrant given by the signs of the inputs (|x| = |denominator|, |y| = |numerator|):
     *
     * <code>
     * r = atan(min(|x|,|y|) / max(|x|,|y|));
     * if (|y| > |x|) r = pi/2 - r;
     * if (denominator < 0) r = pi - r;
     * r = copysign(r, numerator);
     * </code>
     */
    public Value genAtan2(NodeLIRBuilderTool builder, PTXArithmeticTool lirGen, Value numerator, Value denominator) {
        PTXKind kind = (PTXKind) LIRKind.combine(numerator, denominator).getPlatformKind();
        LIRKind lirKind = LIRKind.value(kind);
        Value zero = genFloatConstant(kind, 0.0);
        Value halfPi = genFloatConstant(kind, Math.PI / 2.0);
        Value pi = genFloatConstant(kind, Math.PI);

        Value absX = genMaterialize(builder, genFloatAbs(denominator), lirKind);
        Value absY = genMaterialize(builder, genFloatAbs(numerator), lirKind);

        Value swap = genFloatCompare(builder, PTXAssembler.PTXBinaryOp.SETP_GT, absY, absX);
        Value minAbs = genFloatSelect(builder, swap, absX, absY);
        Value maxAbs = genFloatSelect(builder, swap, absY, absX);
        Value ratio = lirGen.emitDiv(minAbs, maxAbs, null);
        Value maxIsZero = genFloatCompare(builder, PTXAssembler.PTXBinaryOp.SETP_EQ, maxAbs, zero);
        ratio = genFloatSelect(builder, maxIsZero, zero, ratio);

        Value angle = genMaterialize(builder, genAtanReduced(lirGen, ratio), lirKind);
        angle = genFloatSelect(builder, swap, lirGen.emitSub(halfPi, angle, false), angle);
        Value denominatorNegative = genFloatCompare(builder, PTXAssembler.PTXBinaryOp.SETP_LT, denominator, zero);
        angle = genFloatSelect(builder, denominatorNegative, lirGen.emitSub(pi, angle, false), angle);

        return genMaterialize(builder, lirGen.emitMathCopySign(angle, numerator), lirKind);
    }

    /**
     * Computes asin(x) for x in [-1, 1] using the identity asin(x) = atan2(x, sqrt(1 - x*x)), returning a materialized
     * value. The denominator is non-negative, so atan2 yields a result in [-pi/2, pi/2] as required.
     */
    public Value genAsin(NodeLIRBuilderTool builder, PTXArithmeticTool lirGen, Value x) {
        PTXKind kind = (PTXKind) x.getPlatformKind();
        LIRKind lirKind = LIRKind.value(kind);
        Value one = genFloatConstant(kind, 1.0);
        Value oneMinusXSquared = lirGen.emitSub(one, lirGen.emitMul(x, x, false), false);
        Value denominator = genMaterialize(builder, genFloatSqrt(oneMinusXSquared), lirKind);
        return genAtan2(builder, lirGen, x, denominator);
    }

}
