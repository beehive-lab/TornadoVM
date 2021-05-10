package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;

import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.SPIRVModule;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;

public abstract class SPIRVLIROp extends Value {

    protected SPIRVModule module;

    protected SPIRVLIROp(LIRKind valueKind) {
        super(valueKind);
    }

    protected SPIRVLIROp(LIRKind valueKind, SPIRVModule module) {
        super(valueKind);
        this.module = module;
    }

    public final void emit(SPIRVCompilationResultBuilder crb) {
        emit(crb, crb.getAssembler());
    }

    public abstract void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm);

    public LIRKind getLIRKind() {
        return (LIRKind) this.getValueKind();
    }

    public SPIRVKind getSPIRVPlatformKind() {
        PlatformKind kind = getPlatformKind();
        return (kind instanceof SPIRVKind) ? (SPIRVKind) kind : SPIRVKind.ILLEGAL;
    }

}
