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
 * Algorithms for the all-neighbors k-NN graph ({@code cuvsAllNeighborsAlgo} in
 * {@code cuvs/neighbors/all_neighbors.h}). The values are the C enum values.
 */
public enum CuVSAllNeighborsAlgo {
    /** Exact k-NN graph by brute force. */
    BRUTE_FORCE(0),
    /** Approximate k-NN graph with IVF-PQ (host-resident datasets only). */
    IVF_PQ(1),
    /** Approximate k-NN graph with NN-Descent. */
    NN_DESCENT(2);

    private final int value;

    CuVSAllNeighborsAlgo(int value) {
        this.value = value;
    }

    /** @return the {@code cuvsAllNeighborsAlgo} value */
    public int value() {
        return value;
    }
}
