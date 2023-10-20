package uk.ac.manchester.tornado.api.data.nativetypes;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.type.annotations.PanamaElementSize;

@PanamaElementSize(size = 1)
public class ByteArray extends TornadoNativeArray {
    private final int BYTE_BYTES = 1;
    private MemorySegment segment;
    private int numberOfElements;
    private int arrayHeaderSize;

    private int baseIndex;
    private int arraySizeHeaderPosition;

    private long segmentByteSize;

    public ByteArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / BYTE_BYTES;
        arraySizeHeaderPosition = baseIndex - 4;
        segmentByteSize = numberOfElements * BYTE_BYTES + arrayHeaderSize;

        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    public ByteArray(byte... values) {
        this(values.length);
        for (int i = 0; i < values.length; i++) {
            set(i, values[i]);
        }
    }

    public void set(int index, byte value) {
        segment.setAtIndex(JAVA_BYTE, baseIndex + index, value);
    }

    public byte get(int index) {
        return segment.getAtIndex(JAVA_BYTE, baseIndex + index);
    }

    public void init(byte value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_BYTE, baseIndex + i, value);
        }
    }

    @Override
    public int getSize() {
        return numberOfElements;
    }

    @Override
    public MemorySegment getSegment() {
        return segment;
    }

    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize;
    }
}
