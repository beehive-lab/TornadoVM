package uk.ac.manchester.tornado.api.types.tensors;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.api.types.tensors.dtype.DType;

public final class Tensor<T extends DType> extends TornadoNativeArray {

    private int numberOfElements;
    private MemorySegment segment;
    private int arrayHeaderSize;
    private int baseIndex;
    private long segmentByteSize;
    private Shape shape;
    private T dtype;

    public Tensor(Shape shape, T dtype) {
        this.shape = shape;
        this.numberOfElements = shape.getSize();
        this.dtype = dtype;

        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / dtype.getByteSize();
        segmentByteSize = numberOfElements * dtype.getByteSize() + arrayHeaderSize;

        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    //Create an instance of T

    @Override
    public int getSize() {
        return 0;
    }

    public String getDTYPE() {
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

    @Override
    public int getElementSize() {
        return 0;
    }

    public Shape getShape() {
        return shape;
    }
}
