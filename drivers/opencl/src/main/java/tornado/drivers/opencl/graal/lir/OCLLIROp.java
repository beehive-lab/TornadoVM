package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.compiler.common.LIRKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

public abstract class OCLLIROp extends Value {

    public OCLLIROp(LIRKind lirKind) {
        super(lirKind);
    }

    public final void emit(OCLCompilationResultBuilder crb) {
        emit(crb, crb.getAssembler());
    }

    public abstract void emit(OCLCompilationResultBuilder crb, OCLAssembler asm);

    public LIRKind getLIRKind() {
        return (LIRKind) this.getValueKind();
    }

    public OCLKind getOCLKind() {
        PlatformKind pk = getPlatformKind();
        return (pk instanceof OCLKind) ? (OCLKind) pk : OCLKind.ILLEGAL;
    }

}
