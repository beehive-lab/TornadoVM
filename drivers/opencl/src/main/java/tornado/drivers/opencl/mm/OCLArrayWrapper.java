package tornado.drivers.opencl.mm;

import java.lang.reflect.Array;
import jdk.vm.ci.meta.JavaKind;
import tornado.common.ObjectBuffer;
import tornado.common.exceptions.TornadoOutOfMemoryException;
import tornado.drivers.opencl.OCLDeviceContext;

import static tornado.common.RuntimeUtilities.humanReadableByteCount;
import static tornado.common.Tornado.*;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.runtime.TornadoRuntime.getVMConfig;

public abstract class OCLArrayWrapper<T> implements ObjectBuffer {

    private final int arrayHeaderSize;

    private final int arrayLengthOffset;

    private long bufferOffset;

    private long bytes;

    protected final OCLDeviceContext deviceContext;

    private final JavaKind kind;
    private boolean onDevice;
    private boolean isFinal;

    // TODO remove this
    private final int[] internalEvents = new int[2];

    public OCLArrayWrapper(final OCLDeviceContext device, final JavaKind kind) {
        this(device, kind, false);
    }

    public OCLArrayWrapper(final OCLDeviceContext device, final JavaKind kind, final boolean isFinal) {
        this.deviceContext = device;
        this.kind = kind;
        this.isFinal = isFinal;

        arrayLengthOffset = getVMConfig().arrayOopDescLengthOffset();
        arrayHeaderSize = getVMConfig().getArrayBaseOffset(kind);
        onDevice = false;
        bufferOffset = -1;
    }

    @SuppressWarnings("unchecked")
    private T cast(Object array) {
        try {
            return (T) array;
        } catch (Exception | Error e) {
            shouldNotReachHere("Unable to cast object: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void allocate(Object value) throws TornadoOutOfMemoryException {
        if (bufferOffset == -1) {
            final T ref = cast(value);
            bytes = sizeOf(ref);

            bufferOffset = deviceContext.getMemoryManager().tryAllocate(ref.getClass(), bytes, arrayHeaderSize, getAlignment());

            info("allocated: array kind=%s, size=%s, length offset=%d, header size=%d, bo=0x%x",
                    kind.getJavaName(), humanReadableByteCount(bytes, true),
                    arrayLengthOffset, arrayHeaderSize, bufferOffset);
            info("allocated: %s", toString());
        }
    }

    @Override
    public long size() {
        return bytes;
    }

    /*
     * Retrieves a buffer that will contain the contents of the array header.
     * The header is also populated using the header from the given array.
     */
    private OCLByteBuffer buildArrayHeader(final T array) {
        final OCLByteBuffer header = getArrayHeader();
        int index = 0;
        while (index < arrayLengthOffset) {
            header.buffer.put((byte) 0);
            index++;
        }
        header.buffer.putInt(Array.getLength(array));
        // header.dump(8);
        return header;
    }

    @Override
    public int enqueueRead(final Object value, final int[] events) {
        final T array = cast(value);
        if (isFinal) {
            return enqueueReadArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes
                    - arrayHeaderSize, array, events);
        } else {
            internalEvents[0] = prepareArrayHeader().enqueueRead(null);
            internalEvents[1] = enqueueReadArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes
                    - arrayHeaderSize, array, events);
            return ENABLE_OOO_EXECUTION | VM_USE_DEPS ? deviceContext.enqueueMarker(internalEvents) : internalEvents[1];
        }
    }

    abstract protected int enqueueReadArrayData(long bufferId, long offset, long bytes,
            T value, int[] waitEvents);

    @Override
    public int enqueueWrite(final Object value, final int[] events) {
        final T array = cast(value);

        if (isFinal && onDevice) {
            return enqueueWriteArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes
                    - arrayHeaderSize, array, events);
        } else {
            if (!onDevice || !isFinal) {
                internalEvents[0] = buildArrayHeader((T) array).enqueueWrite(
                        events);
            }
            internalEvents[1] = enqueueWriteArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes
                    - arrayHeaderSize, array, events);
            onDevice = true;
            return ENABLE_OOO_EXECUTION | VM_USE_DEPS ? deviceContext.enqueueMarker(internalEvents) : internalEvents[1];
        }
    }

    abstract protected int enqueueWriteArrayData(long bufferId, long offset, long bytes,
            T value, int[] waitEvents);

    @Override
    public int getAlignment() {
        return 64;
    }

    private OCLByteBuffer getArrayHeader() {
        final OCLByteBuffer header = deviceContext.getMemoryManager().getSubBuffer(
                (int) bufferOffset, arrayHeaderSize);
        header.buffer.clear();
        return header;
    }

    @Override
    public long getBufferOffset() {
        return bufferOffset;
    }

    @Override
    public boolean isValid() {
        return onDevice;
    }

    @Override
    public void invalidate() {
        onDevice = false;
    }

    /*
     * Retrieves a buffer that will contain the contents of the array header.
     * This also re-sizes the buffer.
     */
    private OCLByteBuffer prepareArrayHeader() {
        final OCLByteBuffer header = getArrayHeader();
        header.buffer.position(header.buffer.capacity());
        return header;
    }

    @Override
    public void read(final Object value) {
        final T array = cast(value);
        if (validateArrayHeader(array)) {
            readArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes - arrayHeaderSize,
                    array, null);
        } else {
            shouldNotReachHere("Array header is invalid");
        }
    }

    abstract protected void readArrayData(long bufferId, long offset, long bytes, T value,
            int[] waitEvents);

    private int sizeOf(final T array) {
        return arrayHeaderSize + (Array.getLength(array) * kind.getByteCount());
    }

    @Override
    public long toAbsoluteAddress() {
        return deviceContext.getMemoryManager().toAbsoluteDeviceAddress(bufferOffset);
    }

    @Override
    public long toBuffer() {
        return deviceContext.getMemoryManager().toBuffer();
    }

    @Override
    public long toRelativeAddress() {
        return bufferOffset;
    }

    @Override
    public String toString() {
        return String.format("buffer<%s> %s @ 0x%x (0x%x)", kind.getJavaName(),
                humanReadableByteCount(bytes, true), toAbsoluteAddress(),
                toRelativeAddress());
    }

    @Override
    public void printHeapTrace() {
        System.out.printf("0x%x\ttype=%s\n", toAbsoluteAddress(), kind.getJavaName());

    }

    /*
     * Retrieves a buffer that will contain the contents of the array header.
     * This also re-sizes the buffer.
     */
    private boolean validateArrayHeader(final T array) {
        final OCLByteBuffer header = prepareArrayHeader();
        header.read();
        // header.dump(8);
        final int numElements = header.getInt(arrayLengthOffset);
        final boolean valid = numElements == Array.getLength(array);
        if (!valid) {
            fatal("Array: expected=%d, got=%d", Array.getLength(array), numElements);
            header.dump(8);
        }
        return valid;
    }

    @Override
    public void write(final Object value) {
        final T array = cast(value);
        buildArrayHeader(array).write();
        writeArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes - arrayHeaderSize, array,
                null);
        onDevice = true;

    }

    abstract protected void writeArrayData(long bufferId, long offset, long bytes, T value,
            int[] waitEvents);

}
