package uk.ac.manchester.tornado.drivers.spirv.mm;

import static uk.ac.manchester.tornado.runtime.common.Tornado.info;

import java.util.ArrayList;
import java.util.List;

import jdk.incubator.foreign.MemorySegment;
import uk.ac.manchester.tornado.api.data.nativetypes.DoubleArray;
import uk.ac.manchester.tornado.api.data.nativetypes.FloatArray;
import uk.ac.manchester.tornado.api.data.nativetypes.IntArray;
import uk.ac.manchester.tornado.api.data.nativetypes.LongArray;
import uk.ac.manchester.tornado.api.data.nativetypes.ShortArray;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.memory.ObjectBuffer;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.runtime.common.Tornado;

public class SPIRVMemorySegmentWrapper implements ObjectBuffer {

    private final MemorySegment segment;
    private final SPIRVDeviceContext deviceContext;

    private long bufferId;
    private boolean onDevice;
    private static final int INIT_VALUE = -1;

    public SPIRVMemorySegmentWrapper(MemorySegment segment, SPIRVDeviceContext deviceContext) {
        this.segment = segment;
        this.deviceContext = deviceContext;
        onDevice = false;
    }

    @Override
    public long toBuffer() {
        return this.bufferId;
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

    public int read(Object reference, long hostOffset, int[] events, boolean useDeps) {
        MemorySegment segment;
        if (reference instanceof IntArray) {
            segment = ((IntArray) reference).getSegment();
        } else if(reference instanceof FloatArray) {
            segment = ((FloatArray) reference).getSegment();
        } else if(reference instanceof DoubleArray) {
            segment = ((DoubleArray) reference).getSegment();
        } else if(reference instanceof LongArray) {
            segment = ((LongArray) reference).getSegment();
        } else if (reference instanceof ShortArray) {
            segment = ((ShortArray) reference).getSegment();
        } else {
            segment = (MemorySegment) reference;
        }

        final int returnEvent;
        returnEvent = deviceContext.readBuffer(toBuffer(), 0, segment.byteSize(),
                segment.address().toRawLongValue(), hostOffset, (useDeps) ? events : null);
        return returnEvent;
    }

    @Override
    public void write(Object reference) {
        MemorySegment segment = (MemorySegment) reference;
        deviceContext.writeBuffer(toBuffer(), 0, segment.byteSize(), segment.address().toRawLongValue(), 0, null);
    }

    @Override
    public int enqueueRead(Object reference, long hostOffset, int[] events, boolean useDeps) {
        MemorySegment segment = (MemorySegment) reference;

        final int returnEvent;
        returnEvent = deviceContext.enqueueReadBuffer(toBuffer(), 0, segment.byteSize(),
                segment.address().toRawLongValue(), hostOffset, (useDeps) ? events : null);
        return useDeps ? returnEvent : -1;
    }

    @Override
    public List<Integer> enqueueWrite(Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        List<Integer> returnEvents = new ArrayList<>();
        MemorySegment seg;
        if (reference instanceof IntArray) {
            seg = ((IntArray) reference).getSegment();
        } else if(reference instanceof FloatArray) {
            seg = ((FloatArray) reference).getSegment();
        } else if(reference instanceof DoubleArray) {
            seg = ((DoubleArray) reference).getSegment();
        } else if(reference instanceof LongArray) {
            seg = ((LongArray) reference).getSegment();
        } else if (reference instanceof ShortArray) {
            seg = ((ShortArray) reference).getSegment();
        } else {
            seg = (MemorySegment) reference;
        }

        int internalEvent = deviceContext.enqueueWriteBuffer(toBuffer(), 0,
                seg.byteSize(), seg.address().toRawLongValue(), hostOffset, (useDeps) ? events : null);
        returnEvents.add(internalEvent);

        onDevice = true;
        return useDeps ? returnEvents : null;
    }

    @Override
    public void allocate(Object reference, long batchSize) throws TornadoOutOfMemoryException, TornadoMemoryException {
        MemorySegment memref;
        if (reference instanceof IntArray) {
            memref = ((IntArray) reference).getSegment();
        } else if(reference instanceof FloatArray) {
            memref = ((FloatArray) reference).getSegment();
        } else if(reference instanceof DoubleArray) {
            memref = ((DoubleArray) reference).getSegment();
        } else if(reference instanceof LongArray) {
            memref = ((LongArray) reference).getSegment();
        } else if (reference instanceof ShortArray) {
            memref = ((ShortArray) reference).getSegment();
        } else {
            memref = (MemorySegment) reference;
        }
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

    @Override
    public long size() {
        return segment.byteSize();
    }

    @Override
    public void setSizeSubRegion(long batchSize) {

    }

    @Override
    public long getSizeSubRegion() {
        return 0;
    }

}
