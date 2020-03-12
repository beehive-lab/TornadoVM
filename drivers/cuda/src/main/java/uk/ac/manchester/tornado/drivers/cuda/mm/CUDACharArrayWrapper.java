package uk.ac.manchester.tornado.drivers.cuda.mm;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;

public class CUDACharArrayWrapper extends CUDAArrayWrapper<char[]> {
    public CUDACharArrayWrapper(CUDADeviceContext deviceContext) {
        super(deviceContext, JavaKind.Char, false);
    }

    /**
     * Copy data from the device to the main host.
     *
     * @param address   Device Buffer ID
     * @param bytes      Bytes to be copied back to the host
     * @param value      Host array that resides the final data
     * @param hostOffset
     * @param waitEvents List of events to wait for.
     * @return Event information
     */
    @Override
    protected int enqueueReadArrayData(long address, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueReadBuffer(address, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected int readArrayData(long address, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.readBuffer(address, bytes, value, hostOffset, waitEvents);
    }

    /**
     * Copy data that resides in the host to the target device.
     *
     * @param address   Device Buffer ID
     * @param bytes      Bytes to be copied
     * @param value      Host array to be copied
     * @param hostOffset
     * @param waitEvents List of events to wait for.
     * @return Event information
     */
    @Override
    protected int enqueueWriteArrayData(long address, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueWriteBuffer(address, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected void writeArrayData(long address, long bytes, char[] value, int hostOffset, int[] waitEvents) {
        deviceContext.writeBuffer(address, bytes, value, hostOffset, waitEvents);
    }
}
