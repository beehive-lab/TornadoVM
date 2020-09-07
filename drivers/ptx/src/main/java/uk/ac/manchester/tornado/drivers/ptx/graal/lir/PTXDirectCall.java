package uk.ac.manchester.tornado.drivers.ptx.graal.lir;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResultBuilder;

import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.CALL;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.COLON;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.COMMA;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.CURLY_BRACKETS_CLOSE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.ROUND_BRACKETS_CLOSE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.ROUND_BRACKETS_OPEN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.SPACE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.TAB;

public class PTXDirectCall extends PTXLIROp {

    protected DirectCallTargetNode target;
    @LIRInstruction.Def protected Value result;
    @LIRInstruction.Use protected Value[] parameters;

    public PTXDirectCall(DirectCallTargetNode target, Value result, Value[] parameters) {
        super(LIRKind.value(result.getPlatformKind()));
        this.result = result;
        this.parameters = parameters;
        this.target = target;
    }

    @Override
    public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
        final String methodName = PTXCodeUtil.makeMethodName(target.targetMethod());

        asm.emitSymbol(TAB);
        asm.emitSymbol(CALL + SPACE);
        if (result != null && !Value.ILLEGAL.equals(result)) {
            asm.emit("(%s), ", PTXAssembler.toString(result));
        }
        asm.emit(methodName);
        asm.emit(COMMA + SPACE + ROUND_BRACKETS_OPEN);
        int i = 0;
        for (Value param : parameters) {
            PTXKind paramKind = (PTXKind) param.getPlatformKind();
            if (paramKind.isVector()) {
                TornadoInternalError.guarantee(param instanceof Variable, "Function parameter should be a variable !");
                PTXVectorSplit vectorSplit = new PTXVectorSplit((Variable) param);
                for (int j = 0; j < vectorSplit.vectorNames.length; j++) {
                    asm.emit(vectorSplit.vectorNames[j]);
                    if (j < vectorSplit.vectorNames.length - 1) {
                        asm.emit(COMMA + SPACE);
                    }
                }
            } else {
                asm.emit(PTXAssembler.toString(param));
            }
            if (i < parameters.length - 1) {
                asm.emit(COMMA + SPACE);
            }

            i++;
        }
        asm.emit(ROUND_BRACKETS_CLOSE);

        crb.addNonInlinedMethod(target.targetMethod());
    }
}
