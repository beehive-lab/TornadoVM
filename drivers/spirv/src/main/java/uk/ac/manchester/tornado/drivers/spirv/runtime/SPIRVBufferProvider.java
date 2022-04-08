package uk.ac.manchester.tornado.drivers.spirv.runtime;

import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;

public class SPIRVBufferProvider extends TornadoBufferProvider {

    public SPIRVBufferProvider(SPIRVDeviceContext deviceContext) {
        super(deviceContext);
    }

    @Override
    public long allocateBuffer(long size) {
        return ((SPIRVDeviceContext) deviceContext).getSpirvContext().allocateMemory(deviceContext.getDeviceIndex(), size);
    }

    @Override
    protected void releaseBuffer(long buffer) {
        ((SPIRVDeviceContext) deviceContext).getSpirvContext().freeMemory(buffer, deviceContext.getDeviceIndex());
    }
}
