package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.core.match.ComplexMatchValue;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.lir.*;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.*;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.options.OptionValues;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture.PTXBuiltInRegister;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXStampFactory;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.*;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector.VectorValueNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXBinaryOp;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXNullaryOp;
import static uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind.ILLEGAL;
import static uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIRStmt.AssignStmt;
import static uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIRStmt.ExprStmt;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

public class PTXNodeLIRBuilder extends NodeLIRBuilder {
    private final Map<String, Variable> builtInAllocations;
    public PTXNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen, PTXNodeMatchRules ptxNodeMatchRules) {
        super(graph, lirGen, ptxNodeMatchRules);
        builtInAllocations = new HashMap<>();
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        return false;
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps,
                                  LIRFrameState callState) {
        unimplemented();
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps,
                                    LIRFrameState callState) {
        unimplemented();
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        unimplemented();
    }

    @Override
    public void visitBreakpointNode(BreakpointNode i) {
        unimplemented();
    }

    protected void emitPrologue(StructuredGraph graph, boolean isKernel) {
        if (isKernel) {
            getGen().emitParameterAlloc();
            for (final ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                setResult(param, getGen().getPTXGenTool().emitParameterLoad(param, param.index()));
            }
        } else {
            final Local[] locals = graph.method().getLocalVariableTable().getLocalsAt(0);
            int index = 0;
            for (final ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                LIRKind lirKind = getGen().getLIRKind(param.stamp(NodeView.DEFAULT));
                setResult(param, new PTXNullary.Parameter(locals[index].getName(), lirKind));
                index++;
            }
        }
    }

    private PTXLIRGenerator getGen() {
        return (PTXLIRGenerator) gen;
    }

    public void doBlock(Block block, StructuredGraph graph, BlockMap<List<Node>> blockMap, boolean isKernel) {
        OptionValues options = graph.getOptions();
        trace("%s - block %s", graph.method()
                                    .getName(), block);
        try (LIRGeneratorTool.BlockScope blockScope = gen.getBlockScope(block)) {

            if (block == gen.getResult()
                            .getLIR()
                            .getControlFlowGraph()
                            .getStartBlock()) {
                assert block.getPredecessorCount() == 0;
                emitPrologue(graph, isKernel);
            }

            final List<Node> nodes = blockMap.get(block);

            // Allow NodeLIRBuilder subclass to specialise code generation of any
            // interesting groups of instructions
            matchComplexExpressions(block, graph.getLastSchedule());

            for (int i = 0; i < nodes.size(); i++) {
                final Node node = nodes.get(i);
                if (node instanceof ValueNode) {
                    final ValueNode valueNode = (ValueNode) node;
                    // System.out.printf("do block: node=%s\n", valueNode);
                    if (LIRGenerator.Options.TraceLIRGeneratorLevel.getValue(options) >= 3) {
                        TTY.println("LIRGen for " + valueNode);
                    }

                    if (!hasOperand(valueNode)) {
                        if (!peephole(valueNode)) {
                            try {
                                doRoot(valueNode);
                                platformPatch(isKernel);
                            }
                            catch (final Throwable e) {
                                System.out.println("e: " + e.toString());
                                e.printStackTrace();
                                throw new TornadoInternalError(e).addContext(valueNode.toString());
                            }
                        }
                    }
                    else {
                        Value operand = operand(valueNode);
                        if (ComplexMatchValue.INTERIOR_MATCH.equals(operand)) {
                            // Doesn't need to be evaluated
                            getDebugContext().log("interior match for %s", valueNode);
                        }
                        else if (operand instanceof ComplexMatchValue) {
                            getDebugContext().log("complex match for %s", valueNode);
                            final ComplexMatchValue match = (ComplexMatchValue) operand;
                            operand = match.evaluate(this);
                            if (operand != null) {
                                setResult(valueNode, operand);
                            }
                        }
                        else if (valueNode instanceof VectorValueNode) {
                            // There can be cases in which the result of an
                            // instruction is already set before by other
                            // instructions. case where vector value is used as an input to a phi
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
        getDebugContext().log("Visiting %s", instr);
        emitNode(instr);
        if (hasOperand(instr)) {
            getDebugContext().log("Operand for %s = %s", instr, operand(instr));
        }
    }

    private void platformPatch(boolean isKernel) {
        final List<LIRInstruction> insns = getLIRGeneratorTool().getResult().getLIR().getLIRforBlock(gen.getCurrentBlock());
        final int index = insns.size() - 1;
        final LIRInstruction op = insns.get(index);

        if (!isKernel) {
            return;
        }

        if (op instanceof ExprStmt) {
            ExprStmt expr = (ExprStmt) op;
            if (expr.getExpr() instanceof PTXUnary.Expr && ((PTXUnary.Expr) expr.getExpr()).getOpcode().equals(PTXNullaryOp.RETURN)) {
                PTXUnary.Expr returnExpr = (PTXUnary.Expr) expr.getExpr();
                append(new ExprStmt(new PTXNullary.Expr(PTXNullaryOp.RETURN, LIRKind.value(ILLEGAL))));
                insns.remove(index);
                LIRKind lirKind = LIRKind.value(returnExpr.getPlatformKind());
                final AllocatableValue slotAddress = new PTXReturnSlot(lirKind);
                // double check this works properly
                insns.set(index, new AssignStmt(slotAddress, returnExpr.getValue()));
            }
        }

    }

    @Override
    protected void emitNode(final ValueNode node) {
        trace("emitNode: %s", node);
        if (node instanceof LoopBeginNode) {
            emitLoopBegin((LoopBeginNode) node);
        }
        else if (node instanceof ShortCircuitOrNode) {
            unimplemented("Unimplemented ShortCircuitOrNode");
        }
        super.emitNode(node);
    }

    @Override
    public void visitLoopEnd(LoopEndNode node) {
        LoopBeginNode begin  = node.loopBegin();
        final List<ValuePhiNode> phis = begin.valuePhis().snapshot();

        for (ValuePhiNode phi : phis) {
            AllocatableValue dest = gen.asAllocatable(operandForPhi(phi));
            Value src  = operand(phi.valueAt(node));

            if (!dest.equals(src)) {
                append(new PTXLIRStmt.AssignStmt(dest, src));
            }
        }

        // Get first IfNode after loop begin to get the loop exit condition
        IfNode ifNode = null;
        Iterator<FixedNode> nodesIterator = begin.getBlockNodes().iterator();
        while (nodesIterator.hasNext() && ifNode == null) {
            FixedNode fNode = nodesIterator.next();
            if (fNode instanceof IfNode) ifNode = (IfNode) fNode;
        }

        if (ifNode == null) shouldNotReachHere("Could not find condition");
        final Variable predicate = emitLogicNode(ifNode.condition());
        Block loopStart = ((ControlFlowGraph) gen.getResult().getLIR().getControlFlowGraph()).blockFor(begin);
        getGen().emitConditionalBranch(
                LabelRef.forSuccessor(gen.getResult().getLIR(), loopStart, 0),
                predicate,
                false
        );
    }

    @Override
    public void visitMerge(final AbstractMergeNode mergeNode) {
        trace("visitMerge: ", mergeNode);

        boolean loopExitMerge = true;
        for (EndNode end : mergeNode.forwardEnds()) {
            loopExitMerge &= end.predecessor() instanceof LoopExitNode;
        }

        for (ValuePhiNode phi : mergeNode.valuePhis()) {
            final ValueNode value = phi.singleValueOrThis();
            if (value != phi) {
                AllocatableValue dest = gen.asAllocatable(operandForPhi(phi));
                Value src = operand(value);

                if (!dest.equals(src)) {
                    append(new PTXLIRStmt.AssignStmt(dest, src));
                }
            } else if (loopExitMerge) {
                AllocatableValue dest = gen.asAllocatable(operandForPhi(phi));
                Value src = operand(phi.valueAt(1));

                append(new PTXLIRStmt.AssignStmt(dest, src));
            }
        }
    }

    @Override
    public void emitIf(final IfNode x) {
        trace("emitIf: %s, condition=%s\n", x, x.condition().getClass().getName());

        /**
         * test to see if this is an exception check need to implement this properly? or
         * omit!
         */
        final LabelRef falseBranch = getLIRBlock(x.falseSuccessor());
        if (falseBranch.getTargetBlock().isExceptionEntry()) {
            trace("emitExceptionEntry");
            shouldNotReachHere("exceptions are unimplemented");
        }

        final boolean isLoop = gen.getCurrentBlock().isLoopHeader();
        final boolean invertedLoop = isLoop && x.trueSuccessor() instanceof LoopExitNode;

        final Variable predicate = emitLogicNode(x.condition());

        if (isLoop) {
            // Branch away if already
            getGen().emitConditionalBranch(getLIRBlock(x.falseSuccessor()), predicate, !invertedLoop);

        } else {
            getGen().emitConditionalBranch(getLIRBlock(x.falseSuccessor()), predicate, true);
        }
    }

    private Variable emitLogicNode(final LogicNode node) {
        //Value result = null;
        trace("emitLogicNode: %s", node);
        LIRKind intLirKind = LIRKind.value(PTXKind.S32);
        LIRKind boolLirKind = LIRKind.value(PTXKind.PRED);
        Variable pred = getGen().newVariable(LIRKind.value(PTXKind.PRED));
        if (node instanceof IntegerBelowNode) {
            final IntegerBelowNode condition = (IntegerBelowNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            unimplemented("Logic: IntegerBelowNode");
            append(new AssignStmt(pred, new PTXBinary.Expr(PTXBinaryOp.SETP_LE, intLirKind, x, y)));
        } else if (node instanceof IntegerEqualsNode) {
            final IntegerEqualsNode condition = (IntegerEqualsNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            append(new AssignStmt(pred, new PTXBinary.Expr(PTXBinaryOp.SETP_EQ, intLirKind, x, y)));
        } else if (node instanceof IntegerLessThanNode) {
            final IntegerLessThanNode condition = (IntegerLessThanNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            append(new AssignStmt(pred, new PTXBinary.Expr(PTXBinaryOp.SETP_LT, intLirKind, x, y)));
        } else if (node instanceof IsNullNode) {
            final IsNullNode condition = (IsNullNode) node;
            final Value value = operand(condition.getValue());
            unimplemented("Logic: IsNullNode");
            //result = getGen().getArithmetic().genBinaryExpr(PTXBinaryOp.RELATIONAL_EQ, boolLirKind, value, new ConstantValue(intLirKind, PrimitiveConstant.NULL_POINTER));
        } else {
            throw new TornadoRuntimeException(String.format("logic node (class=%s)", node.getClass().getName()));
        }
        setResult(node, pred);
        return pred;
    }

    private void emitLoopBegin(final LoopBeginNode loopBeginNode) {

        trace("visiting emitLoopBegin %s", loopBeginNode);

        final Block block = (Block) gen.getCurrentBlock();
        final LIR lir = getGen().getResult().getLIR();
        final StandardOp.LabelOp label = (StandardOp.LabelOp) lir.getLIRforBlock(block).get(0);

        List<ValuePhiNode> valuePhis = loopBeginNode.valuePhis().snapshot();
        for (ValuePhiNode phi : valuePhis) {
            final Value value = operand(phi.firstValue());
            if (phi.singleBackValueOrThis() == phi && value instanceof Variable) {
                /*
                 * preserve loop-carried dependencies outside of loops
                 */
                setResult(phi, value);
            } else {
                final AllocatableValue result = (AllocatableValue) operandForPhi(phi);
                append(new PTXLIRStmt.AssignStmt(result, value));
            }
        }
        label.clearIncomingValues();
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
            if (!phi.isLoopPhi() && phi.singleValueOrThis() == phi || (value instanceof PhiNode && !((PhiNode) value).isLoopPhi())) {
                final AllocatableValue result = gen.asAllocatable(operandForPhi(phi));
                append(new PTXLIRStmt.AssignStmt(result, operand(value)));
            }
        }

        Iterator<? extends Node> predecessors = end.cfgPredecessors().iterator();
        Node dominator = null;
        BeginNode beginNode = null;
        while (predecessors.hasNext() && dominator == null) {
            Node predecessor = predecessors.next();
            if (predecessor instanceof BeginNode) {
                beginNode = (BeginNode) predecessor;
                dominator = predecessor.predecessor();
            }
        }

        if (dominator != null) {
            if (dominator instanceof IfNode) {
                emitElseBranch((IfNode) dominator, beginNode, end);
            }
        }
    }

    private void emitElseBranch(IfNode dominator, BeginNode beginNode, AbstractEndNode node) {
        boolean isElse = dominator.trueSuccessor().equals(beginNode);
        boolean hasElse = dominator.falseSuccessor() instanceof BeginNode;

        if (isElse && hasElse) {
            append(new PTXControlFlow.Branch(
                    LabelRef.forSuccessor(gen.getResult().getLIR(), gen.getCurrentBlock(), 0),
                    false
            ));
        }
    }

    public Value operandForPhi(ValuePhiNode phi) {
        Value result = operand(phi);
        if (result == null) {
            Variable newOperand = gen.newVariable(getPhiKind(phi));
            setResult(phi, newOperand);
            return newOperand;
        } else {
            return result;
        }
    }

    @Override
    protected LIRKind getPhiKind(PhiNode phi) {
        Stamp stamp = phi.stamp(NodeView.DEFAULT);
        if (stamp.isEmpty()) {
            for (ValueNode n : phi.values()) {
                if (stamp.isEmpty()) {
                    stamp = n.stamp(NodeView.DEFAULT);
                } else {
                    stamp = stamp.meet(n.stamp(NodeView.DEFAULT));
                }
            }
            phi.setStamp(stamp);
        } else if (stamp instanceof ObjectStamp) {
            ObjectStamp oStamp = (ObjectStamp) stamp;
            PTXKind kind = PTXKind.fromResolvedJavaType(oStamp.javaType(gen.getMetaAccess()));
            if (kind != ILLEGAL && kind.isVector()) {
                stamp = PTXStampFactory.getStampFor(kind);
                phi.setStamp(stamp);
            }
        }
        return gen.getLIRKind(stamp);
    }

    public Variable getBuiltInAllocation(PTXBuiltInRegister builtIn) {
        if (builtInAllocations.containsKey(builtIn.getName())) return builtInAllocations.get(builtIn.getName());

        Variable allocateTo = getGen().newVariable(builtIn.getValueKind());
        append(new AssignStmt(allocateTo, builtIn));
        builtInAllocations.put(builtIn.getName(), allocateTo);
        return allocateTo;
    }
}
