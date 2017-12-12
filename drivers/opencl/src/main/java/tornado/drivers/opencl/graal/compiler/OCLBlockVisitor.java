/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl.graal.compiler;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;

import jdk.vm.ci.meta.JavaConstant;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;

public class OCLBlockVisitor implements ControlFlowGraph.RecursiveVisitor<Block> {

    OCLCompilationResultBuilder resBuilder;
    OCLAssembler asm;
    Set<Block> merges;
    Set<Block> switches;
    Set<Node> switchClosed;

    public OCLBlockVisitor(OCLCompilationResultBuilder resBuilder) {
        this.resBuilder = resBuilder;
        this.asm = resBuilder.getAssembler();
        merges = new HashSet<>();
        switches = new HashSet<>();
        switchClosed = new HashSet<>();
    }

    @Override
    public Block enter(Block b) {

        boolean isMerge = b.getBeginNode() instanceof MergeNode;
        if (isMerge) {
            asm.eolOn();
            merges.add(b);
        }

        if (b.isLoopHeader()) {
            resBuilder.emitLoopHeader(b);
            asm.beginScope();
        } else {
            final Block dom = b.getDominator();

            if (dom != null && !isMerge && !dom.isLoopHeader() && isIfBlock(dom)) {
                final IfNode ifNode = (IfNode) dom.getEndNode();
                if (ifNode.falseSuccessor() == b.getBeginNode()) {
                    asm.indent();
                    asm.elseStmt();
                    asm.eol();
                }
                asm.beginScope();
                asm.eolOn();
            } else if (dom != null && !isMerge && !dom.isLoopHeader() && isSwitchBlock(dom)) {
                final IntegerSwitchNode switchNode = (IntegerSwitchNode) dom.getEndNode();
                asm.indent();
                Node beginNode = b.getBeginNode();
                switches.add(b);
                
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
                
                if (defaultSuccessorIndex == caseIndex) {
                    asm.emit(OCLAssemblerConstants.DEFAULT_CASE + OCLAssemblerConstants.COLON);
                } else {
                    asm.emit(OCLAssemblerConstants.CASE +  " ");
                    JavaConstant keyAt = switchNode.keyAt(caseIndex);
                    asm.emit(keyAt.toValueString());
                    asm.emit(OCLAssemblerConstants.COLON);
                }
                
                //asm.eol();
            }

            resBuilder.emitBlock(b);
        }

        return null;
    }

    @Override
    public void exit(Block b, Block value) {
        if (b.isLoopEnd()) {
            //asm.emitLine(String.format("// block %d exits loop %d", b.getId(), b.getLoop().getHeader().getId()));
            asm.endScope();
        } 
        if (b.getPostdominator() != null) {
            Block pdom = b.getPostdominator();
            //AbstractBeginNode beginNode = pdom.getBeginNode();
            if (!merges.contains(pdom) && isMergeBlock(pdom) && !switches.contains(b)) {
                //asm.eolOff();
                //asm.emitLine(String.format("// block %d merges control flow -> pdom = %d depth=%d",b.getId(), pdom.getId(), pdom.getDominatorDepth()));
                asm.endScope();
            } else if (!merges.contains(pdom) && isMergeBlock(pdom) && switches.contains(b) && isSwitchBlock(b.getDominator())) {
                
                asm.emitLine(OCLAssemblerConstants.BREAK + OCLAssemblerConstants.STMT_DELIMITER);
                
                final IntegerSwitchNode switchNode = (IntegerSwitchNode) b.getDominator().getEndNode();
                int blockNumber = getBlockIndexForSwitchStatement(b, switchNode);
                int numCases = getNumberOfCasesForSwitch(switchNode);
                
                if ((numCases-1) == blockNumber) {
                    asm.endScope();
                    switchClosed.add(switchNode);
                }
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
        if ((numCases-1) == blockNumber) {
            if (!switchClosed.contains(switchNode)) {
                asm.endScope();
                switchClosed.add(switchNode);
            }
        }
    }
    
    private void closeBranchBlock(Block block) {
        final Block dom = block.getDominator();
        boolean isMerge = block.getBeginNode() instanceof MergeNode;
        if (dom != null && !isMerge && !dom.isLoopHeader() && isIfBlock(dom)) {
            closeIfBlock(block, dom);
        } else if (dom != null && !isMerge && !dom.isLoopHeader() && isSwitchBlock(dom)) {
            closeSwitchBlock(block, dom);
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
