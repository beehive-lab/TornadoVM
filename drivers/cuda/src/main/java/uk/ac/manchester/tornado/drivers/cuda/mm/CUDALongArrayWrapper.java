package uk.ac.manchester.tornado.drivers.cuda.mm;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;

public class CUDALongArrayWrapper extends CUDAArrayWrapper<long[]> {
    public CUDALongArrayWrapper(CUDADeviceContext deviceContext) {
        super(deviceContext, JavaKind.Long, false);
    }

    @Override
    protected int readArrayData(long address, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.readBuffer(address, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected void writeArrayData(long address, long bytes, long[] value, int hostOffset, int[] waitEvents) {
        deviceContext.writeBuffer(address, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected int enqueueReadArrayData(long address, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueReadBuffer(address, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected int enqueueWriteArrayData(long address, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueWriteBuffer(address, bytes, value, hostOffset, waitEvents);
    }
}
