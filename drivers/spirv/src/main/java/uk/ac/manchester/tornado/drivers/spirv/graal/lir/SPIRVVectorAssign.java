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

    public static class Assign2Expr extends SPIRVLIROp {

        private final Value s0;
        private final Value s1;

        public Assign2Expr(LIRKind valueKind, Value s0, Value s1) {
            super(valueKind);
            this.s0 = s0;
            this.s1 = s1;
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

            SPIRVId spirvIdS0 = getId(s0, asm, (SPIRVKind) s0.getPlatformKind());

            SPIRVId compositeInsertId0 = asm.module.getNextId();

            SPIRVId vectorType = asm.primitives.getTypePrimitive(getSPIRVPlatformKind());

            SPIRVId undef = asm.primitives.getUndef(getSPIRVPlatformKind());

            asm.currentBlockScope().add(new SPIRVOpCompositeInsert( //
                    vectorType, //
                    compositeInsertId0, //
                    spirvIdS0, //
                    undef, //
                    new SPIRVMultipleOperands<>(new SPIRVLiteralInteger(0))));

            SPIRVId spirvIdS1 = getId(s1, asm, (SPIRVKind) s1.getPlatformKind());

            SPIRVId compositeInsertId1 = asm.module.getNextId();

            asm.currentBlockScope().add(new SPIRVOpCompositeInsert( //
                    vectorType, //
                    compositeInsertId1, //
                    spirvIdS1, //
                    compositeInsertId0, //
                    new SPIRVMultipleOperands<>(new SPIRVLiteralInteger(1))));

            SPIRVLogger.traceCodeGen("emit VectorComposite: " + this + " s0: " + s0 + " s1: " + s1 + " type:" + getSPIRVPlatformKind());

            asm.registerLIRInstructionValue(this, compositeInsertId1);
        }
    }

}
