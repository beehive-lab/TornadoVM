package tornado.drivers.opencl.mm;

import tornado.common.RuntimeUtilities;
import tornado.common.Tornado;
import tornado.common.TornadoLogger;
import tornado.common.exceptions.TornadoOutOfMemoryException;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.enums.OCLMemFlags;

public class OCLMemoryManager extends TornadoLogger {

    private final long callStackLimit;
    private long callStackPosition;
    private long deviceBufferAddress;
    private final OCLDeviceContext deviceContext;

    private long buffer;
    private long heapLimit;

    private long heapPosition;

    private boolean initialised;

    public OCLMemoryManager(final OCLDeviceContext device) {

        deviceContext = device;
        callStackLimit = 8192;
        initialised = false;

        reset();
    }

    public void reset() {
        callStackPosition = 0;
        heapPosition = callStackLimit;
    }

    private static final long align(final long address, final long alignment) {
        return (address % alignment == 0) ? address : address
                + (alignment - address % alignment);
    }

    public long tryAllocate(final long bytes, int alignment)
            throws TornadoOutOfMemoryException {
        long offset = heapPosition;
        if (heapPosition + bytes < heapLimit) {
            heapPosition = align(heapPosition + bytes, alignment);
        } else {
            throw new TornadoOutOfMemoryException("Out of memory on device: "
                    + deviceContext.getDevice().getName());
        }

        return offset;
    }

    public OCLCallStack createCallStack(final int maxArgs) {

        OCLCallStack callStack = new OCLCallStack(callStackPosition, maxArgs,
                deviceContext);

        if (callStackPosition + callStack.getSize() < callStackLimit) {
            callStackPosition = align(callStackPosition + callStack.getSize(),
                    16);
        } else {
            callStack = null;
            Tornado.fatal("Out of call-stack memory");
            System.exit(-1);
        }

        return callStack;
    }

    public long getBytesRemaining() {
        return heapLimit - heapPosition;
    }

    /***
     * Returns sub-buffer that can be use to access a region managed by the
     * memory manager.
     *
     * @param offset
     *            offset within the memory managers heap
     * @param length
     *            size in bytes of the sub-buffer
     * @return
     */
    public OCLByteBuffer getSubBuffer(final int offset, final int length) {
        return new OCLByteBuffer(deviceContext, offset, length);
    }

    public void allocateRegion(long numBytes) {

        /*
         * Allocate space on the device
         */
        heapLimit = numBytes;
        buffer = deviceContext.getPlatformContext().createBuffer(
                OCLMemFlags.CL_MEM_READ_WRITE, numBytes);
    }

    public void setRegionAddress(long address) {
        deviceBufferAddress = address;
        initialised = true;
        Tornado.info("Located heap @ 0x%x (%s) on %s", deviceBufferAddress,
                RuntimeUtilities.humanReadableByteCount(heapLimit, true),
                deviceContext.getDevice().getName());
    }

    public long toAbsoluteAddress() {
        return deviceBufferAddress;
    }

    public long toAbsoluteDeviceAddress(final long address) {
        long result = address;
        if ((Long.compareUnsigned(address, deviceBufferAddress) < 0 || Long
                .compareUnsigned(address, (deviceBufferAddress + heapLimit)) > 0)) {
            result += deviceBufferAddress;
        }
        return result;
    }

    public long toBuffer() {
        return buffer;
    }

    public long toRelativeAddress() {
        return 0;
    }

    public long toRelativeDeviceAddress(final long address) {
        long result = address;
        if (!(Long.compareUnsigned(address, deviceBufferAddress) < 0 || Long
                .compareUnsigned(address, (deviceBufferAddress + heapLimit)) > 0)) {
            result -= deviceBufferAddress;
        }
        return result;
    }

    public boolean isInitialised() {
        return initialised;
    }
}
