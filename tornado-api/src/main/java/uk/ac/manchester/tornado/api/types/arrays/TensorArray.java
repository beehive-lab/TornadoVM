package uk.ac.manchester.tornado.api.types.arrays;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.types.tensors.Shape;
import uk.ac.manchester.tornado.api.types.tensors.dtype.DType;

public final class TensorArray<T extends DType> extends TornadoNativeArray {

    private int numberOfElements;
    private MemorySegment segment;
    private int arrayHeaderSize;
    private int baseIndex;
    private long segmentByteSize;
    private Shape shape;

    private T dtype;

    public TensorArray(Shape shape) {
        this.shape = shape;
        this.numberOfElements = shape.getSize();
        final int bytes = cast(dtype);

        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / dtype.getByteSize();
        segmentByteSize = numberOfElements * dtype.getByteSize() + arrayHeaderSize;

        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    private T cast(Object array) {
        try {
            return (T) array;
        } catch (Exception | Error e) {
            shouldNotReachHere("[ERROR] Unable to cast object: " + e.getMessage());
        }
        return null;
    }

    tensorArray.make(new @SuppressWarnings("unchecked")

    private int cast(DType array) {
        try {
            return array.getByteSize();
        } catch (Exception | Error e) {
            shouldNotReachHere("[ERROR] Unable to cast object: " + e.getMessage());
        }
        return -1;
    });

    //    tensorArray.createFromSegmnent(shape, floatArray.getSegment);

    @Override
    public int getSize() {
        return 0;
    }

    public DType getDTYPE() {
        return dtype;
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
