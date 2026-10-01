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
 * Tuning of {@link CuVS#allNeighbors} with {@link CuVSAllNeighborsAlgo#NN_DESCENT}: fields of cuVS's
 * {@code cuvsNNDescentIndexParams}. A value of 0 keeps the cuVS default. (cuVS 26.08's all-neighbors C API does not
 * forward the NN-Descent distance precision, so it is not exposed here.)
 */
public final class CuVSAllNeighborsOptions {

    private long intermediateGraphDegree;
    private long maxIterations;
    private float terminationThreshold;

    /** Degree of the intermediate graph NN-Descent refines (larger: better recall, slower). */
    public CuVSAllNeighborsOptions withIntermediateGraphDegree(long degree) {
        this.intermediateGraphDegree = degree;
        return this;
    }

    /** Maximum number of NN-Descent iterations. */
    public CuVSAllNeighborsOptions withMaxIterations(long iterations) {
        this.maxIterations = iterations;
        return this;
    }

    /** Early-stopping threshold on the fraction of updated edges. */
    public CuVSAllNeighborsOptions withTerminationThreshold(float threshold) {
        this.terminationThreshold = threshold;
        return this;
    }

    public long getIntermediateGraphDegree() {
        return intermediateGraphDegree;
    }

    public long getMaxIterations() {
        return maxIterations;
    }

    public float getTerminationThreshold() {
        return terminationThreshold;
    }

}
