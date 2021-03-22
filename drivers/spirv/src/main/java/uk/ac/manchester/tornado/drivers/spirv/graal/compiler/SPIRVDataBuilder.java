package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import jdk.vm.ci.meta.Constant;
import org.graalvm.compiler.code.DataSection;
import org.graalvm.compiler.lir.asm.DataBuilder;

public class SPIRVDataBuilder extends DataBuilder {
    @Override
    public DataSection.Data createDataItem(Constant c) {
        return null;
    }
}
