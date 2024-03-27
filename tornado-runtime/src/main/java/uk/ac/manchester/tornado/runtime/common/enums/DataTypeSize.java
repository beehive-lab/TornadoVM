/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
