package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.opencl.OCLExecutionEnvironment;

public class SPIRVOCLDeviceContext extends SPIRVDeviceContext {

    public SPIRVOCLDeviceContext(SPIRVDevice device, SPIRVCommandQueue queue, OCLExecutionEnvironment context) {
        super(device, queue, context);
    }
}
