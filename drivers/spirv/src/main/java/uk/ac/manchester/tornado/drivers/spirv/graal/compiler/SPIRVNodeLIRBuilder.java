package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.core.gen.NodeMatchRules;
import org.graalvm.compiler.core.match.ComplexMatchValue;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.BlockScope;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.ShortCircuitOrNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.extended.SwitchNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.PragmaUnrollNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.ThreadConfigurationNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStamp;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStampFactory;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler.SPIRVBinaryOp;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBinary;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVControlFlow;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;

/**
 * It traverses the HIR instructions from the Graal CFP and it generates LIR for
 * the SPIR-V backend.
 *
 * SPIR-V Visitor from HIR to LIR.
 * 
 */
public class SPIRVNodeLIRBuilder extends NodeLIRBuilder {

    private final Map<String, Variable> builtInAllocations;

    public SPIRVNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, NodeMatchRules nodeMatchRules) {
        super(graph, gen, nodeMatchRules);
        this.builtInAllocations = new HashMap<>();
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        return false;
    }

    private boolean isIllegal(Value value) {
        assert value != null;
        return Value.ILLEGAL.equals(value);
    }

    private boolean isLegal(Value value) {
        return !isIllegal(value);
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        SPIRVLogger.trace("emitDirectCall: callTarget=%s result=%s callState=%s", callTarget, result, callState);
        if (isLegal(result) & ((SPIRVKind) result.getPlatformKind()).isVector()) {
            throw new RuntimeException("[SPIRV] CAll with Vector types not supported yet");
        }
        // TODO: Analyze first how is a direct call in SPIRV
        // append(new SPIRVLIRStmt.ExprStmt);
        throw new RuntimeException("Not supported yet");
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void visitBreakpointNode(BreakpointNode i) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void emitInvoke(Invoke x) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public Value[] visitInvokeArguments(CallingConvention invokeCc, Collection<ValueNode> arguments) {
        final Value[] values = new Value[arguments.size()];
        int j = 0;
        for (ValueNode arg : arguments) {
            if (arg != null) {
                Value operand = operand(arg);
                values[j] = operand;
                j++;
            } else {
                throw shouldNotReachHere("I thought we no longer have null entries for two-slot types...");
            }
        }
        return values;
    }

    private SPIRVLIRGenerator getGen() {
        return (SPIRVLIRGenerator) gen;
    }

    protected void emitPrologue(final StructuredGraph graph, boolean isKernel) {
        if (isKernel) {
            for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                setResult(param, getGen().getSPIRVGenTool().emitParameterLoad(param, param.index()));
            }
        } else {
            throw new RuntimeException("Unimplemented - Pending prologue for non main kernels");
        }
    }

    private void doRoot(ValueNode instr) {
        SPIRVLogger.trace("Visiting %s", instr);
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

        if (op instanceof SPIRVLIRStmt.ExprStmt) {
            throw new RuntimeException(">>>>>>>>>>>>>>>>>> MISSING PLATFORM PATCH FOR RETURN STATEMENT WITH VALUE");
        }

    }

    public void doBlock(final Block block, final StructuredGraph graph, final BlockMap<List<Node>> blockMap, boolean isKernel) {
        SPIRVLogger.trace("SPIR-V LIR Builder %s - block %s", graph.method().getName(), block);
        OptionValues options = graph.getOptions();
        try (BlockScope ignored = gen.getBlockScope(block)) {

            if (block == gen.getResult().getLIR().getControlFlowGraph().getStartBlock()) {
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
                                // platformPatch(isKernel);
                            } catch (final Throwable e) {
                                e.printStackTrace();
                                throw new TornadoInternalError(e).addContext(valueNode.toString());
                            }
                        }
                    } else {
                        Value operand = operand(valueNode);
                        if (ComplexMatchValue.INTERIOR_MATCH.equals(operand)) {
                            // Doesn't need to be evaluated
                            getDebugContext().log("interior match for %s", valueNode);
                        } else if (operand instanceof ComplexMatchValue) {
                            getDebugContext().log("complex match for %s", valueNode);
                            final ComplexMatchValue match = (ComplexMatchValue) operand;
                            operand = match.evaluate(this);
                            if (operand != null) {
                                setResult(valueNode, operand);
                            }
                        } else if (valueNode instanceof VectorValueNode) {
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

    private void emitBranch(IfNode dominator) {
        SPIRVLogger.trace("µInst emitBranch: " + dominator);
        boolean hasElse = dominator.trueSuccessor() instanceof BeginNode && dominator.falseSuccessor() instanceof BeginNode;

        if (hasElse) {
            throw new RuntimeException("Else not supported");
        }
    }

    @Override
    public void visitEndNode(final AbstractEndNode end) {
        SPIRVLogger.trace("µInst visitEnd: " + end);

        if (end instanceof LoopEndNode) {
            return;
        }

        final AbstractMergeNode merge = end.merge();
        for (ValuePhiNode phi : merge.valuePhis()) {
            final ValueNode value = phi.valueAt(end);
            if (!phi.isLoopPhi() && phi.singleValueOrThis() == phi || (value instanceof PhiNode && !((PhiNode) value).isLoopPhi())) {
                final AllocatableValue result = gen.asAllocatable(operandForPhi(phi));
                append(new SPIRVLIRStmt.AssignStmtWithLoad(result, operand(value)));
            }
        }

        Node beginNode = end.predecessor();
        while (beginNode != null && beginNode.predecessor() != null && !(beginNode instanceof BeginNode)) {
            beginNode = beginNode.predecessor();
        }
        assert beginNode != null;
        Node dominator = beginNode.predecessor();

        if (dominator != null) {
            if (dominator instanceof IfNode) {
                emitBranch((IfNode) dominator);
            }
            if (dominator instanceof IntegerSwitchNode) {
                throw new RuntimeException("SWITCH CASE not supported");
            }
        } else if (beginNode instanceof MergeNode) {
            throw new RuntimeException("Merge within an endNode not supported");
        }
    }

    @Override
    protected LIRKind getPhiKind(PhiNode phi) {
        SPIRVLogger.trace("µInst phi node: " + phi);
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
            SPIRVStamp oStamp = (SPIRVStamp) stamp;
            SPIRVKind kind = SPIRVKind.fromResolvedJavaType(oStamp.javaType(gen.getMetaAccess()));
            if (kind != SPIRVKind.ILLEGAL && kind.isVector()) {
                stamp = SPIRVStampFactory.getStampFor(kind);
                phi.setStamp(stamp);
            }
        }
        return gen.getLIRKind(stamp);
    }

    private Variable emitLogicNode(final LogicNode node) {
        SPIRVLogger.traceBuildLIR("emitLogicNode: %s", node);
        LIRKind boolLIRKind = LIRKind.value(SPIRVKind.OP_TYPE_BOOL);
        Variable result = getGen().newVariable(LIRKind.value(SPIRVKind.OP_TYPE_BOOL));
        if (node instanceof IntegerLessThanNode) {
            SPIRVLogger.traceBuildLIR("IntegerLessThanNode: %s", node);
            final IntegerLessThanNode condition = (IntegerLessThanNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            SPIRVBinaryOp op = SPIRVBinaryOp.INTEGER_LESS_THAN;
            append(new SPIRVLIRStmt.IgnorableAssignStmt(result, new SPIRVBinary.Expr(op, boolLIRKind, x, y)));
        } else {
            throw new RuntimeException("Condition Not implemented yet: " + node.getClass());
        }
        setResult(node, result);
        return result;
    }

    @Override
    public void emitIf(final IfNode x) {
        SPIRVLogger.traceBuildLIR("emitIf: %s, condition=%s\n", x, x.condition().getClass().getName());

        /*
         * test to see if this is an exception check need to implement this properly? or
         * omit!
         */
        final LabelRef falseBranch = getLIRBlock(x.falseSuccessor());
        if (falseBranch.getTargetBlock().isExceptionEntry()) {
            trace("emitExceptionEntry");
            shouldNotReachHere("exceptions are unimplemented");
        }

        final boolean isLoop = gen.getCurrentBlock().isLoopHeader();
        final boolean isNegated = isLoop && x.trueSuccessor() instanceof LoopExitNode;

        if (isLoop) {
            AbstractBlockBase<?> currentBlock = getGen().getCurrentBlock();
            append(new SPIRVControlFlow.LoopBeginLabel(currentBlock.toString()));
        }

        final Variable condition = emitLogicNode(x.condition());
        if (isLoop) {
            getGen().emitConditionalBranch(condition, getLIRBlock(x.trueSuccessor()), getLIRBlock(x.falseSuccessor()));
        } else {
            throw new RuntimeException("If statements for non-loops not supported yet");
        }

    }

    @Override
    public void visitLoopEnd(final LoopEndNode loopEnd) {
        SPIRVLogger.traceBuildLIR("visiting LoopEndNode: %s", loopEnd);
        final LoopBeginNode loopBegin = loopEnd.loopBegin();
        final List<ValuePhiNode> phis = loopBegin.valuePhis().snapshot();

        for (ValuePhiNode phi : phis) {
            AllocatableValue dest = gen.asAllocatable(operandForPhi(phi));
            Value src = operand(phi.valueAt(loopEnd));

            if (!dest.equals(src)) {
                append(new SPIRVLIRStmt.AssignStmtWithLoad(dest, src));
            }
        }
        getGen().emitJump(getLIRBlock(loopBegin), true);
    }

    @Override
    public void visitMerge(final AbstractMergeNode mergeNode) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void emitSwitch(SwitchNode x) {
        throw new RuntimeException("Not supported");
    }

    private void emitLoopBegin(final LoopBeginNode loopBeginNode) {
        SPIRVLogger.traceBuildLIR("visiting emitLoopBegin %s", loopBeginNode);

        final Block block = (Block) gen.getCurrentBlock();
        final LIR lir = getGen().getResult().getLIR();
        final LabelOp label = (LabelOp) lir.getLIRforBlock(block).get(0);

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
                append(new SPIRVLIRStmt.AssignStmtWithLoad(result, value));
            }
        }

        // append(new SPIRVControlFlow.LoopLabel(block.toString()));
        label.clearIncomingValues();
    }

    private void emitLoopExit(LoopExitNode node) {
        if (gen.getCurrentBlock().getSuccessors().length != 0) {
            throw new RuntimeException("EMIT LOOP BREAK PENDING");
        }
    }

    @Override
    protected void emitNode(final ValueNode node) {
        SPIRVLogger.traceBuildLIR(" [emitNote] visiting: %s", node);
        if (node instanceof LoopBeginNode) {
            emitLoopBegin((LoopBeginNode) node);
        } else if (node instanceof LoopExitNode) {
            emitLoopExit((LoopExitNode) node);
        } else if (node instanceof ShortCircuitOrNode) {
            throw new RuntimeException("Unimplemented");
        } else if (node instanceof PragmaUnrollNode || node instanceof ThreadConfigurationNode) {
            // ignore emit-action
        } else {
            if (node instanceof ConstantNode) {
                // Annotate the constant
            }
            super.emitNode(node);
        }
    }

}
