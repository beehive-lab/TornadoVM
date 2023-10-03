package uk.ac.manchester.tornado.api.data.nativetypes;

import static java.lang.foreign.ValueLayout.JAVA_CHAR;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public class CharArray {
    private MemorySegment segment;
    private final int CHAR_BYTES = 2;

    private int numberOfElements;

    public CharArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        segment = Arena.ofAuto().allocate(numberOfElements * CHAR_BYTES, 1);
    }

    public void set(int index, char value) {
        segment.setAtIndex(JAVA_CHAR, index, value);
    }

    public char get(int index) {
        return segment.getAtIndex(JAVA_CHAR, index);
    }

    public void init(char value) {
        for (int i = 0; i < segment.byteSize() / CHAR_BYTES; i++) {
            segment.setAtIndex(JAVA_CHAR, i, value);
        }
    }

    public int getSize() {
        return numberOfElements;
    }

    public MemorySegment getSegment() {
        return segment;
    }

    @Override
    public String toString() {
        String arrayContents = String.valueOf(this.get(0));
        for (int i = 1; i < numberOfElements; i++) {
            arrayContents += ", " + this.get(i);
        }
        return arrayContents;
    }

    public long getNumBytesOfSegment() {
        return numberOfElements * CHAR_BYTES;
    }
}
