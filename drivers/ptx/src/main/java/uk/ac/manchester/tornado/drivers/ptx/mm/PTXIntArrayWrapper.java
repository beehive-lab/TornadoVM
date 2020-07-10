package uk.ac.manchester.tornado.drivers.ptx.mm;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;

public class PTXIntArrayWrapper extends PTXArrayWrapper<int[]> {

    public PTXIntArrayWrapper(PTXDeviceContext deviceContext) {
        super(deviceContext, JavaKind.Int, false);
    }

    @Override
    protected int readArrayData(long address, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.readBuffer(address, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected void writeArrayData(long address, long bytes, int[] value, int hostOffset, int[] waitEvents) {
        deviceContext.writeBuffer(address, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected int enqueueReadArrayData(long address, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueReadBuffer(address, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected int enqueueWriteArrayData(long address, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueWriteBuffer(address, bytes, value, hostOffset, waitEvents);
    }
}
