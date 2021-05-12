package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVBuiltIn;

public enum SPIRVOCLBuiltIn {

    // @formatter:off
    GLOBAL_THREAD_ID("spirv_BuiltInGlobalInvocationId", SPIRVBuiltIn.GlobalInvocationId()), 
    GLOBAL_SIZE("spirv_BuiltInGlobalSize", SPIRVBuiltIn.GlobalSize());
    // @formatter:on

    String name;
    SPIRVBuiltIn builtIn;

    SPIRVOCLBuiltIn(String idName, SPIRVBuiltIn builtIn) {
        this.name = idName;
        this.builtIn = builtIn;
    }

    public String getName() {
        return name;
    }

    public SPIRVBuiltIn getBuiltIn() {
        return builtIn;
    }

}
