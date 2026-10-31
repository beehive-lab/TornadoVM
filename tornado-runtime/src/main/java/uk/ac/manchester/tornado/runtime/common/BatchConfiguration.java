/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023-2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.common;

import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.LinkedHashSet;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.options.DebugFlags;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.runtime.common.enums.DataTypeSize;
import uk.ac.manchester.tornado.runtime.graph.TornadoExecutionContext;

/**
 * It presents the configuration for processing data in batches. This class
 * provides methods to compute chunk sizes based on the batch size and input
 * objects.
 */
/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.batches.TestBatches
 * </code>
 * </p>
 */
public class BatchConfiguration {

    private final int totalChunks;
    private final long remainingChunkSize;
    private final short numBytesType;

    /**
     * Constructs a BatchConfiguration object with the specified parameters.
     *
     * @param totalChunks
     *     The total number of chunks.
     * @param remainingChunkSize
     *     The size of the remaining chunk.
     * @param numBytesType
     *     The number of bytes for the data type.
     */
    public BatchConfiguration(int totalChunks, long remainingChunkSize, short numBytesType) {
        this.totalChunks = totalChunks;
        this.remainingChunkSize = remainingChunkSize;
        this.numBytesType = numBytesType;
    }

    public static BatchConfiguration computeChunkSizes(TornadoExecutionContext context, long batchSize) {
        // Get the size of the batch
        long totalSize = 0;

        HashSet<Long> inputSizes = new HashSet<>();
        LinkedHashSet<Byte> elementSizes = new LinkedHashSet<>();

        for (Object o : context.getObjects()) {
            if (o.getClass().isArray()) {
                Class<?> componentType = o.getClass().getComponentType();
                DataTypeSize dataTypeSize = DataTypeSize.findDataTypeSize(componentType);
                if (dataTypeSize == null) {
                    throw new TornadoRuntimeException("[UNSUPPORTED] Data type not supported for processing in batches");
                }
                long size = Array.getLength(o);
                totalSize = size * dataTypeSize.getSize();

                elementSizes.add(dataTypeSize.getSize());
                inputSizes.add(totalSize);
            } else if (o instanceof TornadoNativeArray tornadoNativeArray) {
                totalSize = tornadoNativeArray.getNumBytesOfSegment();
                inputSizes.add(totalSize);
                byte elementSize = switch (tornadoNativeArray) {
                    case IntArray _ -> DataTypeSize.INT.getSize();
                    case FloatArray _ -> DataTypeSize.FLOAT.getSize();
                    case DoubleArray _ -> DataTypeSize.DOUBLE.getSize();
                    case LongArray _ -> DataTypeSize.LONG.getSize();
                    case ShortArray _ -> DataTypeSize.SHORT.getSize();
                    case ByteArray _ -> DataTypeSize.BYTE.getSize();
                    case CharArray _ -> DataTypeSize.CHAR.getSize();
                    default -> throw new TornadoRuntimeException("Unsupported array type: " + o.getClass());
                };
                elementSizes.add(elementSize);
            } else {
                throw new TornadoRuntimeException("Unsupported type: " + o.getClass());
            }
        }

        if (inputSizes.size() > 1) {
            throw new TornadoRuntimeException("[UNSUPPORTED] Input objects with different sizes not currently supported");
        }

        if (elementSizes.size() > 1) {
            throw new TornadoRuntimeException("[UNSUPPORTED] Input objects with different element sizes not currently supported");
        }

        int totalChunks = (int) (totalSize / batchSize);
        long remainingChunkSize = totalSize % batchSize;

        if (DebugFlags.DEBUG.get()) {
            System.out.println("Batch Size: " + batchSize);
            System.out.println("Total chunks: " + totalChunks);
            System.out.println("remainingChunkSize: " + remainingChunkSize);
        }
        return new BatchConfiguration(totalChunks, remainingChunkSize, elementSizes.getFirst());
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public long getRemainingChunkSize() {
        return remainingChunkSize;
    }

    public short getNumBytesType() {
        return numBytesType;
    }

}
