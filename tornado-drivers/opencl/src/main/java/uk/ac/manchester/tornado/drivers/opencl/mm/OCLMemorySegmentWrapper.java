package uk.ac.manchester.tornado.drivers.opencl.mm;

import static uk.ac.manchester.tornado.runtime.common.Tornado.info;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.collections.types.VectorDouble2;
import uk.ac.manchester.tornado.api.collections.types.VectorDouble3;
import uk.ac.manchester.tornado.api.collections.types.VectorDouble4;
import uk.ac.manchester.tornado.api.collections.types.VectorDouble8;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat2;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat3;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat4;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat8;
import uk.ac.manchester.tornado.api.collections.types.VectorInt2;
import uk.ac.manchester.tornado.api.collections.types.VectorInt3;
import uk.ac.manchester.tornado.api.collections.types.VectorInt4;
import uk.ac.manchester.tornado.api.collections.types.VectorInt8;
import uk.ac.manchester.tornado.api.data.nativetypes.ByteArray;
import uk.ac.manchester.tornado.api.data.nativetypes.CharArray;
import uk.ac.manchester.tornado.api.data.nativetypes.DoubleArray;
import uk.ac.manchester.tornado.api.data.nativetypes.FloatArray;
import uk.ac.manchester.tornado.api.data.nativetypes.IntArray;
import uk.ac.manchester.tornado.api.data.nativetypes.LongArray;
import uk.ac.manchester.tornado.api.data.nativetypes.ShortArray;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.memory.ObjectBuffer;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.runtime.common.Tornado;

public class OCLMemorySegmentWrapper implements ObjectBuffer {

    private static final int INIT_VALUE = -1;
    private final OCLDeviceContext deviceContext;
    private final long batchSize;
    private long bufferId;
    private long bufferOffset;
    private boolean onDevice;
    private long bufferSize;

    private long subregionSize;

    public OCLMemorySegmentWrapper(OCLDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = INIT_VALUE;
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        onDevice = false;
    }

    public OCLMemorySegmentWrapper(ByteArray byteSegment, OCLDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = byteSegment.getSegment().byteSize();
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        onDevice = false;
    }

    public OCLMemorySegmentWrapper(FloatArray floatSegment, OCLDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = floatSegment.getSegment().byteSize();
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        onDevice = false;
    }

    public OCLMemorySegmentWrapper(DoubleArray doubleSegment, OCLDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = doubleSegment.getSegment().byteSize();
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        onDevice = false;
    }

    public OCLMemorySegmentWrapper(IntArray intSegment, OCLDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = intSegment.getSegment().byteSize();
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        onDevice = false;
    }

    public OCLMemorySegmentWrapper(ShortArray shortSegment, OCLDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = shortSegment.getSegment().byteSize();
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        onDevice = false;
    }

    @Override
    public long toBuffer() {
        return this.bufferId; 
    }

    @Override
    public void setBuffer(ObjectBufferWrapper bufferWrapper) {
        this.bufferId = bufferWrapper.buffer;
        this.bufferOffset = bufferWrapper.bufferOffset;

        bufferWrapper.bufferOffset += bufferSize;
    }

    @Override
    public long getBufferOffset() {
        return bufferOffset;
    }

    @Override
    public void read(final Object reference) {
        read(reference, 0, null, false);
    }

    @Override
    public int read(final Object reference, long hostOffset, int[] events, boolean useDeps) {
        MemorySegment segment;
        if (reference instanceof IntArray) {
            segment = ((IntArray) reference).getSegment();
        } else if (reference instanceof FloatArray) {
            segment = ((FloatArray) reference).getSegment();
        } else if (reference instanceof DoubleArray) {
            segment = ((DoubleArray) reference).getSegment();
        } else if (reference instanceof LongArray) {
            segment = ((LongArray) reference).getSegment();
        } else if (reference instanceof ShortArray) {
            segment = ((ShortArray) reference).getSegment();
        } else if (reference instanceof ByteArray) {
            segment = ((ByteArray) reference).getSegment();
        } else if (reference instanceof CharArray) {
            segment = ((CharArray) reference).getSegment();
        } else if (reference instanceof VectorFloat2) {
            segment = ((VectorFloat2) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat3) {
            segment = ((VectorFloat3) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat4) {
            segment = ((VectorFloat4) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat8) {
            segment = ((VectorFloat8) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble2) {
            segment = ((VectorDouble2) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble3) {
            segment = ((VectorDouble3) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble4) {
            segment = ((VectorDouble4) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble8) {
            segment = ((VectorDouble8) reference).getArray().getSegment();
        } else if (reference instanceof VectorInt2) {
            segment = ((VectorInt2) reference).getArray().getSegment();
        } else if (reference instanceof VectorInt3) {
            segment = ((VectorInt3) reference).getArray().getSegment();
        } else if (reference instanceof VectorInt4) {
            segment = ((VectorInt4) reference).getArray().getSegment();
        } else if (reference instanceof VectorInt8) {
            segment = ((VectorInt8) reference).getArray().getSegment();
        } else {
            segment = (MemorySegment) reference;
        }

        final int returnEvent;
        final long numBytes = getSizeSubRegionSize() > 0 ? getSizeSubRegionSize() : bufferSize;
        if (batchSize <= 0) {
            returnEvent = deviceContext.readBuffer(toBuffer(), bufferOffset, numBytes, segment.address(), hostOffset, (useDeps) ? events : null);
        } else {
            returnEvent = deviceContext.readBuffer(toBuffer(), 24L, numBytes, segment.address(), hostOffset, (useDeps) ? events : null);
        }

        return useDeps ? returnEvent : -1;
    }

    @Override
    public void write(Object reference) {
        MemorySegment seg;
        if (reference instanceof IntArray) {
            seg = ((IntArray) reference).getSegment();
        } else if (reference instanceof FloatArray) {
            seg = ((FloatArray) reference).getSegment();
        } else if (reference instanceof DoubleArray) {
            seg = ((DoubleArray) reference).getSegment();
        } else if (reference instanceof LongArray) {
            seg = ((LongArray) reference).getSegment();
        } else if (reference instanceof ShortArray) {
            seg = ((ShortArray) reference).getSegment();
        } else if (reference instanceof ByteArray) {
            seg = ((ByteArray) reference).getSegment();
        } else if (reference instanceof CharArray) {
            seg = ((CharArray) reference).getSegment();
        } else if (reference instanceof VectorFloat2) {
            seg = ((VectorFloat2) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat3) {
            seg = ((VectorFloat3) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat4) {
            seg = ((VectorFloat4) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat8) {
            seg = ((VectorFloat8) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble2) {
            seg = ((VectorDouble2) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble3) {
            seg = ((VectorDouble3) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble4) {
            seg = ((VectorDouble4) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble8) {
            seg = ((VectorDouble8) reference).getArray().getSegment();
        } else {
            seg = (MemorySegment) reference;
        }
        deviceContext.writeBuffer(toBuffer(), bufferOffset, bufferSize, seg.address(), 0, null);
        onDevice = true;
    }

    @Override
    public int enqueueRead(Object reference, long hostOffset, int[] events, boolean useDeps) {

        MemorySegment seg;
        if (reference instanceof IntArray) {
            seg = ((IntArray) reference).getSegment();
        } else if (reference instanceof FloatArray) {
            seg = ((FloatArray) reference).getSegment();
        } else if (reference instanceof DoubleArray) {
            seg = ((DoubleArray) reference).getSegment();
        } else if (reference instanceof LongArray) {
            seg = ((LongArray) reference).getSegment();
        } else if (reference instanceof ShortArray) {
            seg = ((ShortArray) reference).getSegment();
        } else if (reference instanceof ByteArray) {
            seg = ((ByteArray) reference).getSegment();
        } else if (reference instanceof CharArray) {
            seg = ((CharArray) reference).getSegment();
        } else if (reference instanceof VectorFloat2) {
            seg = ((VectorFloat2) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat3) {
            seg = ((VectorFloat3) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat4) {
            seg = ((VectorFloat4) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat8) {
            seg = ((VectorFloat8) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble2) {
            seg = ((VectorDouble2) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble3) {
            seg = ((VectorDouble3) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble4) {
            seg = ((VectorDouble4) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble8) {
            seg = ((VectorDouble8) reference).getArray().getSegment();
        } else {
            seg = (MemorySegment) reference;
        }

        final int returnEvent;
        if (batchSize <= 0) {
            returnEvent = deviceContext.enqueueReadBuffer(toBuffer(), bufferOffset, bufferSize, seg.address(), hostOffset, (useDeps) ? events : null);
        } else {
            returnEvent = deviceContext.enqueueReadBuffer(toBuffer(), bufferOffset, bufferSize, seg.address(), hostOffset, (useDeps) ? events : null);
        }
        return useDeps ? returnEvent : -1;
    }

    @Override
    public List<Integer> enqueueWrite(Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        List<Integer> returnEvents = new ArrayList<>();
        MemorySegment seg;
        if (reference instanceof IntArray) {
            seg = ((IntArray) reference).getSegment();
        } else if (reference instanceof FloatArray) {
            seg = ((FloatArray) reference).getSegment();
        } else if (reference instanceof DoubleArray) {
            seg = ((DoubleArray) reference).getSegment();
        } else if (reference instanceof LongArray) {
            seg = ((LongArray) reference).getSegment();
        } else if (reference instanceof ShortArray) {
            seg = ((ShortArray) reference).getSegment();
        } else if (reference instanceof ByteArray) {
            seg = ((ByteArray) reference).getSegment();
        } else if (reference instanceof CharArray) {
            seg = ((CharArray) reference).getSegment();
        } else if (reference instanceof VectorFloat2) {
            seg = ((VectorFloat2) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat3) {
            seg = ((VectorFloat3) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat4) {
            seg = ((VectorFloat4) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat8) {
            seg = ((VectorFloat8) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble2) {
            seg = ((VectorDouble2) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble3) {
            seg = ((VectorDouble3) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble4) {
            seg = ((VectorDouble4) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble8) {
            seg = ((VectorDouble8) reference).getArray().getSegment();
        } else {
            seg = (MemorySegment) reference;
        }

        // We first write the header for the object, and then we write actual buffer
        //        final int headerEvent;
        //        if (batchSize <= 0) {
        //            headerEvent = buildArrayHeader((int) (bufferSize - panamaHeaderSize)).enqueueWrite((useDeps) ? events : null);
        //        } else {
        //            headerEvent = buildArrayHeaderBatch((int) batchSize).enqueueWrite((useDeps) ? events : null);
        //        }
        int internalEvent;
        if (batchSize <= 0) {
            internalEvent = deviceContext.enqueueWriteBuffer(toBuffer(), bufferOffset, bufferSize, seg.address(), hostOffset, (useDeps) ? events : null);
        } else {
            internalEvent = deviceContext.enqueueWriteBuffer(toBuffer(), bufferOffset + 24, bufferSize, seg.address(), hostOffset, (useDeps) ? events : null);

        }
        returnEvents.add(internalEvent);
        onDevice = true;
        return useDeps ? returnEvents : null;
    }

    @Override
    public void allocate(Object reference, long batchSize) throws TornadoOutOfMemoryException, TornadoMemoryException {
        MemorySegment memref;
        if (reference instanceof IntArray) {
            memref = ((IntArray) reference).getSegment();
        } else if (reference instanceof FloatArray) {
            memref = ((FloatArray) reference).getSegment();
        } else if (reference instanceof DoubleArray) {
            memref = ((DoubleArray) reference).getSegment();
        } else if (reference instanceof LongArray) {
            memref = ((LongArray) reference).getSegment();
        } else if (reference instanceof ShortArray) {
            memref = ((ShortArray) reference).getSegment();
        } else if (reference instanceof ByteArray) {
            memref = ((ByteArray) reference).getSegment();
        } else if (reference instanceof CharArray) {
            memref = ((CharArray) reference).getSegment();
        } else if (reference instanceof VectorFloat2) {
            memref = ((VectorFloat2) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat3) {
            memref = ((VectorFloat3) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat4) {
            memref = ((VectorFloat4) reference).getArray().getSegment();
        } else if (reference instanceof VectorFloat8) {
            memref = ((VectorFloat8) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble2) {
            memref = ((VectorDouble2) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble3) {
            memref = ((VectorDouble3) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble4) {
            memref = ((VectorDouble4) reference).getArray().getSegment();
        } else if (reference instanceof VectorDouble8) {
            memref = ((VectorDouble8) reference).getArray().getSegment();
        } else {
            memref = (MemorySegment) reference;
        }

        if (batchSize <= 0) {
            bufferSize = memref.byteSize();
            bufferId = deviceContext.getBufferProvider().getBufferWithSize(bufferSize);

        } else {
            bufferSize = batchSize;
            bufferId = deviceContext.getBufferProvider().getBufferWithSize(bufferSize + 24L);
        }

        if (bufferSize <= 0) {
            throw new TornadoMemoryException("[ERROR] Bytes Allocated <= 0: " + bufferSize);
        }

        if (Tornado.FULL_DEBUG) {
            info("allocated: %s", toString());
        }
    }

    // TODO: Check if correct
    @Override
    public void deallocate() throws TornadoMemoryException {
        TornadoInternalError.guarantee(bufferId != INIT_VALUE, "Fatal error: trying to deallocate an invalid buffer");
        // long bufferSize = memref.byteSize();
        deviceContext.getBufferProvider().markBufferReleased(bufferId, bufferSize);
        bufferId = INIT_VALUE;
        bufferSize = INIT_VALUE;

        if (Tornado.FULL_DEBUG) {
            info("deallocated: %s", toString());
        }
    }

    @Override
    public long size() {
        return bufferSize;
    }

    @Override
    public void setSizeSubRegion(long batchSize) {
        this.subregionSize = batchSize;
    }

    @Override
    public long getSizeSubRegionSize() {
        return subregionSize;
    }

    public long getBatchSize() {
        return batchSize;
    }
}
