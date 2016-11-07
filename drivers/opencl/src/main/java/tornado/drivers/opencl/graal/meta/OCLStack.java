package tornado.drivers.opencl.graal.meta;

import com.oracle.graal.compiler.common.LIRKind;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.OCLArchitecture;
import tornado.drivers.opencl.graal.OCLArchitecture.OCLRegister;
import tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;

public class OCLStack extends Value {

    // @formatter:off
    public static final OCLStack STACK = new OCLStack(OCLAssemblerConstants.STACK_REF_NAME);
    // @formatter:on

    private final String name;

    protected OCLStack(String name) {
        super(LIRKind.Illegal);
        this.name = name;
    }

    public OCLRegister getBase() {
        return OCLArchitecture.sp;

    }

    public String name() {
        return name;
    }
}
