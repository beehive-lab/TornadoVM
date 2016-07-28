package tornado.drivers.opencl.mm;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import tornado.api.Event;
import tornado.common.RuntimeUtilities;
import tornado.common.Tornado;
import tornado.common.exceptions.TornadoOutOfMemoryException;
import tornado.drivers.opencl.OCLDeviceContext;

/**
 * A buffer for inspecting data within an OpenCL device. It is not backed by any
 * userspace object.
 */
public class OCLByteBuffer {

    protected ByteBuffer buffer;
    protected long bufferOffset;
    protected long bytes;

    protected final OCLDeviceContext deviceContext;

    protected long offset;

    protected OCLByteBuffer(final OCLDeviceContext device) {
        this.deviceContext = device;
    }

    public OCLByteBuffer(final OCLDeviceContext device, final long offset, final long numBytes) {
        this.deviceContext = device;
        this.offset = offset;
        this.bytes = numBytes;
        buffer = ByteBuffer.allocate((int) numBytes);
        buffer.order(deviceContext.getByteOrder());
    }

    public void allocate(final long numBytes) throws TornadoOutOfMemoryException {
        bytes = numBytes;

        offset = deviceContext.getMemoryManager().tryAllocate(byte[].class, numBytes, 0, getAlignment());

        buffer = ByteBuffer.allocate((int) numBytes);
        buffer.order(deviceContext.getByteOrder());

    }

    public ByteBuffer buffer() {
        return buffer;
    }

    public void read() {
        readAfterAll(null);
    }

    public void readAfter(final Event event) {
        List<Event> waitEvents = new ArrayList<Event>(1);
        waitEvents.add(event);
        readAfterAll(waitEvents);
    }

    public void readAfterAll(final List<Event> events) {
        deviceContext.readBuffer(toBuffer(), offset, bytes, buffer.array(), events);
    }

    public Event enqueueRead() {
        return enqueueReadAfterAll(null);
    }

    public Event enqueueReadAfter(final Event event) {
        List<Event> waitEvents = new ArrayList<Event>(1);
        waitEvents.add(event);
        return enqueueReadAfterAll(waitEvents);
    }

    public Event enqueueReadAfterAll(final List<Event> events) {
        return deviceContext.enqueueReadBuffer(toBuffer(), offset, bytes, buffer.array(), events);
    }

    public void write() {
        writeAfterAll(null);
    }

    public void writeAfter(final Event event) {
        final List<Event> waitEvents = new ArrayList<Event>(1);
        waitEvents.add(event);
        writeAfterAll(waitEvents);
    }

    public void writeAfterAll(final List<Event> events) {
        deviceContext.writeBuffer(toBuffer(), offset, bytes, buffer.array(), events);
    }

    public Event enqueueWrite() {
        return enqueueWriteAfterAll(null);
    }

    public Event enqueueWriteAfter(final Event event) {
        final List<Event> waitEvents = new ArrayList<Event>(1);
        waitEvents.add(event);
        return enqueueWriteAfterAll(waitEvents);

    }

    public Event enqueueWriteAfterAll(final List<Event> events) {
        return deviceContext.enqueueWriteBuffer(toBuffer(), offset, bytes, buffer.array(), events);
    }

    public void dump() {
        dump(64);
    }

    public void dump(final int width) {
        buffer.position(buffer.capacity());
        System.out.printf("Buffer  : capacity = %s, in use = %s, device = %s \n", RuntimeUtilities
                .humanReadableByteCount(bytes, true), RuntimeUtilities.humanReadableByteCount(
                buffer.position(), true), deviceContext.getDevice().getName());
        for (int i = 0; i < buffer.position(); i += width) {
            System.out.printf("[0x%04x]: ", i + toAbsoluteAddress());
            for (int j = 0; j < Math.min(buffer.capacity() - i, width); j++) {
                if (j % 2 == 0) {
                    System.out.printf(" ");
                }
                if (j < buffer.position() - i) {
                    System.out.printf("%02x", buffer.get(i + j));
                } else {
                    System.out.printf("..");
                }
            }
            System.out.println();
        }
    }

    public byte get() {
        return buffer.get();
    }

    public ByteBuffer get(final byte[] dst) {
        return buffer.get(dst);
    }

    public ByteBuffer get(final byte[] dst, final int offset, final int length) {
        return buffer.get(dst, offset, length);
    }

    public byte get(final int index) {
        return buffer.get(index);
    }

    public int getAlignment() {
        return 64;
    }

    public long getBufferOffset() {
        return offset;
    }

    public char getChar() {
        return buffer.getChar();
    }

    public char getChar(final int index) {
        return buffer.getChar(index);
    }

    public double getDouble() {
        return buffer.getDouble();
    }

    public double getDouble(final int index) {
        return buffer.getDouble(index);
    }

    public float getFloat() {
        return buffer.getFloat();
    }

    public float getFloat(final int index) {
        return buffer.getFloat(index);
    }

    public int getInt() {
        return buffer.getInt();
    }

    public int getInt(final int index) {
        return buffer.getInt(index);
    }

    public long getLong() {
        return buffer.getLong();
    }

    public long getLong(final int index) {
        return buffer.getLong(index);
    }

    public short getShort() {
        return buffer.getShort();
    }

    public short getShort(final int index) {
        return buffer.getShort(index);
    }

    public long getSize() {
        return bytes;
    }

    public ByteBuffer put(final byte b) {
        return buffer.put(b);
    }

    public final ByteBuffer put(final byte[] src) {
        return buffer.put(src);
    }

    public ByteBuffer put(final byte[] src, final int offset, final int length) {
        return buffer.put(src, offset, length);
    }

    public ByteBuffer put(final ByteBuffer src) {
        return buffer.put(src);
    }

    public ByteBuffer put(final int index, final byte b) {
        return buffer.put(index, b);
    }

    public ByteBuffer putChar(final char value) {
        return buffer.putChar(value);
    }

    public ByteBuffer putChar(final int index, final char value) {
        return buffer.putChar(index, value);
    }

    public ByteBuffer putDouble(final double value) {
        return buffer.putDouble(value);
    }

    public ByteBuffer putDouble(final int index, final double value) {
        return buffer.putDouble(index, value);
    }

    public ByteBuffer putFloat(final float value) {
        return buffer.putFloat(value);
    }

    public ByteBuffer putFloat(final int index, final float value) {
        return buffer.putFloat(index, value);
    }

    public ByteBuffer putInt(final int value) {
        return buffer.putInt(value);
    }

    public ByteBuffer putInt(final int index, final int value) {
        return buffer.putInt(index, value);
    }

    public ByteBuffer putLong(final int index, final long value) {
        return buffer.putLong(index, value);
    }

    public ByteBuffer putLong(final long value) {
        return buffer.putLong(value);
    }

    public ByteBuffer putShort(final int index, final short value) {
        return buffer.putShort(index, value);
    }

    public ByteBuffer putShort(final short value) {
        return buffer.putShort(value);
    }

    public long toAbsoluteAddress() {
        return deviceContext.getMemoryManager().toAbsoluteDeviceAddress(offset);
    }

    public long toBuffer() {
        return deviceContext.getMemoryManager().toBuffer();
    }

    public long toRelativeAddress() {
        return deviceContext.getMemoryManager().toRelativeDeviceAddress(offset);
    }

    public Object value() {
        return buffer.array();
    }

    public void zeroMemory() {
        Tornado.warn("zero memory unimplemented");

    }
}
