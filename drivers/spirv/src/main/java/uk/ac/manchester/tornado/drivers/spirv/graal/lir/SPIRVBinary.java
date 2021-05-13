package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpIAdd;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpSLessThan;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpShiftLeftLogical;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler.SPIRVBinaryOp;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;

/**
 * To be Completed
 */
public class SPIRVBinary {

    protected static class BinaryConsumer extends SPIRVLIROp {

        @Opcode
        protected SPIRVBinaryOp opcode;

        @Use
        protected Value x;

        @Use
        protected Value y;

        protected BinaryConsumer(SPIRVBinaryOp instruction, LIRKind valueKind, Value x, Value y) {
            super(valueKind);
            this.opcode = instruction;
            this.x = x;
            this.y = y;
        }

        public SPIRVBinaryOp getInstruction() {
            return opcode;
        }

        public Value getX() {
            return x;
        }

        public Value getY() {
            return y;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

        }

        protected SPIRVId getId(Value inputValue, SPIRVAssembler asm, SPIRVId typeOperation, SPIRVKind spirvKind) {
            if (inputValue instanceof ConstantValue) {
                return asm.constants.get(((ConstantValue) inputValue).getConstant().toValueString());
            } else {
                SPIRVLogger.traceCodeGen("emit LOAD Variable: " + inputValue);
                // We need to perform a load first
                SPIRVId param = asm.lookUpLIRInstructions(inputValue);
                SPIRVId load = asm.module.getNextId();
                asm.currentBlockScope.add(new SPIRVOpLoad(//
                        typeOperation, //
                        load, //
                        param, //
                        new SPIRVOptionalOperand<>( //
                                SPIRVMemoryAccess.Aligned( //
                                        new SPIRVLiteralInteger(spirvKind.getByteCount())))//
                ));
                return load;
            }
        }

    }

    public static class Expr extends BinaryConsumer {

        public Expr(SPIRVBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }
    }

    /**
     * Additions - based o the LIRKind (type), we will perform an addition.
     */
    public static class AddExpr extends BinaryConsumer {

        public AddExpr(SPIRVBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            LIRKind lirKind = getLIRKind();
            SPIRVKind spirvKind = (SPIRVKind) lirKind.getPlatformKind();
            SPIRVId typeOperation = asm.primitives.getTypePrimitive(spirvKind);

            SPIRVId a = getId(x, asm, typeOperation, spirvKind);
            SPIRVId b = getId(y, asm, typeOperation, spirvKind);

            SPIRVLogger.traceCodeGen("emit SPIRVOpIAdd: " + x + " + " + y);

            SPIRVId addId = asm.module.getNextId();
            if (spirvKind.isInteger()) {
                asm.currentBlockScope.add(new SPIRVOpIAdd( //
                        typeOperation, //
                        addId, //
                        a, //
                        b));
            } else {
                throw new RuntimeException("Addition type not supported");
            }

            asm.registerLIRInstructionValue(this, addId);
        }
    }

    public static class IntegerLessThan extends BinaryConsumer {

        public IntegerLessThan(SPIRVBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            LIRKind lirKind = getLIRKind();
            SPIRVKind spirvKind = (SPIRVKind) lirKind.getPlatformKind();
            SPIRVId typeOperation = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_32);

            SPIRVId a = getId(x, asm, typeOperation, spirvKind);
            SPIRVId b = getId(y, asm, typeOperation, spirvKind);

            SPIRVLogger.traceCodeGen("emit SPIRVOpSLessThan: " + x + " < " + y);

            SPIRVId typeBoolean = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_BOOL);

            SPIRVId result = asm.module.getNextId();
            asm.currentBlockScope.add(new SPIRVOpSLessThan( //
                    typeBoolean, //
                    result, //
                    a, //
                    b));

            asm.registerLIRInstructionValue(this, result);

        }
    }

    public static class ShiftLeft extends BinaryConsumer {

        public ShiftLeft(SPIRVBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            LIRKind lirKind = getLIRKind();
            SPIRVKind spirvKind = (SPIRVKind) lirKind.getPlatformKind();
            SPIRVId typeOperation = asm.primitives.getTypePrimitive(spirvKind);

            SPIRVId a = getId(x, asm, typeOperation, spirvKind);
            SPIRVId b = getId(y, asm, typeOperation, spirvKind);

            SPIRVLogger.traceCodeGen("emit SPIRVOpShiftLeftLogical: " + x + " << " + y);

            SPIRVId result = asm.module.getNextId();
            asm.currentBlockScope.add(new SPIRVOpShiftLeftLogical( //
                    typeOperation, //
                    result, //
                    a, //
                    b));

            asm.registerLIRInstructionValue(this, result);
        }
    }
}
