package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction.Def;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.nodes.DirectCallTargetNode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpFunctionCall;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpName;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;

public class SPIRVDirectCall extends SPIRVLIROp {

    private DirectCallTargetNode targetNode;
    private LIRFrameState frameState;

    @Def
    private Value result;

    @Use
    private Value[] parameters;

    public SPIRVDirectCall(DirectCallTargetNode targetNode, Value result, Value[] parameters, LIRFrameState frameState) {
        super(LIRKind.value(result.getPlatformKind()));
        this.targetNode = targetNode;
        this.result = result;
        this.parameters = parameters;
        this.frameState = frameState;
    }

    @Override
    public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

        SPIRVLogger.traceCodeGen("emit OpFunctionCall for method: " + targetNode.targetMethod().getName());

        final String methodName = targetNode.targetMethod().getName();

        int paramIndex = 0;
        SPIRVId[] idsForParameters = new SPIRVId[parameters.length];
        for (Value parameter : parameters) {
            SPIRVKind spirvKind = (SPIRVKind) parameter.getPlatformKind();
            if (spirvKind.isVector()) {
                throw new RuntimeException("No supported yet");
            } else {
                // Emit Load
                idsForParameters[paramIndex] = getId(parameter, asm, spirvKind);
            }
            paramIndex++;
        }

        SPIRVKind resultType = (SPIRVKind) result.getPlatformKind();
        SPIRVId resultTypeId = asm.primitives.getTypePrimitive(resultType);

        SPIRVId functionResult = asm.module.getNextId();

        SPIRVMultipleOperands<SPIRVId> operands = new SPIRVMultipleOperands<>(idsForParameters);

        // XX: Fix this ID for the function call
        SPIRVId functionToCall = asm.module.getNextId();
        asm.module.add(new SPIRVOpName(functionToCall, new SPIRVLiteralString(methodName)));

        asm.currentBlockScope().add(new SPIRVOpFunctionCall( //
                resultTypeId, //
                functionResult, //
                functionToCall, //
                operands));

        asm.registerLIRInstructionValue(result, functionResult);

        crb.addNonInlinedMethod(targetNode.targetMethod());
    }
}
