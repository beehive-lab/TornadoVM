package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstruction.Def;
import com.oracle.graal.lir.LIRInstruction.Use;
import com.oracle.graal.nodes.DirectCallTargetNode;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.OCLUtils;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

import static tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.HEAP_REF_NAME;
import static tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.STACK_REF_NAME;

public class OCLDirectCall extends OCLEmitable {

    protected DirectCallTargetNode target;
    protected LIRFrameState frameState;
    @Def
    protected Value result;
    @Use
    protected Value[] parameters;

    public OCLDirectCall(DirectCallTargetNode target, Value result,
            Value[] parameters, LIRFrameState frameState) {
        super(LIRKind.value(result.getPlatformKind()));
        this.result = result;
        this.parameters = parameters;
        this.target = target;
        this.frameState = frameState;
    }

    @Override
    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {

        final String methodName = OCLUtils
                .makeMethodName(target.targetMethod());

        asm.emit(methodName);
        asm.emit("(");
        int i = 0;
        asm.emit(String.format("%s, ", HEAP_REF_NAME));
        asm.emit(String.format("%s, ", STACK_REF_NAME));
        for (Value param : parameters) {
            // System.out.printf("param: %s\n",param);
            asm.emit(asm.toString(param));
            if (i < parameters.length - 1) {
                asm.emit(", ");
            }

            i++;
        }
        asm.emit(")");

        // System.out.printf("direct call: method=%s, frameState=%s\n",target.targetMethod(),frameState);
        crb.addNonInlinedMethod(target.targetMethod());

    }
}
