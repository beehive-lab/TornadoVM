package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import jdk.vm.ci.meta.Constant;
import org.graalvm.compiler.code.DataSection;
import org.graalvm.compiler.lir.asm.DataBuilder;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

public class PTXDataBuilder extends DataBuilder {
    @Override
    public DataSection.Data createDataItem(Constant c) {
        unimplemented();
        return null;
    }
}
