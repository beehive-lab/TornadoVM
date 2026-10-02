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
package uk.ac.manchester.tornado.cuvs;

/**
 * Distance metrics of cuVS ({@code cuvsDistanceType} in {@code cuvs/distance/distance.h}). The values are the C
 * enum values.
 */
public enum CuVSDistance {
    /** Squared Euclidean distance, computed as {@code |x|^2 + |y|^2 - 2 x.y}. */
    L2_EXPANDED(0),
    /** Euclidean distance. */
    L2_SQRT_EXPANDED(1),
    /** Cosine distance, {@code 1 - cos(x, y)}. */
    COSINE_EXPANDED(2),
    /** L1 distance. */
    L1(3),
    /** Inner product (a similarity: larger is closer). */
    INNER_PRODUCT(6);

    private final int value;

    CuVSDistance(int value) {
        this.value = value;
    }

    /** @return the {@code cuvsDistanceType} value */
    public int value() {
        return value;
    }
}
