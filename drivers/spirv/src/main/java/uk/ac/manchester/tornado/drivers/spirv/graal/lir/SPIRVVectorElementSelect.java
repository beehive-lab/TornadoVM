package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.Variable;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpCompositeExtract;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

@Opcode("VSEL")
public class SPIRVVectorElementSelect extends SPIRVLIROp {

    private final Variable vector;
    private final int laneId;

    public SPIRVVectorElementSelect(LIRKind lirKind, Variable vector, int laneId) {
        super(lirKind);
        this.vector = vector;
        this.laneId = laneId;
    }

    public int getLaneId() {
        return laneId;
    }

    public Variable getVector() {
        return this.vector;
    }

    protected SPIRVId getId(Value inputValue, SPIRVAssembler asm, SPIRVKind spirvKind) {
        if (inputValue instanceof ConstantValue) {
            SPIRVKind kind = (SPIRVKind) inputValue.getPlatformKind();
            return asm.lookUpConstant(((ConstantValue) inputValue).getConstant().toValueString(), kind);
        } else {
            SPIRVId param = asm.lookUpLIRInstructions(inputValue);
            if (!TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                // We need to perform a load first
                SPIRVLogger.traceCodeGen("emit LOAD Variable: " + inputValue);
                SPIRVId load = asm.module.getNextId();
                SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);
                asm.currentBlockScope().add(new SPIRVOpLoad(//
                        type, //
                        load, //
                        param, //
                        new SPIRVOptionalOperand<>( //
                                SPIRVMemoryAccess.Aligned( //
                                        new SPIRVLiteralInteger(spirvKind.getByteCount())))//
                ));
                return load;
            } else {
                return param;
            }
        }
    }

    @Override
    public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
        SPIRVId vectorId = getId(vector, asm, getSPIRVPlatformKind());

        SPIRVKind vectorElementKind = getSPIRVPlatformKind().getElementKind();
        SPIRVId idElementKind = asm.primitives.getTypePrimitive(vectorElementKind);

        SPIRVId resultSelect1 = asm.module.getNextId();
        asm.currentBlockScope().add(new SPIRVOpCompositeExtract(idElementKind, resultSelect1, vectorId, new SPIRVMultipleOperands<>(new SPIRVLiteralInteger(getLaneId()))));

        asm.registerLIRInstructionValue(this, resultSelect1);
    }
}
