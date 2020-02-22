package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Variable;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResultBuilder;

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
}
