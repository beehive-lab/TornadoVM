package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class LevelZeroByteBuffer {

    private long ptrBuffer;
    private int size;
    private int alignment;

    public LevelZeroByteBuffer() {
        this.ptrBuffer = -1;
    }

    public LevelZeroByteBuffer(int size, int alignment) {
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

    public void memset(byte value, int bufferSize) {
        memset_native(this, value, bufferSize);
    }

    public boolean isEqual(LevelZeroByteBuffer bufferB, int size) {
        return isEqual(this.ptrBuffer, bufferB.getPtrBuffer(), size);
    }

    public void initPtr() {
        this.ptrBuffer = -1;
    }

    private native void memset_native(LevelZeroByteBuffer javaBuffer, byte value, int bufferSize);

    private native boolean isEqual(long bufferAPtr, long bufferBPtr, int size);

    private native void copy_native(long ptrBuffer, byte[] array);

    /**
     * Copies the input array into the LevelZeroBuffer
     * 
     * @param array
     */
    public void copy(byte[] array) {
        copy_native(this.ptrBuffer, array);
    }

    private native byte[] getByteBuffer_native(long ptrBuffer, int size);

    public byte[] getByteBuffer() {
        return getByteBuffer_native(this.ptrBuffer, this.size);
    }
}
