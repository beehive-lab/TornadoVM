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
package uk.ac.manchester.tornado.api.types.tensors;

import java.lang.foreign.ValueLayout;

/**
 * The {@code DType} enum represents the various data types can be found in models.
 */
public enum DType {
    // @formatter:off
    /**
     * Represents a half-precision floating-point data type using 2 bytes.
     */
    HALF_FLOAT(2, ValueLayout.JAVA_SHORT),
    /**
     * Represents a single-precision 32-bit IEEE floating-point data type using 4 bytes.
     */
    FLOAT(4, ValueLayout.JAVA_FLOAT),
    /**
     * Represents a double-precision 64-bit IEEE floating-point data type using 8 bytes.
     */
    DOUBLE(8, ValueLayout.JAVA_DOUBLE),
    /**
     * Represents an 8-bit signed integer data type using 1 byte.
     */
    INT8(1, ValueLayout.JAVA_BYTE),
    /**
     * Represents a 16-bit signed integer data type using 2 bytes.
     */
    INT16(2, ValueLayout.JAVA_SHORT),
    /**
     * Represents a 32-bit signed integer data type using 4 bytes.
     */
    INT32(4, ValueLayout.JAVA_INT),
    /**
     * Represents a 64-bit signed integer data type using 8 bytes.
     */
    INT64(8, ValueLayout.JAVA_LONG),
    /**
     * Represents an 8-bit unsigned integer data type using 1 byte.
     */
    UINT8(1, ValueLayout.JAVA_BYTE),
    /**
     * Represents a boolean data type using 1 byte for true/false values.
     */
    BOOL(1, ValueLayout.JAVA_BYTE),
    /**
     * Represents a quantized 8-bit signed integer used in specialized applications like machine learning, using 1 byte.
     */
    QINT8(1, ValueLayout.JAVA_BYTE),
    /**
     * Represents a quantized 8-bit unsigned integer used in specialized applications like machine learning, using 1 byte.
     */
    QUINT8(1, ValueLayout.JAVA_BYTE);
    // @formatter:on

    /**
     * The size of the data type in bytes.
     */
    private final int size;

    /**
     * The layout of the data type in memory.
     */
    private final ValueLayout layout;

    /**
     * Constructs an instance of the enum constant with the specified size and memory layout.
     *
     * @param size
     *     The size of the data type in bytes.
     * @param layout
     *     The {@link ValueLayout} specifying how the data is laid out in memory.
     */
    DType(int size, ValueLayout layout) {
        this.size = size;
        this.layout = layout;
    }

    /**
     * Returns the size of the data type in bytes.
     *
     * @return The size of the data type.
     */
    public int getByteSize() {
        return size;
    }

    /**
     * Returns the {@link ValueLayout} of the data type, which describes how the data is laid out in memory.
     *
     * @return The memory layout of the data type.
     */
    public ValueLayout getLayout() {
        return layout;
    }

}
