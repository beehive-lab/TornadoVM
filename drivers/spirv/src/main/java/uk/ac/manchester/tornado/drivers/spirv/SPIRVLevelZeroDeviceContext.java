package uk.ac.manchester.tornado.drivers.spirv;

public class SPIRVLevelZeroDeviceContext extends SPIRVDeviceContext {

    public SPIRVLevelZeroDeviceContext(SPIRVDevice device, SPIRVCommandQueue queue, SPIRVContext context) {
        super(device, queue, context);
        this.spirvContext = context;
    }
}
