package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.lir.Opcode;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

@Opcode("PRINTF")
public class OCLPrintf extends OCLLIROp {

    private Value[] inputs;

    public OCLPrintf(Value[] inputs) {
        super(LIRKind.Illegal);
        this.inputs = inputs;
    }

    @Override
    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
        asm.emit("printf( \"tornado[%%3d,%%3d,%%3d]> %s", asm.formatConstant((ConstantValue) inputs[0]));

        asm.emit("\", ");
        for (int i = 0; i < 2; i++) {
            asm.emit("get_global_id(%d), ", i);
        }
        asm.emit("get_global_id(%d) ", 2);
        if (inputs.length > 1) {
            asm.emit(", ");
        }
        for (int i = 1; i < inputs.length - 1; i++) {
            asm.emitValue(crb, inputs[i]);
            asm.emit(", ");
        }

        if (inputs.length > 1) {
            asm.emitValue(crb, inputs[inputs.length - 1]);
        }
        asm.emit(")");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("printf( %s", inputs[0]));
        if (inputs.length > 1) {
            sb.append(", ");
        }
        for (int i = 1; i < inputs.length - 1; i++) {
            sb.append(inputs[i]);
            sb.append(", ");
        }
        if (inputs.length > 1) {
            sb.append(inputs[inputs.length - 1]);
        }
        sb.append(" )");
        return sb.toString();
    }

}
