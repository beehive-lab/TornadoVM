package uk.ac.manchester.tornado.drivers.metal.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Opcode;

import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompilationResultBuilder;

@Opcode("Unroll")
public class MetalPragmaUnroll extends MetalLIROp {

    private final int unrollFactor;

    public MetalPragmaUnroll(int unrollFactor) {
        super(LIRKind.Illegal);
        this.unrollFactor = unrollFactor;
    }

    @Override
    public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
        asm.emitLine("#pragma unroll " + unrollFactor);
    }
}
