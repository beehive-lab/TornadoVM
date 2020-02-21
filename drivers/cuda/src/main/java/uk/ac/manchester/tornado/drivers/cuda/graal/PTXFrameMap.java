package uk.ac.manchester.tornado.drivers.cuda.graal;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.lir.framemap.FrameMap;
import uk.ac.manchester.tornado.drivers.cuda.graal.backend.PTXBackend;

public class PTXFrameMap extends FrameMap {
    public PTXFrameMap(CodeCacheProvider codeCache,
                       RegisterConfig registerConfig,
                       ReferenceMapBuilderFactory mapBuilderFactory) {
        super(codeCache, registerConfig, mapBuilderFactory);
    }

    @Override
    public int totalFrameSize() {
        return frameSize() + returnAddressSize();
    }

    @Override
    public int currentFrameSize() {
        return alignFrameSize(outgoingSize + spillSize - returnAddressSize());
    }

    @Override
    protected int alignFrameSize(int size) {
        return NumUtil.roundUp(
                size + returnAddressSize(),
                getTarget().stackAlignment
        ) - returnAddressSize();
    }
}
