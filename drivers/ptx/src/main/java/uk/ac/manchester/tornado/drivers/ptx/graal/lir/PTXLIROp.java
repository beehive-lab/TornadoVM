package uk.ac.manchester.tornado.drivers.ptx.graal.lir;

import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Variable;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResultBuilder;

public abstract class PTXLIROp extends Value {
    public PTXLIROp(ValueKind<?> valueKind) {
        super(valueKind);
    }

    public abstract void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest);

    public final void emit(PTXCompilationResultBuilder crb, Variable dest) {
        emit(crb, crb.getAssembler(), dest);
    }

    public LIRKind getLIRKind() {
        return (LIRKind) this.getValueKind();
    }

    public PTXKind getPTXPlatformKind() {
        PlatformKind platformKind = getPlatformKind();
        return (platformKind instanceof PTXKind) ? (PTXKind) platformKind : PTXKind.ILLEGAL;
    }
}
