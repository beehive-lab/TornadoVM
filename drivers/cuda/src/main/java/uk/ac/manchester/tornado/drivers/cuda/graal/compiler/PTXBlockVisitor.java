package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;

public class PTXBlockVisitor implements ControlFlowGraph.RecursiveVisitor<Block> {
    private final PTXCompilationResultBuilder crb;
    private PTXAssembler asm;

    public PTXBlockVisitor(PTXCompilationResultBuilder resultBuilder, PTXAssembler asm) {
        this.crb = resultBuilder;
        this.asm = asm;
    }

    @Override
    public Block enter(Block block) {
        asm.eol();
        asm.emitBlockLabel(block);
        crb.emitBlock(block);
        return null;
    }

    @Override
    public void exit(Block b, Block value) {
    }
}
