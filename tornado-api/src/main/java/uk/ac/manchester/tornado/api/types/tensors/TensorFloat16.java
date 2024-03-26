package uk.ac.manchester.tornado.api.types.tensors;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

@SegmentElementSize(size = 2)
public final class TensorFloat16 extends TornadoNativeArray implements AbstractTensor {
    private static final int HALF_FLOAT_BYTES = 2;
    /**
     * The data type of the elements contained within the tensor.
     */
    private final DType dType;
    private final Shape shape;

    private final HalfFloatArray tensorStorage;

    /**
     * The total number of elements in the tensor.
     */
    private int numberOfElements;

    /**
     * The memory segment representing the tensor data in native memory.
     */

    public TensorFloat16(Shape shape) {
        this.shape = shape;
        this.numberOfElements = shape.getSize();
        this.dType = DType.HALF_FLOAT;
        this.tensorStorage = new HalfFloatArray(numberOfElements);
    }

    public void init(HalfFloat value) {
        for (int i = 0; i < getSize(); i++) {
            tensorStorage.getSegmentWithHeader().setAtIndex(JAVA_SHORT, getBaseIndex() + i, value.getHalfFloatValue());
        }
    }

    public void set(int index, HalfFloat value) {
        tensorStorage.getSegmentWithHeader().setAtIndex(JAVA_SHORT, getBaseIndex() + index, value.getHalfFloatValue());
    }

    private long getBaseIndex() {
        return (int) TornadoNativeArray.ARRAY_HEADER / HALF_FLOAT_BYTES;
    }

    /**
     * Gets the half_float value stored at the specified index of the {@link HalfFloatArray} instance.
     *
     * @param index
     *     The index of which to retrieve the float value.
     * @return
     */
    public HalfFloat get(int index) {
        short halfFloatValue = tensorStorage.getSegmentWithHeader().getAtIndex(JAVA_SHORT, getBaseIndex() + index);
        return new HalfFloat(halfFloatValue);
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
        init(new HalfFloat(0));
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

    public static void initialize(TensorFloat32 tensor, HalfFloat value) {
        for (@Parallel int i = 0; i < tensor.getSize(); i++) {
            tensor.set(i, value);
        }
    }
}
