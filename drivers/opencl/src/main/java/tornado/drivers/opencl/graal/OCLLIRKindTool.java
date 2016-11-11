package tornado.drivers.opencl.graal;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.spi.LIRKindTool;
import tornado.drivers.opencl.OCLTargetDescription;
import tornado.drivers.opencl.graal.lir.OCLKind;

import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public class OCLLIRKindTool implements LIRKindTool {

    private final OCLTargetDescription target;

    public OCLLIRKindTool(OCLTargetDescription target) {
        this.target = target;
    }

    @Override
    public LIRKind getIntegerKind(int numBits) {
        if (numBits <= 8) {
            return LIRKind.value(OCLKind.CHAR);
        } else if (numBits <= 16) {
            return LIRKind.value(OCLKind.SHORT);
        } else if (numBits <= 32) {
            return LIRKind.value(OCLKind.INT);
        } else if (numBits <= 64) {
            return LIRKind.value(OCLKind.LONG);
        } else {
            throw shouldNotReachHere();
        }
    }

    @Override
    public LIRKind getFloatingKind(int numBits) {
        switch (numBits) {
            case 32:
                return LIRKind.value(OCLKind.FLOAT);
            case 64:
                return LIRKind.value(OCLKind.DOUBLE);
            default:
                throw shouldNotReachHere();
        }
    }

    @Override
    public LIRKind getObjectKind() {
        return getWordKind();
    }

    @Override
    public LIRKind getWordKind() {
        return LIRKind.value(target.getArch().getWordKind());
    }

}
