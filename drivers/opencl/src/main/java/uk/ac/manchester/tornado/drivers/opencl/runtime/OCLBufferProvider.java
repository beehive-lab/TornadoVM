package uk.ac.manchester.tornado.drivers.opencl.runtime;

import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLMemFlags;

public class OCLBufferProvider extends TornadoBufferProvider {

    public OCLBufferProvider(OCLDeviceContext deviceContext) {
        super(deviceContext);
    }

    @Override
    public long allocateBuffer(long size) {
        return ((OCLDeviceContext) deviceContext).getMemoryManager().createBuffer(size, OCLMemFlags.CL_MEM_READ_WRITE).getBuffer();
    }

    @Override
    protected void releaseBuffer(long buffer) {
        ((OCLDeviceContext) deviceContext).getMemoryManager().releaseBuffer(buffer);
    }

}
