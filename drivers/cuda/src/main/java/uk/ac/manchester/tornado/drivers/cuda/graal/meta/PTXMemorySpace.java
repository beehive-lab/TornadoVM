package uk.ac.manchester.tornado.drivers.cuda.graal.meta;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants;

public class PTXMemorySpace extends Value {

    public static final PTXMemorySpace GLOBAL = new PTXMemorySpace(PTXAssemblerConstants.GLOBAL_MEM_MODIFIER);
    public static final PTXMemorySpace SHARED = new PTXMemorySpace(PTXAssemblerConstants.SHARED_MEM_MODIFIER);
    public static final PTXMemorySpace PARAM = new PTXMemorySpace(PTXAssemblerConstants.PARAM_MEM_MODIFIER);
    private final String name;

    protected PTXMemorySpace(String name) {
        super(LIRKind.Illegal);
        this.name = name;
    }

    public String name() {
        return name;
    }
}
