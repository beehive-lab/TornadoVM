package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.lir.Opcode;
import jdk.vm.ci.meta.AllocatableValue;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryOp;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

@Opcode("RETURN VALUE")
public class OCLReturnSlot extends AllocatableValue {

    private OCLNullaryOp op;

    public OCLReturnSlot(LIRKind lirKind) {
        super(lirKind);
        op = OCLNullaryOp.SLOTS_BASE_ADDRESS;
    }

    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
        op.emit(crb);
    }

    @Override
    public String toString() {
        return op.toString();
    }

}
