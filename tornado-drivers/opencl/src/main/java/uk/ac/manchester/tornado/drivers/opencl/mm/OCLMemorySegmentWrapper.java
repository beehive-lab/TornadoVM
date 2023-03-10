package uk.ac.manchester.tornado.drivers.opencl.mm;

import jdk.incubator.foreign.MemorySegment;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.memory.ObjectBuffer;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.runtime.common.Tornado;

import java.util.ArrayList;
import java.util.List;

import static uk.ac.manchester.tornado.runtime.common.Tornado.info;

public class OCLMemorySegmentWrapper implements ObjectBuffer {

    private final MemorySegment segment;
    private final OCLDeviceContext deviceContext;

    private long bufferId;

    private boolean onDevice;
    private static final int INIT_VALUE = -1;

    public OCLMemorySegmentWrapper(MemorySegment segment, OCLDeviceContext deviceContext) {
        this.segment = segment;
        this.deviceContext = deviceContext;
        onDevice = false;
    }

    @Override
    public long toBuffer() {
        //guarantee(deviceContext.getSegmentToBufferMap().containsKey(segment), "Should contain the segment by this point");
        return this.bufferId; //deviceContext.getSegmentToBufferMap().get(segment).getBufferId();
    }

    @Override
    public void setBuffer(ObjectBufferWrapper bufferWrapper) {

    }

    @Override
    public long getBufferOffset() {
        // Will always be 0 since there is not parent buffer.
        return 0;
    }

    @Override
    public void read(Object reference) {

    }

//    @Override
//    public long toAbsoluteAddress() {
//        guarantee(deviceContext.getSegmentToBufferMap().containsKey(segment), "Should contain the segment by this point");
//        return deviceContext.getSegmentToBufferMap().get(segment).getDevicePointer();
//    }

//    @Override
//    public long toRelativeAddress() {
//        // Will always be 0 since there is no parent buffer.
//        return 0;
//    }

    @Override
    public int read(Object reference, long hostOffset, int[] events, boolean useDeps) {
        MemorySegment segment = (MemorySegment) reference;
        //OCLBufferInfo bufferInfo = deviceContext.getSegmentToBufferMap().get(segment);

        final int returnEvent;
        returnEvent = deviceContext.readBuffer(toBuffer(), 0, segment.byteSize(),
                segment.address().toRawLongValue(), hostOffset, (useDeps) ? events : null);
        return useDeps ? returnEvent : -1;
    }

    @Override
    public void write(Object reference) {
        MemorySegment segment = (MemorySegment) reference;
        OCLBufferInfo bufferInfo = deviceContext.getSegmentToBufferMap().get(segment);
        deviceContext.writeBuffer(bufferInfo.getBufferId(), 0, bufferInfo.getBufferSize(), bufferInfo.getHostBufferPointer(), 0, null);
    }

    @Override
    public int enqueueRead(Object reference, long hostOffset, int[] events, boolean useDeps) {
        MemorySegment segment = (MemorySegment) reference;
        OCLBufferInfo bufferInfo = deviceContext.getSegmentToBufferMap().get(segment);

        final int returnEvent;
        returnEvent = deviceContext.enqueueReadBuffer(toBuffer(), 0, bufferInfo.getBufferSize(),
                bufferInfo.getHostBufferPointer(), hostOffset, (useDeps) ? events : null);
        return useDeps ? returnEvent : -1;
    }

    @Override
    public List<Integer> enqueueWrite(Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        List<Integer> returnEvents = new ArrayList<>();
        MemorySegment seg = (MemorySegment) reference;
        int internalEvent = deviceContext.enqueueWriteBuffer(toBuffer(), 0,
                seg.byteSize(), seg.address().toRawLongValue(), hostOffset, (useDeps) ? events : null);
        returnEvents.add(internalEvent);

        onDevice = true;
        return useDeps ? returnEvents : null;
    }

    @Override
    public void allocate(Object reference, long batchSize) throws TornadoOutOfMemoryException, TornadoMemoryException {

        MemorySegment memref = (MemorySegment) reference;
        long bufferSize = memref.byteSize();

        if (bufferSize <= 0) {
            throw new TornadoMemoryException("[ERROR] Bytes Allocated <= 0: " + bufferSize);
        }

        bufferId = deviceContext.getBufferProvider().getBufferWithSize(bufferSize);

        if (Tornado.FULL_DEBUG) {
            //info("allocated: array kind=%s, size=%s, length offset=%d, header size=%d", kind.getJavaName(), humanReadableByteCount(bufferSize, true), arrayLengthOffset, arrayHeaderSize);
            info("allocated: %s", toString());
        }
    }

    // TODO: Check if correct
    @Override
    public void deallocate() throws TornadoMemoryException {
        TornadoInternalError.guarantee(bufferId != INIT_VALUE, "Fatal error: trying to deallocate an invalid buffer");
        MemorySegment memref = segment;
        long bufferSize = memref.byteSize();
        deviceContext.getBufferProvider().markBufferReleased(bufferId, bufferSize);
        bufferId = INIT_VALUE;
        bufferSize = INIT_VALUE;

        if (Tornado.FULL_DEBUG) {
          //  info("deallocated: array kind=%s, size=%s, length offset=%d, header size=%d", kind.getJavaName(), humanReadableByteCount(bufferSize, true), arrayLengthOffset, arrayHeaderSize);
            info("deallocated: %s", toString());
        }
    }

//    @Override
//    public int getAlignment() {
//        return 0;
//    }

//    @Override
//    public boolean isValid() {
//        return onDevice;
//    }

//    @Override
//    public void invalidate() {
//        onDevice = false;
//    }

//    @Override
//    public void printHeapTrace() {
//        System.out.printf("0x%x\ttype=%s\n", toAbsoluteAddress(), MemorySegment.class.getName());
//    }

    @Override
    public long size() {
        return deviceContext.getSegmentToBufferMap().get(segment).getBufferSize();
    }

    @Override
    public void setSizeSubRegion(long batchSize) {

    }

    @Override
    public long getSizeSubRegion() {
        return 0;
    }
}
