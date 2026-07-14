package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.lir.Opcode;

import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

@Opcode("Unroll")
public class OCLPragmaUnroll extends OCLLIROp {

    private final int unrollFactor;

    public OCLPragmaUnroll(int unrollFactor) {
        super(LIRKind.Illegal);
        this.unrollFactor = unrollFactor;
    }

    @Override
    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
        asm.emitLine("#pragma unroll " + unrollFactor);
    }
}
