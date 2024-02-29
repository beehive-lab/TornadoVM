package uk.ac.manchester.tornado.api.types.tensors;

import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.tensors.dtype.HalfFloat;

public non-sealed class HalfFloatTensor extends Tensor {

    private HalfFloatArray tensorData;
    //    private DType dType =

    public HalfFloatTensor(Shape shape) {
        super(shape, new HalfFloat());
        this.tensorData = new HalfFloatArray(shape.getSize());
    }

    public HalfFloatArray getTensorData() {
        return tensorData;
    }

    public MemorySegment getAsMemorySegment() {
        return tensorData.getSegment();
    }

    //    public HalfFloatTensor squeeze() {
    //        Shape shape = getShape();  // Assuming a getShape() method exists to retrieve the shape
    //        Shape newShape = shape.squeeze();  // Assuming a squeeze() method in Shape to remove singular dimensions
    //
    //        if (Arrays.equals(newShape.dimensions(), shape.dimensions())) {
    //            // No dimensions to squeeze, return a view of the same array
    //            return this;
    //        }
    //
    //        // Create a new array with the squeezed shape
    //        HalfFloatArray squeezedArray = new HalfFloatArray(IntStream.of(newShape.dimensions()).reduce(1, (a, b) -> a * b));
    //
    //        // Copy elements directly, preserving order
    //        for (int i = 0; i < numberOfElements; i++) {
    //            squeezedArray.set(i, get(i));  // Assuming a get() method to retrieve elements
    //        }
    //
    //        return squeezedArray;
    //    }

    @Override
    public int getSize() {
        return 0;
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
    public void clear() {

    }

    @Override
    public int getElementSize() {
        return 0;
    }

    @Override
    public void reshape(Shape newShape) {

    }
}
