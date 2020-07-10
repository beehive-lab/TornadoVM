package uk.ac.manchester.tornado.drivers.ptx.graal;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilderImpl;

public class PTXFrameMapBuilder extends FrameMapBuilderImpl {
    public PTXFrameMapBuilder(FrameMap frameMap, CodeCacheProvider codeCache, RegisterConfig registerConfig) {
        super(frameMap, codeCache, registerConfig);
    }
}
