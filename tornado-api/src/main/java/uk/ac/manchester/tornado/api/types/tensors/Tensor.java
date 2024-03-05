package uk.ac.manchester.tornado.api.types.tensors;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.api.types.tensors.dtype.DType;

@SegmentElementSize(size = 2)
public final class Tensor<T extends DType> extends TornadoNativeArray {

    private int numberOfElements;
    private MemorySegment segment;
    private int arrayHeaderSize;
    private int baseIndex;
    private long segmentByteSize;
    private Shape shape;
    private T dtype;

    public Tensor(int size, MemorySegment memorySegment, T dtype) {
        assert size >= 0;
        this.numberOfElements = size;
        this.segment = memorySegment;
        this.dtype = dtype;
    }

    public Tensor(Shape shape, T dtype) {
        this.shape = shape;
        this.numberOfElements = shape.getSize();
        this.dtype = dtype;

        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / dtype.getByteSize();
        segmentByteSize = (long) numberOfElements * dtype.getByteSize() + arrayHeaderSize;

        this.segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    @Override
    public int getSize() {
        return 0;
    }

    public String getDTypeAsString() {
        return dtype.getDType();
    }

    @Override
    public MemorySegment getSegment() {
        return null;
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

    public HalfFloat get(int index) {
        short halfFloatValue = segment.getAtIndex(JAVA_SHORT, baseIndex + index);
        return new HalfFloat(halfFloatValue);
    }

    public void init(HalfFloat value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_SHORT, baseIndex + i, value.getHalfFloatValue());
        }
    }

    @Override
    public int getElementSize() {
        return 0;
    }

    public Shape getShape() {
        return shape;
    }
}
