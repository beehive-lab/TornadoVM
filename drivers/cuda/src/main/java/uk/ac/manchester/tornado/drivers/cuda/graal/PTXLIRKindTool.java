package uk.ac.manchester.tornado.drivers.cuda.graal;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDescription;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

public class PTXLIRKindTool implements LIRKindTool {
    private CUDATargetDescription target;

    public PTXLIRKindTool(CUDATargetDescription target) {
        this.target = target;
    }

    @Override
    public LIRKind getIntegerKind(int bits) {
        if (bits <= 8) return LIRKind.value(PTXKind.S8);
        if (bits <= 16) return LIRKind.value(PTXKind.S16);
        if (bits <= 32) return LIRKind.value(PTXKind.S32);
        if (bits <= 64) return LIRKind.value(PTXKind.S64);
        throw shouldNotReachHere();
    }

    @Override
    public LIRKind getFloatingKind(int bits) {
        if (bits == 32) return LIRKind.value(PTXKind.F32);
        if (bits == 64) return LIRKind.value(PTXKind.F64);
        throw shouldNotReachHere();
    }

    @Override
    public LIRKind getObjectKind() {
        return getWordKind();
    }

    @Override
    public LIRKind getWordKind() {
        return LIRKind.value(target.getArch().getWordKind());
    }

    @Override
    public LIRKind getNarrowOopKind() {
        unimplemented("GetNarrowOop not supported yet");
        return null;
    }

    @Override
    public LIRKind getNarrowPointerKind() {
        unimplemented("getNarrowPointerKind not supported yet");
        return null;
    }
}
