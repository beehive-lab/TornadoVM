/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.common.enums;

import java.util.Arrays;

public enum DataTypeSize {
    // @formatter:off
    BYTE(byte.class, (byte) 1), //
    CHAR(char.class, (byte) 2), //
    SHORT(short.class, (byte) 2), //
    INT(int.class, (byte) 4), //
    FLOAT(float.class, (byte) 4), //
    LONG(long.class, (byte) 8), //
    DOUBLE(double.class, (byte) 8), //
    BYTE_WRAPPER(Byte.class, (byte) 1),
    CHAR_WRAPPER(Character.class, (byte) 2),
    SHORT_WRAPPER(Short.class, (byte) 2),
    INT_WRAPPER(Integer.class, (byte) 4),
    FLOAT_WRAPPER(Float.class, (byte) 4),
    LONG_WRAPPER(Long.class, (byte) 8),
    DOUBLE_WRAPPER(Double.class, (byte) 8);
    // @formatter:on

    private final Class<?> dataType;
    private final byte size;

    DataTypeSize(Class<?> dataType, byte size) {
        this.dataType = dataType;
        this.size = size;
    }

    public static DataTypeSize findDataTypeSize(Class<?> dataType) {
        return Arrays.stream(DataTypeSize.values()).filter(size -> size.getDataType().equals(dataType)).findFirst().orElse(null);
    }

    public Class<?> getDataType() {
        return dataType;
    }

    public byte getSize() {
        return size;
    }
}
