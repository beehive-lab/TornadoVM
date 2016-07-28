package tornado.drivers.opencl.mm;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import tornado.api.Event;
import tornado.common.ObjectBuffer;
import tornado.common.RuntimeUtilities;
import tornado.common.Tornado;
import tornado.common.exceptions.TornadoInternalError;
import tornado.common.exceptions.TornadoOutOfMemoryException;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.OCLEvent;
import tornado.runtime.TornadoRuntime;
import tornado.runtime.api.TaskUtils;

public abstract class OCLArrayWrapper<T> implements ObjectBuffer {

    private final int arrayHeaderSize;

    private final int arrayLengthOffset;

    private long bufferOffset;

    private long bytes;

    protected final OCLDeviceContext deviceContext;

    private final Kind kind;
    private boolean onDevice;
    private boolean isFinal;

    public OCLArrayWrapper(final OCLDeviceContext device, final Kind kind) {
        this(device, kind, false);
    }

    public OCLArrayWrapper(final OCLDeviceContext device, final Kind kind, final boolean isFinal) {
        this.deviceContext = device;
        this.kind = kind;
        this.isFinal = isFinal;

        final HotSpotGraalRuntimeProvider runtime = TornadoRuntime.getVMRuntimeProvider();

        arrayLengthOffset = runtime.getConfig().arrayLengthOffset;
        arrayHeaderSize = runtime.getArrayBaseOffset(kind);
        onDevice = false;
    }

    @SuppressWarnings("unchecked")
    private T cast(Object array) {
        try {
            return (T) array;
        } catch (Exception | Error e) {
            TornadoInternalError.shouldNotReachHere("Unable to cast object: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void allocate(Object value) throws TornadoOutOfMemoryException {
        final T ref = cast(value);
        bytes = sizeOf(ref);

        bufferOffset = deviceContext.getMemoryManager().tryAllocate(ref.getClass(), bytes, arrayHeaderSize, getAlignment());

        Tornado.info("allocated: array kind=%s, size=%s, length offset=%d, header size=%d, bo=0x%x",
                kind.getJavaName(), RuntimeUtilities.humanReadableByteCount(bytes, true),
                arrayLengthOffset, arrayHeaderSize, bufferOffset);
        Tornado.info("allocated: %s", toString());
    }

    @Override
    public long size() {
        return bytes;
    }

    /*
     * Retrieves a buffer that will contain the contents
     * of the array header. The header is also populated using the header from the given array.
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
    public Event enqueueRead(final Object value) {
        final T array = cast(value);
        final List<Event> waitEvents = new ArrayList<Event>(0);
        return enqueueReadAfterAll(array, waitEvents);
    }

    @Override
    public Event enqueueReadAfter(final Object value, final Event event) {
        final T array = cast(value);
        final List<Event> waitEvents = new ArrayList<Event>(1);
        waitEvents.add(event);
        return enqueueReadAfterAll(array, waitEvents);
    }

    @Override
    public Event enqueueReadAfterAll(final Object value, final List<Event> events) {
        final T array = cast(value);
        if (isFinal) {
            return enqueueReadArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes
                    - arrayHeaderSize, array, events);
        } else {
            final List<Event> arrayEvents = new ArrayList<>(2);
            arrayEvents.add(prepareArrayHeader().enqueueReadAfterAll(Collections.emptyList()));
            arrayEvents.add(enqueueReadArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes
                    - arrayHeaderSize, array, events));
            return deviceContext.enqueueBarrier(arrayEvents);
        }
    }

    abstract protected OCLEvent enqueueReadArrayData(long bufferId, long offset, long bytes,
            T value, List<Event> waitEvents);

    @Override
    public Event enqueueWrite(final Object value) {
        final T array = cast(value);
        final List<Event> waitEvents = new ArrayList<>(1);
        return enqueueWriteAfterAll(array, waitEvents);
    }

    @Override
    public Event enqueueWriteAfter(final Object value, final Event event) {
        final T array = cast(value);
        final List<Event> waitEvents = new ArrayList<>(1);
        waitEvents.add(event);
        return enqueueWriteAfterAll(array, waitEvents);
    }

    @Override
    public Event enqueueWriteAfterAll(final Object value, final List<Event> events) {
        final T array = cast(value);
        TaskUtils.waitForEvents(events);

        if (isFinal && onDevice) {
            return enqueueWriteArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes
                    - arrayHeaderSize, array, events);
        } else {
            final List<Event> arrayEvents = new ArrayList<>(2);
            if (!onDevice || !isFinal) {
                arrayEvents.add(buildArrayHeader((T) array).enqueueWriteAfterAll(
                        events));
            }
            arrayEvents.add(enqueueWriteArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes
                    - arrayHeaderSize, array, events));
            onDevice = true;
            return deviceContext.enqueueBarrier(arrayEvents);
        }
    }

    abstract protected OCLEvent enqueueWriteArrayData(long bufferId, long offset, long bytes,
            T value, List<Event> waitEvents);

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
     * Retrieves a buffer that will contain the contents
     * of the array header. This also re-sizes the buffer.
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
            Tornado.fatal("Array header is invalid");
        }
    }

    abstract protected void readArrayData(long bufferId, long offset, long bytes, T value,
            List<Event> waitEvents);

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
                RuntimeUtilities.humanReadableByteCount(bytes, true), toAbsoluteAddress(),
                toRelativeAddress());
    }

    @Override
    public void printHeapTrace() {
        System.out.printf("0x%x\ttype=%s\n", toAbsoluteAddress(), kind.getJavaName());

    }

    /*
     * Retrieves a buffer that will contain the contents
     * of the array header. This also re-sizes the buffer.
     */
    private boolean validateArrayHeader(final T array) {
        final OCLByteBuffer header = prepareArrayHeader();
        header.read();
        // header.dump(8);
        final int numElements = header.getInt(arrayLengthOffset);
        final boolean valid = numElements == Array.getLength(array);
        if (!valid) {
            Tornado.fatal("Array: expected=%d, got=%d", Array.getLength(array), numElements);
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
            List<Event> waitEvents);

}
