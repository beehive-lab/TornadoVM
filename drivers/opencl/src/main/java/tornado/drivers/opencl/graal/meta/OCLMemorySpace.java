package tornado.drivers.opencl.graal.meta;

import com.oracle.graal.api.meta.AbstractValue;
import com.oracle.graal.api.meta.LIRKind;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import tornado.drivers.opencl.graal.OCLArchitecture;
import tornado.drivers.opencl.graal.asm.OpenCLAssemblerConstants;

public class OCLMemorySpace extends AbstractValue {
    // @formatter:off

    public static final OCLMemorySpace GLOBAL = new OCLMemorySpace(OpenCLAssemblerConstants.GLOBAL_MEM_MODIFIER);
//        public static final OCLMemorySpace SHARED = new OCLMemorySpace(OpenCLAssemblerConstants.SHARED_MEM_MODIFIER);
    public static final OCLMemorySpace LOCAL = new OCLMemorySpace(OpenCLAssemblerConstants.LOCAL_MEM_MODIFIER);
    public static final OCLMemorySpace PRIVATE = new OCLMemorySpace(OpenCLAssemblerConstants.PRIVATE_MEM_MODIFIER);
    public static final OCLMemorySpace CONSTANT = new OCLMemorySpace(OpenCLAssemblerConstants.CONSTANT_MEM_MODIFIER);
    public static final OCLMemorySpace HEAP = new OCLMemorySpace("heap");
    // @formatter:on

    private final String name;

    protected OCLMemorySpace(String name) {
        super(LIRKind.Illegal);
        this.name = name;
    }

    public OCLArchitecture.OCLMemoryBase getBase() {

        if (this == GLOBAL || this == HEAP) {
            return OCLArchitecture.hp;
        } else if (this == LOCAL) {
            return OCLArchitecture.lp;
        } else if (this == CONSTANT) {
            return OCLArchitecture.cp;
        } else if (this == PRIVATE) {
            return OCLArchitecture.pp;
        } 

        shouldNotReachHere();
        return null;
    }

    public String name() {
        return name;
    }
}
