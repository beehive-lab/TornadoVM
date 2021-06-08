package uk.ac.manchester.tornado.drivers.spirv.graal.meta;

import org.graalvm.compiler.core.common.LIRKind;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssemblerConstants;

public class SPIRVMemorySpace extends Value {

    public static final SPIRVMemorySpace GLOBAL = new SPIRVMemorySpace(SPIRVAssemblerConstants.GLOBAL_MEM_MODIFIER);
    public static final SPIRVMemorySpace CONSTANT = new SPIRVMemorySpace(SPIRVAssemblerConstants.CONSTANT_MEM_MODIFIER);
    public static final SPIRVMemorySpace SHARED = new SPIRVMemorySpace(OCLAssemblerConstants.SHARED_MEM_MODIFIER);
    public static final SPIRVMemorySpace LOCAL = new SPIRVMemorySpace(OCLAssemblerConstants.LOCAL_MEM_MODIFIER);
    public static final SPIRVMemorySpace PRIVATE = new SPIRVMemorySpace(OCLAssemblerConstants.PRIVATE_REGION_NAME);

    private String name;

    public SPIRVMemorySpace(String name) {
        super(LIRKind.Illegal);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
