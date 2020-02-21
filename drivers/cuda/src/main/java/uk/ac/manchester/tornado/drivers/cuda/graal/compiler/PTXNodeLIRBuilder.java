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
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.options.OptionValues;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXStampFactory;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.*;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector.VectorValueNode;

import java.util.List;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.*;
import static uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind.ILLEGAL;
import static uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIRStmt.*;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

public class PTXNodeLIRBuilder extends NodeLIRBuilder {
    public PTXNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen, PTXNodeMatchRules ptxNodeMatchRules) {
        super(graph, lirGen, ptxNodeMatchRules);
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
                    if (true/*LIRGenerator.Options.TraceLIRGeneratorLevel.getValue(options) >= 3*/) {
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
            TTY.println("LoopBeginNode");
            emitLoopBegin((LoopBeginNode) node);
        } else if (node instanceof LoopExitNode) {
            TTY.println("LoopExitNode");
            //emitLoopExit((LoopExitNode) node);
        } else if (node instanceof ShortCircuitOrNode) {
            unimplemented("Unimplemented ShortCircuitOrNode");
        } else {

        }
        super.emitNode(node);
    }

    private void emitLoopBegin(final LoopBeginNode loopBeginNode) {

        trace("visiting emitLoopBegin %s", loopBeginNode);

        final Block block = (Block) gen.getCurrentBlock();
        final Block currentBlockDominator = block.getDominator();
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
        //emitPragmaLoopUnroll(currentBlockDominator);
        append(new PTXControlFlow.LoopInitOp());
        //append(new PTXControlFlow.LoopPostOp());
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
}
