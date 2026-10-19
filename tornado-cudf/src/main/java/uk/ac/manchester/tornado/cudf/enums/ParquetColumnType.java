/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.cudf.enums;

/**
 * The Parquet type {@code Cudf.writeParquetColumns} writes a column as, with the TornadoVM array
 * it is read from.
 *
 * <p>
 * The codes cross the ABI into the shim, so they are written out rather than taken from
 * {@link #ordinal()}.
 */
public enum ParquetColumnType {

    /** INT32, from an {@code IntArray}. */
    INT32(0),

    /** INT64, from a {@code LongArray}. */
    INT64(1),

    /** DOUBLE, from a {@code DoubleArray}. */
    FLOAT64(2),

    /** DATE, days since the epoch, from an {@code IntArray}. */
    DATE(3),

    /** TIMESTAMP in microseconds, from a {@code LongArray}. */
    TIMESTAMP_MICROS(4),
    /** FLOAT, from a {@code DoubleArray}: each double narrowed to a float as Java's cast does. */
    FLOAT32(5),
    /**
     * DECIMAL of up to 18 digits, from a {@code LongArray} of unscaled values, with the precision and
     * scale given with it (see {@code Cudf.writeParquetColumns}): stored as INT32 up to 9 digits and
     * INT64 above, as Parquet's convention, Spark and Iceberg have it.
     */
    DECIMAL64(6);

    private final int code;

    ParquetColumnType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
