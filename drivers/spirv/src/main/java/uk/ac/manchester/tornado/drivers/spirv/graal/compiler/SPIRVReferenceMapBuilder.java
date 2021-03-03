package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import jdk.vm.ci.code.ReferenceMap;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.framemap.ReferenceMapBuilder;

public class SPIRVReferenceMapBuilder extends ReferenceMapBuilder {

    @Override
    public void addLiveValue(Value value) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public ReferenceMap finish(LIRFrameState state) {
        throw new RuntimeException("Unimplemented");
    }
}
