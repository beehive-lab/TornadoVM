package uk.ac.manchester.tornado.drivers.cuda.mm;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;

public class CUDAFloatArrayWrapper extends CUDAArrayWrapper<float[]> {
    public CUDAFloatArrayWrapper(CUDADeviceContext deviceContext) {
        super(deviceContext, JavaKind.Float, false);
    }

    /**
     * Copy data from the device to the main host.
     *
     * @param bufferId   Device Buffer ID
     * @param offset     Offset within the device buffer
     * @param bytes      Bytes to be copied back to the host
     * @param value      Host array that resides the final data
     * @param hostOffset
     * @param waitEvents List of events to wait for.
     * @return Event information
     */
    @Override
    protected int enqueueReadArrayData(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueReadBuffer(bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected int readArrayData(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.readBuffer(bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    /**
     * Copy data that resides in the host to the target device.
     *
     * @param bufferId   Device Buffer ID
     * @param offset     Offset within the device buffer
     * @param bytes      Bytes to be copied
     * @param value      Host array to be copied
     * @param hostOffset
     * @param waitEvents List of events to wait for.
     * @return Event information
     */
    @Override
    protected int enqueueWriteArrayData(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueWriteBuffer(bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected void writeArrayData(long bufferId, long offset, long bytes, float[] value, int hostOffset, int[] waitEvents) {
        deviceContext.writeBuffer(bufferId, offset, bytes, value, hostOffset, waitEvents);
    }
}
