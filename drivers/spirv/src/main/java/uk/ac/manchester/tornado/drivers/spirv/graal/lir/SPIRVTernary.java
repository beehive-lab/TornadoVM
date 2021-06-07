package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRInstruction;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpExtInst;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralExtInstInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class SPIRVTernary {

    /**
     * Abstract operation which consumes two inputs
     */
    protected static class TernaryConsumer extends SPIRVLIROp {

        @LIRInstruction.Use
        protected Value x;
        @LIRInstruction.Use
        protected Value y;
        @LIRInstruction.Use
        protected Value z;

        protected TernaryConsumer(LIRKind lirKind, Value x, Value y, Value z) {
            super(lirKind);
            this.x = x;
            this.y = y;
            this.z = z;
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

        }
    }

    public static class TernaryIntrinsic extends TernaryConsumer {

        private SPIRVUnary.Intrinsic.OpenCLIntrinsic builtIn;

        public TernaryIntrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic builtIn, LIRKind lirKind, Value x, Value y, Value z) {
            super(lirKind, x, y, z);
            this.builtIn = builtIn;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            LIRKind lirKind = getLIRKind();
            SPIRVKind spirvKind = (SPIRVKind) lirKind.getPlatformKind();
            SPIRVId typeOperation = asm.primitives.getTypePrimitive(spirvKind);

            SPIRVId a = getId(x, asm, (SPIRVKind) x.getPlatformKind());
            SPIRVId b = getId(y, asm, (SPIRVKind) y.getPlatformKind());
            SPIRVId c = getId(z, asm, (SPIRVKind) z.getPlatformKind());

            SPIRVLogger.traceCodeGen("emit SPIRVLiteralExtInstInteger (Ternary Intrinsic): " + builtIn.getName() + " (" + a + "," + b + "," + c + ")");

            SPIRVId result = asm.module.getNextId();
            SPIRVId set = asm.getOpenclImport();
            SPIRVLiteralExtInstInteger intrinsic = new SPIRVLiteralExtInstInteger(builtIn.getValue(), builtIn.getName());
            asm.currentBlockScope().add(new SPIRVOpExtInst(typeOperation, result, set, intrinsic, new SPIRVMultipleOperands<>(a, b, c)));
            asm.registerLIRInstructionValue(this, result);
        }

    }
}
