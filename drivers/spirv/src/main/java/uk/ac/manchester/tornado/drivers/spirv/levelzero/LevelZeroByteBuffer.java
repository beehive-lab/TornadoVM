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

    native void memset_native(LevelZeroByteBuffer javaBuffer, byte value, int bufferSize);

    native boolean isEqual(long bufferAPtr, long bufferBPtr, int size);
}
