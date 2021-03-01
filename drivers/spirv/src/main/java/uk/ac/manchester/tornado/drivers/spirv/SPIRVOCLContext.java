package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.opencl.OCLExecutionEnvironment;

import java.util.List;

public class SPIRVOCLContext extends SPIRVContext {

    private OCLExecutionEnvironment context;
    private SPIRVOCLDeviceContext spirvoclDeviceContext;

    public SPIRVOCLContext(SPIRVPlatform platform, List<SPIRVDevice> devices, OCLExecutionEnvironment context) {
        super(platform, devices);
        this.context = context;

        // Create a command queue per device;
        for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
            context.createCommandQueue(deviceIndex);
        }
    }

    @Override
    public SPIRVDeviceContext createDeviceContext(int deviceIndex) {
        // We do not need command queue from this class, it was already created in the
        // constructor
        spirvoclDeviceContext = new SPIRVOCLDeviceContext(devices.get(deviceIndex), null, context);
        return spirvoclDeviceContext;
    }

    @Override
    public SPIRVDeviceContext getDeviceContext() {
        return spirvoclDeviceContext;
    }
}
