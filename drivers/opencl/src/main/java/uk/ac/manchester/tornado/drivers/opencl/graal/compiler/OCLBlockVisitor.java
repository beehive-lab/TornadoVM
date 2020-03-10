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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.IfNode;
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
    private int loopCount;
    private int loopEnds;

    public OCLBlockVisitor(OCLCompilationResultBuilder resBuilder) {
        this.openclBuilder = resBuilder;
        this.asm = resBuilder.getAssembler();
        merges = new HashSet<>();
        switches = new HashSet<>();
        switchClosed = new HashSet<>();
        closedLoops = new HashSet<>();
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
            openclBuilder.emitLoopHeader(block);
            // Temporary fix to remove the end scope of the most outer loop
            // without changing the loop schematics in IR level.
            if (openclBuilder.shouldRemoveLoop()) {
                if (loopCount > 1) { // TODO: Add a more generic fix for
                    asm.beginScope();
                }
            } else {
                asm.beginScope();
            }

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

    private void checkClosingBlockInsideIf(Block b, Block pdom) {
        if (pdom.isLoopHeader() && b.getDominator() != null && isIfBlock(b.getDominator())) {
            if ((b.getDominator().getDominator() != null) && (isIfBlock(b.getDominator().getDominator()))) {
                asm.endScope();
            }
        }
    }

    private void closeSwitchStatement(Block block) {
        asm.emitLine(OCLAssemblerConstants.BREAK + OCLAssemblerConstants.STMT_DELIMITER);

        final IntegerSwitchNode switchNode = (IntegerSwitchNode) block.getDominator().getEndNode();
        int blockNumber = getBlockIndexForSwitchStatement(block, switchNode);
        int numCases = getNumberOfCasesForSwitch(switchNode);
        asm.emitLine("// BN: " + blockNumber + " numCases: " + numCases);

        if ((numCases - 1) == blockNumber) {
            asm.endScope();
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

    @Override
    public void exit(Block b, Block value) {
        if (b.isLoopEnd()) {
            // Temporary fix to remove the end scope of the most outer loop
            // without changing the loop schematics in IR level.
            loopEnds++;
            if (openclBuilder.shouldRemoveLoop()) {
                if (loopCount - loopEnds > 0) {
                    asm.endScope();
                    closedLoops.add(b);
                }
            } else {
                asm.endScope();
                closedLoops.add(b);
            }
        }
        if (b.getPostdominator() != null) {
            Block pdom = b.getPostdominator();
            if (!merges.contains(pdom) && isMergeBlock(pdom) && !switches.contains(b)) {

                // Check also if the current and next blocks are not merges
                // block with more than 2 predecessors.
                // In that case, we do not generate end scope.
                if (!(pdom.getBeginNode() instanceof MergeNode && merges.contains(b) && b.getPredecessorCount() > 2)) {

                    // We need to check that none of the blocks reachable from dominators has been
                    // already closed.
                    if (!wasBlockAlreadyClosed(b.getDominator())) {
                        asm.endScope();
                    }
                }
            } else if (!merges.contains(pdom) && isMergeBlock(pdom) && switches.contains(b) && isSwitchBlock(b.getDominator())) {
                closeSwitchStatement(b);
            } else {
                checkClosingBlockInsideIf(b, pdom);
            }
        } else {
            closeBranchBlock(b);
        }
    }

    private void closeIfBlock(Block block, Block dom) {
        final IfNode ifNode = (IfNode) dom.getEndNode();
        if ((ifNode.falseSuccessor() == block.getBeginNode()) || (ifNode.trueSuccessor() == block.getBeginNode())) {
            asm.endScope();
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
                asm.endScope();
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

    private void closeBranchBlock(Block block) {
        final Block dom = block.getDominator();
        if (isIfBlockNode(block)) {
            closeIfBlock(block, dom);
        } else if (isSwitchBlockNode(block)) {
            closeSwitchBlock(block, dom);
        } else if (isNestedIfNode(block) && (!isStartNode(block))) {
            asm.endScope();
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
