package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

public abstract class DType {
    private final int size;
    private final ValueLayout layout;

    public DType(int size, ValueLayout layout) {
        this.size = size;
        this.layout = layout;
    }

    public int getByteSize() {
        return size;
    }

    public ValueLayout getLayout() {
        return layout;
    }

    public int calculateSize(int[] shape) {
        int size = 1;
        for (int dim : shape) {
            size *= dim;
        }
        return size;
    }
}