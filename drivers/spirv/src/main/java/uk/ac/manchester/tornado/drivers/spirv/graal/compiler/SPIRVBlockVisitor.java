package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;

import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;

public class SPIRVBlockVisitor implements ControlFlowGraph.RecursiveVisitor<Block> {

    private final SPIRVCompilationResultBuilder crb;
    private SPIRVAssembler assembler;

    public SPIRVBlockVisitor(SPIRVCompilationResultBuilder resultBuilder) {
        this.crb = resultBuilder;
        this.assembler = resultBuilder.getAssembler();
    }

    @Override
    public Block enter(Block b) {
        System.out.println("Traversing block: " + b);
        // assembler.emitBlockLabel(block);
        // crb.emitBlock(block);
        return null;
    }

    @Override
    public void exit(Block b, Block value) {
        // empty implementation
    }
}
