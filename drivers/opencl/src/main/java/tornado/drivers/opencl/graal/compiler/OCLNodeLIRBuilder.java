package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.cfg.BlockMap;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.gen.NodeLIRBuilder;
import com.oracle.graal.compiler.gen.NodeMatchRules;
import com.oracle.graal.compiler.match.ComplexMatchValue;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.graph.Node;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.gen.LIRGenerator.Options;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.lir.gen.LIRGeneratorTool.BlockScope;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.Block;
import com.oracle.graal.nodes.extended.SwitchNode;
import java.util.Collection;
import java.util.List;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryOp;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryTemplate;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.ExprStmt;
import tornado.drivers.opencl.graal.lir.*;
import tornado.drivers.opencl.graal.nodes.logic.LogicalAndNode;
import tornado.drivers.opencl.graal.nodes.logic.LogicalEqualsNode;
import tornado.drivers.opencl.graal.nodes.logic.LogicalNotNode;
import tornado.drivers.opencl.graal.nodes.logic.LogicalOrNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;

import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;
import static tornado.graal.compiler.TornadoCodeGenerator.trace;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;
import static tornado.graal.compiler.TornadoCodeGenerator.trace;

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
//            if (x.asNode().stamp().javaType(gen.getMetaAccess())
//                    .getAnnotation(Vector.class) != null) {
//                lirKind = LIRKind.value(VectorKind.fromResolvedJavaType(x
//                        .asNode().stamp().javaType(gen.getMetaAccess())));
//            }
            result = gen.newVariable(lirKind);
        } else {
            lirKind = LIRKind.Illegal;
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
            shouldNotReachHere();
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
                throw shouldNotReachHere("I thought we no longer have null entries for two-slot types...");
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

    public OCLNodeLIRBuilder(final StructuredGraph graph,
            final LIRGeneratorTool gen, NodeMatchRules nodeMatchRules) {
        super(graph, gen, nodeMatchRules);
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

                    if (!hasOperand(valueNode)) {
                        if (!peephole(valueNode)) {
                            try {
                                doRoot(valueNode);
                                platformPatch(isKernel);
                            } catch (final Throwable e) {
                                System.out.println("e: " + e.toString());
                                e.printStackTrace();
                                throw new TornadoInternalError(e)
                                        .addContext(valueNode.toString());
                            }
                        }
                    } else {
                        Value operand = operand(valueNode);
                        if (ComplexMatchValue.INTERIOR_MATCH.equals(operand)) {
                            // Doesn't need to be evaluated
                            Debug.log("interior match for %s", valueNode);
                        } else if (operand instanceof ComplexMatchValue) {
                            Debug.log("complex match for %s", valueNode);
                            final ComplexMatchValue match = (ComplexMatchValue) operand;
                            operand = match.evaluate(this);
                            if (operand != null) {
                                setResult(valueNode, operand);
                            }
                        } else if (valueNode instanceof VectorValueNode) {
                            // There can be cases in which the result of an
                            // instruction is already set
                            // before by other instructions.
                            // case where vector value is used as an input to a phi
                            // node before it is assigned to
                            final VectorValueNode vectorNode = (VectorValueNode) valueNode;
                            vectorNode.generate(this);
                        }
                    }
                }
            }

            assert LIR.verifyBlock(gen.getResult().getLIR(), block);
        }
    }

    private void doRoot(ValueNode instr) {
        Debug.log("Visiting %s", instr);
        emitNode(instr);
        if (hasOperand(instr)) {
            Debug.log("Operand for %s = %s", instr, operand(instr));
        }
    }

    private void platformPatch(boolean isKernel) {
        final List<LIRInstruction> insns = getLIRGeneratorTool().getResult()
                .getLIR().getLIRforBlock(gen.getCurrentBlock());
        final int index = insns.size() - 1;
        final LIRInstruction op = insns.get(index);

        if (!isKernel) {
            return;
        }

        if (op instanceof ExprStmt) {
            ExprStmt expr = (ExprStmt) op;
            if (expr.getExpr() instanceof OCLUnary.Expr
                    && ((OCLUnary.Expr) expr.getExpr()).getOpcode().equals(
                            OCLUnaryOp.RETURN)) {

                OCLUnary.Expr returnExpr = (OCLUnary.Expr) expr.getExpr();

                append(new ExprStmt(new OCLNullary.Expr(OCLNullaryOp.RETURN,
                        LIRKind.value(OCLKind.ILLEGAL))));
                insns.remove(index);
                LIRKind lirKind = LIRKind.value(returnExpr.getPlatformKind());
                final AllocatableValue slotAddress = new OCLReturnSlot(lirKind);
                // double check this works properly
                insns.set(index, new AssignStmt(slotAddress, returnExpr.getValue()));
            }
        }

    }

    private Value emitNegatedLogicNode(final LogicNode node) {
        Value result = null;
        trace("emitLogicNode: %s", node);
        LIRKind lirKind = LIRKind.value(OCLKind.BOOL);
        if (node instanceof LogicalEqualsNode) {
            final LogicalEqualsNode condition = (LogicalEqualsNode) node;
            final Value x = operandOrConjunction(condition.getX());
            final Value y = operandOrConjunction(condition.getY());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryOp.RELATIONAL_NE, lirKind, x, y);
        } else if (node instanceof FloatEqualsNode) {
            final FloatEqualsNode condition = (FloatEqualsNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryIntrinsic.FLOAT_IS_NOT_EQUAL, lirKind, x, y);
        } else if (node instanceof FloatLessThanNode) {
            final FloatLessThanNode condition = (FloatLessThanNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryIntrinsic.FLOAT_IS_GREATER_EQUAL, lirKind, x,
                    y);
        } else if (node instanceof IntegerBelowNode) {
            final IntegerBelowNode condition = (IntegerBelowNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryOp.RELATIONAL_GTE, lirKind, x, y);
        } else if (node instanceof IntegerEqualsNode) {
            final IntegerEqualsNode condition = (IntegerEqualsNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryOp.RELATIONAL_NE, lirKind, x, y);
        } else if (node instanceof IntegerLessThanNode) {
            final IntegerLessThanNode condition = (IntegerLessThanNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            // if (condition.getX().isConstant())
            // result = getGen().getArithmetic().genBinaryExpr(
            // OCLBinaryOp.RELATIONAL_GTE, Kind.Boolean, y, x);
            // else
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryOp.RELATIONAL_GTE, lirKind, x, y);
        } else if (node instanceof IsNullNode) {
            final IsNullNode condition = (IsNullNode) node;
            final Value value = operand(condition.getValue());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryOp.RELATIONAL_NE, lirKind, value,
                    new ConstantValue(lirKind, PrimitiveConstant.NULL_POINTER));
        } else {
            unimplemented(String.format(
                    "logic node (class=%s)", node.getClass().getName()));
        }

        setResult(node, result);

        return (OCLEmitable) result;
    }

    private OCLEmitable emitLogicNode(final LogicNode node) {
        Value result = null;
        trace("emitLogicNode: %s", node);
        LIRKind lirKind = LIRKind.value(OCLKind.BOOL);
        if (node instanceof LogicalEqualsNode) {
            final LogicalEqualsNode condition = (LogicalEqualsNode) node;
            final Value x = operandOrConjunction(condition.getX());
            final Value y = operandOrConjunction(condition.getY());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryOp.RELATIONAL_EQ, lirKind, x, y);
        } else if (node instanceof LogicalOrNode) {
            final LogicalOrNode condition = (LogicalOrNode) node;
            final Value x = operandOrConjunction(condition.getX());
            final Value y = operandOrConjunction(condition.getY());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryOp.LOGICAL_OR, lirKind, x, y);
        } else if (node instanceof LogicalAndNode) {
            final LogicalAndNode condition = (LogicalAndNode) node;
            final Value x = operandOrConjunction(condition.getX());
            final Value y = operandOrConjunction(condition.getY());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryOp.LOGICAL_AND, lirKind, x, y);
        } else if (node instanceof LogicalNotNode) {
            final LogicalNotNode condition = (LogicalNotNode) node;
            final Value value = operandOrConjunction(condition.getValue());
            result = getGen().getArithmetic().genUnaryExpr(
                    OCLUnaryOp.LOGICAL_NOT, lirKind, value);
        } else if (node instanceof FloatEqualsNode) {
            final FloatEqualsNode condition = (FloatEqualsNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryIntrinsic.FLOAT_IS_EQUAL, lirKind, x, y);
        } else if (node instanceof FloatLessThanNode) {
            final FloatLessThanNode condition = (FloatLessThanNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryIntrinsic.FLOAT_IS_LESS, lirKind, x, y);
        } else if (node instanceof IntegerBelowNode) {
            final IntegerBelowNode condition = (IntegerBelowNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryOp.RELATIONAL_LT, lirKind, x, y);
        } else if (node instanceof IntegerEqualsNode) {
            final IntegerEqualsNode condition = (IntegerEqualsNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryOp.RELATIONAL_EQ, lirKind, x, y);
        } else if (node instanceof IntegerLessThanNode) {
            final IntegerLessThanNode condition = (IntegerLessThanNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryOp.RELATIONAL_LT, lirKind, x, y);
        } else if (node instanceof IsNullNode) {
            final IsNullNode condition = (IsNullNode) node;
            final Value value = operand(condition.getValue());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryOp.RELATIONAL_EQ, lirKind, value,
                    new ConstantValue(lirKind, PrimitiveConstant.NULL_POINTER));
        } else if (node instanceof ShortCircuitOrNode) {
            final ShortCircuitOrNode condition = (ShortCircuitOrNode) node;
            final Value x = operandOrConjunction(condition.getX());
            final Value y = operandOrConjunction(condition.getY());
            result = getGen().getArithmetic().genBinaryExpr(
                    OCLBinaryOp.LOGICAL_OR, lirKind, x, y);
        } else {
            unimplemented(String.format(
                    "logic node (class=%s)", node.getClass().getName()));
        }

        setResult(node, result);

        return (OCLEmitable) result;
    }

    private Value operandOrConjunction(ValueNode value) {
        if (operand(value) != null) {
            return operand(value);
        } else if (value instanceof LogicNode) {
            return emitLogicNode((LogicNode) value);
        } else {
            shouldNotReachHere();
        }
        return null;
    }

    @Override
    protected void emitDirectCall(final DirectCallTargetNode callTarget,
            final Value result, final Value[] parameters, final Value[] temps,
            final LIRFrameState callState) {

        final OCLDirectCall call = new OCLDirectCall(
                callTarget, result, parameters, callState);
        if (isLegal(result)) {
            append(new OCLLIRStmt.AssignStmt(gen.asAllocatable(result),
                    call));
            // setResult(callTarget,result);
            // System.out.printf("arg0: %s\n", callTarget);
            // System.out.printf("assign: %s\n",assign);
            // System.out.printf("result: %s\n", result);
        } else {
            append(new OCLLIRStmt.ExprStmt(call));
            // setResult(callTarget,expr);
        }
    }

    @Override
    protected void emitIndirectCall(final IndirectCallTargetNode arg0,
            final Value arg1, final Value[] arg2, final Value[] arg3,
            final LIRFrameState arg4) {
        unimplemented();

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
        final Value condition = (invertedLoop) ? emitNegatedLogicNode(x
                .condition()) : emitLogicNode(x.condition());
        trace("condition: %s", condition);

        if (isLoop) {
            append(new OCLControlFlow.LoopConditionOp(condition));
        } else if (elseClause) {
            append(new OCLControlFlow.LinkedConditionalBranchOp(condition));
        } else {
            Value operand = operand(x.condition());
            Variable newVariable = getGen().newVariable(LIRKind.value(OCLKind.BOOL));
            append(new AssignStmt(newVariable, operand));
            append(new OCLControlFlow.ConditionalBranchOp(newVariable));
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
            // TODO check what has been changed here
//            if (!(value instanceof PhiNode)) {
            if (phi.singleBackValue() == PhiNode.MULTIPLE_VALUES
                    && value instanceof Variable) {
                /*
                 * preserve loop-carried dependencies outside of loops
                 */
                // System.out.printf("phi: phi=%s, value=%s\n", phi, value);
                setResult(phi, value);
            } else {
                final AllocatableValue result = (AllocatableValue) operandForPhi(phi);
                append(new OCLLIRStmt.AssignStmt(result, value));
                // System.out.printf("phi-else: phi=%s, value=%s\n", phi,
                // value);
                // setResult(phi,value);
            }
//            }
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
        // append(new OCLLIRStmt.AssignStmt(result, src));
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
        // append(new OCLLIRStmt.AssignStmt(result, src));
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
//            final ValueNode value = phi.singleBackValue();
//            final int valueCount = phi.values().count();
            // if (value == PhiNode.MULTIPLE_VALUES && value != phi &&
            // valueCount > 2) {
            // System.out.printf("emitting: phi=%s, value=%s\n",phi,value);
            AllocatableValue dest = gen.asAllocatable(operandForPhi(phi));
            Value src = operand(phi.valueAt(loopEnd));

            if (!dest.equals(src)) {
                append(new OCLLIRStmt.AssignStmt(dest, src));
            }
            // }
        }

    }

    @Override
    public void visitMerge(final AbstractMergeNode mergeNode) {
        trace("visitMerge: ", mergeNode);
        // System.out.printf("merge: %s\n",mergeNode);

        boolean loopExitMerge = true;
        for (EndNode end : mergeNode.forwardEnds()) {
            loopExitMerge &= end.predecessor() instanceof LoopExitNode;
        }

        for (ValuePhiNode phi : mergeNode.valuePhis()) {
            // System.out.printf("visitMerge: merge=%s, phi=%s, operand=%s\n",mergeNode,
            // phi,operandForPhi(phi));
            final ValueNode value = phi.singleValue();
            if (value != PhiNode.MULTIPLE_VALUES) {
                // System.out.printf("emitting: phi=%s, value=%s\n",phi,value);
                AllocatableValue dest = gen.asAllocatable(operandForPhi(phi));
                Value src = operand(value);

                if (!dest.equals(src)) {
                    append(new OCLLIRStmt.AssignStmt(dest, src));
                }
            } else if (loopExitMerge) {
                AllocatableValue dest = gen.asAllocatable(operandForPhi(phi));
                Value src = operand(phi.valueAt(1));

                append(new OCLLIRStmt.AssignStmt(dest, src));
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
        unimplemented();
//        assert x.defaultSuccessor() != null;
//        LabelRef defaultTarget = getLIRBlock(x.defaultSuccessor());
//        int keyCount = x.keyCount();
//        if (keyCount == 0) {
//            gen.emitJump(defaultTarget);
//        } else {
//            Variable value = gen.load(operand(x.value()));
//            if (keyCount == 1) {
//                assert defaultTarget != null;
//                double probability = x.probability(x.keySuccessor(0));
//                PlatformKind kind = gen.getLIRKind(x.value().stamp())
//                        .getPlatformKind();
//                gen.emitCompareBranch(kind, gen.load(operand(x.value())),
//                        x.keyAt(0), Condition.EQ, false,
//                        getLIRBlock(x.keySuccessor(0)), defaultTarget,
//                        probability);
//            } else {
//                LabelRef[] keyTargets = new LabelRef[keyCount];
//                JavaConstant[] keyConstants = new JavaConstant[keyCount];
//                double[] keyProbabilities = new double[keyCount];
//                for (int i = 0; i < keyCount; i++) {
//                    keyTargets[i] = getLIRBlock(x.keySuccessor(i));
//                    keyConstants[i] = x.keyAt(i);
//                    keyProbabilities[i] = x.keyProbability(i);
//                    // System.out.printf("switch: key=%s, target=%s\n",keyConstants[i],keyTargets[i]);
//                }
//                gen.emitStrategySwitch(keyConstants, keyProbabilities,
//                        keyTargets, defaultTarget, value);
//            }
//        }
    }

    private void emitShortCircuitOrNode(ShortCircuitOrNode node) {
        LIRKind lirKind = LIRKind.value(OCLKind.BOOL);
        final Variable result = gen.newVariable(lirKind);
        final Value x = operandOrConjunction(node.getX());
        final Value y = operandOrConjunction(node.getY());
        append(new AssignStmt(result, new OCLBinary.Expr(
                OCLBinaryOp.LOGICAL_OR, lirKind, x, y)));
        setResult(node, result);

    }

    private void emitLoopExit(LoopExitNode node) {
        if (!node.loopBegin().getBlockNodes()
                .contains((FixedNode) node.predecessor())) {
            append(new OCLControlFlow.LoopBreakOp());
        }
    }

    protected void emitPrologue(final StructuredGraph graph, boolean isKernel) {

        if (isKernel) {
//            final CallingConvention incomingArguments = OpenCLCodeUtil
//                    .getCallingConvention(gen.getCodeCache(), Type.JavaCallee,
//                            graph.method(), false);

//            final Value[] params = new Value[incomingArguments
//                    .getArgumentCount()];
//            for (int i = 0; i < params.length; i++) {
//                params[i] = LIRGenerator.toStackKind(incomingArguments
//                        .getArgument(i));
//            }
//            gen.emitIncomingValues(params);
            for (final ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
//                final Value paramValue = params[param.index()];
                // verify/validate param
                setResult(param, getGen().getOCLGenTool().emitParameterLoad(param, param.index()));
            }
        } else {
            final Local[] locals = graph.method().getLocalVariableTable()
                    .getLocalsAt(0);
            int index = 0;
            for (final ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                setResult(param, new OCLNullary.Expr(new OCLNullaryTemplate(
                        locals[index].getName()), getGen().getLIRKind(param.stamp())));
                index++;
            }
        }
    }

    private OCLLIRGenerator getGen() {
        return (OCLLIRGenerator) gen;
    }

    private OCLBuiltinTool getBuiltinTool() {
        return getGen().getOCLBuiltinTool();
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
        unimplemented();
    }

    @Override
    public void visitEndNode(final AbstractEndNode end) {
        trace("visitEnd: %s", end);

        if (end instanceof LoopEndNode) {
            return;
        }

        final AbstractMergeNode merge = end.merge();
        for (ValuePhiNode phi : merge.valuePhis()) {
            final ValueNode value = phi.valueAt(end);
            if (!phi.isLoopPhi()
                    && phi.singleValue() == PhiNode.MULTIPLE_VALUES
                    || (value instanceof PhiNode && !((PhiNode) value)
                    .isLoopPhi())) {
                final AllocatableValue result = gen
                        .asAllocatable(operandForPhi(phi));
                append(new OCLLIRStmt.AssignStmt(result, operand(value)));
            }
        }
    }

    public Value operandForPhi(ValuePhiNode phi) {
        Value result = operand(phi);
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
//		if (phi.valueAt(0) instanceof VectorValueNode)
//			return LIRKind.value(((VectorValueNode) phi.valueAt(0))
//					.getVectorKind());
//		else
        return gen.getLIRKind(phi.stamp());
    }

    @Override
    public void visitSafepointNode(final SafepointNode arg0) {
        unimplemented();

    }

}
