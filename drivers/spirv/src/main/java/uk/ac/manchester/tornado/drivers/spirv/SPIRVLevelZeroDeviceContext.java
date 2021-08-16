package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.runtime.EmptyEvent;

public class SPIRVLevelZeroDeviceContext extends SPIRVDeviceContext {

    protected static final Event EMPTY_EVENT = new EmptyEvent();

    public SPIRVLevelZeroDeviceContext(SPIRVDevice device, SPIRVCommandQueue queue, SPIRVContext context) {
        super(device, queue, context);
        this.spirvContext = context;
    }

}
