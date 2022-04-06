package uk.ac.manchester.tornado.drivers.ptx.runtime;

import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;

public class PTXBufferProvider extends TornadoBufferProvider {

    public PTXBufferProvider(PTXDeviceContext deviceContext) {
        super(deviceContext);
    }

    @Override
    public long allocateBuffer(long size) {
        return ((PTXDeviceContext) deviceContext).getDevice().getPTXContext().allocateMemory(size);
    }

    @Override
    protected void releaseBuffer(long buffer) {
        ((PTXDeviceContext) deviceContext).getDevice().getPTXContext().freeMemory(buffer);
    }
}
