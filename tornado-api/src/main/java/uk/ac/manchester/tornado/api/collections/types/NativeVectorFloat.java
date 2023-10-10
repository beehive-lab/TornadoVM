package uk.ac.manchester.tornado.api.collections.types;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.data.nativetypes.TornadoNativeArray;

public class NativeVectorFloat extends TornadoNativeArray {
    private MemorySegment segment;
    private final int FLOAT_BYTES = 4;

    private int numberOfElements;

    private long segmentByteSize;

    NativeVectorFloat(int numElements) {
        segmentByteSize = numElements * FLOAT_BYTES;
        this.numberOfElements = numElements;
        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
    }

    public void set(int index, float value) {
        segment.setAtIndex(JAVA_FLOAT, index, value);
    }

    public float get(int index) {
        return segment.getAtIndex(JAVA_FLOAT, index);
    }

    public void init(float value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_FLOAT, i, value);
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
