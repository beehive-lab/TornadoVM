package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Opcode;

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
