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
 * The physical column types {@code Cudf.readParquetColumns} reads and {@code Cudf.containedIn}
 * probes, each with the TornadoVM array that holds it.
 *
 * <p>
 * The codes cross the ABI into the shim, so they are written out rather than taken from
 * {@link #ordinal()}.
 */
public enum CudfType {

    /** 32-bit integers, into an {@code IntArray}: INT32, and Parquet DATE (days since the epoch). */
    INT32(0),

    /**
     * 64-bit integers, into a {@code LongArray}: INT64, and every Parquet TIMESTAMP, whose value
     * arrives in the unit the file declares.
     */
    INT64(1),

    /** FP64, into a {@code DoubleArray}. */
    FLOAT64(2);

    private final int code;

    CudfType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
