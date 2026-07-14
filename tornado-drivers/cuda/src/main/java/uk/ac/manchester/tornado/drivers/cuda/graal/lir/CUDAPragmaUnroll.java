package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.lir.Opcode;

import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResultBuilder;

@Opcode("Unroll")
public class CUDAPragmaUnroll extends CUDALIROp {

    private final int unrollFactor;

    public CUDAPragmaUnroll(int unrollFactor) {
        super(LIRKind.Illegal);
        this.unrollFactor = unrollFactor;
    }

    @Override
    public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
        asm.emitLine("#pragma unroll " + unrollFactor);
    }
}
