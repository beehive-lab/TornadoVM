package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class LevelZeroBufferInteger {

    private long ptrBuffer;

    public LevelZeroBufferInteger() {
        this.ptrBuffer = -1;
    }

    public long getPtrBuffer() {
        return ptrBuffer;
    }

    public void memset(int value, int bufferSize) {
        memset_native(this, value, bufferSize);
    }

    public boolean isEqual(LevelZeroBufferInteger bufferB, int size) {
        return isEqual(this.ptrBuffer, bufferB.getPtrBuffer(), size);
    }

    public void initPtr() {
        this.ptrBuffer = -1;
    }

    native void memset_native(LevelZeroBufferInteger javaBuffer, int value, int bufferSize);

    native boolean isEqual(long bufferAPtr, long bufferBPtr, int size);
}
