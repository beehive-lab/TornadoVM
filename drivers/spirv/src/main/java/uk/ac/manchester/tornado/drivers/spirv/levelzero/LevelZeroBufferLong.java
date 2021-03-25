package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class LevelZeroBufferLong {

    private long ptrBuffer;
    private int size;
    private int alignment;

    public LevelZeroBufferLong() {
        this.ptrBuffer = -1;
    }

    public LevelZeroBufferLong(int size, int alignment) {
        this.size = size;
        this.alignment = alignment;
        this.ptrBuffer = -1;
    }

    public long getPtrBuffer() {
        return this.ptrBuffer;
    }

    public int getSize() {
        return this.size;
    }

    public int getAlignment() {
        return this.alignment;
    }

    public void memset(long value, int bufferSize) {
        memset_native(this, value, bufferSize);
    }

    public boolean isEqual(LevelZeroBufferLong bufferB, int size) {
        return isEqual(this.ptrBuffer, bufferB.getPtrBuffer(), size);
    }

    public void initPtr() {
        this.ptrBuffer = -1;
    }

    private native void memset_native(LevelZeroBufferLong javaBuffer, long value, int bufferSize);

    private native boolean isEqual(long bufferAPtr, long bufferBPtr, int size);

    private native void copy_native(long ptrBuffer, long[] array);

    /**
     * Copies the input array into the LevelZeroBuffer
     *
     * @param array
     */
    public void copy(long[] array) {
        copy_native(this.ptrBuffer, array);
    }

    private native long[] getLongBuffer_native(long ptrBuffer, int size);

    public long[] getLongBuffer() {
        return getLongBuffer_native(this.ptrBuffer, this.size);
    }

}
