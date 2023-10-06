package uk.ac.manchester.tornado.api.data.nativetypes;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public class ByteArray {
    private MemorySegment segment;
    private final int BYTE_BYTES = 1;

    private int numberOfElements;

    public ByteArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        segment = Arena.ofAuto().allocate((numberOfElements * BYTE_BYTES) + 24L, 1);
    }

    public void set(int index, byte value) {
        segment.setAtIndex(JAVA_BYTE, index, value);
    }

    public byte get(int index) {
        return segment.getAtIndex(JAVA_BYTE, index);
    }

    public void init(byte value) {
        for (int i = 0; i < segment.byteSize() / BYTE_BYTES; i++) {
            segment.setAtIndex(JAVA_BYTE, i, value);
        }
    }

    public int getSize() {
        return numberOfElements;
    }

    public MemorySegment getSegment() {
        return segment;
    }

    public int getNumBytesOfSegment() {
        return numberOfElements;
    }
}
