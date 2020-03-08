package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants;

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
        asm.eol();

        final IfNode ifNode = (IfNode) dom.getEndNode();
        if (ifNode.falseSuccessor() == block.getBeginNode()) {
            asm.emitBlockLabel(block.getId());
        }
    }

    private void emitBeginBlockForSwitchStatements(Block beginBlockNode) {
        switches.add(beginBlockNode);

        asm.emitBlock(beginBlockNode.getId());
        asm.emitSymbol(PTXAssemblerConstants.COLON);
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
                emitBeginBlockForSwitchStatements(block);
            }
            crb.emitBlock(block);
        }
        return null;
    }

    private void closeSwitchStatement(Block b) {
        final IntegerSwitchNode switchNode = (IntegerSwitchNode) b.getDominator().getEndNode();
        int blockNumber = getBlockIndexForSwitchStatement(b, switchNode);
        int numCases = getNumberOfCasesForSwitch(switchNode);

        asm.emitSymbol(PTXAssemblerConstants.TAB);
        asm.emit("bra.uni");
        asm.emitSymbol(PTXAssemblerConstants.TAB);
        asm.emitBlock(b.getFirstSuccessor().getId());
        asm.delimiter();
        asm.eol();

        if ((numCases - 1) == blockNumber) {
            switchClosed.add(switchNode);
            asm.eol();
            asm.emitBlock(b.getFirstSuccessor().getId());
            asm.emitSymbol(PTXAssemblerConstants.COLON);
        }
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
