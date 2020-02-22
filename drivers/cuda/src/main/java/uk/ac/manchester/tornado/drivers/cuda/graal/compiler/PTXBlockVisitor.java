package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

public class PTXBlockVisitor implements ControlFlowGraph.RecursiveVisitor<Block> {
    private final PTXCompilationResultBuilder crb;
    private PTXAssembler asm;
    Set<Block> merges;
    Set<Block> closedLoops;
    Set<Block> switches;
    Set<Node> switchClosed;

    public PTXBlockVisitor(PTXCompilationResultBuilder resultBuilder) {
        this.crb = resultBuilder;
        asm = crb.getAssembler();
        merges = new HashSet<>();
        switches = new HashSet<>();
        switchClosed = new HashSet<>();
        closedLoops = new HashSet<>();
    }

    private void emitBeginBlockForElseStatement(Block dom, Block block) {
        unimplemented("emitBeginBlockForElseStatement in CUDA-PTX");
    }

    private void emitBeginBlockForSwitchStatements(Block dom, Block beginBlockNode) {
        unimplemented("emitBeginBlockForSwitchStatements in CUDA-PTX");
    }

    @Override
    public Block enter(Block block) {
        boolean isMerge = block.getBeginNode() instanceof MergeNode;
        if (isMerge) {
            merges.add(block);
        }

        if (block.isLoopHeader()) {
            crb.emitLoopHeader(block);

        } else {
            // We emit either an ELSE statement or a SWITCH statement
            final Block dom = block.getDominator();
            if (dom != null && !isMerge && !dom.isLoopHeader() && isIfBlock(dom)) {
                emitBeginBlockForElseStatement(dom, block);
            } else if (dom != null && !isMerge && !dom.isLoopHeader() && isSwitchBlock(dom)) {
                emitBeginBlockForSwitchStatements(dom, block);
            }
            crb.emitBlock(block);
        }
        return null;
    }

    private void checkClosingBlockInsideIf(Block b, Block pdom) {
        if (pdom.isLoopHeader() && b.getDominator() != null && isIfBlock(b.getDominator())) {
            if ((b.getDominator().getDominator() != null) && (isIfBlock(b.getDominator().getDominator()))) {
                asm.emitLine("END_SCOPE");
            }
        }
    }

    private void closeSwitchStatement(Block b) {
        unimplemented("closeSwitchStatement in CUDA-PTX");
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
            closedLoops.add(b);
        }
        if (b.getPostdominator() != null) {
            Block pdom = b.getPostdominator();
            if (!merges.contains(pdom) && isMergeBlock(pdom) && switches.contains(b) && isSwitchBlock(b.getDominator())) {
                closeSwitchStatement(b);
            } else {
                checkClosingBlockInsideIf(b, pdom);
            }
        } else {
            closeBranchBlock(b);
        }
    }

    private void closeIfBlock(Block block, Block dom) {
        unimplemented("closeIfBlock in CUDA-PTX");
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
            switchClosed.add(switchNode);
        }
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

    private void closeBranchBlock(Block block) {
        final Block dom = block.getDominator();
        if (isIfBlockNode(block)) {
            closeIfBlock(block, dom);
        } else if (isSwitchBlockNode(block)) {
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
