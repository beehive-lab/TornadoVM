package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;

public class SPIRVUnary {

    protected static class UnaryConsumer extends SPIRVLIROp {

        @Opcode
        protected final SPIRVAssembler.SPIRVUnaryOp opcode;

        @LIRInstruction.Use
        protected Value value;

        protected UnaryConsumer(SPIRVAssembler.SPIRVUnaryOp opcode, LIRKind valueKind, Value value) {
            super(valueKind);
            this.opcode = opcode;
            this.value = value;
        }

        public Value getValue() {
            return value;
        }

        public SPIRVAssembler.SPIRVUnaryOp getOpcode() {
            return opcode;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            // TODO: from my view, we can generate SPIRV-bin with the module spirv-proto
            opcode.emit(crb, value);
        }

    }

}
