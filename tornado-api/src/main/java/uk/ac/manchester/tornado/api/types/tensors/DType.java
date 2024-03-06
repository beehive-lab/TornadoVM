package uk.ac.manchester.tornado.api.types.tensors;

import java.lang.foreign.ValueLayout;

import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

public enum DType {
    // @formatter:off
    HALF_FLOAT(2, ValueLayout.JAVA_SHORT),
    FLOAT(4, ValueLayout.JAVA_FLOAT),
    DOUBLE(8, ValueLayout.JAVA_DOUBLE),
    INT8(1, ValueLayout.JAVA_BYTE),
    INT16(2, ValueLayout.JAVA_SHORT),
    INT32(4, ValueLayout.JAVA_INT),
    INT64(8, ValueLayout.JAVA_LONG),
    UINT8(1, ValueLayout.JAVA_BYTE),
    BOOL(1, ValueLayout.JAVA_BYTE),
    QINT8(1, ValueLayout.JAVA_BYTE),
    QUINT8(1, ValueLayout.JAVA_BYTE),
    QINT32(4, ValueLayout.JAVA_INT);
    // @formatter:on

    private final int size;
    private final ValueLayout layout;

    DType(int size, ValueLayout layout) {
        this.size = size;
        this.layout = layout;
    }

    public int getByteSize() {
        return size;
    }

    public ValueLayout getLayout() {
        return layout;
    }

    public TornadoNativeArray createArray(int[] shape) {
        switch (this) {
            case HALF_FLOAT:
            case FLOAT:
                return new FloatArray(calculateSize(shape));
            case DOUBLE:
                return new DoubleArray(calculateSize(shape));
            case INT8:
                return new IntArray(calculateSize(shape));
            // Add cases for other DType implementations
            default:
                throw new IllegalArgumentException("Unsupported dtype: " + this);
        }
    }

    private int calculateSize(int[] shape) {
        int size = 1;
        for (int dim : shape) {
            size *= dim;
        }
        return size;
    }
}
