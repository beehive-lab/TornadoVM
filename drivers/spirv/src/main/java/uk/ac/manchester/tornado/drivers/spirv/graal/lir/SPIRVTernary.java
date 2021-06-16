package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpExtInst;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralExtInstInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;

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

            SPIRVId a = loadSPIRVId(crb, asm, x);
            SPIRVId b = loadSPIRVId(crb, asm, y);
            SPIRVId c = loadSPIRVId(crb, asm, z);

            SPIRVLogger.traceCodeGen("emit SPIRVLiteralExtInstInteger (Ternary Intrinsic): " + builtIn.getName() + " (" + x + "," + y + "," + z + ")");
            SPIRVLogger.traceCodeGen("" + x.getClass());

            SPIRVId result = asm.module.getNextId();
            SPIRVId set = asm.getOpenclImport();
            SPIRVLiteralExtInstInteger intrinsic = new SPIRVLiteralExtInstInteger(builtIn.getValue(), builtIn.getName());
            asm.currentBlockScope().add(new SPIRVOpExtInst(typeOperation, result, set, intrinsic, new SPIRVMultipleOperands<>(a, b, c)));
            asm.registerLIRInstructionValue(this, result);
        }

    }
}
