package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.Opcode;

import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

@Opcode("Unroll")
public class OCLPragmaUnroll extends OCLLIROp {

    private int unroll;

    public OCLPragmaUnroll(int unroll) {
        super(LIRKind.Illegal);
        this.unroll = unroll;
    }

    @Override
    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
        asm.emitLine("#pragma unroll " + unroll);
    }
}
