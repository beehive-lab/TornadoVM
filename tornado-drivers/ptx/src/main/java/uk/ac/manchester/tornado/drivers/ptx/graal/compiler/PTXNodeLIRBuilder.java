/*
 * Copyright (c) 2020, 2025, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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

package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryOp;
import static uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind.ILLEGAL;
import static uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt.AssignStmt;
import static uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt.ExprStmt;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jdk.vm.ci.meta.PlatformKind;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.core.match.ComplexMatchValue;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicConstantNode;
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
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FloatEqualsNode;
import org.graalvm.compiler.nodes.calc.FloatLessThanNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.IntegerTestNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.cfg.HIRBlock;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture.PTXBuiltInRegister;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXStampFactory;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXArithmeticTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBinary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXControlFlow;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXDirectCall;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXNullary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXUnary;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector.VectorValueNode;

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

    private LIRKind resolveStamp(Stamp stamp) {
        LIRKind lirKind = LIRKind.Illegal;
        if (!stamp.isEmpty()) {
            if (stamp instanceof ObjectStamp) {
                ObjectStamp os = (ObjectStamp) stamp;
                ResolvedJavaType type = os.javaType(gen.getMetaAccess());
                PTXKind ptxKind = PTXKind.fromResolvedJavaType(type);
                if (ptxKind != PTXKind.ILLEGAL) {
                    lirKind = LIRKind.value(ptxKind);
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
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitInvoke: x=%s ", x);
        LoweredCallTargetNode callTarget = (LoweredCallTargetNode) x.callTarget();

        final Stamp stamp = x.asNode().stamp(NodeView.DEFAULT);
        LIRKind lirKind = resolveStamp(stamp);
        AllocatableValue result = Value.ILLEGAL;

        if (lirKind != LIRKind.Illegal) {
            result = gen.newVariable(lirKind);
        }

        CallingConvention invokeCc = new CallingConvention(0, result);
        gen.getResult().getFrameMapBuilder().callsMethod(invokeCc);

        Value[] parameters = visitInvokeArguments(invokeCc, callTarget.arguments());

        LabelRef exceptionEdge = null;
        LIRFrameState callState = stateWithExceptionEdge(x, exceptionEdge);

        if (callTarget instanceof DirectCallTargetNode) {
            emitDirectCall((DirectCallTargetNode) callTarget, result, parameters, AllocatableValue.NONE, callState);
        } else if (callTarget instanceof IndirectCallTargetNode) {
            emitIndirectCall((IndirectCallTargetNode) callTarget, result, parameters, AllocatableValue.NONE, callState);
        } else {
            shouldNotReachHere();
        }

        if (isLegal(result)) {
            setResult(x.asNode(), result);
        }

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
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitDirectCall: callTarget=%s result=%s callState=%s", callTarget, result, callState);
        if (isLegal(result) && ((PTXKind) result.getPlatformKind()).isVector()) {
            PTXKind resultKind = (PTXKind) result.getPlatformKind();
            Variable returnBuffer = getGen().newVariable(LIRKind.value(PTXKind.B8), true);
            ConstantValue paramSize = new ConstantValue(LIRKind.value(PTXKind.S32), JavaConstant.forInt(resultKind.getSizeInBytes()));
            PTXBinary.Expr declaration = new PTXBinary.Expr(PTXAssembler.PTXBinaryTemplate.NEW_ALIGNED_PARAM_BYTE_ARRAY, LIRKind.value(PTXKind.B8), returnBuffer, paramSize);
            ExprStmt expr = new ExprStmt(declaration);
            append(expr);
            append(new ExprStmt(new PTXDirectCall(callTarget, returnBuffer, parameters)));
            append(new PTXLIRStmt.VectorLoadStmt((Variable) result, new PTXUnary.MemoryAccess(PTXArchitecture.paramSpace, returnBuffer, null)));
            return;
        }

        append(new PTXLIRStmt.ExprStmt(new PTXDirectCall(callTarget, result, parameters)));
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        unimplemented();
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

    @Override
    public void visitSafepointNode(SafepointNode i) {
        unimplemented();
    }

    @Override
    public void visitBreakpointNode(BreakpointNode i) {
        unimplemented();
    }

    protected void emitPrologue(StructuredGraph graph, boolean isKernel) {
        final Local[] locals = graph.method().getLocalVariableTable().getLocalsAt(0);
        if (isKernel) {
            getGen().emitParameterAlloc();
            for (final ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                setResult(param, getGen().getPTXGenTool().emitParameterLoad(locals[param.index()], param));
            }
        } else {
            for (final ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                LIRKind lirKind = getGen().getLIRKind(param.stamp(NodeView.DEFAULT));
                Variable result = getGen().newVariable(lirKind);
                getGen().append(new PTXLIRStmt.AssignStmt(result, new PTXNullary.Parameter(locals[param.index()].getName(), lirKind)));
                setResult(param, result);
            }
        }
    }

    private PTXLIRGenerator getGen() {
        return (PTXLIRGenerator) gen;
    }

    public void doBlock(HIRBlock block, StructuredGraph graph, BlockMap<List<Node>> blockMap, boolean isKernel) {
        OptionValues options = graph.getOptions();
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "%s - block %s", graph.method().getName(), block);
        try (LIRGeneratorTool.BlockScope blockScope = gen.getBlockScope(block)) {

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
                    if (LIRGenerator.Options.TraceLIRGeneratorLevel.getValue(options) >= 3) {
                        TTY.println("LIRGen for " + valueNode);
                    }

                    if (!hasOperand(valueNode)) {
                        if (!peephole(valueNode)) {
                            try {
                                doRoot(valueNode);
                            } catch (final Throwable e) {
                                System.out.println("e: " + e.toString());
                                e.printStackTrace();
                                throw new TornadoBailoutRuntimeException("[Error during LIR generation !]");
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

    @Override
    protected void emitNode(final ValueNode node) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitNode: %s", node);
        if (node instanceof LoopBeginNode) {
            emitLoopBegin((LoopBeginNode) node);
        } else if (node instanceof LoopExitNode) {
            emitLoopExit((LoopExitNode) node);
        } else if (node instanceof ShortCircuitOrNode) {
            emitShortCircuitOrNode((ShortCircuitOrNode) node);
        } else if (node instanceof ConditionalNode conditionalNode) {
            emitConditionalNode(conditionalNode);
        } else {
            super.emitNode(node);
        }
    }

    private void emitLoopExit(LoopExitNode node) {
        if (gen.getCurrentBlock().getSuccessorCount() != 0) {
            append(new PTXControlFlow.LoopBreakOp(LabelRef.forSuccessor(gen.getResult().getLIR(), gen.getCurrentBlock(), 0), false, false));
        }
    }

    private void emitShortCircuitOrNode(ShortCircuitOrNode node) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitShortCircuitOrNode: %s, (X: %s - isNegated: %s) || (Y: %s - isNegated: %s)", node, node.getX(), node.isXNegated(), node.getY(), node
                .isYNegated());
        PTXArithmeticTool tool = (PTXArithmeticTool) gen.getArithmetic();
        final Value x = getProcessedOperand(node.getX(), node.isXNegated());
        final Value y = getProcessedOperand(node.getY(), node.isYNegated());

        Variable result = tool.emitBinaryAssign(PTXBinaryOp.BITWISE_OR, LIRKind.value(PTXKind.PRED), x, y);
        setResult(node, result);
    }

    private Value getProcessedOperand(LogicNode operand, boolean isNegated) {
        return isNegated ? emitNegatedLogicNode(operand) : operandOrConjunction(operand);
    }

    public void emitConditionalNode(ConditionalNode conditional) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitConditionalNode: %s", conditional);
        Value tVal = operand(conditional.trueValue());
        Value fVal = operand(conditional.falseValue());
        setResult(conditional, emitConditional(conditional.condition(), tVal, fVal));
    }

    @Override
    public Variable emitConditional(LogicNode node, Value trueValue, Value falseValue) {
        if (node instanceof IsNullNode isNullNode) {
            LIRKind kind = gen.getLIRKind(isNullNode.getValue().stamp(NodeView.DEFAULT));
            Value nullValue = gen.emitConstant(kind, isNullNode.nullConstant());
            return gen.emitConditionalMove(kind.getPlatformKind(), operand(isNullNode.getValue()), nullValue, Condition.EQ, false, trueValue, falseValue);
        } else if (node instanceof CompareNode compare) {
            PlatformKind kind = gen.getLIRKind(compare.getX().stamp(NodeView.DEFAULT)).getPlatformKind();
            return gen.emitConditionalMove(kind, operand(compare.getX()), operand(compare.getY()), compare.condition().asCondition(), compare.unorderedIsTrue(), trueValue, falseValue);
        } else if (node instanceof LogicConstantNode logicConstant) {
            return gen.emitMove(logicConstant.getValue() ? trueValue : falseValue);
        } else if (node instanceof IntegerTestNode test) {
            return gen.emitIntegerTestMove(operand(test.getX()), operand(test.getY()), trueValue, falseValue);
        } else if (node instanceof ShortCircuitOrNode orNode) {
            Value orValue = operand(orNode);
            if (!(orValue.getValueKind() instanceof LIRKind)) {
                throw new GraalError("Expected LIRKind, but got: " + orValue.getValueKind());
            }
            LIRKind lirKind = (LIRKind) (operand(orNode)).getValueKind();

            return gen.emitConditionalMove(lirKind.getPlatformKind(), //
                    orValue, gen.emitConstant(lirKind, JavaConstant.forBoolean(true)), //
                    Condition.EQ,   //
                    false,          //
                    trueValue,      //
                    falseValue);    //
        } else {
            throw unimplemented(node.toString());
        }
    }

    /**
     * TODO A possible optimization is to perform the loop condition check once
     * before the first iteration and then for the rest of the iterations, have the
     * condition check before the loop back edge. This prevents an "useless" jump
     * when the loop condition no longer holds.
     *
     * Currently, with this method, we will jump back to the loop header and perform
     * the loop initialization on every iteration, instead of jumping to the first
     * block in the loop body.
     */
    private void visitLoopEndImproved(LoopEndNode node) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "visiting loopEndNode: %s", node);
        LoopBeginNode begin = node.loopBegin();
        final List<ValuePhiNode> phis = begin.valuePhis().snapshot();

        for (ValuePhiNode phi : phis) {
            AllocatableValue dest = gen.asAllocatable(operandForPhi(phi));
            Value src = operand(phi.valueAt(node));

            if (!dest.equals(src)) {
                append(new PTXLIRStmt.AssignStmt(dest, src));
            }
        }

        IfNode ifNode = null;
        Iterator<FixedNode> nodesIterator = begin.getBlockNodes().iterator();
        while (nodesIterator.hasNext() && ifNode == null) {
            FixedNode fNode = nodesIterator.next();
            if (fNode instanceof IfNode) {
                ifNode = (IfNode) fNode;
            }
        }

        if (ifNode == null) {
            shouldNotReachHere("Could not find condition");
        }
        final Variable predicate = emitLogicNode(ifNode.condition());
        boolean isNegated = ifNode.trueSuccessor() instanceof LoopExitNode;
        getGen().emitConditionalBranch(getLIRBlock(begin), predicate, isNegated, true);
    }

    @Override
    public void visitLoopEnd(LoopEndNode node) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "visiting loopEndNode: %s", node);
        LoopBeginNode begin = node.loopBegin();
        final List<ValuePhiNode> phis = begin.valuePhis().snapshot();

        for (ValuePhiNode phi : phis) {
            AllocatableValue dest = gen.asAllocatable(operandForPhi(phi));
            Value src = operand(phi.valueAt(node));

            if (!dest.equals(src)) {
                append(new PTXLIRStmt.AssignStmt(dest, src));
            }
        }

        getGen().emitJump(getLIRBlock(begin), true);
    }

    @Override
    public void visitMerge(final AbstractMergeNode mergeNode) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "visitMerge: ", mergeNode);

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
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitIf: %s, condition=%s\n", x, x.condition().getClass().getName());

        /*
         * test to see if this is an exception check need to implement this properly? or
         * omit!
         */
        final LabelRef falseBranch = getLIRBlock(x.falseSuccessor());
        if (falseBranch.getTargetBlock().isExceptionEntry()) {
            Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitExceptionEntry");
            shouldNotReachHere("exceptions are unimplemented");
        }

        final boolean isLoop = gen.getCurrentBlock().isLoopHeader();
        final boolean isNegated = isLoop && x.trueSuccessor() instanceof LoopExitNode;

        final Variable predicate = emitLogicNode(x.condition());

        if (isLoop) {
            getGen().emitConditionalBranch(isNegated ? getLIRBlock(x.trueSuccessor()) : getLIRBlock(x.falseSuccessor()), predicate, !isNegated, false);
        } else {
            getGen().emitConditionalBranch(getLIRBlock(x.falseSuccessor()), predicate, true, false);
        }
    }

    private Value emitNegatedLogicNode(final LogicNode node) {
        Value result = getGen().newVariable(LIRKind.value(PTXKind.PRED));
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitNegatedLogicNode: %s", node);
        LIRKind intLirKind = LIRKind.value(PTXKind.S32);
        LIRKind boolLirKind = LIRKind.value(PTXKind.PRED);

        if (node instanceof FloatEqualsNode floatEqualsNode) {
            final Value x = operand(floatEqualsNode.getX());
            final Value y = operand(floatEqualsNode.getY());
            append(new AssignStmt(result, new PTXBinary.Expr(PTXBinaryOp.SETP_NE, intLirKind, x, y)));
        } else if (node instanceof FloatLessThanNode floatLessThanNode) {
            final Value x = operand(floatLessThanNode.getX());
            final Value y = operand(floatLessThanNode.getY());
            append(new AssignStmt(result, new PTXBinary.Expr(PTXBinaryOp.SETP_GE, intLirKind, x, y)));
        } else if (node instanceof IntegerBelowNode integerBelowNode) {
            final Value x = operand(integerBelowNode.getX());
            final Value y = operand(integerBelowNode.getY());

            Value cond1 = getGen().newVariable(LIRKind.value(PTXKind.PRED));
            append(new AssignStmt(cond1, new PTXBinary.Expr(PTXBinaryOp.SETP_LT, boolLirKind, x, gen.emitConstant(intLirKind, JavaConstant.forInt(0)))));
            Value cond2 = getGen().newVariable(LIRKind.value(PTXKind.PRED));
            append(new AssignStmt(cond2, new PTXBinary.Expr(PTXBinaryOp.SETP_GE, boolLirKind, x, y)));
            append(new AssignStmt(result, new PTXBinary.Expr(PTXBinaryOp.BITWISE_OR, boolLirKind, cond1, cond2)));
        } else if (node instanceof IntegerEqualsNode integerEqualsNode) {
            final Value x = operand(integerEqualsNode.getX());
            final Value y = operand(integerEqualsNode.getY());
            append(new AssignStmt(result, new PTXBinary.Expr(PTXBinaryOp.SETP_NE, boolLirKind, x, y)));
        } else if (node instanceof IntegerLessThanNode integerLessThanNode) {
            final Value x = operand(integerLessThanNode.getX());
            final Value y = operand(integerLessThanNode.getY());
            append(new AssignStmt(result, new PTXBinary.Expr(PTXBinaryOp.SETP_GE, boolLirKind, x, y)));
        } else if (node instanceof IsNullNode isNullNode) {
            final Value value = operand(isNullNode.getValue());
            append(new AssignStmt(result, new PTXBinary.Expr(PTXBinaryOp.SETP_NE, boolLirKind, value, new ConstantValue(intLirKind, PrimitiveConstant.NULL_POINTER))));
        } else if (node instanceof IntegerTestNode testNode) {
            final Value x = operand(testNode.getX());
            final Value y = operand(testNode.getY());
            Value andRes = gen.getArithmetic().emitAnd(x, y);
            append(new AssignStmt(result, new PTXBinary.Expr(PTXBinaryOp.SETP_NE, boolLirKind, andRes, new ConstantValue(boolLirKind, PrimitiveConstant.INT_0))));
        } else if (node instanceof ShortCircuitOrNode shortCircuitOrNode) {
            final Value notX = gen.getArithmetic().emitNot(operand(shortCircuitOrNode.getX()));
            final Value notY = gen.getArithmetic().emitNot(operand(shortCircuitOrNode.getY()));
            append(new AssignStmt(result, new PTXBinary.Expr(PTXBinaryOp.BITWISE_AND, boolLirKind, notX, notY)));
        } else {
            throw new TornadoRuntimeException(String.format("logic node (class=%s)", node.getClass().getName()));
        }

        setResult(node, result);
        return result;
    }

    private Variable emitLogicNode(final LogicNode node) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitLogicNode: %s", node);
        LIRKind intLirKind = LIRKind.value(PTXKind.S32);
        LIRKind boolLirKind = LIRKind.value(PTXKind.PRED);
        Variable pred = getGen().newVariable(LIRKind.value(PTXKind.PRED));
        if (node instanceof FloatEqualsNode floatEqualsNode) {
            final Value x = operand(floatEqualsNode.getX());
            final Value y = operand(floatEqualsNode.getY());
            append(new AssignStmt(pred, new PTXBinary.Expr(PTXBinaryOp.SETP_EQ, intLirKind, x, y)));
        } else if (node instanceof FloatLessThanNode floatLessThanNode) {
            final Value x = operand(floatLessThanNode.getX());
            final Value y = operand(floatLessThanNode.getY());
            append(new AssignStmt(pred, new PTXBinary.Expr(PTXBinaryOp.SETP_LT, intLirKind, x, y)));
        } else if (node instanceof IntegerBelowNode integerBelowNode) {
            final Value x = operand(integerBelowNode.getX());
            final Value y = operand(integerBelowNode.getY());

            Value cond1 = getGen().newVariable(LIRKind.value(PTXKind.PRED));
            append(new AssignStmt(cond1, new PTXBinary.Expr(PTXBinaryOp.SETP_GE, boolLirKind, x, gen.emitConstant(intLirKind, JavaConstant.forInt(0)))));
            Value cond2 = getGen().newVariable(LIRKind.value(PTXKind.PRED));
            append(new AssignStmt(cond2, new PTXBinary.Expr(PTXBinaryOp.SETP_LT, boolLirKind, x, y)));
            append(new AssignStmt(pred, new PTXBinary.Expr(PTXBinaryOp.BITWISE_AND, boolLirKind, cond1, cond2)));
        } else if (node instanceof IntegerEqualsNode integerEqualsNode) {
            final Value x = operand(integerEqualsNode.getX());
            final Value y = operand(integerEqualsNode.getY());
            append(new AssignStmt(pred, new PTXBinary.Expr(PTXBinaryOp.SETP_EQ, intLirKind, x, y)));
        } else if (node instanceof IntegerLessThanNode integerLessThanNode) {
            final Value x = operand(integerLessThanNode.getX());
            final Value y = operand(integerLessThanNode.getY());
            append(new AssignStmt(pred, new PTXBinary.Expr(PTXBinaryOp.SETP_LT, intLirKind, x, y)));
        } else if (node instanceof IsNullNode isNullNode) {
            final Value value = operand(isNullNode.getValue());
            unimplemented("Logic: IsNullNode");
        } else if (node instanceof IntegerTestNode testNode) {
            final Value x = operand(testNode.getX());
            final Value y = operand(testNode.getY());
            Value andRes = gen.getArithmetic().emitAnd(x, y);
            append(new AssignStmt(pred, new PTXBinary.Expr(PTXBinaryOp.SETP_EQ, boolLirKind, andRes, new ConstantValue(boolLirKind, PrimitiveConstant.INT_0))));
        } else if (node instanceof ShortCircuitOrNode shortCircuitOrNode) {
            final Value x = getProcessedOperand(shortCircuitOrNode.getX(), shortCircuitOrNode.isXNegated());
            final Value y = getProcessedOperand(shortCircuitOrNode.getY(), shortCircuitOrNode.isYNegated());
            append(new AssignStmt(pred, new PTXBinary.Expr(PTXBinaryOp.BITWISE_OR, boolLirKind, x, y)));
        } else {
            throw new TornadoRuntimeException(String.format("logic node (class=%s)", node.getClass().getName()));
        }
        setResult(node, pred);
        return pred;
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

    private void emitLoopBegin(final LoopBeginNode loopBeginNode) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "visiting emitLoopBegin %s", loopBeginNode);

        final HIRBlock block = (HIRBlock) gen.getCurrentBlock();
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

        append(new PTXControlFlow.LoopLabel(block.getId()));

        label.clearIncomingValues();
    }

    @Override
    public void visitEndNode(final AbstractEndNode end) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "visitEnd: %s", end);

        if (end instanceof LoopEndNode) {
            return;
        }

        final AbstractMergeNode merge = end.merge();
        for (ValuePhiNode phi : merge.valuePhis()) {
            final ValueNode value = phi.valueAt(end);
            if (!phi.isLoopPhi() && phi.singleValueOrThis() == phi || (value instanceof PhiNode && !((PhiNode) value).isLoopPhi())) {
                final AllocatableValue result = gen.asAllocatable(operandForPhi(phi));
                if (value instanceof FixedArrayNode) {
                    // if a FixedArrayNode instance is assigned to a ValuePhiNode we have a conditional copy-by-reference
                    append(new PTXLIRStmt.LocalMemoryAccessStmt(result, operand(value)));
                } else {
                    append(new PTXLIRStmt.AssignStmt(result, operand(value)));
                }
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
                emitSwitchBreak(end);
            }
        } else if (beginNode instanceof MergeNode) {
            // TODO if we have nested if/else conditions then we outer condition will have a
            // MergeNode as a successor instead of a BeginNode.
            // I am not sure how we could check if the associated BeginNode and IfNode
            // exist, and therefore
            // we always generate branch instructions to the next block in this case.
            // This is to circumvent the case when we fall through the nested if/else
            // statements.
            append(new PTXControlFlow.Branch(LabelRef.forSuccessor(gen.getResult().getLIR(), gen.getCurrentBlock(), 0), false, false));
        }
    }

    private void emitBranch(IfNode dominator) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitBranch dominator: %s", dominator);
        // If we have an if/else statement, we must make sure we branch to the successor
        // block and not `accidentally`
        // execute the whole if/else statement
        boolean hasElse = dominator.trueSuccessor() instanceof BeginNode && dominator.falseSuccessor() instanceof BeginNode;

        if (hasElse) {
            append(new PTXControlFlow.Branch(LabelRef.forSuccessor(gen.getResult().getLIR(), gen.getCurrentBlock(), 0), false, false));
        }
    }

    private void emitSwitchBreak(AbstractEndNode end) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitSwitchBreak end: %s", end);
        append(new PTXControlFlow.Branch(getLIRBlock(end.merge()), false, false));
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

    /**
     * Currently we are breaking the SSA form since we are reusing the registers to
     * which the built-in variables have been assigned previously. We do this
     * because PTX only allows for the built-ins to be used in <b>mov</b> and
     * <b>cvt</b> instructions.
     */
    public Variable getBuiltInAllocation(PTXBuiltInRegister builtIn) {
        if (builtInAllocations.containsKey(builtIn.getName()))
            return builtInAllocations.get(builtIn.getName());

        Variable allocateTo = getGen().newVariable(builtIn.getValueKind());
        append(new AssignStmt(allocateTo, builtIn));
        builtInAllocations.put(builtIn.getName(), allocateTo);
        return allocateTo;
    }
}
