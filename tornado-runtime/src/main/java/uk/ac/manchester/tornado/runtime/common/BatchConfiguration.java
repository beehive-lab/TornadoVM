/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.graph.TornadoExecutionContext;

/**
 * It presents the configuration for processing data in batches. This class
 * provides methods to compute chunk sizes based on the batch size and input
 * objects.
 */
public class BatchConfiguration {
    private final int totalChunks;
    private final int remainingChunkSize;
    private final short numBytesType;

    /**
     * Constructs a BatchConfiguration object with the specified parameters.
     *
     * @param totalChunks
     *            The total number of chunks.
     * @param remainingChunkSize
     *            The size of the remaining chunk.
     * @param numBytesType
     *            The number of bytes for the data type.
     */
    public BatchConfiguration(int totalChunks, int remainingChunkSize, short numBytesType) {
        this.totalChunks = totalChunks;
        this.remainingChunkSize = remainingChunkSize;
        this.numBytesType = numBytesType;
    }

    public static BatchConfiguration computeChunkSizes(TornadoExecutionContext context, long batchSize) {
        // Get the size of the batch
        List<Object> inputObjects = context.getObjects();
        long totalSize = 0;
        DataTypeSize dataTypeSize = null;

        HashSet<Class<?>> classObjects = new HashSet<>();
        HashSet<Long> inputSizes = new HashSet<>();

        for (Object o : inputObjects) {
            if (o.getClass().isArray()) {
                Class<?> componentType = o.getClass().getComponentType();
                dataTypeSize = findDataTypeSize(componentType);
                if (dataTypeSize == null) {
                    throw new TornadoRuntimeException("[UNSUPPORTED] Data type not supported for processing in batches");
                }
                long size = Array.getLength(o);
                totalSize = size * dataTypeSize.getSize();

                classObjects.add(componentType);
                inputSizes.add(totalSize);
                if (classObjects.size() > 1) {
                    throw new TornadoRuntimeException("[UNSUPPORTED] Input objects with different data types not currently supported");
                }
                if (inputSizes.size() > 1) {
                    throw new TornadoRuntimeException("[UNSUPPORTED] Input objects with different sizes not currently supported");
                }
            }
        }

        assert dataTypeSize != null;

        int totalChunks = (int) (totalSize / batchSize);
        int remainingChunkSize = (int) (totalSize % batchSize);

        if (Tornado.DEBUG) {
            System.out.println("Batch Size: " + batchSize);
            System.out.println("Total chunks: " + totalChunks);
            System.out.println("remainingChunkSize: " + remainingChunkSize);
        }
        return new BatchConfiguration(totalChunks, remainingChunkSize, dataTypeSize.getSize());
    }

    private static DataTypeSize findDataTypeSize(Class<?> dataType) {
        return Arrays.stream(DataTypeSize.values()).filter(size -> size.getDataType().equals(dataType)).findFirst().orElse(null);
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

    private enum DataTypeSize {
        BYTE(byte.class, (byte) 1), //
        CHAR(char.class, (byte) 2), //
        SHORT(short.class, (byte) 2), //
        INT(int.class, (byte) 4), //
        FLOAT(float.class, (byte) 4), //
        LONG(long.class, (byte) 8), //
        DOUBLE(double.class, (byte) 8);

        private final Class<?> dataType;
        private final byte size;

        DataTypeSize(Class<?> dataType, byte size) {
            this.dataType = dataType;
            this.size = size;
        }

        public Class<?> getDataType() {
            return dataType;
        }

        public byte getSize() {
            return size;
        }
    }
}