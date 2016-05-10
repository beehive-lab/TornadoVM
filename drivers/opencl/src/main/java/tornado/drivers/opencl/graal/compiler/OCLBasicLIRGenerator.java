package tornado.drivers.opencl.graal.compiler;

import static com.oracle.graal.api.code.ValueUtil.isConstant;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.OCLProviders;
import tornado.drivers.opencl.graal.OpenCLCodeCache;
import tornado.drivers.opencl.graal.asm.OpenCLAssemblerConstants;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLBinaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLBinaryOp;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLMemorySpace;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLNullaryOp;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLTernaryTemplate;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLUnaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLUnaryOp;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLUnaryTemplate;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLControlFlow;
import tornado.drivers.opencl.graal.lir.OCLNullary;
import tornado.drivers.opencl.graal.lir.OCLTernary;
import tornado.drivers.opencl.graal.lir.OCLUnary;
import tornado.drivers.opencl.graal.lir.OCLAddressOps.OCLVectorElement;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.*;
import tornado.drivers.opencl.graal.lir.OCLUnary.MemoryAccess;
import tornado.drivers.opencl.graal.nodes.logic.LogicalAndNode;
import tornado.drivers.opencl.graal.nodes.logic.LogicalEqualsNode;
import tornado.drivers.opencl.graal.nodes.logic.LogicalNotNode;
import tornado.drivers.opencl.graal.nodes.logic.LogicalOrNode;

import com.oracle.graal.api.code.CallingConvention;
import com.oracle.graal.api.code.CodeCacheProvider;
import com.oracle.graal.api.code.ForeignCallLinkage;
import com.oracle.graal.api.code.Register;
import com.oracle.graal.api.code.StackSlotValue;
import com.oracle.graal.api.meta.AllocatableValue;
import com.oracle.graal.api.meta.Constant;
import com.oracle.graal.api.meta.DeoptimizationAction;
import com.oracle.graal.api.meta.DeoptimizationReason;
import com.oracle.graal.api.meta.JavaConstant;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.RawConstant;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.calc.FloatConvert;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRValueUtil;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.SwitchStrategy;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.DefaultLIRKindTool;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGenerator;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodes.calc.FloatEqualsNode;
import com.oracle.graal.nodes.calc.FloatLessThanNode;
import com.oracle.graal.nodes.calc.IntegerBelowNode;
import com.oracle.graal.nodes.calc.IntegerEqualsNode;
import com.oracle.graal.nodes.calc.IntegerLessThanNode;
import com.oracle.graal.nodes.calc.IsNullNode;

import static tornado.graal.compiler.TornadoCodeGenerator.*;

public class OCLBasicLIRGenerator extends LIRGenerator {

	@Override
	public void emitIncomingValues(Value[] params) {
		trace("emitIncomingValues");
		super.emitIncomingValues(params);
	}

	
	 
	@Override
	public Value emitLoadConstant(LIRKind kind, Constant constant) {
		trace("emitLoadConstant: %s",constant);
		return (JavaConstant) constant;
	}

	@Override
	public Variable emitMove(Value input) {
		trace("emitMove: %s", input.toString());
		return super.emitMove(input);
	}

	@Override
	public Variable newVariable(LIRKind lirKind) {
		final Variable var = super.newVariable(lirKind);
		trace("newVariable: %s <- %s (%s)", var.toString(),
				lirKind.toString(), lirKind.getClass().getName());
		
		return var;
	}

	//private OpenCLSpillMoveFactory	moveFactory;
	private final OpenCLCodeCache codeCache;

	public OCLBasicLIRGenerator(
			OCLProviders providers,
			OpenCLCodeCache codeCache,
			CallingConvention cc,
			LIRGenerationResult res) {
		super(new DefaultLIRKindTool(
				codeCache.getTarget().wordKind), providers, cc,
				res);
		this.codeCache = codeCache;
	}

	@Override
	public CodeCacheProvider getCodeCache() {
		return codeCache;
	}



	@Override
	public Variable emitAddress(StackSlotValue arg0) {
		trace("emitAddress: %s", arg0.toString());
		return null;
	}
	
	@Override
	public Value emitAddress(Value base, long displacement, Value index, int scale) {
		trace("emitAddress: base=%s, disp=%d, index=%s, scale=%d\n",base,displacement,index,scale);
    	if(displacement > 0){
    		Variable result = null;
    		final Variable baseValue = newVariable(LIRKind.value(Kind.Object));
    		append(new AssignStmt(baseValue, new OCLBinary.Expr(OCLBinaryOp.ADD, Kind.Object, base, JavaConstant.forLong(displacement))));
    		
    		if(index != Value.ILLEGAL && scale != 1){
    			
    				Value indexValue =  (isConstant(index))  ? index : newVariable(LIRKind.value(Kind.Object));
    				if(!isConstant(index)){
    					append(new AssignStmt((AllocatableValue) indexValue, index));
    				} 
    			
    				final Variable scaleValue = newVariable(LIRKind.value(Kind.Object));
    				append(new AssignStmt(scaleValue, new OCLBinary.Expr(OCLBinaryOp.MUL, Kind.Object, indexValue, new RawConstant(scale))));
    			
    				final Variable scaledAddressValue = newVariable(LIRKind.value(Kind.Object));
    				append(new AssignStmt(scaledAddressValue, new OCLBinary.Expr(OCLBinaryOp.ADD, Kind.Object, baseValue, scaleValue)));
    				result = scaledAddressValue;
    		} else if (index != Value.ILLEGAL ) {
    			Value indexValue =  (isConstant(index))  ? index : newVariable(LIRKind.value(Kind.Object));
				if(!isConstant(index)){
					append(new AssignStmt((AllocatableValue) indexValue, index));
				} 
				
				final Variable indexedAddressValue = newVariable(LIRKind.value(Kind.Object));
				append(new AssignStmt(indexedAddressValue, new OCLBinary.Expr(OCLBinaryOp.ADD, Kind.Object, baseValue, indexValue)));
				result = indexedAddressValue;
    		} else {
    			result = baseValue;	
    		}
			return new OCLUnary.MemoryAccess(OCLMemorySpace.GLOBAL, result);
    	} 
    	
		return new OCLUnary.MemoryAccess(OCLMemorySpace.GLOBAL, base);
	}

	@Override
	public Variable emitArrayEquals(Kind arg0, Value arg1, Value arg2,
			Value arg3) {
		TornadoInternalError.unimplemented();
		return null;
	}

	
	@Override
	public Variable emitByteSwap(Value arg0) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Variable emitCompareAndSwap(Value arg0, Value arg1, Value arg2,
			Value arg3, Value arg4) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public void emitData(AllocatableValue arg0, byte[] arg1) {
		trace("emitData");

	}

	@Override
	public void emitDeoptimize(Value actionAndReason, Value speculation, LIRFrameState state) {
		//Variable node  = (Variable) actionAndReason;
		DeoptimizationReason reason = getMetaAccess().decodeDeoptReason((JavaConstant) actionAndReason);
		DeoptimizationAction action = getMetaAccess().decodeDeoptAction((JavaConstant) actionAndReason);
		
		int debugId = getMetaAccess().decodeDebugId((JavaConstant) actionAndReason);
		
		
		trace("emitDeoptimize: id=%d, reason=%s, action=%s",debugId, reason,action);
		//this.getMetaAccess().decodeDeoptReason((JavaConstant) actionAndReason);
		
		append(new OCLControlFlow.DeoptOp(actionAndReason));
	}

	@Override
	public void emitMembar(int arg0) {
		TornadoInternalError.unimplemented();

	}

	@Override
	public void emitMove(AllocatableValue result, Value value) {
		trace("emitMove: %s (%s) <- %s", result.toString(), result.getKind().getJavaName(), value.toString());
		append(new AssignStmt(result,value));
	}

	@Override
	public void emitNullCheck(Value arg0, LIRFrameState arg1) {
		trace("emitNullCheck");

	}

	@Override
	public void emitReturn(Value input) {
		trace("emitReturn");
		//AllocatableValue operand = Value.ILLEGAL;
		if(input != null){
			//operand = new OCLReturnSlot(input.getLIRKind());
			//emitMove(operand,input);
			ExprStmt stmt = new ExprStmt(new OCLUnary.Expr(OCLUnaryOp.RETURN, input.getKind(),input));
//			System.out.printf("emitReturn: %s\n",stmt);
			append(stmt);
		} else {
			append(new ExprStmt(new OCLNullary.Expr(OCLNullaryOp.RETURN, Kind.Illegal)));
		}
	}

	@Override
	public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state) {
		trace("emitLoad: %s <- %s\nstate:%s", kind, address, state);
		final Variable result = newVariable(kind);
		
		final MemoryAccess memAccess = (MemoryAccess) address;
		memAccess.setKind(result.getKind());
		
		emitMove(result,new OCLUnary.Expr(OCLUnaryTemplate.INDIRECTION, result.getKind(), address));
		return result;
	}



	@Override
	public void emitStore(LIRKind lirKind, Value address, Value input,
			LIRFrameState state) {
		
		trace("emitStore: kind=%s, address=%s, input=%s",lirKind,address,input);
		
		final MemoryAccess memAccess = (MemoryAccess) address;
		memAccess.setKind(lirKind.getPlatformKind());
		
		append(new StoreStmt(new OCLUnary.Expr(OCLUnaryTemplate.INDIRECTION, input.getKind(), address ),input));
	}

	@Override
	public void emitUnwind(Value arg0) {
		TornadoInternalError.unimplemented();

	}

	@Override
	public Value emitAdd(Value input1, Value input2, boolean arg2) {
		trace("emitAdd: %s + %s",input1,input2);
		return emitBinaryAssign(OCLBinaryOp.ADD,LIRKind.derive(input1,input2),loadNonConst(input1),loadNonConst(input2));
	}

	@Override
	public Value emitAnd(Value input1, Value input2) {
		trace("emitAnd: %s = %s & %s",input1,input2);
		return emitBinaryAssign(OCLBinaryOp.BITWISE_AND,LIRKind.derive(input1,input2),loadNonConst(input1),loadNonConst(input2));
	}

	@Override
	public Value emitDiv(Value input1, Value input2, LIRFrameState arg2) {
		trace("emitDiv: %s / %s",input1,input2);
		return emitBinaryAssign(OCLBinaryOp.DIV,LIRKind.derive(input1,input2),loadNonConst(input1),loadNonConst(input2));
	}

	@Override
	public Value emitFloatConvert(FloatConvert arg0, Value arg1) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitMul(Value input1, Value input2, boolean arg2) {
		trace("emitMul: %s * %s",input1,input2);
		return emitBinaryAssign(OCLBinaryOp.MUL,LIRKind.derive(input1,input2),loadNonConst(input1),loadNonConst(input2));
	}

	@Override
	public Value emitMulHigh(Value arg0, Value arg1) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitNegate(Value input1) {
		trace("emitNegate:  - %s",input1);
		return emitUnaryAssign(OCLUnaryOp.NEGATE,LIRKind.derive(input1),loadNonConst(input1));
	}

	@Override
	public Value emitNot(Value input1) {
		// TODO check that this is LOGICAL_NOT and not BITWISE_NOT
		trace("emitNegate:  - %s",input1);
		return emitUnaryAssign(OCLUnaryOp.LOGICAL_NOT,LIRKind.derive(input1),loadNonConst(input1));
	}

	@Override
	public Value emitOr(Value input1, Value input2) {
		trace("emitOr: %s | %s",input1,input2);
		return emitBinaryAssign(OCLBinaryOp.BITWISE_OR,LIRKind.derive(input1,input2),loadNonConst(input1),loadNonConst(input2));
	}

	@Override
	public Value emitReinterpret(LIRKind arg0, Value arg1) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitRem(Value arg0, Value arg1, LIRFrameState arg2) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitShl(Value input1, Value input2) {
		trace("emitShl: %s << %s",input1,input2);
		return emitBinaryAssign(OCLBinaryOp.BITWISE_LEFT_SHIFT,LIRKind.derive(input1,input2),loadNonConst(input1),loadNonConst(input2));
	}

	
	
	@Override
	public Value emitShr(Value input1, Value input2) {
		trace("emitShr: %s >> %s",input1,input2);
		return emitBinaryAssign(OCLBinaryOp.BITWISE_RIGHT_SHIFT,LIRKind.derive(input1,input2),loadNonConst(input1),loadNonConst(input2));
	}

	
	private OCLUnaryOp getSignExtendOp(int toBits){
		switch(toBits){
			case 8:
				return OCLUnaryOp.CAST_TO_BYTE;
			case 16:
				return OCLUnaryOp.CAST_TO_SHORT;
			case 32:
				return OCLUnaryOp.CAST_TO_INT;
			case 64:
				return OCLUnaryOp.CAST_TO_LONG;
			default:
				TornadoInternalError.unimplemented();
		}
		return null;
	}
	
	@Override
	public Value emitNarrow(Value value, int toBits) {
		trace("emitNarrow: %s, %d",value,toBits);
		return emitUnaryAssign(getSignExtendOp(toBits),LIRKind.derive(value),loadNonConst(value));
	}



	@Override
	public Value emitSignExtend(Value value, int fromBits, int toBits) {
		trace("emitSignExtend: %s, %d, %d",value,fromBits,toBits);
		return emitUnaryAssign(getSignExtendOp(toBits),LIRKind.derive(value),loadNonConst(value));
	}

	@Override
	public Value emitSub(Value input1, Value input2, boolean arg2) {
		trace("emitSub: %s - %s",input1,input2);
		return emitBinaryAssign(OCLBinaryOp.SUB,LIRKind.derive(input1,input2),loadNonConst(input1),loadNonConst(input2));
	}

	@Override
	public Value emitUDiv(Value arg0, Value arg1, LIRFrameState arg2) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitUMulHigh(Value arg0, Value arg1) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitURem(Value arg0, Value arg1, LIRFrameState arg2) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Value emitUShr(Value input1, Value input2) {
		trace("emitUShr: %s >>> %s",input1,input2);
		return emitBinaryAssign(OCLBinaryOp.BITWISE_RIGHT_SHIFT,LIRKind.derive(input1,input2),loadNonConst(input1),loadNonConst(input2));
	}

	@Override
	public Value emitXor(Value input1, Value input2) {
		trace("emitXor: %s ^ %s",input1,input2);
		return emitBinaryAssign(OCLBinaryOp.BITWISE_XOR,LIRKind.derive(input1,input2),loadNonConst(input1),loadNonConst(input2));
	}

	@Override
	public Value emitZeroExtend(Value arg0, int arg1, int arg2) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	protected boolean canInlineConstant(JavaConstant arg0) {
		return true;
	}

	@Override
	public void emitCompareBranch(PlatformKind cmpKind, Value left,
			Value right, Condition cond, boolean unorderedIsTrue,
			LabelRef trueLabel, LabelRef falseLabel, double trueLabelProbability) {
		trace("emitCompareBranch");
		
		TornadoInternalError.unimplemented();
		
	}

	
	/**
	 * This method emits the compare instruction, and may reorder the operands.
	 * It returns true if
	 * it did so.
	 *
	 * @param a
	 *            the left operand of the comparison
	 * @param b
	 *            the right operand of the comparison
	 * @return true if the left and right operands were switched, false
	 *         otherwise
	 */
	private boolean emitCompare(PlatformKind cmpKind, Value a, Value b) {
		trace("emitCompareOp");
		Variable left;
		Value right;
		boolean mirrored;
		if (LIRValueUtil.isVariable(b)) {
			left = load(b);
			right = loadNonConst(a);
			mirrored = true;
		} else {
			left = load(a);
			right = loadNonConst(b);
			mirrored = false;
		}
		emitCompareOp(cmpKind, left, right);
		return mirrored;
	}

	protected void emitCompareOp(PlatformKind cmpKind, Variable left,
			Value right) {
		
		TornadoInternalError.unimplemented();
	}

	private OCLBinaryOp getConditionalOp(Condition condition) {
		switch (condition) {
			case AE:
			case GE:
				return OCLBinaryOp.RELATIONAL_GTE;
			case AT:
			case GT:
				return OCLBinaryOp.RELATIONAL_GT;

			case EQ:
				return OCLBinaryOp.RELATIONAL_EQ;

			case BE:
			case LE:
				return OCLBinaryOp.RELATIONAL_LTE;

			case BT:
			case LT:
				return OCLBinaryOp.RELATIONAL_LT;
			case NE:
				return OCLBinaryOp.RELATIONAL_NE;
			default:
				TornadoInternalError.shouldNotReachHere();
				break;

		}
		return null;
	}

	@Override
	public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
		trace("emitConditionalMove?");

		System.out.printf(
			"platform kind: %s\n", cmpKind);

		System.out.printf(
			"left: %s\n", left);
		System.out.printf(
			"cond: %s\n", cond);
		System.out.printf(
			"right: %s\n", right);

		final OCLBinaryOp condOp = getConditionalOp(cond);
		final OCLBinary.Expr condExpr = new OCLBinary.Expr(condOp, LIRKind.value(cmpKind), left,
				right);
		final OCLTernary.Expr selectExpr = new OCLTernary.Expr(OCLTernaryTemplate.SELECT,
				LIRKind.derive(
					trueValue, falseValue), condExpr, trueValue, falseValue);

		// result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(OCLBinaryOp.RELATIONAL_EQ,
		// Kind.Boolean, x, y);

		System.out.printf(
			"true: %s\n", trueValue);
		System.out.printf(
			"false: %s\n", falseValue);

		final Variable variable = newVariable(LIRKind.derive(
			trueValue, falseValue));
		final AssignStmt assignStmt = new AssignStmt(variable, selectExpr);
		append(assignStmt);
		// TornadoInternalError.unimplemented();

		return variable;
	}

	@Override
	protected void emitForeignCallOp(ForeignCallLinkage arg0, Value arg1,
			Value[] arg2, Value[] arg3, LIRFrameState arg4) {
		TornadoInternalError.unimplemented();

	}

	@Override
	public void emitIntegerTestBranch(Value arg0, Value arg1, LabelRef arg2,
			LabelRef arg3, double arg4) {
		trace("emitIntegerTestBranch");

	}

	@Override
	public Variable emitIntegerTestMove(Value arg0, Value arg1, Value arg2,
			Value arg3) {
		trace("emitIntegerTestMove");
		return null;
	}

	@Override
	public void emitJump(LabelRef label) {
		trace("emitJump");
		
	}

	@Override
	public void emitOverflowCheckBranch(LabelRef arg0, LabelRef arg1,
			LIRKind arg2, double arg3) {
		trace("emitOverflowCheckBranch");

	}

	@Override
	public void emitStrategySwitch(JavaConstant[] keyConstants, double[] keyProbabilities,
			LabelRef[] keyTargets, LabelRef defaultTarget, Variable value) {
		trace("emitStrategySwitch: key=%s", value);
		append(new OCLControlFlow.SwitchOp(value,keyConstants, keyTargets, defaultTarget));
		
//		for(int i=0;i<keyTargets.length;i++){
//			final LabelRef target = keyTargets[i];
//			System.out.printf("switch: key=%s, target=%s\n",keyConstants[i],target.getTargetBlock());
//			getResult().getLIR().setLIRforBlock(target.getTargetBlock(), instructions);	
//		}
//		
//		if(defaultTarget != null){
//			final LabelRef target = defaultTarget;
//			final List<LIRInstruction> instructions = new ArrayList<LIRInstruction>();
//			instructions.add(new OCLControlFlow.DefaultCaseOp());
//			getResult().getLIR().setLIRforBlock(target.getTargetBlock(), instructions);	
//			
//		}
	}

	@Override
	protected void emitTableSwitch(int arg0, LabelRef arg1, LabelRef[] arg2,
			Value arg3) {
		TornadoInternalError.unimplemented();

	}

	private class OpenCLSpillMoveFactory implements
			LIRGeneratorTool.SpillMoveFactory {

		@Override
		public LIRInstruction createMove(AllocatableValue result, Value input) {
			return null;
		}

		@Override
		public LIRInstruction createStackMove(AllocatableValue result,
				Value input) {
			return null;
		}

	}

	protected AssignStmt createMove(AllocatableValue dst, Value src) {
		trace("createMove");
		return new AssignStmt(dst, src);
	}

	protected LIRInstruction createStackMove(AllocatableValue result,
			Value input) {
		TornadoInternalError.unimplemented();
		return null;
	}

	protected LIRInstruction createStackMove(AllocatableValue result,
			Value input, Register scratchRegister, StackSlotValue backupSlot) {
		TornadoInternalError.unimplemented();
		return null;
	}

	public SpillMoveFactory getSpillMoveFactory() {
		return new OpenCLSpillMoveFactory();
	}

	public Value emitParameterLoad(Value paramValue, int index) {
		trace("emitParameterLoad");
		
		// assert !(paramValue instanceof Variable) : "Creating a copy of a variable via this method is not supported (and potentially a bug): " + paramValue;
	        Variable result = newVariable(paramValue.getLIRKind());
	        emitParameterLoad(result, index);
	        return result;
	}

	
	private OCLUnaryOp getParameterLoadOp(Kind type){
		switch(type){
			
			case Double:
				return OCLUnaryTemplate.LOAD_PARAM_DOUBLE;
			case Float:
				return OCLUnaryTemplate.LOAD_PARAM_FLOAT;
			case Int:
				return OCLUnaryTemplate.LOAD_PARAM_INT;
			case Long:
				return OCLUnaryTemplate.LOAD_PARAM_LONG;
			case Object:
				return OCLUnaryTemplate.LOAD_PARAM_OBJECT;
			default:
				TornadoInternalError.unimplemented();
				break;
		}
		return null;
	}
	private void emitParameterLoad(AllocatableValue dst, int index) {
		final OCLUnaryOp op = getParameterLoadOp(dst.getKind());
		append(new AssignStmt(dst, new OCLUnary.Expr(op , dst.getLIRKind(), new RawConstant(index + OpenCLAssemblerConstants.STACK_BASE_OFFSET))));
	}

	public Value emitSlotsAddress(Value index) {
		return emitUnaryAssign(OCLUnaryTemplate.SLOT_ADDRESS, Kind.Object, index);
	}

	public Value emitThreadId(Value index) {	
		return emitUnaryAssign(OCLUnaryIntrinsic.GLOBAL_ID,Kind.Int,index);
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
	public Value emitMathAbs(Value arg0) {
		TornadoInternalError.unimplemented();
		return null;
	}



	@Override
	public Value emitMathSqrt(Value arg0) {
		TornadoInternalError.unimplemented();
		return null;
	}


	public Value emitFloatEqualsNode(FloatEqualsNode node, Value x, Value y) {
		return emitBinaryAssign(OCLBinaryIntrinsic.FLOAT_IS_EQUAL,Kind.Boolean,x,y);
	}



	public Value emitFloatLessThanNode(FloatLessThanNode node, Value x, Value y) {
		return emitBinaryAssign(OCLBinaryIntrinsic.FLOAT_IS_LESS,Kind.Boolean,x,y);
	}



	public Value emitIntegerBelowNode(IntegerBelowNode node, Value x, Value y) {
		return emitBinaryAssign(OCLBinaryOp.RELATIONAL_LT,Kind.Boolean,x,y);
	}



	public Value emitIntegerEqualsNode(IntegerEqualsNode node, Value x, Value y) {
		return emitBinaryAssign(OCLBinaryOp.RELATIONAL_EQ,Kind.Boolean,x,y);
	}



	public Value emitIntegerLessThanNode(IntegerLessThanNode node, Value x, Value y) {
		return emitBinaryAssign(OCLBinaryOp.RELATIONAL_LT,Kind.Boolean,x,y);
	}



	public Value emitIsNullNode(IsNullNode node, Value value) {
		return emitBinaryAssign(OCLBinaryOp.RELATIONAL_EQ,Kind.Boolean,value,JavaConstant.NULL_POINTER);
	}



	public Value emitBooleanEqualsNode(LogicalEqualsNode node, Value x, Value y) {
		return emitBinaryAssign(OCLBinaryOp.RELATIONAL_EQ,Kind.Boolean,x,y);
	}



	public Value emitBooleanOrNode(LogicalOrNode node, Value x, Value y) {
		return emitBinaryAssign(OCLBinaryOp.LOGICAL_OR,Kind.Boolean,x,y);
	}



	public Value emitBooleanAndNode(LogicalAndNode node, Value x, Value y) {
		return emitBinaryAssign(OCLBinaryOp.LOGICAL_AND,Kind.Boolean,x,y);
	}



	public Value emitBooleanNotNode(LogicalNotNode node, Value value) {
		return emitUnaryAssign(OCLUnaryOp.LOGICAL_NOT,Kind.Boolean,value);
	}
	
	public Value emitBinaryExpr(OCLBinaryOp op, Kind kind, Value x, Value y){
		return emitBinaryExpr(op,LIRKind.value(kind), x,y);
	}
	
	public Value emitBinaryExpr(OCLBinaryOp op, LIRKind lirKind, Value x, Value y){
		return new OCLBinary.Expr(op, lirKind, x, y);
	}
	
	public Value emitBinaryExpr(OCLBinaryIntrinsic op, Kind kind, Value x, Value y){
		return emitBinaryExpr(op,LIRKind.value(kind), x,y);
	}
	
	public Value emitBinaryExpr(OCLBinaryIntrinsic op, LIRKind lirKind, Value x, Value y){
		return new OCLBinary.Intrinsic(op, lirKind, x, y);
	}
	
	
	public Variable emitBinaryAssign(OCLBinaryOp op, Kind kind, Value x, Value y){
		return emitBinaryAssign(op,LIRKind.value(kind), x,y);
	}
	
	public Variable emitBinaryAssign(OCLBinaryOp op, LIRKind lirKind, Value x, Value y){
		final Variable result = newVariable(lirKind);
		append(new AssignStmt(result, emitBinaryExpr(op, lirKind, x, y)));
		return result;
	}
	
	public Variable emitBinaryAssign(OCLBinaryIntrinsic op, Kind kind, Value x, Value y){
		return emitBinaryAssign(op, LIRKind.value(kind), x, y);
	}
	
	public Variable emitBinaryAssign(OCLBinaryIntrinsic op, LIRKind lirKind, Value x, Value y){
		final Variable result = newVariable(lirKind);
		append(new AssignStmt(result, emitBinaryExpr(op, lirKind, x, y)));
		return result;
	}
	
	public Value emitUnaryExpr(OCLUnaryOp op, Kind kind, Value value){
		return emitUnaryExpr(op,LIRKind.value(kind),value);
	}
	
	public Value emitUnaryExpr(OCLUnaryOp op,  LIRKind lirKind, Value value){
		return new OCLUnary.Expr(op, lirKind, value);
	}
	
	public Value emitUnaryExpr(OCLUnaryIntrinsic op, Kind kind, Value value){
		return emitUnaryExpr(op,LIRKind.value(kind),value);
	}
	
	public Value emitUnaryExpr(OCLUnaryIntrinsic op,  LIRKind lirKind, Value value){
		return new OCLUnary.Intrinsic(op, lirKind, value);
	}
	
	public Variable emitUnaryAssign(OCLUnaryOp op, Kind kind, Value value){
		return emitUnaryAssign(op,LIRKind.value(kind),value);
	}
	
	public Variable emitUnaryAssign(OCLUnaryOp op, LIRKind lirKind, Value value){
		final Variable result = newVariable(lirKind);
		append(new AssignStmt(result, emitUnaryExpr(op, lirKind, value)));
		return result;
	}
	
	public Variable emitUnaryAssign(OCLUnaryIntrinsic op, Kind kind, Value value){
		return emitUnaryAssign(op,LIRKind.value(kind),value);
	}
	
	public Variable emitUnaryAssign(OCLUnaryIntrinsic op, LIRKind lirKind, Value value){
		final Variable result = newVariable(lirKind);
		append(new AssignStmt(result, emitUnaryExpr(op, lirKind, value)));
		return result;
	}

	@Override
	 public Value loadNonConst(Value value) {
	        if (isConstant(value) && canInlineConstant((JavaConstant) value)) {
	            return value;
	        } else if(value instanceof OCLVectorElement){
	        	return value;
	        } else if (value instanceof Variable){
	        	return value;
	        }
	        return emitMove(value);
	    }



	@Override
	public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets,
			LabelRef defaultTarget) {
		TornadoInternalError.unimplemented();
		
	}	
}
