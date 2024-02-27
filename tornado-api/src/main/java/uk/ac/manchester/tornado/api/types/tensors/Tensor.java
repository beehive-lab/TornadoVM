package uk.ac.manchester.tornado.api.types.tensors;

import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;

public class Tensor<T> {

    private final DType dtype;
    private final int[] shape;
    private final T data;

    public Tensor(DType dtype, int[] shape) {
        if (dtype == null) {
            throw new IllegalArgumentException("DType cannot be null");
        }

        this.dtype = dtype;
        this.shape = shape;

        // Initialize data based on DType
        this.data = createData(dtype, calculateDataSize(shape));
    }

    private T createData(DType dtype, int size) {
        return switch (dtype) {
            case HALF_FLOAT -> (T) new HalfFloatArray(size);
            case FLOAT -> (T) new FloatArray(size);
            case DOUBLE -> (T) new DoubleArray(size);
            case INT8, UINT8, BOOL -> (T) new ByteArray(size);
            case INT16 -> (T) new ShortArray(size);
            case INT32 -> (T) new IntArray(size);
            case INT64 -> (T) new LongArray(size);
            //            case TORNADO_NATIVE: // Assuming your TornadoNativeArray implementation
            //                return (T) new TornadoNativeArray(dtype, size); // Create TornadoNativeArray
            default -> throw new IllegalArgumentException(STR."Unsupported DType: \{dtype}");
        };
    }

    private int calculateDataSize(int[] shape) {
        int size = 1;
        for (int dim : shape) {
            size *= dim;
        }
        return size * dtype.getByteSize();
    }

    public DType getDType() {
        return dtype;
    }

    public int[] getShape() {
        return shape;
    }

    @SuppressWarnings("unchecked")
    public <U extends T> U getData() {
        return (U) this.data;
    }

    // Additional methods specific to TornadoNativeArray if needed

}