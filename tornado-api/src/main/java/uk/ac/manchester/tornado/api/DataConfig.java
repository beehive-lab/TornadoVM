package uk.ac.manchester.tornado.api;

import java.lang.foreign.ValueLayout;
import java.lang.reflect.Array;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

public class DataConfig {

    private long offset;

    private long offsetMaterialized;
    private long partialSize;

    private long partialSizeMaterialized;

    private ValueLayout layout;

    private boolean isMaterialized;

    private long totalSizeInBytes;

    private int elementSize;

    public DataConfig(Object object, ValueLayout layout) {
        this.layout = layout;
        computeElementSize();
        computeTotalSizeOfObject(object);
    }

    private void computeElementSize() {
        // layout is mandatory
        elementSize = switch (layout) {
            case ValueLayout.OfFloat _, ValueLayout.OfInt _ -> 4;
            case ValueLayout.OfDouble _, ValueLayout.OfLong _ -> 8;
            case ValueLayout.OfShort _, ValueLayout.OfChar _ -> 2;
            case ValueLayout.OfByte _ -> 1;
            case null, default -> throw new TornadoRuntimeException("[error] Data type not supported");
        };
    }

    private void computeTotalSizeOfObject(Object object) {
        if (object instanceof TornadoNativeArray array) {
            totalSizeInBytes = array.getNumBytesWithoutHeader();
        } else if (object.getClass().isArray()) {
            totalSizeInBytes = (long) Array.getLength(object) * elementSize;
        } else {
            throw new TornadoRuntimeException("[error] Data type not supported");
        }
    }

    public DataConfig withOffset(long offset) {
        this.offset = offset;
        return this;
    }

    public DataConfig withSize(long size) {
        this.partialSize = size;
        return this;
    }

    public long getOffset() {
        if (isMaterialized) {
            return this.offsetMaterialized;
        } else {
            throw new TornadoRuntimeException("[error] Invoke materialize() before any getter");
        }
    }

    public long getPartialSize() {
        if (isMaterialized) {
            return this.partialSizeMaterialized;
        } else {
            throw new TornadoRuntimeException("[error] Invoke materialize() before any getter");
        }
    }

    public ValueLayout getLayout() {
        return this.layout;
    }

    /**
     * Compute offset and size based on the information we have.
     */
    public void materialize() {
        // compute offset and size based on the information we have

        long arrayHeader = TornadoNativeArray.ARRAY_HEADER;
        if (partialSize == 0) {
            // copy from offset to the end of the array
            partialSizeMaterialized = totalSizeInBytes;
        } else {
            partialSizeMaterialized = partialSize * elementSize;
        }

        if (offset == 0) {
            // copy from the beginning
            offsetMaterialized = arrayHeader;
        } else {
            offsetMaterialized = arrayHeader + (offset * elementSize);
        }
        isMaterialized = true;
    }
}
