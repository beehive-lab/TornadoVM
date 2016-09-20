package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.api.code.CallingConvention;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.AllocatableValue;
import com.oracle.graal.api.meta.JavaConstant;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.Local;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.common.GraalInternalError;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.cfg.BlockMap;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.gen.NodeLIRBuilder;
import com.oracle.graal.compiler.match.ComplexMatchValue;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.graph.GraalGraphInternalError;
import com.oracle.graal.graph.Node;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGenerator;
import com.oracle.graal.lir.gen.LIRGenerator.Options;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.lir.gen.LIRGeneratorTool.BlockScope;
import com.oracle.graal.nodes.AbstractEndNode;
import com.oracle.graal.nodes.AbstractMergeNode;
import com.oracle.graal.nodes.BreakpointNode;
import com.oracle.graal.nodes.DirectCallTargetNode;
import com.oracle.graal.nodes.EndNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.IfNode;
import com.oracle.graal.nodes.IndirectCallTargetNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.LoopBeginNode;
import com.oracle.graal.nodes.LoopEndNode;
import com.oracle.graal.nodes.LoopExitNode;
import com.oracle.graal.nodes.LoweredCallTargetNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.PhiNode;
import com.oracle.graal.nodes.SafepointNode;
import com.oracle.graal.nodes.ShortCircuitOrNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.ValuePhiNode;
import com.oracle.graal.nodes.calc.FloatEqualsNode;
import com.oracle.graal.nodes.calc.FloatLessThanNode;
import com.oracle.graal.nodes.calc.IntegerBelowNode;
import com.oracle.graal.nodes.calc.IntegerEqualsNode;
import com.oracle.graal.nodes.calc.IntegerLessThanNode;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.cfg.Block;
import com.oracle.graal.nodes.extended.SwitchNode;
import java.util.Collection;
import java.util.List;
import tornado.api.Vector;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.OpenCLCodeUtil;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLBinaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLBinaryOp;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLNullaryOp;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLNullaryTemplate;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLUnaryOp;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLControlFlow;
import tornado.drivers.opencl.graal.lir.OCLDirectCall;
import tornado.drivers.opencl.graal.lir.OCLEmitable;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.AssignStmt;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.ExprStmt;
import tornado.drivers.opencl.graal.lir.OCLNullary;
import tornado.drivers.opencl.graal.lir.OCLReturnSlot;
import tornado.drivers.opencl.graal.lir.OCLUnary;
import tornado.drivers.opencl.graal.nodes.logic.LogicalAndNode;
import tornado.drivers.opencl.graal.nodes.logic.LogicalEqualsNode;
import tornado.drivers.opencl.graal.nodes.logic.LogicalNotNode;
import tornado.drivers.opencl.graal.nodes.logic.LogicalOrNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;
import static tornado.graal.compiler.TornadoCodeGenerator.trace;
import tornado.graal.nodes.vector.VectorKind;

public class OCLNodeLIRBuilder extends NodeLIRBuilder {

	@Override
	public void emitInvoke(Invoke x) {
		LoweredCallTargetNode callTarget = (LoweredCallTargetNode) x
				.callTarget();

		final Stamp stamp = x.asNode().stamp();
		LIRKind lirKind;
		AllocatableValue result;
		if (!stamp.isEmpty()) {
			lirKind = gen.getLIRKind(x.asNode().stamp());
			if (x.asNode().stamp().javaType(gen.getMetaAccess())
					.getAnnotation(Vector.class) != null) {
				lirKind = LIRKind.value(VectorKind.fromResolvedJavaType(x
						.asNode().stamp().javaType(gen.getMetaAccess())));
			}
			result = gen.newVariable(lirKind);
		} else {
			lirKind = LIRKind.value(Kind.Void);
			result = Value.ILLEGAL;
		}

		CallingConvention invokeCc = new CallingConvention(0, result);
		// gen.getResult().getFrameMapBuilder().getRegisterConfig().getCallingConvention(callTarget.callType(),
		// x.asNode().stamp().javaType(gen.getMetaAccess()),
		// callTarget.signature(), gen.target(), false);
		gen.getResult().getFrameMapBuilder().callsMethod(invokeCc);

		Value[] parameters = visitInvokeArguments(invokeCc,
				callTarget.arguments());

		LabelRef exceptionEdge = null;
		// if (x instanceof InvokeWithExceptionNode) {
		// exceptionEdge = getLIRBlock(((InvokeWithExceptionNode)
		// x).exceptionEdge());
		// }
		LIRFrameState callState = stateWithExceptionEdge(x, exceptionEdge);

		if (callTarget instanceof DirectCallTargetNode) {
			emitDirectCall((DirectCallTargetNode) callTarget, result,
					parameters, AllocatableValue.NONE, callState);
		} else if (callTarget instanceof IndirectCallTargetNode) {
			emitIndirectCall((IndirectCallTargetNode) callTarget, result,
					parameters, AllocatableValue.NONE, callState);
		} else {
			throw GraalInternalError.shouldNotReachHere();
		}

		if (isLegal(result)) {
			setResult(x.asNode(), result);
		 }

		// if (x instanceof InvokeWithExceptionNode) {
		// gen.emitJump(getLIRBlock(((InvokeWithExceptionNode) x).next()));
		// }
	}

	@Override
	public Value[] visitInvokeArguments(CallingConvention invokeCc,
			Collection<ValueNode> arguments) {
		final Value[] values = new Value[arguments.size()];
		int j = 0;
		for (ValueNode arg : arguments) {
			if (arg != null) {
				Value operand = operand(arg);
				// gen.emitMove(operand, operand(arg));
				values[j] = operand;
				j++;
			} else {
				throw GraalInternalError
						.shouldNotReachHere("I thought we no longer have null entries for two-slot types...");
			}
		}
		return values;
	}

	public static boolean isIllegal(Value value) {
		assert value != null;
		return Value.ILLEGAL.equals(value);
	}

	public static boolean isLegal(Value value) {
		return !isIllegal(value);
	}

	private boolean elseClause;

	// @SuppressWarnings("deprecation")
	public OCLNodeLIRBuilder(final StructuredGraph graph,
			final LIRGeneratorTool gen) {
		super(graph, gen);

		// MatchRuleRegistry.insertMatchStatementSet(getClass(), new
		// OCLNodeLIRBuilder_MatchStatementSet());
	}

	public void doBlock(final Block block, final StructuredGraph graph,
			final BlockMap<List<Node>> blockMap, boolean isKernel) {
		trace("%s - block %s", graph.method().getName(), block);
		// System.out.printf("emit: block=%s\n",block);
		try (BlockScope blockScope = gen.getBlockScope(block)) {

			if (block == gen.getResult().getLIR().getControlFlowGraph()
					.getStartBlock()) {
				assert block.getPredecessorCount() == 0;
				emitPrologue(graph, isKernel);
			}

			final List<Node> nodes = blockMap.get(block);

			// Allow NodeLIRBuilder subclass to specialize code generation of
			// any interesting groups
			// of instructions
			matchComplexExpressions(nodes);

			for (int i = 0; i < nodes.size(); i++) {
				final Node node = nodes.get(i);
				if (node instanceof ValueNode) {
					final ValueNode valueNode = (ValueNode) node;
					// System.out.printf("do block: node=%s\n", valueNode);
					if (Options.TraceLIRGeneratorLevel.getValue() >= 3) {
						TTY.println("LIRGen for " + valueNode);
					}
					Value operand = getOperand(valueNode);
					if (operand == null) {
						if (!peephole(valueNode)) {
							try {

								// emitInputsAndNode(valueNode);

								doRoot(valueNode);

								platformPatch(isKernel);
							} catch (final GraalInternalError e) {
								System.out.println("e: " + e.toString());
								e.printStackTrace();
								throw GraalGraphInternalError
										.transformAndAddContext(e, valueNode);
							} catch (final Throwable e) {
								System.out.println("e: " + e.toString());
								e.printStackTrace();
								throw new GraalGraphInternalError(e)
										.addContext(valueNode);
							}
						}
					} else if (ComplexMatchValue.INTERIOR_MATCH.equals(operand)) {
						// Doesn't need to be evaluated
						Debug.log("interior match for %s", valueNode);
					} else if (operand instanceof ComplexMatchValue) {
						Debug.log("complex match for %s", valueNode);
						final ComplexMatchValue match = (ComplexMatchValue) operand;
						operand = match.evaluate(this);
						if (operand != null) {
							setResult(valueNode, operand);
						}
					} else {
						// There can be cases in which the result of an
						// instruction is already set
						// before by other instructions.

						// case where vector value is used as an input to a phi
						// node
						// before it is assigned to
						if (valueNode instanceof VectorValueNode) {
							final VectorValueNode vectorNode = (VectorValueNode) valueNode;
							vectorNode.generate(this);
						}
					}
				}
			}

			assert LIR.verifyBlock(gen.getResult().getLIR(), block);
		}
	}

	private void platformPatch(boolean isKernel) {
		final List<LIRInstruction> insns = getLIRGeneratorTool().getResult()
				.getLIR().getLIRforBlock(gen.getCurrentBlock());
		final int index = insns.size() - 1;
		final LIRInstruction op = insns.get(index);

		if (!isKernel)
			return;

		if (op instanceof ExprStmt) {
			ExprStmt expr = (ExprStmt) op;
			if (expr.getExpr() instanceof OCLUnary.Expr
					&& ((OCLUnary.Expr) expr.getExpr()).getOpcode().equals(
							OCLUnaryOp.RETURN)) {

				OCLUnary.Expr returnExpr = (OCLUnary.Expr) expr.getExpr();

				append(new ExprStmt(new OCLNullary.Expr(OCLNullaryOp.RETURN,
						Kind.Illegal)));
				insns.remove(index);
				final AllocatableValue slotAddress = new OCLReturnSlot(
						returnExpr.getLIRKind());
				insns.set(index,
						new AssignStmt(slotAddress, returnExpr.getValue()));
			}
		}

	}

	private void emitInputsAndNode(ValueNode value) {

		for (ValueNode input : value.inputs().filter(ValueNode.class)) {
			if (operand(input) == null) {
				// System.out.printf("found un-generated input (%s)...\n",
				// input);
				if (input instanceof ValuePhiNode)
					operandForPhi((ValuePhiNode) input);
				else
					emitInputsAndNode(input);
				// System.out.printf("input is now %s\n", operand(input));
			}
		}

		doRoot(value);
	}

	private OCLEmitable emitNegatedLogicNode(final LogicNode node) {
		Value result = null;
		trace("emitLogicNode: %s", node);
		if (node instanceof LogicalEqualsNode) {
			final LogicalEqualsNode condition = (LogicalEqualsNode) node;
			final Value x = operandOrConjunction(condition.getX());
			final Value y = operandOrConjunction(condition.getY());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryOp.RELATIONAL_NE, Kind.Boolean, x, y);
		} else if (node instanceof FloatEqualsNode) {
			final FloatEqualsNode condition = (FloatEqualsNode) node;
			final Value x = operand(condition.getX());
			final Value y = operand(condition.getY());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryIntrinsic.FLOAT_IS_NOT_EQUAL, Kind.Boolean, x, y);
		} else if (node instanceof FloatLessThanNode) {
			final FloatLessThanNode condition = (FloatLessThanNode) node;
			final Value x = operand(condition.getX());
			final Value y = operand(condition.getY());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryIntrinsic.FLOAT_IS_GREATER_EQUAL, Kind.Boolean, x,
					y);
		} else if (node instanceof IntegerBelowNode) {
			final IntegerBelowNode condition = (IntegerBelowNode) node;
			final Value x = operand(condition.getX());
			final Value y = operand(condition.getY());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryOp.RELATIONAL_GTE, Kind.Boolean, x, y);
		} else if (node instanceof IntegerEqualsNode) {
			final IntegerEqualsNode condition = (IntegerEqualsNode) node;
			final Value x = operand(condition.getX());
			final Value y = operand(condition.getY());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryOp.RELATIONAL_NE, Kind.Boolean, x, y);
		} else if (node instanceof IntegerLessThanNode) {
			final IntegerLessThanNode condition = (IntegerLessThanNode) node;
			final Value x = operand(condition.getX());
			final Value y = operand(condition.getY());
			// if (condition.getX().isConstant())
			// result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
			// OCLBinaryOp.RELATIONAL_GTE, Kind.Boolean, y, x);
			// else
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryOp.RELATIONAL_GTE, Kind.Boolean, x, y);
		} else if (node instanceof IsNullNode) {
			final IsNullNode condition = (IsNullNode) node;
			final Value value = operand(condition.getValue());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryOp.RELATIONAL_NE, Kind.Boolean, value,
					JavaConstant.NULL_POINTER);
		} else {
			TornadoInternalError.unimplemented(String.format(
					"logic node (class=%s)", node.getClass().getName()));
		}

		setResult(node, result);

		return (OCLEmitable) result;
	}

	private OCLEmitable emitLogicNode(final LogicNode node) {
		Value result = null;
		trace("emitLogicNode: %s", node);
		if (node instanceof LogicalEqualsNode) {
			final LogicalEqualsNode condition = (LogicalEqualsNode) node;
			final Value x = operandOrConjunction(condition.getX());
			final Value y = operandOrConjunction(condition.getY());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryOp.RELATIONAL_EQ, Kind.Boolean, x, y);
		} else if (node instanceof LogicalOrNode) {
			final LogicalOrNode condition = (LogicalOrNode) node;
			final Value x = operandOrConjunction(condition.getX());
			final Value y = operandOrConjunction(condition.getY());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryOp.LOGICAL_OR, Kind.Boolean, x, y);
		} else if (node instanceof LogicalAndNode) {
			final LogicalAndNode condition = (LogicalAndNode) node;
			final Value x = operandOrConjunction(condition.getX());
			final Value y = operandOrConjunction(condition.getY());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryOp.LOGICAL_AND, Kind.Boolean, x, y);
		} else if (node instanceof LogicalNotNode) {
			final LogicalNotNode condition = (LogicalNotNode) node;
			final Value value = operandOrConjunction(condition.getValue());
			result = ((OCLBasicLIRGenerator) gen).emitUnaryExpr(
					OCLUnaryOp.LOGICAL_NOT, Kind.Boolean, value);
		} else if (node instanceof FloatEqualsNode) {
			final FloatEqualsNode condition = (FloatEqualsNode) node;
			final Value x = operand(condition.getX());
			final Value y = operand(condition.getY());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryIntrinsic.FLOAT_IS_EQUAL, Kind.Boolean, x, y);
		} else if (node instanceof FloatLessThanNode) {
			final FloatLessThanNode condition = (FloatLessThanNode) node;
			final Value x = operand(condition.getX());
			final Value y = operand(condition.getY());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryIntrinsic.FLOAT_IS_LESS, Kind.Boolean, x, y);
		} else if (node instanceof IntegerBelowNode) {
			final IntegerBelowNode condition = (IntegerBelowNode) node;
			final Value x = operand(condition.getX());
			final Value y = operand(condition.getY());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryOp.RELATIONAL_LT, Kind.Boolean, x, y);
		} else if (node instanceof IntegerEqualsNode) {
			final IntegerEqualsNode condition = (IntegerEqualsNode) node;
			final Value x = operand(condition.getX());
			final Value y = operand(condition.getY());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryOp.RELATIONAL_EQ, Kind.Boolean, x, y);
		} else if (node instanceof IntegerLessThanNode) {
			final IntegerLessThanNode condition = (IntegerLessThanNode) node;
			final Value x = operand(condition.getX());
			final Value y = operand(condition.getY());
//			if (condition.getX().isConstant())
//				result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
//						OCLBinaryOp.RELATIONAL_LT, Kind.Boolean, y, x);
//			else
				result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
						OCLBinaryOp.RELATIONAL_LT, Kind.Boolean, x, y);
		} else if (node instanceof IsNullNode) {
			final IsNullNode condition = (IsNullNode) node;
			final Value value = operand(condition.getValue());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryOp.RELATIONAL_EQ, Kind.Boolean, value,
					JavaConstant.NULL_POINTER);
		} else if (node instanceof ShortCircuitOrNode) {
			final ShortCircuitOrNode condition = (ShortCircuitOrNode) node;
			final Value x = operandOrConjunction(condition.getX());
			final Value y = operandOrConjunction(condition.getY());
			result = ((OCLBasicLIRGenerator) gen).emitBinaryExpr(
					OCLBinaryOp.LOGICAL_OR, Kind.Boolean, x, y);
		} else {
			TornadoInternalError.unimplemented(String.format(
					"logic node (class=%s)", node.getClass().getName()));
		}

		setResult(node, result);

		return (OCLEmitable) result;
	}

	private Value operandOrConjunction(ValueNode value) {
		if (operand(value) != null)
			return operand(value);
		else if (value instanceof LogicNode)
			return emitLogicNode((LogicNode) value);
		else
			TornadoInternalError.shouldNotReachHere();
		return null;
	}

	@Override
	protected void emitDirectCall(final DirectCallTargetNode callTarget,
			final Value result, final Value[] parameters, final Value[] temps,
			final LIRFrameState callState) {

		final OCLDirectCall call = new OCLDirectCall(result.getKind(),
				callTarget, result, parameters, callState);
		if (isLegal(result)) {
			append(new OCLLIRInstruction.AssignStmt(gen.asAllocatable(result),
					call));
			// setResult(callTarget,result);
			// System.out.printf("arg0: %s\n", callTarget);
			// System.out.printf("assign: %s\n",assign);
			// System.out.printf("result: %s\n", result);
		} else {
			append(new OCLLIRInstruction.ExprStmt(call));
			// setResult(callTarget,expr);
		}
	}

	@Override
	protected void emitIndirectCall(final IndirectCallTargetNode arg0,
			final Value arg1, final Value[] arg2, final Value[] arg3,
			final LIRFrameState arg4) {
		TornadoInternalError.unimplemented();

	}

	@Override
	public void emitIf(final IfNode x) {
		trace("emitIf: %s", x);

		/**
		 * test to see if this is an exception check need to implement this
		 * properly? or omit!
		 */
		final LabelRef falseBranch = getLIRBlock(x.falseSuccessor());
		if (falseBranch.getTargetBlock().isExceptionEntry()) {
			trace("emitExceptionEntry");
		}

		final boolean isLoop = gen.getCurrentBlock().isLoopHeader();
		final boolean invertedLoop = isLoop
				&& x.trueSuccessor() instanceof LoopExitNode;

		trace("condition: %s", x.condition());
		final OCLEmitable condition = (invertedLoop) ? emitNegatedLogicNode(x
				.condition()) : emitLogicNode(x.condition());
		trace("condition: %s", condition);

		if (isLoop) {
			append(new OCLControlFlow.LoopConditionOp(condition));
		} else {
			if (elseClause) {
				append(new OCLControlFlow.LinkedConditionalBranchOp(condition));
			} else {
				append(new OCLControlFlow.ConditionalBranchOp(condition));
			}
		}

		// append(new OCLControlFlow.BeginScopeOp());

	}

	private void emitLoopBegin(final LoopBeginNode node) {
		// System.out.printf("emitter: loop begin=%s\n",node);
		trace("emitLoopBegin");
		// trace("result = %s", gen.getResult());
		// for (PhiNode phi : node.phis())
		// trace("phi: %s", phi);

		final Block block = (Block) gen.getCurrentBlock();

		final LIR lir = getGen().getResult().getLIR();

		final LabelOp label = (LabelOp) lir.getLIRforBlock(block).get(0);

		List<ValuePhiNode> valuePhis = node.valuePhis().snapshot();

		// for(final ValuePhiNode phi : valuePhis){
		// assert operandForPhi(phi) != null : "no operand for phi=" + phi;
		// }

		// System.out.printf("here: phi generate...\n");
		for (ValuePhiNode phi : valuePhis) {
			final Value value = operand(phi.firstValue());
			if (!(value instanceof PhiNode)) {
				if (phi.singleBackValue() == PhiNode.MULTIPLE_VALUES
						&& value instanceof Variable) {
					/*
					 * preserve loop-carried dependencies outside of loops
					 */
					// System.out.printf("phi: phi=%s, value=%s\n", phi, value);
					setResult(phi, value);
				} else {
					final AllocatableValue result = (AllocatableValue) operandForPhi(phi);
					append(new OCLLIRInstruction.AssignStmt(result, value));
					// System.out.printf("phi-else: phi=%s, value=%s\n", phi,
					// value);
					// setResult(phi,value);
				}
			}
		}

		// System.out.printf("here: loop init op\n");
		append(new OCLControlFlow.LoopInitOp());

		// for (ValuePhiNode phi : valuePhis) {
		// System.out.printf("emitLoopBegin: phi=%s, operand=%s\n",phi,operandForPhi(phi));
		// final ValueNode value = phi.singleBackValue();
		// final int valueCount = phi.values().distinct().count();
		// if (value == PhiNode.MULTIPLE_VALUES) {
		// if (valueCount == 2) {
		// AllocatableValue result = (AllocatableValue) operandForPhi(phi);
		// Value src = operand(phi.valueAt(1));
		// // System.out.printf("here: phi=%s, value=%s\n", phi,
		// phi.valueAt(1));
		// append(new OCLLIRInstruction.AssignStmt(result, src));
		// } else {
		// System.out.printf("unhandled phi: %s\n",phi);
		// }
		// } else if (!(value instanceof PhiNode)) {
		// // System.out.printf("here: phi=%s, operand=%s\n", phi,
		// operandForPhi(phi));
		// final AllocatableValue result = (AllocatableValue)
		// operandForPhi(phi);
		// // System.out.printf("here: phi=%s, value=%s\n", phi, value);
		// Value src = operand(value);
		// if (src == null) {
		// emitInputsAndNode(value);
		// src = operand(value);
		// }
		//
		// append(new OCLLIRInstruction.AssignStmt(result, src));
		// }
		// }

		// System.out.printf("here: loop post op\n");
		append(new OCLControlFlow.LoopPostOp());

		label.clearIncomingValues();

	}

	@Override
	public void visitLoopEnd(final LoopEndNode loopEnd) {
		trace("visitLoopEnd: %s", loopEnd);

		final LoopBeginNode loopBegin = loopEnd.loopBegin();
		final List<ValuePhiNode> phis = loopBegin.valuePhis().snapshot();

		for (ValuePhiNode phi : phis) {
			final ValueNode value = phi.singleBackValue();
			final int valueCount = phi.values().distinct().count();
			// if (value == PhiNode.MULTIPLE_VALUES && value != phi &&
			// valueCount > 2) {
			// System.out.printf("emitting: phi=%s, value=%s\n",phi,value);
			AllocatableValue dest = gen.asAllocatable(operandForPhi(phi));
			Value src = operand(phi.valueAt(loopEnd));

			if (!dest.equals(src))
				append(new OCLLIRInstruction.AssignStmt(dest, src));
			// }
		}

	}

	@Override
	public void visitMerge(final AbstractMergeNode mergeNode) {
		trace("visitMerge: ", mergeNode);
		// System.out.printf("merge: %s\n",mergeNode);

		boolean loopExitMerge = true;
		for (EndNode end : mergeNode.forwardEnds())
			loopExitMerge &= end.predecessor() instanceof LoopExitNode;

		for (ValuePhiNode phi : mergeNode.valuePhis()) {
			// System.out.printf("visitMerge: merge=%s, phi=%s, operand=%s\n",mergeNode,
			// phi,operandForPhi(phi));
			final ValueNode value = phi.singleValue();
			if (value != PhiNode.MULTIPLE_VALUES) {
				// System.out.printf("emitting: phi=%s, value=%s\n",phi,value);
				AllocatableValue dest = gen.asAllocatable(operandForPhi(phi));
				Value src = operand(value);

				if (!dest.equals(src))
					append(new OCLLIRInstruction.AssignStmt(dest, src));
			} else if (loopExitMerge) {
				AllocatableValue dest = gen.asAllocatable(operandForPhi(phi));
				Value src = operand(phi.valueAt(1));

				append(new OCLLIRInstruction.AssignStmt(dest, src));
			}
		}
	}

	@Override
	protected void emitNode(final ValueNode node) {
		trace("emitNode: %s", node);
		if (node instanceof LoopBeginNode) {
			emitLoopBegin((LoopBeginNode) node);
		} else if (node instanceof LoopExitNode) {
			emitLoopExit((LoopExitNode) node);
		} else if (node instanceof ShortCircuitOrNode) {
			emitShortCircuitOrNode((ShortCircuitOrNode) node);
		} else {
			super.emitNode(node);
		}
	}

	@Override
	public void emitSwitch(SwitchNode x) {
		assert x.defaultSuccessor() != null;
		LabelRef defaultTarget = getLIRBlock(x.defaultSuccessor());
		int keyCount = x.keyCount();
		if (keyCount == 0) {
			gen.emitJump(defaultTarget);
		} else {
			Variable value = gen.load(operand(x.value()));
			if (keyCount == 1) {
				assert defaultTarget != null;
				double probability = x.probability(x.keySuccessor(0));
				PlatformKind kind = gen.getLIRKind(x.value().stamp())
						.getPlatformKind();
				gen.emitCompareBranch(kind, gen.load(operand(x.value())),
						x.keyAt(0), Condition.EQ, false,
						getLIRBlock(x.keySuccessor(0)), defaultTarget,
						probability);
			} else {
				LabelRef[] keyTargets = new LabelRef[keyCount];
				JavaConstant[] keyConstants = new JavaConstant[keyCount];
				double[] keyProbabilities = new double[keyCount];
				for (int i = 0; i < keyCount; i++) {
					keyTargets[i] = getLIRBlock(x.keySuccessor(i));
					keyConstants[i] = x.keyAt(i);
					keyProbabilities[i] = x.keyProbability(i);
					// System.out.printf("switch: key=%s, target=%s\n",keyConstants[i],keyTargets[i]);
				}
				gen.emitStrategySwitch(keyConstants, keyProbabilities,
						keyTargets, defaultTarget, value);
			}
		}
	}

	private void emitShortCircuitOrNode(ShortCircuitOrNode node) {
		final Variable result = gen.newVariable(LIRKind.value(Kind.Boolean));
		final Value x = operandOrConjunction(node.getX());
		final Value y = operandOrConjunction(node.getY());
		append(new AssignStmt(result, new OCLBinary.Expr(
				OCLBinaryOp.LOGICAL_OR, LIRKind.value(Kind.Boolean), x, y)));
		setResult(node, result);

	}

	private void emitLoopExit(LoopExitNode node) {
		if (!node.loopBegin().getBlockNodes()
				.contains((FixedNode) node.predecessor()))
			append(new OCLControlFlow.LoopBreakOp());
	}

	protected void emitPrologue(final StructuredGraph graph, boolean isKernel) {

		if (isKernel) {
			final CallingConvention incomingArguments = OpenCLCodeUtil
					.getCallingConvention(gen.getCodeCache(), Type.JavaCallee,
							graph.method(), false);

			final Value[] params = new Value[incomingArguments
					.getArgumentCount()];
			for (int i = 0; i < params.length; i++) {
				params[i] = LIRGenerator.toStackKind(incomingArguments
						.getArgument(i));
			}

			gen.emitIncomingValues(params);

			for (final ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
				final Value paramValue = params[param.index()];
				// verify/validate param
				setResult(param,
						getGen().emitParameterLoad(paramValue, param.index()));
			}
		} else {
			final Local[] locals = graph.method().getLocalVariableTable()
					.getLocalsAt(0);
			int index = 0;
			for (final ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
				setResult(param, new OCLNullary.Expr(new OCLNullaryTemplate(
						locals[index].getName()), param.getKind()));
				index++;
			}
		}
	}

	private OCLBasicLIRGenerator getGen() {
		return (OCLBasicLIRGenerator) gen;
	}

	@Override
	protected boolean peephole(final ValueNode value) {

		return false;
	}

	public String toOpenCLSymbol(final Condition condition) {
		switch (condition) {
		case AE:
			return ">=";
		case AT:
			return ">";
		case BE:
			return "=<";
		case BT:
			return "<";
		case EQ:
			return "==";
		case GE:
			return ">=";
		case GT:
			return ">";
		case LE:
			return "=<";
		case LT:
			return "<";
		case NE:
			return "!=";
		default:
			return String.format("<invalid op (%s)>", condition.operator);
		}
	}

	@Override
	public void visitBreakpointNode(final BreakpointNode arg0) {
		TornadoInternalError.unimplemented();

	}

	@Override
	public void visitEndNode(final AbstractEndNode end) {
		trace("visitEnd: %s", end);

		if (end instanceof LoopEndNode)
			return;

		final AbstractMergeNode merge = end.merge();
		for (ValuePhiNode phi : merge.valuePhis()) {
			final ValueNode value = phi.valueAt(end);
			if (!phi.isLoopPhi()
					&& phi.singleValue() == PhiNode.MULTIPLE_VALUES
					|| (value instanceof PhiNode && !((PhiNode) value)
							.isLoopPhi())) {
				final AllocatableValue result = gen
						.asAllocatable(operandForPhi(phi));
				append(new OCLLIRInstruction.AssignStmt(result, operand(value)));
			}
		}
	}

	public Value operandForPhi(ValuePhiNode phi) {
		Value result = getOperand(phi);
		if (result == null) {
			// allocate a variable for this phi
			Variable newOperand = gen.newVariable(getPhiKind(phi));
			setResult(phi, newOperand);
			return newOperand;
		} else {
			return result;
		}
	}

	@Override
	protected LIRKind getPhiKind(PhiNode phi) {
		if (phi.valueAt(0) instanceof VectorValueNode)
			return LIRKind.value(((VectorValueNode) phi.valueAt(0))
					.getVectorKind());
		else
			return gen.getLIRKind(phi.stamp());
	}

	@Override
	public void visitSafepointNode(final SafepointNode arg0) {
		TornadoInternalError.unimplemented();

	}

}
