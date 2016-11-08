package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.spi.LIRKindTool;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLLIRKindTool implements LIRKindTool{

    @Override
    public LIRKind getIntegerKind(int i) {
        unimplemented();
        return LIRKind.Illegal;
    }

    @Override
    public LIRKind getFloatingKind(int i) {
        unimplemented();
        return LIRKind.Illegal;
    }

    @Override
    public LIRKind getObjectKind() {
        unimplemented();
        return LIRKind.Illegal;
    }

    @Override
    public LIRKind getWordKind() {
        unimplemented();
        return LIRKind.Illegal;
    }
    
}
