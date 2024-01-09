package uk.ac.manchester.tornado.api;

import java.lang.foreign.ValueLayout;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

public class DataRange {

    private final TornadoNativeArray tornadoNativeArray;
    private long offset;

    private long offsetMaterialized;
    private long partialSize;

    private long partialSizeMaterialized;

    private ValueLayout layout;

    private boolean isMaterialized;

    private long totalSizeInBytes;

    private int elementSize;

    public DataRange(TornadoNativeArray tornadoNativeArray) {
        this.tornadoNativeArray = tornadoNativeArray;
        elementSize = tornadoNativeArray.getElementSize();
        totalSizeInBytes = tornadoNativeArray.getNumBytesWithoutHeader();
    }

    public DataRange withOffset(long offset) {
        this.offset = offset;
        return this;
    }

    public DataRange withSize(long size) {
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

    public TornadoNativeArray getArray() {
        return tornadoNativeArray;
    }
}
