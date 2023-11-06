package uk.ac.manchester.tornado.drivers.ptx.mm;

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
import uk.ac.manchester.tornado.api.data.nativetypes.TornadoNativeArray;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.memory.ObjectBuffer;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class PTXMemorySegmentWrapper extends TornadoLogger implements ObjectBuffer {
    private static final int INIT_VALUE = -1;
    private final PTXDeviceContext deviceContext;
    private final long batchSize;
    private long bufferId;
    private long bufferOffset;
    private boolean onDevice;
    private long bufferSize;

    private long setSubRegionSize;

    public PTXMemorySegmentWrapper(PTXDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = INIT_VALUE;
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        onDevice = false;
    }

    public PTXMemorySegmentWrapper(ByteArray byteSegment, PTXDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = byteSegment.getSegment().byteSize();
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        onDevice = false;
    }

    public PTXMemorySegmentWrapper(FloatArray floatSegment, PTXDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = floatSegment.getSegment().byteSize();
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        onDevice = false;
    }

    public PTXMemorySegmentWrapper(DoubleArray doubleSegment, PTXDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = doubleSegment.getSegment().byteSize();
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        onDevice = false;
    }

    public PTXMemorySegmentWrapper(IntArray intSegment, PTXDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = intSegment.getSegment().byteSize();
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        onDevice = false;
    }

    public PTXMemorySegmentWrapper(ShortArray shortSegment, PTXDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = shortSegment.getSegment().byteSize();
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        onDevice = false;
    }

    public PTXMemorySegmentWrapper(long bufferSize, PTXDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = bufferSize;
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

    private MemorySegment getSegment(final Object reference) {
        return switch (reference) {
            case IntArray intArray -> intArray.getSegment();
            case FloatArray floatArray -> floatArray.getSegment();
            case DoubleArray doubleArray -> doubleArray.getSegment();
            case LongArray longArray -> longArray.getSegment();
            case ShortArray shortArray -> shortArray.getSegment();
            case ByteArray byteArray -> byteArray.getSegment();
            case CharArray charArray -> charArray.getSegment();
            case VectorFloat2 vectorFloat2 -> vectorFloat2.getArray().getSegment();
            case VectorFloat3 vectorFloat3 -> vectorFloat3.getArray().getSegment();
            case VectorFloat4 vectorFloat4 -> vectorFloat4.getArray().getSegment();
            case VectorFloat8 vectorFloat8 -> vectorFloat8.getArray().getSegment();
            case VectorDouble2 vectorDouble2 -> vectorDouble2.getArray().getSegment();
            case VectorDouble3 vectorDouble3 -> vectorDouble3.getArray().getSegment();
            case VectorDouble4 vectorDouble4 -> vectorDouble4.getArray().getSegment();
            case VectorDouble8 vectorDouble8 -> vectorDouble8.getArray().getSegment();
            case VectorInt2 vectorInt2 -> vectorInt2.getArray().getSegment();
            case VectorInt3 vectorInt3 -> vectorInt3.getArray().getSegment();
            case VectorInt4 vectorInt4 -> vectorInt4.getArray().getSegment();
            case VectorInt8 vectorInt8 -> vectorInt8.getArray().getSegment();
            default -> (MemorySegment) reference;
        };
    }

    @Override
    public int read(final Object reference, long hostOffset, int[] events, boolean useDeps) {
        MemorySegment segment = getSegment(reference);

        final int returnEvent;
        final long numBytes = getSizeSubRegionSize() > 0 ? getSizeSubRegionSize() : bufferSize;
        if (batchSize <= 0) {
            returnEvent = deviceContext.readBuffer(toBuffer(), numBytes, segment.address(), hostOffset, (useDeps) ? events : null);
        } else {
            returnEvent = deviceContext.readBuffer(toBuffer() + TornadoNativeArray.ARRAY_HEADER, bufferSize, segment.address(), hostOffset + TornadoNativeArray.ARRAY_HEADER, (useDeps)
                    ? events
                    : null);
        }
        return returnEvent;
    }

    @Override
    public void write(Object reference) {
        MemorySegment segment = getSegment(reference);

        deviceContext.writeBuffer(toBuffer(), bufferSize, segment.address(), 0, null);
        onDevice = true;
    }

    @Override
    public int enqueueRead(Object reference, long hostOffset, int[] events, boolean useDeps) {
        MemorySegment segment = getSegment(reference);

        final int returnEvent;
        if (batchSize <= 0) {
            returnEvent = deviceContext.enqueueReadBuffer(toBuffer(), bufferSize, segment.address(), hostOffset, (useDeps) ? events : null);
        } else {
            returnEvent = deviceContext.enqueueReadBuffer(toBuffer() + TornadoNativeArray.ARRAY_HEADER, bufferSize - TornadoNativeArray.ARRAY_HEADER, segment.address(), hostOffset, (useDeps)
                    ? events
                    : null);
        }
        return useDeps ? returnEvent : -1;
    }

    @Override
    public List<Integer> enqueueWrite(Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        List<Integer> returnEvents = new ArrayList<>();

        MemorySegment segment = getSegment(reference);

        int internalEvent;
        if (batchSize <= 0) {
            internalEvent = deviceContext.enqueueWriteBuffer(toBuffer(), bufferSize, segment.address(), hostOffset, (useDeps) ? events : null);
        } else {
            internalEvent = deviceContext.enqueueWriteBuffer(toBuffer(), TornadoNativeArray.ARRAY_HEADER, segment.address(), 0, (useDeps) ? events : null);
            internalEvent = deviceContext.enqueueWriteBuffer(toBuffer() + TornadoNativeArray.ARRAY_HEADER, bufferSize, segment.address(), hostOffset + TornadoNativeArray.ARRAY_HEADER, (useDeps)
                    ? events
                    : null);
        }
        returnEvents.add(internalEvent);
        onDevice = true;
        return useDeps ? returnEvents : null;
    }

    @Override
    public void allocate(Object reference, long batchSize) throws TornadoOutOfMemoryException, TornadoMemoryException {
        MemorySegment segment = getSegment(reference);

        if (batchSize <= 0 && segment != null) {
            bufferSize = segment.byteSize();
            bufferId = deviceContext.getBufferProvider().getBufferWithSize(bufferSize);
        } else {
            bufferSize = batchSize;
            bufferId = deviceContext.getBufferProvider().getBufferWithSize(bufferSize + TornadoNativeArray.ARRAY_HEADER);
        }

        if (bufferSize <= 0) {
            throw new TornadoMemoryException("[ERROR] Bytes Allocated <= 0: " + bufferSize);
        }

        if (Tornado.FULL_DEBUG) {
            info("allocated: %s", toString());
        }
    }

    @Override
    public void deallocate() throws TornadoMemoryException {
        TornadoInternalError.guarantee(bufferId != INIT_VALUE, "Fatal error: trying to deallocate an invalid buffer");
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
    public long getSizeSubRegionSize() {
        return setSubRegionSize;
    }

    @Override
    public void setSizeSubRegion(long batchSize) {
        this.setSubRegionSize = batchSize;
    }

    @Override
    public int[] getIntBuffer() {
        return ObjectBuffer.super.getIntBuffer();
    }

    @Override
    public void setIntBuffer(int[] arr) {
        ObjectBuffer.super.setIntBuffer(arr);
    }

}
