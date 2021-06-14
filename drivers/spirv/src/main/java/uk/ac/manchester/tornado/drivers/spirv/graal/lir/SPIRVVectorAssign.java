package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpCompositeInsert;
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

public class SPIRVVectorAssign {

    abstract static class AssignVector extends SPIRVLIROp {

        protected AssignVector(LIRKind valueKind) {
            super(valueKind);
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
    }

    public static class AssignVectorExpr extends AssignVector {

        private final Value[] values;

        public AssignVectorExpr(LIRKind lirKind, Value... values) {
            super(lirKind);
            this.values = values;
        }

        private SPIRVId emitCompositeInsertN(SPIRVAssembler asm, SPIRVId composite, SPIRVId vectorType, int index) {
            SPIRVId spirvIdS1 = getId(values[index], asm, (SPIRVKind) values[index].getPlatformKind());
            SPIRVId compositeInsert = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpCompositeInsert( //
                    vectorType, //
                    compositeInsert, //
                    spirvIdS1, //
                    composite, //
                    new SPIRVMultipleOperands<>(new SPIRVLiteralInteger(index))));
            return compositeInsert;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVId vectorType = asm.primitives.getTypePrimitive(getSPIRVPlatformKind());
            SPIRVId composite0 = asm.primitives.getUndef(getSPIRVPlatformKind());
            for (int i = 0; i < values.length; i++) {
                composite0 = emitCompositeInsertN(asm, composite0, vectorType, i);
            }
            SPIRVLogger.traceCodeGen("emit VectorComposite: " + this + ": " + values.length + getSPIRVPlatformKind());
            asm.registerLIRInstructionValue(this, composite0);
        }
    }
}
