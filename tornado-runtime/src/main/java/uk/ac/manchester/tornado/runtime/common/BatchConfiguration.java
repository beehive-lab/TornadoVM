/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023-2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.common;

import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.LinkedHashSet;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
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
    private final int remainingChunkSize;
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
    public BatchConfiguration(int totalChunks, int remainingChunkSize, short numBytesType) {
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
        int remainingChunkSize = (int) (totalSize % batchSize);

        if (TornadoOptions.DEBUG) {
            System.out.println("Batch Size: " + batchSize);
            System.out.println("Total chunks: " + totalChunks);
            System.out.println("remainingChunkSize: " + remainingChunkSize);
        }
        return new BatchConfiguration(totalChunks, remainingChunkSize, elementSizes.getFirst());
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public int getRemainingChunkSize() {
        return remainingChunkSize;
    }

    public short getNumBytesType() {
        return numBytesType;
    }

}
