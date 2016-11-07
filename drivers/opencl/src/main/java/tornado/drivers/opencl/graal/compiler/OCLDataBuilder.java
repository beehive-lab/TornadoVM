package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.code.DataSection.Data;
import com.oracle.graal.lir.asm.DataBuilder;
import jdk.vm.ci.meta.Constant;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLDataBuilder extends DataBuilder {

    @Override
    public Data createDataItem(Constant cnstnt) {
        unimplemented();
        return null;
    }

    @Override
    public boolean needDetailedPatchingInformation() {
        unimplemented();
        return false;
    }

}
