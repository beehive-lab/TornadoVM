package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.lir.Opcode;
import jdk.vm.ci.meta.AllocatableValue;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryOp;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

import static tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.HEAP_REF_NAME;

@Opcode("RETURN VALUE")
public class OCLReturnSlot extends AllocatableValue {

    private OCLNullaryOp op;

    public OCLReturnSlot(LIRKind lirKind) {
        super(lirKind);
        op = OCLNullaryOp.SLOTS_BASE_ADDRESS;
    }

    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
        OCLKind type = ((OCLKind) getPlatformKind());
        asm.emit("*((__global %s *) %s)", type, HEAP_REF_NAME);
    }

    @Override
    public String toString() {
        return "RETURN_SLOT";
    }

}
