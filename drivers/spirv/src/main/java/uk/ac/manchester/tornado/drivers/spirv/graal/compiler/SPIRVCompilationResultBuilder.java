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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.spi.CodeGenProviders;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;

public class SPIRVCompilationResultBuilder extends CompilationResultBuilder {

    private final Set<ResolvedJavaMethod> nonInlinedMethods;
    private boolean isKernel;
    private int loops = 0;
    private boolean isParallel;
    private SPIRVDeviceContext deviceContext;

    HashSet<Block> rescheduledBasicBlocks;

    public SPIRVCompilationResultBuilder(CodeGenProviders providers, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext, OptionValues options, DebugContext debug,
            CompilationResult compilationResult) {
        super(providers, frameMap, asm, dataBuilder, frameContext, options, debug, compilationResult, Register.None);
        // super(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext,
        // options, getDebugContext(), compilationResult, Register.None);
        nonInlinedMethods = new HashSet<>();
    }

    public Set<ResolvedJavaMethod> getNonInlinedMethods() {
        return nonInlinedMethods;
    }

    public void setKernel(boolean isKernel) {
        this.isKernel = isKernel;
    }

    public void setParallel(boolean isParallel) {
        this.isParallel = isParallel;
    }

    public boolean isParallel() {
        return isParallel;
    }

    public void setDeviceContext(SPIRVDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    public boolean isKernel() {
        return isKernel;
    }

    /**
     * Emits code for {@code lir} in its {@linkplain LIR#codeEmittingOrder()} code
     * emitting order.
     */
    @Override
    public void emit(LIR lir) {
        assert this.lir == null;
        assert currentBlockIndex == 0;
        this.lir = lir;
        this.currentBlockIndex = 0;
        frameContext.enter(this);

        final ControlFlowGraph cfg = (ControlFlowGraph) lir.getControlFlowGraph();
        Logger.traceCodeGen(Logger.BACKEND.SPIRV, "Traversing CFG: ", cfg.graph.name);
        cfg.computePostdominators();
        traverseControlFlowGraph(cfg, new SPIRVBlockVisitor(this));

        Logger.traceCodeGen(Logger.BACKEND.SPIRV, "Finished traversing CFG");
        this.lir = null;
        this.currentBlockIndex = 0;

    }

    private void traverseControlFlowGraph(ControlFlowGraph cfg, SPIRVBlockVisitor visitor) {
        traverseControlFlowGraph(cfg.getStartBlock(), visitor, new HashSet<>(), new HashMap<>());
        if (rescheduledBasicBlocks != null) {
            rescheduledBasicBlocks.clear();
        }
    }

    private void rescheduleBasicBlock(Block basicBlock, SPIRVBlockVisitor visitor, HashSet<Block> visited, HashMap<Block, Block> pending) {
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

    private void traverseControlFlowGraph(Block basicBlock, SPIRVBlockVisitor visitor, HashSet<Block> visited, HashMap<Block, Block> pending) {

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

    private void emitOp(CompilationResultBuilder crb, LIRInstruction op) {
        try {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "op: " + op);
            op.emitCode(crb);
        } catch (AssertionError | RuntimeException t) {
            throw new TornadoInternalError(t);
        }
    }

    void emitBlock(Block block) {
        if (block == null) {
            return;
        }

        Logger.traceCodeGen(Logger.BACKEND.SPIRV, "block: %d", block.getId());

        LIRInstruction breakInst = null;

        for (LIRInstruction op : lir.getLIRforBlock(block)) {
            if (op != null) {

                // if (op instanceof PTXControlFlow.LoopBreakOp) {
                // breakInst = op;
                // continue;
                // }

                try {
                    emitOp(this, op);
                } catch (TornadoInternalError e) {
                    throw e.addContext("lir instruction", block + "@" + op.id() + " " + op + "\n");
                }
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

    public SPIRVAssembler getAssembler() {
        return (SPIRVAssembler) asm;
    }

    public void addNonInlinedMethod(ResolvedJavaMethod targetMethod) {
        nonInlinedMethods.add(targetMethod);
    }
}
