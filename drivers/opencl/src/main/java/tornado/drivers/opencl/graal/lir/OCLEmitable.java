package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.compiler.common.LIRKind;
import jdk.vm.ci.meta.PlatformKind;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

public abstract class OCLEmitable {

    protected LIRKind lirKind;

    public OCLEmitable(LIRKind lirKind) {
        this.lirKind = lirKind;
    }

    public final void emit(OCLCompilationResultBuilder crb) {
        emit(crb, crb.getAssembler());
    }

    public abstract void emit(OCLCompilationResultBuilder crb, OCLAssembler asm);

    public LIRKind getLIRKind() {
        return lirKind;
    }

    public PlatformKind getPlatformKind() {
        return lirKind.getPlatformKind();
    }

}
