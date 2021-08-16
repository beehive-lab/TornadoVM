package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.drivers.opencl.OCLExecutionEnvironment;

public class SPIRVOCLDeviceContext extends SPIRVDeviceContext {

    private OCLExecutionEnvironment context;

    public SPIRVOCLDeviceContext(SPIRVDevice device, SPIRVCommandQueue queue, OCLExecutionEnvironment context) {
        super(device, queue, null);
        this.context = context;
    }

    // TODO: Override all methods to work with the OCLExecutionContext for OpenCL

    @Override
    public Event resolveEvent(int event) {
        return null;
    }

}
