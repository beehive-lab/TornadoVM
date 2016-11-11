package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.lir.Opcode;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

@Opcode("VSEL")
public class OCLVectorElementSelect extends OCLLIROp {

    final Value vector;
    private final Value selection;

    public OCLVectorElementSelect(LIRKind lirKind, Value vector, Value selection) {
        super(lirKind);
        this.vector = vector;
        this.selection = selection;
    }

    @Override
    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
        asm.emitValue(crb, vector);
        asm.emitSymbol(".s");
        asm.emitValue(crb, selection);
    }

}
