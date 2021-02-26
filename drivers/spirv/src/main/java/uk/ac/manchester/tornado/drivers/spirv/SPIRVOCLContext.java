package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.opencl.OCLExecutionEnvironment;

import java.util.List;

public class SPIRVOCLContext extends SPIRVContext {

    private OCLExecutionEnvironment context;

    public SPIRVOCLContext(SPIRVPlatform platform, List<SPIRVDevice> devices, OCLExecutionEnvironment context) {
        super(platform, devices);
        this.context = context;
    }

    @Override
    public SPIRVDeviceContext createDeviceContext(int deviceIndex) {
        throw new RuntimeException("createDeviceContext");
    }

    @Override
    public SPIRVDeviceContext getDeviceContext() {
        throw new RuntimeException("getDeviceContext");
    }
}
