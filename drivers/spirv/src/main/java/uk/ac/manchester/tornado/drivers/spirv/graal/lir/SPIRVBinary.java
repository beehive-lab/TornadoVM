package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpIAdd;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpStore;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler.SPIRVBinaryOp;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;

/**
 * To be Completed
 * 
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
            SPIRVId result = asm.module.getNextId();
            System.out.println("GENERTiNG A + B");
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

            SPIRVId a = asm.lookUpLIRInstructions(x);
            SPIRVId b = asm.lookUpLIRInstructions(y);
            // SPIRVId type = asm.lookUpType(getValueKind());

            SPIRVId ulong = asm.primitives.getTypeInt(SPIRVKind.OP_TYPE_INT_64);

            SPIRVId param = asm.getParameterId(0);

            SPIRVId load = asm.module.getNextId();
            asm.currentBlockScope.add(new SPIRVOpLoad(ulong, //
                    load, //
                    param, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))//
            ));

            // FIXME: THis is for an example
            SPIRVId constant = asm.constants.get("24");

            SPIRVId addId = asm.module.getNextId();
            asm.currentBlockScope.add(new SPIRVOpIAdd(ulong, //
                    addId, //
                    load, //
                    constant));

            SPIRVId param1 = asm.getParameterId(1);
            asm.currentBlockScope.add(new SPIRVOpStore(param1, addId, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

            asm.registerLIRInstructionValue(this, addId);
        }
    }

}
