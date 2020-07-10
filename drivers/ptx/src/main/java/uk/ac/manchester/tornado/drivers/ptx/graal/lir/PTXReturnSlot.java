package uk.ac.manchester.tornado.drivers.ptx.graal.lir;

import jdk.vm.ci.meta.AllocatableValue;
import org.graalvm.compiler.core.common.LIRKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;

import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.STACK_PTR_NAME;
import static uk.ac.manchester.tornado.drivers.ptx.mm.PTXCallStack.RETURN_VALUE_INDEX;

public class PTXReturnSlot extends AllocatableValue {
    public PTXReturnSlot(LIRKind kind) {
        super(kind);
    }

    @Override
    public String toString() {
        return "RETURN_SLOT";
    }

    public void emit(PTXAssembler asm) {
        asm.emit("%s[%d]", STACK_PTR_NAME, RETURN_VALUE_INDEX);
    }
}
