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
 * The aggregations {@code Cudf.groupAggregate} and {@code Cudf.reduce} can ask for.
 *
 * <p>
 * The codes cross the ABI into the shim, so they are written out rather than taken from
 * {@link #ordinal()}: reordering this enum would otherwise change what a compiled shim computes.
 */
public enum CudfAggregation {

    /** Sum of the values in each group, or of the whole column. */
    SUM(0),

    /** Smallest value. */
    MIN(1),

    /** Largest value. */
    MAX(2),

    /** Arithmetic mean. */
    MEAN(3),

    /**
     * Number of rows in each group.
     *
     * <p>
     * Grouped only. Ungrouped it is the row count, which the caller already has, so
     * {@code Cudf.reduce} refuses it rather than returning something the call did not need to make.
     * The count arrives as a {@code double} like every other aggregation, exact past any row count
     * this binding can address.
     */
    COUNT(4);

    private final int code;

    CudfAggregation(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
