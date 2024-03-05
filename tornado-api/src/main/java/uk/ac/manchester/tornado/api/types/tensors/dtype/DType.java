package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

public sealed abstract class DType permits Bool, Double, Float, HalfFloat, Int16, Int32, Int64, Int8, QInt32, QInt8, QUInt8 {
    private final int size;
    private final ValueLayout layout;

    protected DType(int size, ValueLayout layout) {
        this.size = size;
        this.layout = layout;
    }

    public int getByteSize() {
        return size;
    }

    public ValueLayout getLayout() {
        return layout;
    }

    public abstract String getDType();

    public int calculateSize(int[] shape) {
        int size = 1;
        for (int dim : shape) {
            size *= dim;
        }
        return size;
    }
}