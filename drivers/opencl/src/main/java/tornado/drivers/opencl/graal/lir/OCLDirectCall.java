package tornado.drivers.opencl.graal.lir;

import static tornado.drivers.opencl.graal.asm.OpenCLAssemblerConstants.HEAP_REF_NAME;
import static tornado.drivers.opencl.graal.asm.OpenCLAssemblerConstants.STACK_REF_NAME;
import tornado.drivers.opencl.graal.OCLUtils;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstruction.Def;
import com.oracle.graal.lir.LIRInstruction.Use;
import com.oracle.graal.nodes.DirectCallTargetNode;

public class OCLDirectCall implements OCLEmitable {

    protected Kind kind;
    protected DirectCallTargetNode target;
    protected LIRFrameState frameState;
    @Def
    protected Value result;
    @Use
    protected Value[] parameters;

    public OCLDirectCall(Kind kind, DirectCallTargetNode target, Value result,
            Value[] parameters, LIRFrameState frameState) {
        this.kind = kind;
        this.result = result;
        this.parameters = parameters;
        this.target = target;
        this.frameState = frameState;
    }

    @Override
    public void emit(OCLCompilationResultBuilder crb) {
        final OpenCLAssembler asm = crb.getAssembler();

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
            if (i < parameters.length - 1)
                asm.emit(", ");

            i++;
        }
        asm.emit(")");

        // System.out.printf("direct call: method=%s, frameState=%s\n",target.targetMethod(),frameState);

        crb.addNonInlinedMethod(target.targetMethod());

    }

    @Override
    public LIRKind getLIRKind() {
        return LIRKind.value(kind);
    }

    @Override
    public PlatformKind getPlatformKind() {
        return kind;
    }

    @Override
    public Kind getKind() {
        return kind;
    }

}
