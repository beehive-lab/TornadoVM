package uk.ac.manchester.tornado.drivers.spirv.mm;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

import java.util.List;

public abstract class SPIRVArrayWrapper<T> implements ObjectBuffer {

    private final int arrayHeaderSize;
    private final int arrayLengthOffset;
    private boolean onDevice;
    private final int bufferOffset;
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

    @Override
    public void write(Object reference) {

    }

    @Override
    public int enqueueRead(Object reference, long hostOffset, int[] events, boolean useDeps) {
        return 0;
    }

    @Override
    public List<Integer> enqueueWrite(Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        return null;
    }

    @Override
    public void allocate(Object reference, long batchSize) throws TornadoOutOfMemoryException, TornadoMemoryException {

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
