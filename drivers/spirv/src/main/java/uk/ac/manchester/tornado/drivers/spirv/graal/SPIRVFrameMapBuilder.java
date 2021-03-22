package uk.ac.manchester.tornado.drivers.spirv.graal;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilderImpl;

public class SPIRVFrameMapBuilder extends FrameMapBuilderImpl {

    public SPIRVFrameMapBuilder(FrameMap frameMap, CodeCacheProvider codeCache, RegisterConfig registerConfig) {
        super(frameMap, codeCache, registerConfig);
    }

    public StackSlot allocateDeoptimizationRescueSlot() {
        return ((SPIRVFrameMap) getFrameMap()).allocateDeoptimizationRescueSlot();
    }
}
