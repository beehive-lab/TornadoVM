package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.lir.Opcode;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

@Opcode("PRINTF")
public class OCLPrintf extends OCLEmitable {

    private Value[] inputs;

    public OCLPrintf(Value[] inputs) {
        super(LIRKind.Illegal);
        this.inputs = inputs;
    }

    @Override
    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
        asm.emit("printf( \"tornado[%3d,%3d,%3d]> ");
        asm.value(crb, inputs[0]);
        asm.emit("\", ");
        for (int i = 0; i < 3; i++) {
            asm.emit("get_global_id(%d), ", i);
        }
        for (int i = 1; i < inputs.length - 1; i++) {
            asm.value(crb, inputs[i]);
            asm.emit(", ");
        }
        asm.value(crb, inputs[inputs.length - 1]);
        asm.emit(")");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<%s,%s,%s> tprintf( %s", inputs[0], inputs[1], inputs[2], inputs[3]));
        for (int i = 4; i < inputs.length - 1; i++) {
            sb.append(inputs[i]);
            sb.append(", ");
        }
        sb.append(inputs[inputs.length - 1]);
        sb.append(" )");
        return sb.toString();
    }

}
