package uk.ac.manchester.tornado.api.types.tensors;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

@SegmentElementSize(size = 2) //This needs to be fixed
public final class Tensor extends TornadoNativeArray {

    private final DType dType;
    private int numberOfElements;
    private MemorySegment segment;
    private int baseIndex;
    private long segmentByteSize;
    private Shape shape;

    public Tensor(int size, MemorySegment memorySegment, DType dtype) {
        assert size >= 0;
        this.numberOfElements = size;
        this.segment = memorySegment;
        this.dType = dtype;
    }

    public Tensor(Shape shape, DType dtype) {
        this.shape = shape;
        this.numberOfElements = shape.getSize();
        this.dType = dtype;
        this.baseIndex = (int) TornadoNativeArray.ARRAY_HEADER / dtype.getByteSize();
        this.segmentByteSize = (long) numberOfElements * dtype.getByteSize() + TornadoNativeArray.ARRAY_HEADER;
        this.segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    @Override
    public int getSize() {
        return numberOfElements;
    }

    public String getDTypeAsString() {
        return dType.toString();
    }

    @Override
    public MemorySegment getSegment() {
        return segment;
    }

    @Override
    public long getNumBytesOfSegment() {
        return 0;
    }

    @Override
    public long getNumBytesWithoutHeader() {
        return 0;
    }

    @Override
    protected void clear() {
    }

    public void set(int index, HalfFloat value) {
        segment.setAtIndex(JAVA_SHORT, baseIndex + index, value.getHalfFloatValue());
    }

    public void set(int index, float value) {
        segment.setAtIndex(JAVA_FLOAT, baseIndex + index, value);
    }

    public HalfFloat get(int index) {
        short halfFloatValue = segment.getAtIndex(JAVA_SHORT, baseIndex + index);
        return new HalfFloat(halfFloatValue);
    }

    public float getFloatValue(int index) {
        return segment.getAtIndex(JAVA_FLOAT, baseIndex + index);
    }

    public void init(HalfFloat value) {
        assert dType.equals(DType.HALF_FLOAT);
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_SHORT, baseIndex + i, value.getHalfFloatValue());
        }
    }

    public void init(float value) {
        assert dType.equals(DType.FLOAT);
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_FLOAT, baseIndex + i, value);
        }
    }

    @Override
    public int getElementSize() {
        return numberOfElements;
    }

    public Shape getShape() {
        return shape;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Basic information
        sb.append("Tensor(").append(dType.toString()).append(", ").append(shape.toString()).append(")\n");

        int elementsPerLine = 10; // Adjust based on tensor size and preferences
        for (int i = 0; i < getSize(); i++) {
            HalfFloat value = get(i);

            sb.append(value.getFloat32());
            if ((i + 1) % elementsPerLine == 0) {
                sb.append("\n");
            } else {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

}
