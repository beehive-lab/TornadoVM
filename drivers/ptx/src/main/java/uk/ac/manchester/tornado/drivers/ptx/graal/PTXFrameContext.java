package uk.ac.manchester.tornado.drivers.ptx.graal;

import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class PTXFrameContext extends TornadoLogger implements FrameContext {
    @Override
    public void enter(CompilationResultBuilder crb) {
        trace("FrameContext.enter()");

    }

    @Override
    public boolean hasFrame() {
        return false;
    }

    @Override
    public void leave(CompilationResultBuilder crb) {
        trace("FrameContext.leave()");

    }
}
