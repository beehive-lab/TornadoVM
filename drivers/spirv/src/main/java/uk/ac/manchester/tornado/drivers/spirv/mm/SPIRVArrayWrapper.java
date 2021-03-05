package uk.ac.manchester.tornado.drivers.spirv.mm;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

import java.lang.reflect.Array;
import java.util.List;

import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.runtime.common.Tornado.info;

public abstract class SPIRVArrayWrapper<T> implements ObjectBuffer {

    private final int arrayHeaderSize;
    private final int arrayLengthOffset;
    private boolean onDevice;
    private long bufferOffset;
    private long bytesToAllocate;

    protected SPIRVDeviceContext deviceContext;
    private JavaKind kind;
    private boolean isFinal;
    private long size;

    public SPIRVArrayWrapper(SPIRVDeviceContext deviceContext, JavaKind javaKind, boolean isFinal, long batchSize) {
        this.deviceContext = deviceContext;
        this.kind = javaKind;
        this.isFinal = isFinal;
        this.size = batchSize;

        this.arrayLengthOffset = TornadoCoreRuntime.getVMConfig().arrayOopDescLengthOffset();
        this.arrayHeaderSize = TornadoCoreRuntime.getVMConfig().getArrayBaseOffset(kind);
        this.onDevice = false;
        this.bufferOffset = -1;
    }

    public long getBatchSize() {
        return size;
    }

    // FIXME <REFACTOR> <Common for ALl backends>
    private T cast(Object array) {
        try {
            return (T) array;
        } catch (Exception | Error e) {
            throw new RuntimeException("Unable to cast object: " + e.getMessage());
        }
    }

    abstract protected int readArrayData(long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

    abstract protected void writeArrayData(long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

    abstract protected int enqueueReadArrayData(long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

    abstract protected int enqueueWriteArrayData(long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

    @Override
    public long toBuffer() {
        return deviceContext.getMemoryManager().toBuffer();
    }

    @Override
    public long getBufferOffset() {
        return toRelativeAddress();
    }

    @Override
    public long toAbsoluteAddress() {
        return deviceContext.getMemoryManager().toAbsoluteDeviceAddress(bufferOffset);
    }

    @Override
    public long toRelativeAddress() {
        return bufferOffset;
    }

    @Override
    public void read(Object reference) {

    }

    @Override
    public int read(Object reference, long hostOffset, int[] events, boolean useDeps) {
        return 0;
    }

    // FIXME <REFACTOR> <Same for all backends>
    private SPIRVByteBuffer getArrayHeader() {
        final SPIRVByteBuffer header = deviceContext.getMemoryManager().getSubBuffer((int) bufferOffset, arrayHeaderSize);
        header.buffer.clear();
        return header;
    }

    // FIXME <REFACTOR> <Same for all backends>
    private SPIRVByteBuffer buildArrayHeader(final int arraySize) {
        final SPIRVByteBuffer header = getArrayHeader();
        int index = 0;
        while (index < arrayLengthOffset) {
            header.buffer.put((byte) 0);
            index++;
        }
        header.buffer.putInt(arraySize);
        return header;
    }

    @Override
    public void write(final Object valueReference) {
        final T array = cast(valueReference);
        if (array == null) {
            throw new TornadoRuntimeException("[SPIRV][Error] data are NULL");
        }
        buildArrayHeader(Array.getLength(array));

    }

    @Override
    public int enqueueRead(Object reference, long hostOffset, int[] events, boolean useDeps) {
        return 0;
    }

    @Override
    public List<Integer> enqueueWrite(Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        return null;
    }

    // FIXME <REFACTOR> <S>
    private long sizeOf(final T array) {
        return (long) arrayHeaderSize + ((long) Array.getLength(array) * (long) kind.getByteCount());
    }

    // FIXME <REFACTOR> <S>
    private long sizeOfBatch(long batchSize) {
        return (long) arrayHeaderSize + batchSize;
    }

    // FIXME <REFACTOR> <S>
    @Override
    public void allocate(Object objectReference, long batchSize) {
        long newBufferSize = 0;
        if (batchSize > 0) {
            newBufferSize = sizeOfBatch(batchSize);
        }

        if ((batchSize > 0) && (bufferOffset != -1) && (newBufferSize < bytesToAllocate)) {
            bytesToAllocate = newBufferSize;
        }

        if (bufferOffset == -1) {
            final T hostArray = cast(objectReference);
            if (batchSize <= 0) {
                bytesToAllocate = sizeOf(hostArray);
            } else {
                bytesToAllocate = sizeOfBatch(batchSize);
            }

            if (bytesToAllocate <= 0) {
                throw new TornadoMemoryException("[ERROR] Bytes Allocated <= 0: " + bytesToAllocate);
            }

            bufferOffset = deviceContext.getMemoryManager().tryAllocate(bytesToAllocate, arrayHeaderSize, getAlignment());
            System.out.println("[SPIRV] Buffer management: " + bufferOffset);

            if (Tornado.FULL_DEBUG) {
                info("allocated: array kind=%s, size=%s, length offset=%d, header size=%d, bo=0x%x", kind.getJavaName(), humanReadableByteCount(bytesToAllocate, true), arrayLengthOffset,
                        arrayHeaderSize, bufferOffset);
                info("allocated: %s", toString());
            }
        }

    }

    @Override
    public int getAlignment() {
        return TornadoOptions.SPIRV_ARRAY_ALIGNMENT;
    }

    @Override
    public boolean isValid() {
        return onDevice;
    }

    @Override
    public void invalidate() {
        onDevice = false;
    }

    @Override
    public void printHeapTrace() {

    }

    @Override
    public long size() {
        return bytesToAllocate;
    }
}
