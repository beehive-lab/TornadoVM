package uk.ac.manchester.tornado.drivers.spirv.graal.asm;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;

import org.graalvm.compiler.asm.AbstractAddress;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIROp;

public final class SPIRVAssembler extends Assembler {

    public SPIRVAssembler(TargetDescription target) {
        super(target);
    }

    /**
     * Base class for OpenCL opcodes.
     */
    public static class SPIRVOp {

        protected final String opcode;

        protected SPIRVOp(String opcode) {
            this.opcode = opcode;
        }

        protected final void emitOpcode(SPIRVAssembler asm) {
            asm.emit(opcode);
        }

        public boolean equals(SPIRVOp other) {
            return opcode.equals(other.opcode);
        }

        @Override
        public String toString() {
            return opcode;
        }
    }

    public static class SPIRVUnaryOp extends SPIRVOp {

        private final boolean prefix;

        protected SPIRVUnaryOp(String opcode) {
            this(opcode, false);
        }

        protected SPIRVUnaryOp(String opcode, boolean prefix) {
            super(opcode);
            this.prefix = prefix;
        }

        public void emit(SPIRVCompilationResultBuilder crb, Value x) {
            final SPIRVAssembler asm = crb.getAssembler();
            if (prefix) {
                emitOpcode(asm);
                asm.emitValueOrOp(crb, x);
            } else {
                asm.emitValueOrOp(crb, x);
                emitOpcode(asm);
            }
        }
    }

    public void emit(String str) {
        emitSubString(str);
    }

    public void emitSubString(String str) {
        guarantee(str != null, "emitting null string");
        for (byte b : str.getBytes()) {
            emitByte(b);
        }
    }

    @Override
    public void align(int modulus) {

    }

    @Override
    public void jmp(Label l) {

    }

    @Override
    protected void patchJumpTarget(int branch, int jumpTarget) {

    }

    @Override
    public AbstractAddress makeAddress(Register base, int displacement) {
        return null;
    }

    @Override
    public AbstractAddress getPlaceholder(int instructionStartPosition) {
        return null;
    }

    @Override
    public void ensureUniquePC() {

    }

    public void emitValue(SPIRVCompilationResultBuilder crb, Value value) {
        // (toString(value));
    }

    public void emitValueOrOp(SPIRVCompilationResultBuilder crb, Value value) {
        if (value instanceof SPIRVLIROp) {
            ((SPIRVLIROp) value).emit(crb, this);
        } else {
            emitValue(crb, value);
        }
    }
}
