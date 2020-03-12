package uk.ac.manchester.tornado.drivers.cuda.mm;

import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;

import java.nio.ByteBuffer;

public class CUDAByteBuffer {
    protected ByteBuffer buffer;
    private long bytes;
    private long offset;
    private CUDADeviceContext deviceContext;

    public CUDAByteBuffer(long bytes, long offset, CUDADeviceContext deviceContext) {
        this.bytes = bytes;
        this.offset = offset;
        this.deviceContext = deviceContext;

        buffer = ByteBuffer.allocate((int) bytes);
        buffer.order(deviceContext.getByteOrder());
    }

    public long getSize() {
        return bytes;
    }

    public void read() {
        read(null);
    }

    private void read(int[] events) {
        deviceContext.readBuffer(heapPointer() + offset, bytes, buffer.array(), 0, events);
    }

    public int getInt(int offset) {
        return buffer.getInt(offset);
    }

    public void dump(int width) {
        buffer.position(buffer.capacity());
        System.out.printf("Buffer  : capacity = %s, in use = %s, device = %s \n", RuntimeUtilities.humanReadableByteCount(bytes, true),
                RuntimeUtilities.humanReadableByteCount(buffer.position(), true), deviceContext.getDevice().getDeviceName());
        for (int i = 0; i < buffer.position(); i += width) {
            System.out.printf("[0x%04x]: ", i + toAbsoluteAddress());
            for (int j = 0; j < Math.min(buffer.capacity() - i, width); j++) {
                if (j % 2 == 0) {
                    System.out.print(" ");
                }
                if (j < buffer.position() - i) {
                    System.out.printf("%02x", buffer.get(i + j));
                } else {
                    System.out.print("..");
                }
            }
            System.out.println();
        }
    }

    protected long toAbsoluteAddress() {
        return deviceContext.getMemoryManager().toAbsoluteDeviceAddress(offset);
    }

    public void write() {
        write(null);
    }

    public void write(int[] events) {
        deviceContext.writeBuffer(heapPointer() + offset, bytes, buffer.array(), 0, events);
    }

    private long heapPointer() {
        return deviceContext.getMemoryManager().toBuffer();
    }

    public int enqueueWrite() {
        return enqueueWrite(null);
    }

    public int enqueueWrite(int[] events) {
        return deviceContext.enqueueWriteBuffer(heapPointer() + offset, bytes, buffer.array(), 0, events);
    }

    public ByteBuffer buffer() {
        return buffer;
    }
}
