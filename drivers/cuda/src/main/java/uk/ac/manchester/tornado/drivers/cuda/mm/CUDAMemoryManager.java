package uk.ac.manchester.tornado.drivers.cuda.mm;

import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;
import uk.ac.manchester.tornado.drivers.cuda.CUDA;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;

public class CUDAMemoryManager extends TornadoLogger implements TornadoMemoryProvider {

    private static final int STACK_ALIGNMENT_SIZE = 128;

    private long heapPosition;
    private long heapLimit;
    private CUDADeviceContext deviceContext;
    private long callStackPosition;
    private long callStackLimit;
    private long deviceHeapPointer;
    private boolean initialised;
    private ScheduleMetaData scheduleMeta;

    public CUDAMemoryManager(CUDADeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        scheduleMeta = new ScheduleMetaData("mm-" + deviceContext.getDevice().getIndex());
        callStackLimit = CUDA.CALL_STACK_LIMIT;
        initialised = false;
        reset();
    }

    public void init(PTXBackend backend, long address) {
        deviceHeapPointer = address;
        initialised = true;
        info("Located heap @ 0x%x (%s) on %s", deviceHeapPointer, RuntimeUtilities.humanReadableByteCount(heapLimit, false), deviceContext.getDevice().getDeviceName());
        scheduleMeta.setDevice(backend.getDeviceContext().asMapping());
    }

    public void reset() {
        callStackPosition = 0;
        heapPosition = callStackLimit;
        Tornado.info("Reset heap @ 0x%x (%s) on %s",
                     deviceHeapPointer,
                     RuntimeUtilities.humanReadableByteCount(heapLimit, true),
                     deviceContext.getDevice().getDeviceName()
        );
    }

    @Override
    public long getCallStackSize() { return callStackLimit; }

    @Override
    public long getCallStackAllocated() {
        return callStackPosition;
    }

    @Override
    public long getCallStackRemaining() {
        return callStackLimit - callStackPosition;
    }

    @Override
    public long getHeapSize() {
        return heapLimit - callStackLimit;
    }

    @Override
    public long getHeapRemaining() {
        return heapLimit - heapPosition;
    }

    @Override
    public long getHeapAllocated() { return heapPosition - callStackLimit; }

    @Override
    public boolean isInitialised() {
        return initialised;
    }

    public CUDACallStack createCallStack(final int maxArgs) {
        CUDACallStack callStack = new CUDACallStack(callStackPosition, maxArgs, deviceContext);

        if (callStackPosition + callStack.getSize() < callStackLimit) {
            callStackPosition = align(callStackPosition + callStack.getSize(), STACK_ALIGNMENT_SIZE);
        } else {
            callStack = null;
            fatal("Out of call-stack memory");
            System.exit(-1);
        }

        return callStack;
    }

    public long tryAllocate(Class<?> type, long bytes, int headerSize, int alignment) throws TornadoOutOfMemoryException {
        final long alignedDataStart = align(heapPosition + headerSize, alignment);
        final long headerStart = alignedDataStart - headerSize;
        if (headerStart + bytes < heapLimit) {
            heapPosition = headerStart + bytes;
        } else {
            throw new TornadoOutOfMemoryException("Out of memory on the target device -> " +
                            deviceContext.getDevice().getDeviceName() +
                            ". [Heap Limit is: " +
                            RuntimeUtilities.humanReadableByteCount(heapLimit, true) +
                            " and the application requires: " +
                            RuntimeUtilities.humanReadableByteCount(headerStart + bytes, true) +
                            "]"
            );
        }
        return headerStart;
    }

    /**
     * Allocate space on the device
     *
     * @param numBytes
     */
    public void allocateRegion(long numBytes) {
        this.heapLimit = numBytes;
        this.deviceHeapPointer = deviceContext.getDevice().getContext().allocateMemory(numBytes);
    }

    private static long align(final long address, final long alignment) {
        return (address % alignment == 0) ? address : address + (alignment - address % alignment);
    }

    public long toBuffer() {
        return deviceHeapPointer;
    }

    public long toAbsoluteDeviceAddress(long address) {
        long result = address;

        guarantee(address + deviceHeapPointer >= 0, "absolute address may have wrapped arround: %d + %d = %d", address,
                  deviceHeapPointer, address + deviceHeapPointer
        );
        result += deviceHeapPointer;

        return result;
    }

    /**
     * * Returns sub-buffer that can be use to access a region managed by the memory
     * manager.
     *
     * @param offset
     *            offset within the memory managers heap
     * @param length
     *            size in bytes of the sub-buffer
     *
     * @return
     */
    public CUDAByteBuffer getSubBuffer(int offset, int length) {
        return new CUDAByteBuffer(length, offset, deviceContext);
    }
}
