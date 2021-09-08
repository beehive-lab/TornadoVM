package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction.Def;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.nodes.DirectCallTargetNode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpFunctionCall;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVUtils;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

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

        final String methodName = SPIRVUtils.makeMethodName(targetNode.targetMethod());

        SPIRVId[] idsForParameters;
        int paramIndex = 0;
        if (TornadoOptions.DIRECT_CALL_WITH_LOAD_HEAP) {
            SPIRVId[] ids = asm.loadHeapPointerAndFrameIndex();
            paramIndex = 2;
            idsForParameters = new SPIRVId[parameters.length + 2];
            idsForParameters[0] = ids[0];
            idsForParameters[1] = ids[1];
        } else {
            idsForParameters = new SPIRVId[parameters.length];
        }

        for (Value parameter : parameters) {
            SPIRVKind spirvKind = (SPIRVKind) parameter.getPlatformKind();
            if (spirvKind.isVector()) {
                // Load Vector - Not supported for now
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

        // At this point we need to register the new function
        SPIRVId functionToCall = asm.getMethodRegistrationId(methodName);
        if (functionToCall == null) {
            functionToCall = asm.registerNewMethod(methodName);
        }

        asm.currentBlockScope().add(new SPIRVOpFunctionCall( //
                resultTypeId, //
                functionResult, //
                functionToCall, //
                operands));

        asm.registerLIRInstructionValue(result, functionResult);

        // XXX: Enable the following call for generating multiple methods
        // crb.addNonInlinedMethod(targetNode.targetMethod());
    }
}
