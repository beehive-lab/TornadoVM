package uk.ac.manchester.tornado.api.types.tensors;

import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.api.types.tensors.dtype.DType;

public abstract sealed class Tensor extends TornadoNativeArray permits FloatTensor, HalfFloatTensor {
    //
    private final Shape shape;

    private final DType dtype;
    private int numberOfElements;
    private MemorySegment segment;
    private int arrayHeaderSize;
    private int baseIndex;
    private long segmentByteSize;

    public Tensor(Shape shape, DType dtype) {
        this.shape = shape;
        this.dtype = dtype;
    }

    public Shape getShape() {
        return shape;
    }

    public abstract int getSize();

    public abstract MemorySegment getSegment();

    public abstract long getNumBytesOfSegment();

    public abstract long getNumBytesWithoutHeader();

    public abstract void clear();

    public abstract int getElementSize();

    public abstract void reshape(Shape newShape);

    //    private DType getType()
}
