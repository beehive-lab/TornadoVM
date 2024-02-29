package uk.ac.manchester.tornado.api.types.tensors;

import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.tensors.dtype.HalfFloat;

public non-sealed class FloatTensor extends Tensor {

    private FloatArray tensorData;

    public FloatTensor(Shape shape) {
        super(shape, new HalfFloat());
        this.tensorData = new FloatArray(shape.getSize());
    }

    public FloatArray getTensorData() {
        return tensorData;
    }

    public MemorySegment getAsMemorySegment() {
        return tensorData.getSegment();
    }

    @Override
    public int getSize() {
        return super.getShape().getSize();
    }

    @Override
    public MemorySegment getSegment() {
        return tensorData.getSegment();
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
