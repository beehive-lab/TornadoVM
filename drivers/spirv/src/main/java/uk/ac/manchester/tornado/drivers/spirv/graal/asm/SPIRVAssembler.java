package uk.ac.manchester.tornado.drivers.spirv.graal.asm;

import org.graalvm.compiler.asm.AbstractAddress;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

public final class SPIRVAssembler extends Assembler {

    public SPIRVAssembler(TargetDescription target) {
        super(target);
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
}
