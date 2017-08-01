package tornado.drivers.opencl.graal.compiler;

import java.util.HashSet;
import java.util.Set;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import tornado.drivers.opencl.graal.asm.OCLAssembler;

public class OCLBlockVisitor implements ControlFlowGraph.RecursiveVisitor<Block> {

    OCLCompilationResultBuilder resBuilder;
    OCLAssembler asm;
    Set<Block> merges;

    public OCLBlockVisitor(OCLCompilationResultBuilder resBuilder) {
        this.resBuilder = resBuilder;
        this.asm = resBuilder.getAssembler();
        merges = new HashSet<>();
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

            if (dom != null
                    && !isMerge
                    && !dom.isLoopHeader()
                    && isIfBlock(dom)) {
                final IfNode condition = (IfNode) dom.getEndNode();
                if (condition.falseSuccessor() == b.getBeginNode()) {
                    asm.indent();
                    asm.elseStmt();
                    asm.eol();
                }
                asm.beginScope();
                asm.eolOn();
            }

            resBuilder.emitBlock(b);
        }

        return null;
    }

    @Override
    public void exit(Block b, Block value) {
        if (b.isLoopEnd()) {
//            asm.emitLine(String.format("// block %d exits loop %d", b.getId(), b.getLoop().getHeader().getId()));
            asm.endScope();
        }
        if (b.getPostdominator() != null) {
            Block pdom = b.getPostdominator();
            AbstractBeginNode beginNode = pdom.getBeginNode();
            if (!merges.contains(pdom) && isMergeBlock(pdom)) {
                //asm.eolOff();
//                asm.emitLine(
//                        String.format("// block %d merges control flow -> pdom = %d depth=%d",
//                                b.getId(), pdom.getId(), pdom.getDominatorDepth()));
                asm.endScope();
            }
        }

    }

    private static boolean isMergeBlock(Block block) {
        return block.getBeginNode() instanceof MergeNode;
    }

    private static boolean isIfBlock(Block block) {
        return block.getEndNode() instanceof IfNode;
    }

}
