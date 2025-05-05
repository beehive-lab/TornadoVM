/*
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl.graal.compiler;

import static uk.ac.manchester.tornado.runtime.graal.TornadoLIRGenerator.trace;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.stream.IntStream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.spi.CodeGenProviders;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.InstructionValueProcedure;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.cfg.HIRBlock;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLControlFlow;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLControlFlow.LoopConditionOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLControlFlow.LoopInitOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLControlFlow.LoopPostOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class OCLCompilationResultBuilder extends CompilationResultBuilder {

    private final Set<ResolvedJavaMethod> nonInlinedMethods;
    protected LIR lir;
    HashSet<HIRBlock> rescheduledBasicBlocks;
    private int currentBlockIndex;
    private boolean isKernel;
    private int loops = 0;
    private boolean isParallel;
    private TaskDataContext metaData;
    private long[] localGrid;
    private OCLDeviceContextInterface deviceContext;

    public OCLCompilationResultBuilder(CodeGenProviders providers, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext, OptionValues options, DebugContext debug,
            CompilationResult compilationResult, LIR lir) {
        super(providers, frameMap, asm, dataBuilder, frameContext, options, debug, compilationResult, Register.None, EconomicMap.create(Equivalence.DEFAULT), NO_VERIFIERS, lir);
        nonInlinedMethods = new HashSet<ResolvedJavaMethod>();
    }

    private static boolean isMergeHIRBlock(HIRBlock block) {
        return block.getBeginNode() instanceof AbstractMergeNode;
    }

    /**
     * Checks if the {@link OCLNodeLIRBuilder(LoopBeginNode)} has been called right
     * before {@link OCLNodeLIRBuilder#emitIf}. In other words, that there is no
     * data flow/control flow between the {@link LoopBeginNode} and the
     * corresponding {@link IfNode} loop condition.
     *
     * @return true if the {@param loopCondIndex} is right after the LIR
     *     instructions of a loop header ({@param loopPostOpIndex} and
     *     {@param loopInitOpIndex}).
     */
    private static boolean isLoopConditionRightAfterHeader(int loopCondIndex, int loopPostOpIndex, int loopInitOpIndex) {
        return (loopCondIndex - 1 == loopPostOpIndex) && (loopCondIndex - 2 == loopInitOpIndex);
    }

    /**
     * Checks if there are any LIR instructions between the loop condition and the
     * {@link LoopInitOp} and {@link LoopPostOp}. If there are no instructions, it
     * is possible to move the loop condition to the loop header.
     *
     * @return true if there are no instructions.
     */
    private static boolean shouldFormatLoopHeader(List<LIRInstruction> instructions) {
        int loopInitOpIndex = -1, loopPostOpIndex = -1, loopConditionOpIndex = -1;

        for (int index = 0, instructionsSize = instructions.size(); index < instructionsSize; index++) {
            LIRInstruction instruction = instructions.get(index);
            if (instruction instanceof LoopInitOp) {
                loopInitOpIndex = index;
            }
            if (instruction instanceof LoopPostOp) {
                loopPostOpIndex = index;
            }
            if (instruction instanceof LoopConditionOp) {
                loopConditionOpIndex = index;
            }
        }

        return isLoopConditionRightAfterHeader(loopConditionOpIndex, loopPostOpIndex, loopInitOpIndex);
    }

    private static void formatLoopHeader(List<LIRInstruction> instructions) {
        int index = instructions.size() - 1;

        LIRInstruction condition = instructions.get(index);
        while (!(condition instanceof LoopConditionOp)) {
            index--;
            condition = instructions.get(index);
        }
        ((LoopConditionOp) condition).setGenerateIfBreakStatement(false);

        instructions.remove(index);

        final Set<Value> dependencies = new HashSet<>();
        DepFinder df = new DepFinder(dependencies);
        condition.forEachInput(df);

        index--;
        final List<LIRInstruction> moved = new ArrayList<>();
        LIRInstruction insn = instructions.get(index);
        while (!(insn instanceof LoopPostOp)) {
            if (insn instanceof AssignStmt) {
                AssignStmt assign = (AssignStmt) insn;
                if (assign.getResult() instanceof Variable) {
                    Variable var = (Variable) assign.getResult();
                    if (dependencies.contains(var)) {
                        moved.add(instructions.remove(index));
                    }
                }
            }
            index--;
            insn = instructions.get(index);
        }

        LIRInstruction loopInit = instructions.get(instructions.size() - 1);
        while (!(loopInit instanceof LoopInitOp)) {
            index--;
            loopInit = instructions.get(index);
        }

        instructions.add(index + 1, condition);
        instructions.addAll(index - 1, moved);
    }

    private static boolean isLoopDependencyNode(LIRInstruction op) {
        return ((op instanceof OCLControlFlow.LoopInitOp || op instanceof OCLControlFlow.LoopConditionOp || op instanceof OCLControlFlow.LoopPostOp));
    }

    private static void emitOp(CompilationResultBuilder crb, LIRInstruction op) {
        try {
            trace("op: " + op);
            op.emitCode(crb);
        } catch (AssertionError | RuntimeException t) {
            throw new TornadoInternalError(t);
        }
    }

    private static boolean isLoopBlock(HIRBlock block, HIRBlock loopHeader) {

        Set<HIRBlock> visited = new HashSet<>();
        Stack<HIRBlock> stack = new Stack<>();
        stack.push(block);

        while (!stack.isEmpty()) {

            HIRBlock b = stack.pop();
            visited.add(b);

            if (b.getId() < loopHeader.getId()) {
                return false;
            } else if (b == loopHeader) {
                return true;
            } else {
                HIRBlock[] successors = IntStream.range(0, b.getSuccessorCount()).mapToObj(b::getSuccessorAt).toArray(HIRBlock[]::new);
                for (HIRBlock successor : successors) {
                    if (!visited.contains(successor)) {
                        stack.push(successor);
                    }
                }
            }
        }

        return false;
    }

    public boolean isParallel() {
        return isParallel;
    }

    public void setParallel(boolean parallel) {
        this.isParallel = parallel;
    }

    public OCLCompilationResult getResult() {
        return (OCLCompilationResult) compilationResult;
    }

    public boolean shouldRemoveLoop() {
        return (isParallel() && deviceContext.isPlatformFPGA());
    }

    public boolean isKernel() {
        return isKernel;
    }

    public void setKernel(boolean value) {
        isKernel = value;
    }

    public OCLAssembler getAssembler() {
        return (OCLAssembler) asm;
    }

    public void addNonInlinedMethod(ResolvedJavaMethod method) {
        nonInlinedMethods.add(method);
    }

    Set<ResolvedJavaMethod> getNonInlinedMethods() {
        return nonInlinedMethods;
    }

    /**
     * Emits code for {@code lir} in its {@linkplain LIR#codeEmittingOrder() code
     * emitting order}.
     */
    public void emit(LIR lir) {
        assert this.lir == null;
        assert currentBlockIndex == 0;
        this.lir = lir;
        this.currentBlockIndex = 0;
        frameContext.enter(this);

        final ControlFlowGraph cfg = (ControlFlowGraph) lir.getControlFlowGraph();
        trace("Traversing CFG: ", cfg.graph.name);
        cfg.computePostdominators();
        traverseControlFlowGraph(cfg, new OCLBlockVisitor(this));

        trace("Finished traversing CFG");
        this.lir = null;
        this.currentBlockIndex = 0;

    }

    @Override
    public void finish() {
        int position = asm.position();
        compilationResult.setTargetCode(asm.close(true), position);
    }

    void emitLoopBlock(HIRBlock block) {
        final List<LIRInstruction> headerInstructions = lir.getLIRforBlock(block);
        if (shouldFormatLoopHeader(headerInstructions)) {
            formatLoopHeader(headerInstructions);
        }
        emitBlock(block);
    }

    void emitRelocatedInstructions(HIRBlock block) {
        if (block == null) {
            return;
        }

        trace("block on exit %d", block.getId());

        boolean relocatableInstruction = false;
        for (LIRInstruction op : lir.getLIRforBlock(block)) {
            if (op instanceof OCLLIRStmt.MarkRelocateInstruction) {
                relocatableInstruction = true;
            }

            if (op != null && relocatableInstruction) {
                try {
                    emitOp(this, op);
                } catch (TornadoInternalError e) {
                    throw e.addContext("lir instruction", block + "@" + op.id() + " " + op + "\n");
                }
            }
        }
    }

    void emitBlock(HIRBlock block) {
        if (block == null) {
            return;
        }

        trace("block: %d", block.getId());
        printBasicHIRBlockTrace(block);

        LIRInstruction breakInst = null;

        boolean relocatableInstruction = false;
        for (LIRInstruction op : lir.getLIRforBlock(block)) {
            if (op instanceof OCLLIRStmt.MarkRelocateInstruction) {
                relocatableInstruction = true;
            }

            if (op == null || relocatableInstruction) {
                continue;
            } else if (op instanceof OCLControlFlow.LoopBreakOp) {
                breakInst = op;
                continue;
            } else if ((shouldRemoveLoop() && loops == 0) && isLoopDependencyNode(op)) {
                /**
                 * Apply the Loop Flattening optimization for FPGAs,
                 * which omits the outermost for loop along with every data dependency associated with it.
                 */
                if (op instanceof OCLControlFlow.LoopPostOp) {
                    loops++;
                }
                continue;
            }
            if (Options.PrintLIRWithAssembly.getValue(getOptions())) {
                blockComment(String.format("%d %s", op.id(), op));
            }

            try {
                emitOp(this, op);
            } catch (TornadoInternalError e) {
                throw e.addContext("lir instruction", block + "@" + op.id() + " " + op + "\n");
            }
        }

        /*
         * Because of the way Graal handles Phi nodes, we generate the break instruction
         * before any phi nodes are updated, therefore we need to ensure that the break
         * is emitted as the end of the block.
         */
        if (breakInst != null) {
            try {
                emitOp(this, breakInst);
            } catch (TornadoInternalError e) {
                throw e.addContext("lir instruction", block + "@" + breakInst.id() + " " + breakInst + "\n");
            }
        }

    }

    void printBasicHIRBlockTrace(HIRBlock block) {
        if (isMergeHIRBlock(block)) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < block.getPredecessorCount(); i++) {
                sb.append(block.getPredecessorAt(i).getId()).append(" ");
            }
            sb.append("]");
            ((OCLAssembler) asm).emitLine("// BLOCK %d MERGES %s", block.getId(), sb.toString());
        } else {
            ((OCLAssembler) asm).emitLine("// BLOCK %d", block.getId());
        }

        if (Options.PrintLIRWithAssembly.getValue(getOptions())) {
            blockComment(String.format("block B%d %s", block.getId(), block.getLoop()));
        }
    }

    private void traverseControlFlowGraph(ControlFlowGraph cfg, OCLBlockVisitor visitor) {
        traverseControlFlowGraph(cfg.getStartBlock(), visitor, new HashSet<>(), new HashMap<>());
        if (rescheduledBasicBlocks != null) {
            rescheduledBasicBlocks.clear();
        }
    }

    private void rescheduleBasicBlock(HIRBlock basicHIRBlock, OCLBlockVisitor visitor, HashSet<HIRBlock> visited, HashMap<HIRBlock, HIRBlock> pending) {
        HIRBlock block = pending.get(basicHIRBlock);
        visitor.enter(block);
        visitor.exit(block, null);
        visited.add(block);
        pending.remove(block);
        if (rescheduledBasicBlocks == null) {
            rescheduledBasicBlocks = new HashSet<>();
        }
        rescheduledBasicBlocks.add(block);
    }

    private boolean isFalseSuccessorWithLoopEnd(IfNode ifNode, HIRBlock basicHIRBlock) {
        return isCurrentHIRBlockAFalseBranch(ifNode, basicHIRBlock) && basicHIRBlock.getEndNode() instanceof LoopEndNode;
    }

    private boolean isCurrentHIRBlockAFalseBranch(IfNode ifNode, HIRBlock basicHIRBlock) {
        return ifNode.falseSuccessor() == basicHIRBlock.getBeginNode();
    }

    private boolean isTrueBranchALoopExitNode(IfNode ifNode) {
        return ifNode.trueSuccessor() instanceof AbstractBeginNode;
    }

    private boolean isTrueBranchWithEndNodeOrNotControlSplit(HIRBlock blockTrueBranch) {
        return ((blockTrueBranch.getEndNode() instanceof AbstractEndNode) || !(blockTrueBranch.getEndNode() instanceof ControlSplitNode));
    }

    /**
     * From Graal 22.1.0 the graph traversal was changed. This method reschedules
     * the current basic block to generate always the true condition before the
     * false condition only if we have a LoopEndNode node in the false branch, or we
     * have a LoopExit in the true branch contains a {@link LoopExitNode} or it is
     * not a control Split (due to nested control-flow).
     *
     * @param basicBlock
     *     {@link HIRBlock}
     * @param visitor
     *     {@link OCLBlockVisitor}
     * @param visited
     *     {@link HashSet}
     * @param pending
     *     {@link HashMap}
     */
    private void rescheduleTrueBranchConditionsIfNeeded(HIRBlock basicBlock, OCLBlockVisitor visitor, HashSet<HIRBlock> visited, HashMap<HIRBlock, HIRBlock> pending) {
        if (!basicBlock.isLoopHeader() && basicBlock.getDominator() != null && basicBlock.getDominator().getEndNode() instanceof IfNode) {
            IfNode ifNode = (IfNode) basicBlock.getDominator().getEndNode();
            HIRBlock blockTrueBranch = getBlockTrueBranch(basicBlock);
            // implement rescheduling for condition if nodes
            if (isNotLoopBeginIf(ifNode)) {
                boolean shouldReschedule =  isFalseSuccessorWithLoopEnd(ifNode, basicBlock) //
                        || (isCurrentHIRBlockAFalseBranch(ifNode, basicBlock) //
                        && isTrueBranchALoopExitNode(ifNode) //
                        && isTrueBranchWithEndNodeOrNotControlSplit(blockTrueBranch));

                if (shouldReschedule) {
                    for (int i = 0; i < basicBlock.getDominator().getSuccessorCount(); i++) {
                        HIRBlock successor = basicBlock.getDominator().getSuccessorAt(i);
                        if (successor.getBeginNode() == ifNode.trueSuccessor() && !visited.contains(successor)) {
                            pending.put(basicBlock, successor);
                            rescheduleBasicBlock(basicBlock, visitor, visited, pending);
                        }
                    }
                }
            }
        }
    }

    /**
     * This function examines if a given {@code IfNode} is generated for a condition or if it is part of the loop logic (LoopBegin -> If).
     *
     * @param ifNode The {@code IfNode} to be examined.
     * @return true if the {@code IfNode} is not associated with the loop iteration, false otherwise.
     */
    private boolean isNotLoopBeginIf(IfNode ifNode) {
        return !(ifNode.predecessor() instanceof LoopBeginNode);
    }

    private void traverseControlFlowGraph(HIRBlock basicBlock, OCLBlockVisitor visitor, HashSet<HIRBlock> visited, HashMap<HIRBlock, HIRBlock> pending) {

        if (pending.containsKey(basicBlock) && !visited.contains(pending.get(basicBlock))) {
            rescheduleBasicBlock(basicBlock, visitor, visited, pending);
        }

        // New call due to the integration with Graal-IR 22.1.0
        rescheduleTrueBranchConditionsIfNeeded(basicBlock, visitor, visited, pending);

        visitor.enter(basicBlock);
        visited.add(basicBlock);

        HIRBlock firstDominated = basicBlock.getFirstDominated();
        LinkedList<HIRBlock> queue = new LinkedList<>();
        queue.add(firstDominated);

        if (basicBlock.isLoopHeader()) {
            HIRBlock[] successors = IntStream.range(0, basicBlock.getSuccessorCount()).mapToObj(basicBlock::getSuccessorAt).toArray(HIRBlock[]::new);
            LinkedList<HIRBlock> last = new LinkedList<>();
            LinkedList<HIRBlock> pendingList = new LinkedList<>();

            FixedNode endNode = basicBlock.getEndNode();
            IfNode ifNode = null;
            if (endNode instanceof IfNode) {
                ifNode = (IfNode) endNode;
            }
            for (HIRBlock block : successors) {
                boolean isInnerLoop = isLoopBlock(block, basicBlock);
                if (!isInnerLoop) {
                    assert ifNode != null;
                    if (ifNode.trueSuccessor() == block.getBeginNode() && block.getBeginNode() instanceof LoopExitNode && block.getEndNode() instanceof EndNode) {
                        pendingList.addFirst(block);
                        if (block.getPostdominator().getBeginNode() instanceof MergeNode) {
                            // We may need to reschedule this block if it is not closed before visiting the
                            // postDominator.
                            pending.put(block.getPostdominator(), block);
                        }
                    } else {
                        last.addLast(block);
                    }
                } else {
                    queue.addLast(block);
                }
            }

            for (HIRBlock l : pendingList) {
                last.addLast(l);
            }

            for (HIRBlock l : last) {
                queue.addLast(l);
            }
            queue.removeFirst();
        }

        for (HIRBlock block : queue) {
            firstDominated = block;
            while (firstDominated != null) {
                if (!visited.contains(firstDominated)) {
                    traverseControlFlowGraph(firstDominated, visitor, visited, pending);
                }
                firstDominated = firstDominated.getDominatedSibling();
            }
        }

        if (rescheduledBasicBlocks == null || (!rescheduledBasicBlocks.contains(basicBlock))) {
            visitor.exit(basicBlock, null);
        }
    }

    private HIRBlock getBlockTrueBranch(HIRBlock basicHIRBlock) {
        IfNode ifNode = (IfNode) basicHIRBlock.getDominator().getEndNode();
        for (int i = 0; i < basicHIRBlock.getDominator().getSuccessorCount(); i++) {
            if (ifNode.trueSuccessor() == basicHIRBlock.getDominator().getSuccessorAt(i).getBeginNode()) {
                return basicHIRBlock.getDominator().getSuccessorAt(i);
            }
        }
        return null;
    }

    public TaskDataContext getTaskMetaData() {
        return ((OCLCompilationResult) compilationResult).getMeta();
    }

    public OCLDeviceContextInterface getDeviceContext() {
        return this.deviceContext;
    }

    public void setDeviceContext(OCLDeviceContextInterface deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Deprecated
    private static class DepFinder implements InstructionValueProcedure {

        private final Set<Value> dependencies;

        DepFinder(final Set<Value> dependencies) {
            this.dependencies = dependencies;
        }

        @Override
        public Value doValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
            if (value instanceof Variable) {
                dependencies.add(value);
            }

            return value;
        }

        public Set<Value> getDependencies() {
            return dependencies;
        }

    }
}
