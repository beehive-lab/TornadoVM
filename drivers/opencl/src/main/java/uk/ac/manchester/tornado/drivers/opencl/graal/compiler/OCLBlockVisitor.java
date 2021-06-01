/*
 * Copyright (c) 2018 - 2020, APT Group, Department of Computer Science,
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;

import jdk.vm.ci.meta.JavaConstant;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;

public class OCLBlockVisitor implements ControlFlowGraph.RecursiveVisitor<Block> {

    OCLCompilationResultBuilder openclBuilder;
    OCLAssembler asm;
    Set<Block> merges;
    Set<Block> closedLoops;
    Set<Block> switches;
    Set<Node> switchClosed;
    HashMap<Block, Integer> pending;
    private int loopCount;
    private int loopEnds;

    public OCLBlockVisitor(OCLCompilationResultBuilder resBuilder) {
        this.openclBuilder = resBuilder;
        this.asm = resBuilder.getAssembler();
        merges = new HashSet<>();
        switches = new HashSet<>();
        switchClosed = new HashSet<>();
        closedLoops = new HashSet<>();
        pending = new HashMap<>();
    }

    private void emitBeginBlockForElseStatement(Block dom, Block block) {
        final IfNode ifNode = (IfNode) dom.getEndNode();
        if (ifNode.falseSuccessor() == block.getBeginNode()) {
            asm.indent();
            asm.elseStmt();
            asm.eol();
        }
        asm.beginScope();
        asm.eolOn();
    }

    private void emitBeginBlockForSwitchStatements(Block dom, Block beginBlockNode) {
        final IntegerSwitchNode switchNode = (IntegerSwitchNode) dom.getEndNode();
        asm.indent();
        Node beginNode = beginBlockNode.getBeginNode();
        switches.add(beginBlockNode);

        NodeIterable<Node> successors = switchNode.successors();

        int defaultSuccessorIndex = switchNode.defaultSuccessorIndex();
        Iterator<Node> iterator = successors.iterator();

        int caseIndex = -1;
        while (iterator.hasNext()) {
            Node n = iterator.next();
            caseIndex++;
            if (n.equals(beginNode)) {
                break;
            }
        }

        // Add all cases that go to the same block
        ArrayList<Integer> commonCases = new ArrayList<>();
        for (int i = 0; i <= defaultSuccessorIndex; i++) {
            if (caseIndex == switchNode.keySuccessorIndex(i)) {
                commonCases.add(i);
            }
        }

        if (defaultSuccessorIndex == caseIndex) {
            asm.emit(OCLAssemblerConstants.DEFAULT_CASE + OCLAssemblerConstants.COLON);
        } else {
            for (Integer idx : commonCases) {
                asm.emit(OCLAssemblerConstants.CASE + " ");
                JavaConstant keyAt = switchNode.keyAt(idx);
                asm.emit(keyAt.toValueString());
                asm.emit(OCLAssemblerConstants.COLON);
                asm.emitLine("");
                asm.indent();
            }
        }
    }

    @Override
    public Block enter(Block block) {
        boolean isMerge = block.getBeginNode() instanceof MergeNode;
        if (isMerge) {
            asm.eolOn();
            merges.add(block);
        }

        if (block.isLoopHeader()) {
            loopCount++;
            openclBuilder.emitLoopBlock(block);
        } else {
            // We emit either an ELSE statement or a SWITCH statement
            final Block dom = block.getDominator();
            if (dom != null && !isMerge && !dom.isLoopHeader() && isIfBlock(dom)) {
                emitBeginBlockForElseStatement(dom, block);
            } else if (dom != null && !isMerge && !dom.isLoopHeader() && isSwitchBlock(dom)) {
                emitBeginBlockForSwitchStatements(dom, block);
            }
            openclBuilder.emitBlock(block);
        }
        return null;
    }

    private boolean isLoopExitNode(Block block) {
        return block.getBeginNode() instanceof LoopExitNode && block.getEndNode() instanceof EndNode;
    }

    private void checkClosingBlockInsideIf(Block block, Block pdom) {
        if (pdom.isLoopHeader() && block.getDominator() != null && isIfBlock(block.getDominator())) {

            /*
             * If the post-dominator is a loop Header and the dominator of the current block
             * is an if-condition, then we generate the end-scope if we are also inside
             * another if-condition, and the remaining condition is not a LoopEndNode
             * (because the block was already closed)
             */
            if ((block.getDominator().getDominator() != null) && (isIfBlock(block.getDominator().getDominator()))) {

                Block[] successors = block.getDominator().getSuccessors();
                int index = 0;
                if (successors[index] == block) {
                    index = 1;
                }

                // If the current block is a merge-block, and the block does not correspond with
                // any of the if-branches of the dominator, then we do not need the
                // close-bracket.
                if (successors[index] != block && block.getBeginNode() instanceof MergeNode) {
                    return;
                }

                if (!(successors[index].getBeginNode() instanceof LoopExitNode)) {
                    asm.endScope(block.toString());
                }
            }
        } else if ((pdom.getBeginNode() instanceof MergeNode) && (block.getDominator() != null && isIfBlock(block.getDominator()))) {
            /*
             * If the post-dominator is a MergeNode and the dominator of the current block
             * is an if-condition, then we generate the end-scope if we are also inside
             * another if-condition.
             */
            Block dom2 = block.getDominator(2);
            if (dom2 != null && isIfBlock(dom2)) {
                /*
                 * We check that the other else-if block contains the loop-exit -> loop-end
                 * sequence. This means there was a break in the code.
                 */
                Block[] successors = block.getDominator().getSuccessors();
                int index = 0;
                if (successors[index] == block) {
                    index = 1;
                }
                if (successors[index].getBeginNode() instanceof LoopExitNode && successors[index].getEndNode() instanceof LoopEndNode) {
                    asm.endScope(block.toString());
                }
            } else if (isIfBlock(block.getDominator())) {
                IfNode ifNode = (IfNode) block.getDominator().getEndNode();

                if (ifNode.trueSuccessor().equals(block.getBeginNode()) && isLoopExitNode(block)) {
                    asm.endScope(block.toString());
                }

            }
        }
    }

    private void closeSwitchStatement(Block block) {
        asm.emitLine(OCLAssemblerConstants.BREAK + OCLAssemblerConstants.STMT_DELIMITER);

        final IntegerSwitchNode switchNode = (IntegerSwitchNode) block.getDominator().getEndNode();
        int blockNumber = getBlockIndexForSwitchStatement(block, switchNode);
        int numCases = getNumberOfCasesForSwitch(switchNode);

        if ((numCases - 1) == blockNumber) {
            asm.endScope(block.toString());
            switchClosed.add(switchNode);
        }
    }

    private boolean wasBlockAlreadyClosed(Block b) {
        Block[] successors = b.getSuccessors();
        for (Block s : successors) {
            if (closedLoops.contains(s)) {
                return true;
            }
        }
        return false;
    }

    private void closeScope(Block block) {
        if (block.getBeginNode() instanceof LoopExitNode) {
            if (!(block.getDominator().getDominator() != null && block.getDominator().getDominator().getBeginNode() instanceof MergeNode)) {
                /*
                 * Only close scope if the loop-exit node does not depend on a merge node. In
                 * such case, the merge will generate the correct close scope.
                 */
                asm.endScope(block.toString());
                closedLoops.add(block);
            }
        } else {
            asm.endScope(block.toString());
            closedLoops.add(block);
        }
    }

    private boolean isBlockInABreak(Block block, Block pdom) {
        return (pdom.getEndNode() instanceof ReturnNode && //
                block.getBeginNode() instanceof LoopExitNode && //
                block.getEndNode() instanceof EndNode && //
                block.getDominator() != null && //
                block.getDominator().getEndNode() instanceof IfNode && //
                block.getDominator().getBeginNode() instanceof LoopBeginNode);
    }

    @Override
    public void exit(Block block, Block value) {
        if (block.isLoopEnd()) {
            // Temporary fix to remove the end scope of the most outer loop
            // without changing the loop schematics in IR level.
            loopEnds++;
            if (openclBuilder.shouldRemoveLoop()) {
                if (loopCount - loopEnds > 0) {
                    asm.endScope(block.toString());
                    closedLoops.add(block);
                }
            } else {
                closeScope(block);
            }
        }

        if (block.getPostdominator() != null) {
            Block pdom = block.getPostdominator();
            if (!merges.contains(pdom) && isMergeBlock(pdom) && !switches.contains(block)) {
                // Check also if the current and next blocks are not merges block with more than
                // 2 predecessors. In that case, we do not generate end scope.

                if (!(pdom.getBeginNode() instanceof MergeNode && merges.contains(block) && block.getPredecessorCount() > 2)) {
                    // We need to check that none of the blocks reachable from dominators has been
                    // already closed.
                    if (!wasBlockAlreadyClosed(block.getDominator()) && !isBlockInABreak(block, pdom)) {
                        asm.endScope(block.toString());
                    }
                }
            } else if (!merges.contains(pdom) && isMergeBlock(pdom) && switches.contains(block) && isSwitchBlock(block.getDominator())) {
                closeSwitchStatement(block);
            } else {
                checkClosingBlockInsideIf(block, pdom);
            }
        } else {
            closeBranchBlock(block);
        }
    }

    private void closeIfBlock(Block block, Block dom) {
        final IfNode ifNode = (IfNode) dom.getEndNode();
        if ((ifNode.falseSuccessor() == block.getBeginNode()) || (ifNode.trueSuccessor() == block.getBeginNode())) {
            // We cannot close a block that has a loopEnd (already close) that is in the
            // true branch, until the false branch has been closed.
            boolean isLoopEnd = block.getEndNode() instanceof LoopEndNode;
            boolean isTrueBranch = ifNode.trueSuccessor() == block.getBeginNode();
            if (!(isTrueBranch & isLoopEnd)) {
                asm.endScope(block.toString());
            }
        }
    }

    private int getBlockIndexForSwitchStatement(Block block, IntegerSwitchNode switchNode) {
        Node beginNode = block.getBeginNode();

        NodeIterable<Node> successors = switchNode.successors();
        Iterator<Node> iterator = successors.iterator();
        int blockIndex = 0;
        while (iterator.hasNext()) {
            Node n = iterator.next();
            if (n.equals(beginNode)) {
                break;
            }
            blockIndex++;
        }
        return blockIndex;
    }

    private int getNumberOfCasesForSwitch(IntegerSwitchNode switchNode) {
        return switchNode.successors().count();
    }

    private void closeSwitchBlock(Block block, Block dom) {
        final IntegerSwitchNode switchNode = (IntegerSwitchNode) dom.getEndNode();
        int blockNumber = getBlockIndexForSwitchStatement(block, switchNode);
        int numCases = getNumberOfCasesForSwitch(switchNode);
        if ((numCases - 1) == blockNumber) {
            if (!switchClosed.contains(switchNode)) {
                asm.endScope(block.toString());
                switchClosed.add(switchNode);
            }
        }
    }

    private boolean isNestedIfNode(Block block) {
        final Block dom = block.getDominator();
        boolean isMerge = block.getBeginNode() instanceof MergeNode;
        boolean isReturn = block.getEndNode() instanceof ReturnNode;
        return dom != null && isMerge && isReturn && !dom.isLoopHeader() && isIfBlock(dom);
    }

    private boolean isIfBlockNode(Block block) {
        final Block dom = block.getDominator();
        boolean isMerge = block.getBeginNode() instanceof MergeNode;
        return dom != null && !isMerge && !dom.isLoopHeader() && isIfBlock(dom);
    }

    private boolean isSwitchBlockNode(Block block) {
        final Block dom = block.getDominator();
        boolean isMerge = block.getBeginNode() instanceof MergeNode;
        return dom != null && !isMerge && !dom.isLoopHeader() && isSwitchBlock(dom);
    }

    private boolean isStartNode(Block block) {
        return block.getDominator().getBeginNode() instanceof StartNode;
    }

    private boolean isReturnBranchWithMerge(Block dom, Block block) {
        return dom != null && //
                dom.getDominator() != null && // We need to be inside another merge block
                dom.getDominator().getBeginNode() instanceof MergeNode && // We check for the nested merged block
                dom.getBeginNode() instanceof LoopBeginNode && // The dominator is a loop node
                dom.getEndNode() instanceof IfNode && //
                block.getBeginNode() instanceof LoopExitNode && // The current block exits the block with a return
                block.getEndNode() instanceof ReturnNode;
    }

    private void closeBranchBlock(Block block) {
        final Block dom = block.getDominator();
        if (isIfBlockNode(block)) {
            closeIfBlock(block, dom);
        } else if (isSwitchBlockNode(block)) {
            closeSwitchBlock(block, dom);
        } else if (isNestedIfNode(block) && (!isStartNode(block))) {
            asm.endScope(block.toString());
        } else if (isReturnBranchWithMerge(dom, block)) {
            asm.endScope(block.toString());
        }
    }

    private static boolean isMergeBlock(Block block) {
        return block.getBeginNode() instanceof MergeNode;
    }

    private static boolean isIfBlock(Block block) {
        return block.getEndNode() instanceof IfNode;
    }

    private static boolean isSwitchBlock(Block block) {
        return block.getEndNode() instanceof IntegerSwitchNode;
    }
}
