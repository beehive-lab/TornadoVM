package uk.ac.manchester.tornado.api.types.tensors;

import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

@SegmentElementSize(size = 4)
public final class TensorFloat32 extends TornadoNativeArray implements AbstractTensor {

    private static final int FLOAT_BYTES = 4;
    /**
     * The data type of the elements contained within the tensor.
     */
    private final DType dType;
    private final Shape shape;

    private final FloatArray tensorStorage;

    /**
     * The total number of elements in the tensor.
     */
    private int numberOfElements;

    /**
     * The memory segment representing the tensor data in native memory.
     */

    public TensorFloat32(Shape shape) {
        this.shape = shape;
        this.numberOfElements = shape.getSize();
        this.dType = DType.FLOAT;
        this.tensorStorage = new FloatArray(numberOfElements);
    }

    public void init(float value) {
        for (int i = 0; i < getSize(); i++) {
            tensorStorage.getSegmentWithHeader().setAtIndex(JAVA_FLOAT, getBaseIndex() + i, value);
        }
    }

    public void set(int index, HalfFloat value) {
        tensorStorage.getSegmentWithHeader().setAtIndex(JAVA_SHORT, getBaseIndex() + index, value.getHalfFloatValue());
    }

    private long getBaseIndex() {
        return (int) TornadoNativeArray.ARRAY_HEADER / FLOAT_BYTES;
    }

    /**
     * Gets the float value stored at the specified index of the {@link FloatArray} instance.
     *
     * @param index
     *     The index of which to retrieve the float value.
     * @return
     */
    public float get(int index) {
        return tensorStorage.getSegmentWithHeader().getAtIndex(JAVA_FLOAT, getBaseIndex() + index);
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
        init(0.0f);
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
