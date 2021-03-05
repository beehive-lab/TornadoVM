package uk.ac.manchester.tornado.drivers.spirv.mm;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;

public class SPIRVIntArrayWrapper extends SPIRVArrayWrapper<int[]> {

    public SPIRVIntArrayWrapper(SPIRVDeviceContext device, long size) {
        this(device, false, size);
        System.out.println("CREATING AN INT BUFFER");
    }

    public SPIRVIntArrayWrapper(SPIRVDeviceContext device, boolean isFinal, long size) {
        super(device, JavaKind.Int, isFinal, size);
    }

    @Override
    protected int readArrayData(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.readBuffer(bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected void writeArrayData(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        deviceContext.writeBuffer(bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected int enqueueReadArrayData(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueReadBuffer(bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected int enqueueWriteArrayData(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueWriteBuffer(bufferId, offset, bytes, value, hostOffset, waitEvents);
    }
}
