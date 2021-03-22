package uk.ac.manchester.tornado.drivers.spirv.graal;

import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;

/**
 * Mapping for a native frame.
 */
public class SPIRVFrameContext implements FrameContext {
    @Override
    public void enter(CompilationResultBuilder crb) {

    }

    @Override
    public void leave(CompilationResultBuilder crb) {

    }

    @Override
    public void returned(CompilationResultBuilder crb) {

    }

    @Override
    public boolean hasFrame() {
        return false;
    }
}
