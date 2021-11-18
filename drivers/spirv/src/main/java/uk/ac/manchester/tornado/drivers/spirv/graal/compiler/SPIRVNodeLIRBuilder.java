/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler.SPIRVBinaryOp;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.VoidStamp;
import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.core.gen.NodeMatchRules;
import org.graalvm.compiler.core.match.ComplexMatchValue;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.lir.ConstantValue;
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
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.LoweredCallTargetNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.ShortCircuitOrNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.FloatEqualsNode;
import org.graalvm.compiler.nodes.calc.FloatLessThanNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.IntegerTestNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.extended.SwitchNode;
import org.graalvm.compiler.nodes.memory.MemoryPhiNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStamp;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStampFactory;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBinary;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVControlFlow;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVDirectCall;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.PragmaUnrollNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.ThreadConfigurationNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.SPIRVVectorValueNode;

/**
 * It traverses the HIR instructions from the Graal CFP and it generates LIR for
 * the SPIR-V backend.
 * <p>
 * SPIR-V Visitor from HIR to LIR.
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
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitDirectCall: callTarget=%s result=%s callState=%s", callTarget, result, callState);
        final SPIRVDirectCall spirvDirectCall = new SPIRVDirectCall(callTarget, result, parameters, callState);
        SPIRVKind spirvKind = ((SPIRVKind) result.getPlatformKind());
        if (isLegal(result) && spirvKind != SPIRVKind.OP_TYPE_VOID) {
            AllocatableValue allocatableValue = gen.asAllocatable(result);
            append(new SPIRVLIRStmt.AssignStmt(allocatableValue, spirvDirectCall));
            setResult(callTarget, allocatableValue);
        } else {
            append(new SPIRVLIRStmt.ExprStmt(spirvDirectCall));
        }
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

    private LIRKind resolveStamp(Stamp stamp) {
        LIRKind lirKind = LIRKind.Illegal;
        if (!stamp.isEmpty()) {
            if (stamp instanceof ObjectStamp) {
                ObjectStamp os = (ObjectStamp) stamp;
                ResolvedJavaType resolvedJavaType = os.javaType(gen.getMetaAccess());
                SPIRVKind spirvKind = SPIRVKind.fromResolvedJavaTypeToVectorKind(resolvedJavaType);
                if (spirvKind != SPIRVKind.ILLEGAL) {
                    // It is a vector type
                    lirKind = LIRKind.value(spirvKind);
                } else {
                    lirKind = gen.getLIRKind(stamp);
                }
            } else {
                lirKind = gen.getLIRKind(stamp);
            }
        }
        return lirKind;
    }

    @Override
    public void emitInvoke(Invoke x) {

        LoweredCallTargetNode callTargetNode = (LoweredCallTargetNode) x.callTarget();

        final Stamp stamp = x.asNode().stamp(NodeView.DEFAULT);
        LIRKind lirKind = resolveStamp(stamp);
        AllocatableValue result = Value.ILLEGAL;

        if (lirKind != LIRKind.Illegal) {
            result = gen.newVariable(lirKind);
        } else if (stamp instanceof VoidStamp) {
            Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "Generating Void Type Variable for function");
            result = gen.newVariable(LIRKind.value(SPIRVKind.OP_TYPE_VOID));
        }

        CallingConvention callingConvention = new CallingConvention(0, result);
        gen.getResult().getFrameMapBuilder().callsMethod(callingConvention);

        Value[] parameters = visitInvokeArguments(callingConvention, callTargetNode.arguments());

        LIRFrameState callState = stateWithExceptionEdge(x, null);

        if (callTargetNode instanceof DirectCallTargetNode) {
            emitDirectCall((DirectCallTargetNode) callTargetNode, result, parameters, AllocatableValue.NONE, callState);
        } else if (callTargetNode instanceof IndirectCallTargetNode) {
            throw new RuntimeException("Not supported");
        } else {
            throw new RuntimeException("Not supported");
        }
        if (isLegal(result)) {
            setResult(x.asNode(), result);
        }
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
            Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "Generating function");
            final Local[] locals = graph.method().getLocalVariableTable().getLocalsAt(0);
            for (final ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                LIRKind lirKind = getGen().getLIRKind(param.stamp(NodeView.DEFAULT));
                Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "Generating LoadParameter : " + locals[param.index()].getName());
                Variable result = getGen().newVariable(lirKind);
                getGen().append(new SPIRVLIRStmt.StoreParameter(result, new SPIRVUnary.LoadParameter(locals[param.index()], lirKind, param.index())));
                setResult(param, result);
            }
        }
    }

    private void doRoot(ValueNode instr) {
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
                        } else if (valueNode instanceof SPIRVVectorValueNode) {
                            // There can be cases in which the result of an
                            // instruction is already set before by other
                            // instructions. case where vector value is used as an input to a phi
                            // node before it is assigned to
                            final SPIRVVectorValueNode vectorNode = (SPIRVVectorValueNode) valueNode;
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
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "µInst emitBranch: " + dominator);
        boolean hasElse = dominator.trueSuccessor() instanceof BeginNode && dominator.falseSuccessor() instanceof BeginNode;

        if (hasElse) {

            // -----------------------------------------------------------------------------
            // FIXME: We could apply an optimization here.
            // If the else-block statement is empty, then we could potentially ignore this
            // block. However, in the branch conditional (previously generated at this point
            // of execution), need to be update with the label indicated here:
            // LabelRef.forSuccessor(gen.getResult().getLIR(), gen.getCurrentBlock(), 0),
            // instead of the current block
            // -----------------------------------------------------------------------------

            append(new SPIRVControlFlow.BranchIf(LabelRef.forSuccessor(gen.getResult().getLIR(), gen.getCurrentBlock(), 0), false, false));
        }
    }

    @Override
    public void visitEndNode(final AbstractEndNode end) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "µInst visitEnd: " + end);

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
                // throw new RuntimeException("SWITCH CASE not supported");
                emitSwitchBreak(end);
            }
        } else if (beginNode instanceof MergeNode) {
            // This case we have a nested if within a loop
            append(new SPIRVControlFlow.BranchIf(LabelRef.forSuccessor(gen.getResult().getLIR(), gen.getCurrentBlock(), 0), false, false));
        }
    }

    private void emitSwitchBreak(AbstractEndNode end) {
        LabelRef lirBlock = getLIRBlock(end.merge());
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitSwitchBreak end: %s with %s", end, lirBlock);
        append(new SPIRVControlFlow.BranchIf(lirBlock, false, false));
    }

    @Override
    protected LIRKind getPhiKind(PhiNode phi) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "µInst phi node: " + phi);
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
            SPIRVKind kind = SPIRVKind.fromResolvedJavaTypeToVectorKind(oStamp.javaType(gen.getMetaAccess()));
            if (kind != SPIRVKind.ILLEGAL && kind.isVector()) {
                stamp = SPIRVStampFactory.getStampFor(kind);
                phi.setStamp(stamp);
            }
        }
        return gen.getLIRKind(stamp);
    }

    private Variable emitLogicNode(final LogicNode node) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitLogicNode: %s", node);
        LIRKind boolLIRKind = LIRKind.value(SPIRVKind.OP_TYPE_BOOL);
        LIRKind intLIRKind = LIRKind.value(SPIRVKind.OP_TYPE_INT_32);
        Variable result = getGen().newVariable(LIRKind.value(SPIRVKind.OP_TYPE_BOOL));
        if (node instanceof IntegerLessThanNode) {
            Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "IntegerLessThanNode: %s", node);
            final IntegerLessThanNode condition = (IntegerLessThanNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            SPIRVBinaryOp op = SPIRVBinaryOp.INTEGER_LESS_THAN;
            append(new SPIRVLIRStmt.IgnorableAssignStmt(result, new SPIRVBinary.Expr(op, boolLIRKind, x, y)));
        } else if (node instanceof IntegerEqualsNode) {
            Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "IntegerEqualsNode: %s", node);
            final IntegerEqualsNode condition = (IntegerEqualsNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            SPIRVBinaryOp op = SPIRVBinaryOp.INTEGER_EQUALS;
            append(new SPIRVLIRStmt.IgnorableAssignStmt(result, new SPIRVBinary.Expr(op, boolLIRKind, x, y)));
        } else if (node instanceof IntegerBelowNode) {
            Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "IntegerBelowNode: %s", node);
            final IntegerBelowNode condition = (IntegerBelowNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            SPIRVBinaryOp op = SPIRVBinaryOp.INTEGER_BELOW;
            append(new SPIRVLIRStmt.IgnorableAssignStmt(result, new SPIRVBinary.Expr(op, boolLIRKind, x, y)));
        } else if (node instanceof IntegerTestNode) {
            Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "IntegerTestNode: %s", node);
            final IntegerTestNode testNode = (IntegerTestNode) node;
            final Value x = operand(testNode.getX());
            final Value y = operand(testNode.getY());
            SPIRVBinaryOp op = SPIRVBinaryOp.BITWISE_AND;
            // BITWISEAND in SPIRV uses an INT as s result value.
            append(new SPIRVLIRStmt.IgnorableAssignStmt(result, new SPIRVBinary.IntegerTestNode(op, intLIRKind, x, y)));
        } else if (node instanceof IsNullNode) {
            Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "IsNullNode: %s", node);
            final IsNullNode isNullNode = (IsNullNode) node;
            final Value x = operand(isNullNode.getValue());
            SPIRVBinaryOp op = SPIRVBinaryOp.INTEGER_EQUALS;
            append(new SPIRVLIRStmt.IgnorableAssignStmt(result, new SPIRVBinary.Expr(op, boolLIRKind, x, new ConstantValue(intLIRKind, PrimitiveConstant.NULL_POINTER))));
        } else if (node instanceof FloatLessThanNode) {
            Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "FloatLessThanNode: %s", node);
            final FloatLessThanNode condition = (FloatLessThanNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            SPIRVBinaryOp op = SPIRVBinaryOp.FLOAT_LESS_THAN;
            append(new SPIRVLIRStmt.IgnorableAssignStmt(result, new SPIRVBinary.Expr(op, boolLIRKind, x, y)));
        } else if (node instanceof FloatEqualsNode) {
            Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "FloatEqualsNode: %s", node);
            final FloatEqualsNode condition = (FloatEqualsNode) node;
            final Value x = operand(condition.getX());
            final Value y = operand(condition.getY());
            SPIRVBinaryOp op = SPIRVBinaryOp.FLOAT_EQUALS;
            append(new SPIRVLIRStmt.IgnorableAssignStmt(result, new SPIRVBinary.Expr(op, boolLIRKind, x, y)));
        } else {
            throw new RuntimeException("Condition Not implemented yet: " + node.getClass());
        }

        setResult(node, result);
        return result;
    }

    @Override
    public void emitIf(final IfNode x) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitIf: %s, condition=%s", x, x.condition().getClass().getName());

        /*
         * test to see if this is an exception check need to implement this properly? or
         * omit!
         */
        final LabelRef falseBranch = getLIRBlock(x.falseSuccessor());
        if (falseBranch.getTargetBlock().isExceptionEntry()) {
            Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitExceptionEntry");
            shouldNotReachHere("exceptions are unimplemented");
        }

        final boolean isLoop = gen.getCurrentBlock().isLoopHeader();
        final boolean isNegated = isLoop && x.trueSuccessor() instanceof LoopExitNode;

        final Variable condition = emitLogicNode(x.condition());
        if (isLoop) {
            getGen().emitConditionalBranch(condition, getLIRBlock(x.trueSuccessor()), getLIRBlock(x.falseSuccessor()));
        } else {
            getGen().emitConditionalBranch(condition, getLIRBlock(x.trueSuccessor()), getLIRBlock(x.falseSuccessor()));
        }
    }

    @Override
    public void visitLoopEnd(final LoopEndNode loopEnd) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "visiting LoopEndNode: %s", loopEnd);
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
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "visitMerge %s", mergeNode);

        boolean loopExitMerge = true;
        for (EndNode end : mergeNode.forwardEnds()) {
            loopExitMerge &= end.predecessor() instanceof LoopExitNode;
        }

        for (MemoryPhiNode phi : mergeNode.memoryPhis()) {
            Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "MEMORY PHI:  %s", phi);

        }

        for (ValuePhiNode phi : mergeNode.valuePhis()) {
            final ValueNode valuePhi = phi.singleValueOrThis();
            Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "PHI NODE %s", valuePhi);
            if (valuePhi != phi) {
                AllocatableValue dest = gen.asAllocatable(operandForPhi(phi));
                Value src = operand(valuePhi);

                if (!dest.equals(src)) {
                    append(new SPIRVLIRStmt.AssignStmtWithLoad(dest, src));
                }
            } else if (loopExitMerge) {
                AllocatableValue dest = gen.asAllocatable(operandForPhi(phi));
                Value src = operand(phi.valueAt(1));
                append(new SPIRVLIRStmt.AssignStmtWithLoad(dest, src));
            }
        }
    }

    @Override
    public void emitSwitch(SwitchNode x) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "visit emitSwitch %s", x);
        // We guarantee there is always a default successor
        assert x.defaultSuccessor() != null;
        LabelRef defaultTarget = getLIRBlock(x.defaultSuccessor());

        int keyCount = x.keyCount();

        if (keyCount == 0) {
            gen.emitJump(defaultTarget);
        } else {
            Variable value = gen.load(operand(x.value()));
            if (keyCount == 1) {
                assert defaultTarget != null;
                unimplemented();
            } else {
                LabelRef[] keyTargets = new LabelRef[keyCount];
                JavaConstant[] keyConstants = new JavaConstant[keyCount];
                double[] keyProbabilities = new double[keyCount];
                for (int i = 0; i < keyCount; i++) {
                    keyTargets[i] = getLIRBlock(x.keySuccessor(i));
                    keyConstants[i] = (JavaConstant) x.keyAt(i);
                    keyProbabilities[i] = x.keyProbability(i);
                }
                gen.emitStrategySwitch(keyConstants, keyProbabilities, keyTargets, defaultTarget, value);
            }
        }
    }

    private void emitLoopBegin(final LoopBeginNode loopBeginNode) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "visiting emitLoopBegin %s", loopBeginNode);

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

        append(new SPIRVControlFlow.LoopBeginLabel(block.toString()));

        label.clearIncomingValues();
    }

    private void emitLoopExit(LoopExitNode node) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "LoopExitNode: %s", node);
        if (gen.getCurrentBlock().getSuccessors().length != 0) {
            LabelRef labelRef = LabelRef.forSuccessor(gen.getResult().getLIR(), gen.getCurrentBlock(), 0);
            append(new SPIRVControlFlow.BranchLoopConditional(labelRef, false, false));
        }
    }

    @Override
    protected void emitNode(final ValueNode node) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, " [emitNote] visiting: %s", node);
        if (node instanceof LoopBeginNode) {
            emitLoopBegin((LoopBeginNode) node);
        } else if (node instanceof LoopExitNode) {
            emitLoopExit((LoopExitNode) node);
        } else if (node instanceof ShortCircuitOrNode) {
            throw new RuntimeException("Unimplemented");
        } else if (node instanceof PragmaUnrollNode || node instanceof ThreadConfigurationNode) {
            // ignore emit-action
        } else {
            super.emitNode(node);
        }
    }

}
