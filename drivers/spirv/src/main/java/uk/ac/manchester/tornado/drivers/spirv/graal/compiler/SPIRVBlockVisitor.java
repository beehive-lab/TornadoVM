package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;

import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
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
        SPIRVLogger.traceCodeGen("Entering block: " + b);
        if (!b.isLoopHeader() && b.getPredecessorCount() != 0) {
            // Do not generate a label for the first block. This was already generated in
            // the SPIR-V preamble because we need the declaration of all variables.
            assembler.emitBlockLabelIfNotPresent(b, assembler.functionScope);
        }
        if (!b.isLoopHeader()) {
            System.out.println("BLOCK VISITOR");
            String blockName = assembler.composeUniqueLabelName(b.toString());
            System.out.println("BLOCK NAME: + " + blockName);
            System.out.println("FOUND??? : " + assembler.blockTable.get(blockName));
            assembler.pushScope(assembler.blockTable.get(blockName));
        }
        crb.emitBlock(b);
        return null;
    }

    @Override
    public void exit(Block b, Block value) {
        SPIRVLogger.traceCodeGen("EXIT BLOCK: " + b);
        assembler.popScope();
    }
}
