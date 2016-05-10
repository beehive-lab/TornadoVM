package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.api.meta.Value;

public interface OCLFPBuiltinFunctionLIRGenerator {

	/*
	 * Unary intrinsics
	 */
	
	Value emitFloatAbs(Value input);
	
	Value emitFloatACos(Value input);

	Value emitFloatACosh(Value input);

	Value emitFloatACospi(Value input);

	Value emitFloatASin(Value input);

	Value emitFloatASinh(Value input);

	Value emitFloatASinpi(Value input);

	Value emitFloatATan(Value input);

	Value emitFloatATanh(Value input);

	Value emitFloatATanpi(Value input);

	Value emitFloatCbrt(Value input);

	Value emitFloatCeil(Value input);

	Value emitFloatCos(Value input);

	Value emitFloatCosh(Value input);

	Value emitFloatCospi(Value input);

	Value emitFloatErfc(Value input);

	Value emitFloatErf(Value input);

	Value emitFloatExp(Value input);

	Value emitFloatExp2(Value input);

	Value emitFloatExp10(Value input);

	Value emitFloatExpm1(Value input);

	Value emitFloatFloor(Value input);

	Value emitFloatILogb(Value input);

	Value emitFloatLGamma(Value input);

	Value emitFloatLog(Value input);

	Value emitFloatLog2(Value input);

	Value emitFloatLog10(Value input);

	Value emitFloatLog1p(Value input);

	Value emitFloatLogb(Value input);

	Value emitFloatNan(Value input);

	Value emitFloatRint(Value input);

	Value emitFloatRound(Value input);

	Value emitFloatRSqrt(Value input);

	Value emitFloatSin(Value input);

	Value emitFloatSinh(Value input);

	Value emitFloatSinpi(Value input);

	Value emitFloatSqrt(Value input);

	Value emitFloatTan(Value input);

	Value emitFloatTanh(Value input);

	Value emitFloatTanpi(Value input);

	Value emitFloatTGamma(Value input);

	Value emitFloatTrunc(Value input);

	/*
	 * Binary intrinsics
	 */

	Value emitFloatATan2(Value input1, Value input2);

	Value emitFloatATan2pi(Value input1, Value input2);

	Value emitFloatCopySign(Value input1, Value input2);

	Value emitFloatDim(Value input1, Value input2);

	Value emitFloatFma(Value input1, Value input2);

	Value emitFloatMax(Value input1, Value input2);

	Value emitFloatMin(Value input1, Value input2);

	Value emitFloatMod(Value input1, Value input2);

	Value emitFloatFract(Value input1, Value input2);

	Value emitFloatFrexp(Value input1, Value input2);

	Value emitFloatHypot(Value input1, Value input2);

	Value emitFloatLdexp(Value input1, Value input2);

	Value emitFloatMad(Value input1, Value input2);

	Value emitFloatMaxmag(Value input1, Value input2);

	Value emitFloatMinmag(Value input1, Value input2);

	Value emitFloatModf(Value input1, Value input2);

	Value emitFloatNextAfter(Value input1, Value input2);

	Value emitFloatPow(Value input1, Value input2);

	Value emitFloatPown(Value input1, Value input2);

	Value emitFloatPowr(Value input1, Value input2);

	Value emitFloatRemainder(Value input1, Value input2);

	Value emitFloatRootn(Value input1, Value input2);

	Value emitFloatSincos(Value input1, Value input2);
	
	/*
	 * Ternary intrinsics
	 */

	Value emitFloatFMA(Value x, Value y, Value z);

	Value emitFloatMAD(Value x, Value y, Value z);

	Value emitFloatRemquo(Value x, Value y, Value z);

	
}
