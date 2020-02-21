package uk.ac.manchester.tornado.drivers.cuda;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;

public class CUDATargetDescription extends TargetDescription {

    private static final int STACK_ALIGNMENT = 8;
    private static final boolean INLINE_OBJECT = true;

    public CUDATargetDescription(Architecture arch) {
        super(arch, false, STACK_ALIGNMENT, 4096, INLINE_OBJECT);
    }

    public PTXArchitecture getArch() {
        return (PTXArchitecture) arch;
    }

    public PTXKind getPTXKind(JavaKind javaKind) {
        return (PTXKind) arch.getPlatformKind(javaKind);
    }
}
