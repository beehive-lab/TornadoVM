package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.framemap.ReferenceMapBuilder;
import jdk.vm.ci.code.ReferenceMap;
import jdk.vm.ci.meta.Value;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLReferenceMapBuilder extends ReferenceMapBuilder {

    @Override
    public void addLiveValue(Value value) {
        unimplemented();
    }

    @Override
    public ReferenceMap finish(LIRFrameState lirfs) {
        unimplemented();
        return new ReferenceMap() {

        };
    }

}
