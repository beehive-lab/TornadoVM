package uk.ac.manchester.tornado.drivers.spirv.mm;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;

public class SPIRVLongArrayWrapper extends SPIRVArrayWrapper<long[]> {

    public SPIRVLongArrayWrapper(SPIRVDeviceContext device, long size) {
        this(device, false, size);
    }

    public SPIRVLongArrayWrapper(SPIRVDeviceContext device, boolean isFinal, long size) {
        super(device, JavaKind.Long, isFinal, size);
    }

    @Override
    protected int readArrayData(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.readBuffer(bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected void writeArrayData(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        deviceContext.writeBuffer(bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected int enqueueReadArrayData(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueReadBuffer(bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected int enqueueWriteArrayData(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueWriteBuffer(bufferId, offset, bytes, value, hostOffset, waitEvents);
    }
}