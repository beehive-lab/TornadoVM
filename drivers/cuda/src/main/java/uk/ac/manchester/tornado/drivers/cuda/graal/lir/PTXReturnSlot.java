package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.AllocatableValue;
import org.graalvm.compiler.core.common.LIRKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;

import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.FRAME_REF_NAME;
import static uk.ac.manchester.tornado.drivers.cuda.mm.CUDACallStack.RETURN_VALUE_INDEX;

public class PTXReturnSlot extends AllocatableValue {
    public PTXReturnSlot(LIRKind kind) {
        super(kind);
    }

    @Override
    public String toString() {
        return "RETURN_SLOT";
    }

    public void emit(PTXAssembler asm) {
        asm.emit("%s[%d]", FRAME_REF_NAME, RETURN_VALUE_INDEX);
    }
}
