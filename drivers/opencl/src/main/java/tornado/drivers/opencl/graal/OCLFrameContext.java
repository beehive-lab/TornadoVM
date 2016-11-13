package tornado.drivers.opencl.graal;

import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.asm.FrameContext;
import tornado.common.TornadoLogger;

public class OCLFrameContext extends TornadoLogger implements FrameContext {

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
