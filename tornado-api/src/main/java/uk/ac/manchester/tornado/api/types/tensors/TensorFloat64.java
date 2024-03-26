package uk.ac.manchester.tornado.api.types.tensors;

import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;

public final class TensorFloat64 extends TornadoNativeArray implements AbstractTensor {

    private static final int DOUBLE_BYTES = 8;
    /**
     * The data type of the elements contained within the tensor.
     */
    private final DType dType;
    private final Shape shape;

    private final DoubleArray tensorStorage;

    /**
     * The total number of elements in the tensor.
     */
    private int numberOfElements;

    /**
     * The memory segment representing the tensor data in native memory.
     */

    public TensorFloat64(Shape shape) {
        this.shape = shape;
        this.numberOfElements = shape.getSize();
        this.dType = DType.DOUBLE;
        this.tensorStorage = new DoubleArray(numberOfElements);
    }

    public void init(double value) {
        for (int i = 0; i < getSize(); i++) {
            tensorStorage.getSegmentWithHeader().setAtIndex(JAVA_DOUBLE, getBaseIndex() + i, value);
        }
    }

    public void set(int index, double value) {
        tensorStorage.getSegmentWithHeader().setAtIndex(JAVA_DOUBLE, getBaseIndex() + index, value);
    }

    private long getBaseIndex() {
        return (int) TornadoNativeArray.ARRAY_HEADER / DOUBLE_BYTES;
    }

    /**
     * Gets the double value stored at the specified index of the {@link DoubleArray} instance.
     *
     * @param index
     *     The index of which to retrieve the double value.
     * @return
     */
    public double get(int index) {
        return tensorStorage.getSegmentWithHeader().getAtIndex(JAVA_DOUBLE, getBaseIndex() + index);
    }

    @Override
    public int getSize() {
        return numberOfElements;
    }

    @Override
    public MemorySegment getSegment() {
        return tensorStorage.getSegment();
    }

    @Override
    public MemorySegment getSegmentWithHeader() {
        return tensorStorage.getSegmentWithHeader();
    }

    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return tensorStorage.getNumBytesOfSegmentWithHeader();
    }

    @Override
    public long getNumBytesOfSegment() {
        return tensorStorage.getNumBytesOfSegment();
    }

    @Override
    protected void clear() {

    }

    @Override
    public int getElementSize() {
        return 0;
    }

    @Override
    public Shape getShape() {
        return this.shape;
    }

    @Override
    public String getDTypeAsString() {
        return dType.toString();
    }

    @Override
    public DType getDType() {
        return dType;
    }
}
