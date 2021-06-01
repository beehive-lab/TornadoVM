/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.compiler;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.graal.TornadoLIRGenerator.trace;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
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
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.CodeCacheProvider;
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
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;

public class OCLCompilationResultBuilder extends CompilationResultBuilder {

    protected LIR lir;
    private int currentBlockIndex;
    private final Set<ResolvedJavaMethod> nonInlinedMethods;
    private boolean isKernel;
    private int loops = 0;
    private boolean isParallel;
    private OCLDeviceContextInterface deviceContext;
    HashSet<Block> rescheduledBasicBlocks;

    public OCLCompilationResultBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext,
            OCLCompilationResult compilationResult, OptionValues options) {
        super(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, options, getDebugContext(), compilationResult, Register.None);
        nonInlinedMethods = new HashSet<>();
    }

    public boolean isParallel() {
        return isParallel;
    }

    public OCLCompilationResult getResult() {
        return (OCLCompilationResult) compilationResult;
    }

    public void setKernel(boolean value) {
        isKernel = value;
    }

    public boolean shouldRemoveLoop() {
        return (isParallel() && deviceContext.isPlatformFPGA());
    }

    public boolean isKernel() {
        return isKernel;
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
    @Override
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

    private static boolean isMergeBlock(Block block) {
        return block.getBeginNode() instanceof AbstractMergeNode;
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

    /**
     * Checks if the {@link OCLNodeLIRBuilder#emitLoopBegin} has been called right before
     * {@link OCLNodeLIRBuilder#emitIf}. In other words, that there is no data flow/control flow
     * between the {@link LoopBeginNode} and the corresponding {@link IfNode} loop condition.
     * @return true if the {@param loopCondIndex} is right after the LIR instructions of a loop header
     * ({@param loopPostOpIndex} and {@param loopInitOpIndex}).
     */
    private static boolean isLoopConditionRightAfterHeader(int loopCondIndex, int loopPostOpIndex, int loopInitOpIndex) {
        return (loopCondIndex - 1 == loopPostOpIndex) && (loopCondIndex - 2 == loopInitOpIndex);
    }

    /**
     * Checks if there are any LIR instructions between the loop condition and the {@link LoopInitOp} and {@link LoopPostOp}.
     * If there are no instructions, it is possible to move the loop condition to the loop header.
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

    void emitLoopBlock(Block block) {
        final List<LIRInstruction> headerInstructions = lir.getLIRforBlock(block);
        if (shouldFormatLoopHeader(headerInstructions)) {
            formatLoopHeader(headerInstructions);
        }
        emitBlock(block);
    }

    void emitBlock(Block block) {
        if (block == null) {
            return;
        }

        trace("block: %d", block.getId());
        printBasicBlockTrace(block);

        LIRInstruction breakInst = null;

        for (LIRInstruction op : lir.getLIRforBlock(block)) {
            if (op == null) {
                continue;
            } else if (op instanceof OCLControlFlow.LoopBreakOp) {
                breakInst = op;
                continue;
            } else if ((shouldRemoveLoop() && loops == 0) && isLoopDependencyNode(op)) {
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

    void printBasicBlockTrace(Block block) {
        if (isMergeBlock(block)) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (Block pred : block.getPredecessors()) {
                sb.append(pred.getId()).append(" ");
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

    private void traverseControlFlowGraph(ControlFlowGraph cfg, OCLBlockVisitor visitor) {
        traverseControlFlowGraph(cfg.getStartBlock(), visitor, new HashSet<>(), new HashMap<>());
        if (rescheduledBasicBlocks != null) {
            rescheduledBasicBlocks.clear();
        }
    }

    private void rescheduleBasicBlock(Block basicBlock, OCLBlockVisitor visitor, HashSet<Block> visited, HashMap<Block, Block> pending) {
        Block block = pending.get(basicBlock);
        visitor.enter(block);
        visitor.exit(block, null);
        visited.add(block);
        pending.remove(block);
        if (rescheduledBasicBlocks == null) {
            rescheduledBasicBlocks = new HashSet<>();
        }
        rescheduledBasicBlocks.add(block);
    }

    private void traverseControlFlowGraph(Block basicBlock, OCLBlockVisitor visitor, HashSet<Block> visited, HashMap<Block, Block> pending) {

        if (pending.containsKey(basicBlock) && !visited.contains(pending.get(basicBlock))) {
            rescheduleBasicBlock(basicBlock, visitor, visited, pending);
        }
        visitor.enter(basicBlock);
        visited.add(basicBlock);

        Block firstDominated = basicBlock.getFirstDominated();
        LinkedList<Block> queue = new LinkedList<>();
        queue.add(firstDominated);

        if (basicBlock.isLoopHeader()) {
            Block[] successors = basicBlock.getSuccessors();
            LinkedList<Block> last = new LinkedList<>();
            LinkedList<Block> pendingList = new LinkedList<>();

            FixedNode endNode = basicBlock.getEndNode();
            IfNode ifNode = null;
            if (endNode instanceof IfNode) {
                ifNode = (IfNode) endNode;
            }
            for (Block block : successors) {
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

            for (Block l : pendingList) {
                last.addLast(l);
            }

            for (Block l : last) {
                queue.addLast(l);
            }
            queue.removeFirst();
        }

        for (Block block : queue) {
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

    private static boolean isLoopBlock(Block block, Block loopHeader) {

        Set<Block> visited = new HashSet<>();
        Stack<Block> stack = new Stack<>();
        stack.push(block);

        while (!stack.isEmpty()) {

            Block b = stack.pop();
            visited.add(b);

            if (b.getId() < loopHeader.getId()) {
                return false;
            } else if (b == loopHeader) {
                return true;
            } else {
                Block[] successors = b.getSuccessors();
                for (Block bl : successors) {
                    if (!visited.contains(bl)) {
                        stack.push(bl);
                    }
                }
            }
        }

        return false;
    }

    public void setParallel(boolean parallel) {
        this.isParallel = parallel;
    }

    public void setDeviceContext(OCLDeviceContextInterface deviceContext) {
        this.deviceContext = deviceContext;
    }

    public OCLDeviceContextInterface getDeviceContext() {
        return this.deviceContext;
    }
}
