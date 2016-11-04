package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.api.code.CallingConvention;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.OCLProviders;
import tornado.drivers.opencl.graal.OpenCLCodeCache;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLTernaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryIntrinsic;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLFPBuiltinFunctionLIRGenerator;
import tornado.drivers.opencl.graal.lir.OCLIntBuiltinFunctionLIRGenerator;
import tornado.drivers.opencl.graal.lir.OCLTernary;
import tornado.drivers.opencl.graal.lir.OCLUnary;
import static tornado.graal.compiler.TornadoCodeGenerator.*;

public class OCLLIRGenerator extends OCLBasicLIRGenerator implements
		OCLFPBuiltinFunctionLIRGenerator, OCLIntBuiltinFunctionLIRGenerator {

	public OCLLIRGenerator(
			OCLProviders providers,
			OpenCLCodeCache codeCache,
			CallingConvention cc,
			LIRGenerationResult res) {
		super(providers, codeCache, cc, res);
	}
	
	@Override
	public Value emitFloatACos(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatACosh(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatACospi(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatASin(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatASinh(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatASinpi(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatATan(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatATanh(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatATanpi(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatCbrt(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatCeil(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatCos(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatCosh(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatCospi(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatErfc(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatErf(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatExp(Value input) {
		trace("emitExp: exp(%s)",input);
		return new OCLUnary.Intrinsic(OCLUnaryIntrinsic.EXP,LIRKind.derive(input),load(input));
	}



	@Override
	public Value emitFloatExp2(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatExp10(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatExpm1(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatFloor(Value input) {
		trace("emitFloatFloor: floor(%s)",input);
		return new OCLUnary.Intrinsic(OCLUnaryIntrinsic.FLOAT_FLOOR,LIRKind.derive(input),load(input));
	}




	@Override
	public Value emitFloatILogb(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatLGamma(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatLog(Value input) {
		trace("emitLog: log(%s)",input);
		return new OCLUnary.Intrinsic(OCLUnaryIntrinsic.LOG,LIRKind.derive(input),load(input));
	}



	@Override
	public Value emitFloatLog2(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatLog10(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatLog1p(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatLogb(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatNan(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatRint(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatRound(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatRSqrt(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatSin(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatSinh(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatSinpi(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatTan(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatTanh(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatTanpi(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatTGamma(Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatTrunc(Value input) {
		trace("emitFloatTrunc: trunc(%s)",input);
		return new OCLUnary.Intrinsic(OCLUnaryIntrinsic.FLOAT_TRUNC,LIRKind.derive(input),load(input));
	}




	@Override
	public Value emitFloatATan2(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatATan2pi(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatCopySign(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatDim(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatFma(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatMax(Value input1, Value input2) {
		trace("emitFloatMax: max(%s,%s)",input1,input2);
		return new OCLBinary.Intrinsic(OCLBinaryIntrinsic.FLOAT_MAX,LIRKind.derive(input1, input2),load(input1),load(input2));
	}



	@Override
	public Value emitFloatMin(Value input1, Value input2) {
		trace("emitFloatMin: min(%s,%s)",input1,input2);
		return new OCLBinary.Intrinsic(OCLBinaryIntrinsic.FLOAT_MIN,LIRKind.derive(input1, input2),load(input1),load(input2));
	}



	@Override
	public Value emitFloatMod(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatFract(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatFrexp(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatHypot(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatLdexp(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatMad(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatMaxmag(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatMinmag(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatModf(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatNextAfter(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatPow(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatPown(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatPowr(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatRemainder(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitFloatRootn(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitFloatSincos(Value input1, Value input2) {
		TornadoInternalError.unimplemented();
		return null;
	}
	@Override
	public Variable emitBitCount(Value arg0) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Variable emitBitScanForward(Value arg0) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Variable emitBitScanReverse(Value arg0) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitIntAbs(Value input) {
		trace("emitIntAbs: abs(%s)",input);
		return new OCLUnary.Intrinsic(OCLUnaryIntrinsic.ABS,LIRKind.derive(input),load(input));
	}

	@Override
	public Value emitFloatSqrt(Value input) {
		trace("emitAbs: sqrt(%s)",input);
		return new OCLUnary.Intrinsic(OCLUnaryIntrinsic.SQRT,LIRKind.derive(input),load(input));
	}
	
	@Override
	public Value emitIntMax(Value input1, Value input2) {
		trace("emitMax: max(%s,%s)",input1,input2);
		return new OCLBinary.Intrinsic(OCLBinaryIntrinsic.INT_MAX,LIRKind.derive(input1,input2),load(input1),load(input2));
	}



	@Override
	public Value emitIntMin(Value input1, Value input2) {
		trace("emitMin: min(%s,%s)",input1,input2);
		return new OCLBinary.Intrinsic(OCLBinaryIntrinsic.INT_MIN,LIRKind.derive(input1,input2),load(input1),load(input2));
	}

	@Override
	public Value emitIntClz(Value value) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitIntPopcount(Value value) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitIntClamp(Value x, Value y, Value z) {
		trace("emitIntClamp: clamp(%s, %s, %s)",x, y, z);
		return new OCLTernary.Intrinsic(OCLTernaryIntrinsic.CLAMP,LIRKind.derive(x,y,z),x,y,z);
	}


	@Override
	public Value emitIntMad24(Value x, Value y, Value z) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitIntMadHi(Value x, Value y, Value z) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitIntMadSat(Value x, Value y, Value z) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitFloatAbs(Value input) {
		trace("emitFloatAbs: abs(%s)",input);
		return new OCLUnary.Intrinsic(OCLUnaryIntrinsic.FLOAT_ABS,LIRKind.derive(input),load(input));
	}

	@Override
	public Value emitFloatFMA(Value x, Value y, Value z) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitFloatMAD(Value x, Value y, Value z) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitFloatRemquo(Value x, Value y, Value z) {
		TornadoInternalError.unimplemented();
		return null;
	}

	public Value emitGeometricDot(LIRKind lirKind, Value x, Value y) {
		trace("emitDot: dot(%s,%s)",x,y);
		return new OCLBinary.Intrinsic(OCLBinaryIntrinsic.DOT,lirKind,load(x),load(y));
	}

	public Value emitGeometricCross(LIRKind lirKind, Value x, Value y) {
		trace("emitCross: cross(%s,%s)",x,y);
		return new OCLBinary.Intrinsic(OCLBinaryIntrinsic.CROSS,lirKind,load(x),load(y));
	}

}
