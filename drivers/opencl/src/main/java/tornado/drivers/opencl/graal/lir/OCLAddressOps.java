package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.lir.Opcode;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

public class OCLAddressOps {

    @Opcode("VSEL")
    public static class OCLVectorElementSelect extends OCLEmitable {

        final Value vector;
        private final Value selection;

        public OCLVectorElementSelect(LIRKind lirKind, Value vector, Value selection) {
            super(lirKind);
            this.vector = vector;
            this.selection = selection;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.value(crb, vector);
            asm.emitSymbol(".");
            asm.value(crb, selection);
        }

    }

    @Opcode("VMOV")
    public static class OCLVectorElement extends OCLEmitable {

        private final Value vector;
        private final int laneId;

        public OCLVectorElement(LIRKind lirKind, Value vector, int laneId) {
            super(lirKind);
            this.vector = vector;
            this.laneId = laneId;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.value(crb, vector);
            asm.emit(".s");
            asm.emit(Integer.toHexString(laneId).toLowerCase());
        }

    }
}
