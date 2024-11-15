package uk.ac.manchester.tornado.api.types.tensors;

import sun.misc.Unsafe;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;

public class TensorQ8 extends Tensor {
    private final DType dType;
    private final Shape shape;

    private final HalfFloatArray tensorStorage;

    private int numberOfElements;


    public TensorQ8(Shape shape) {
        super(DType.HALF_FLOAT, shape);
        this.shape = shape;
        this.numberOfElements = shape.getSize();
        this.dType = DType.HALF_FLOAT;
        this.tensorStorage = new HalfFloatArray(numberOfElements);
    }


    public TensorQ8(int size, MemorySegment memorySegment) {
        super(DType.HALF_FLOAT, new Shape(size));
        this.dType = DType.HALF_FLOAT;
        this.shape = new Shape(size);
        this.numberOfElements = size;
        this.tensorStorage = HalfFloatArray.fromSegment(memorySegment);
    }


    static short readShort(MemorySegment memorySegment, long offset) {
        return memorySegment.get(ValueLayout.JAVA_SHORT, memorySegment.address()+offset);
    }

    static byte readByte(MemorySegment memorySegment, long offset) {
        return memorySegment.get(ValueLayout.JAVA_BYTE, memorySegment.address()+offset);
    }

    public float getFloat(int index) {
        assert 0 <= index && index < numberOfElements;
        int blockIndex = index / GGMLType.Q8_0.getBlockSize();
        int withinBlockIndex = index % GGMLType.Q8_0.getBlockSize();
        int blockOffset = blockIndex * GGMLType.Q8_0.getTypeSize();
        byte quant = readByte(tensorStorage.getSegment(), blockOffset + Float16.BYTES + withinBlockIndex);
        float scale = Float.float16ToFloat(readShort(tensorStorage.getSegment(), blockOffset));
        return quant * scale;
    }

    @Override
    public Shape getShape() {
        return null;
    }

    @Override
    public String getDTypeAsString() {
        return "";
    }

    @Override
    public DType getDType() {
        return null;
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public MemorySegment getSegment() {
        return null;
    }

    @Override
    public MemorySegment getSegmentWithHeader() {
        return null;
    }

    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return 0;
    }

    @Override
    public long getNumBytesOfSegment() {
        return 0;
    }

    @Override
    protected void clear() {

    }

    @Override
    public int getElementSize() {
        return 0;
    }
}
