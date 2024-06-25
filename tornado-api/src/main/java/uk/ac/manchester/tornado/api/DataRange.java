/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

/**
 * Data Range Class provides a set of utilities to perform partial copies between
 * the accelerator (e.g., a GPU) and the CPU.
 *
 * @since v1.0.1
 */
public class DataRange {

    private final TornadoNativeArray tornadoNativeArray;
    private final long totalSizeInBytes;
    private final int elementSize;
    private long offset;
    private long offsetMaterialized;
    private long partialSize;
    private long partialSizeMaterialized;
    private boolean isMaterialized;

    public DataRange(TornadoNativeArray tornadoNativeArray) {
        this.tornadoNativeArray = tornadoNativeArray;
        elementSize = tornadoNativeArray.getElementSize();
        totalSizeInBytes = tornadoNativeArray.getNumBytesOfSegment();
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

        if ((partialSizeMaterialized + offsetMaterialized) > (totalSizeInBytes + arrayHeader)) {
            throw new TornadoRuntimeException("[Error] Partial Copy size is larger than the array size. Check the value passed to the `withSize` method");
        }

        isMaterialized = true;
    }

    public TornadoNativeArray getArray() {
        return tornadoNativeArray;
    }
}
