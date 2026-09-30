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

import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;

/**
 * NVIDIA cuVS vector-search routines as TornadoVM library tasks.
 *
 * <p>Each method returns a {@link LibraryTaskDescriptor} for {@code TaskGraph.libraryTask}. The arrays are TornadoVM
 * device buffers, handed to cuVS zero-copy as DLPack tensors, and cuVS runs on the execution plan's CUDA stream, so
 * cuVS tasks and Java kernels in the same task graph are ordered and share data without host round trips. All
 * matrices are row-major.
 *
 * <pre>{@code
 * TaskGraph tg = new TaskGraph("knn")
 *         .transferToDevice(DataTransferMode.FIRST_EXECUTION, dataset, queries)
 *         .libraryTask("search", CuVS::bruteForceKnn, dataset, nRows, dim, queries, nQueries, k,
 *                 CuVSDistance.L2_EXPANDED.value(), neighbors, distances)
 *         .transferToHost(DataTransferMode.EVERY_EXECUTION, neighbors, distances);
 * }</pre>
 *
 * <p>Requires the CUDA backend and the cuVS C library ({@code libcuvs_c.so}, e.g. from the {@code libcuvs-cu12}
 * pip wheel or conda) on the library path; see {@link uk.ac.manchester.tornado.cuvs.provider.CuVSLibraryProvider#isAvailable()}.
 */
public final class CuVS {

    /** Library name the provider registers under. */
    public static final String LIBRARY_NAME = "nvidia/cuvs";

    private CuVS() {
    }

    /**
     * Exact k nearest neighbours of every query among the dataset rows ({@code cuvsBruteForceBuild} +
     * {@code cuvsBruteForceSearch}).
     *
     * @param dataset   {@code nRows x dim} vectors
     * @param nRows     number of dataset rows
     * @param dim       dimensionality
     * @param queries   {@code nQueries x dim} query vectors
     * @param nQueries  number of queries
     * @param k         neighbours per query
     * @param metric    a {@link CuVSDistance#value()}
     * @param neighbors output, {@code nQueries x k} dataset row ids, nearest first
     * @param distances output, {@code nQueries x k} distances
     */
    public static LibraryTaskDescriptor bruteForceKnn(FloatArray dataset, int nRows, int dim, FloatArray queries, int nQueries, int k, int metric, LongArray neighbors, FloatArray distances) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cuvsBruteForceKnn") //
                .withParameters(new Object[] { dataset, nRows, dim, queries, nQueries, k, metric, neighbors, distances }) //
                .withAccess(access(9, 7, 8));
    }

    /**
     * The k-NN graph of the whole dataset: the k nearest rows of every row ({@code cuvsAllNeighborsBuild}). For a
     * device-resident dataset cuVS supports {@link CuVSAllNeighborsAlgo#BRUTE_FORCE} (exact) and
     * {@link CuVSAllNeighborsAlgo#NN_DESCENT} (approximate).
     *
     * @param dataset   {@code nRows x dim} vectors
     * @param nRows     number of rows
     * @param dim       dimensionality
     * @param k         neighbours per row (each row's own id is included, as cuVS returns it)
     * @param algo      a {@link CuVSAllNeighborsAlgo#value()}
     * @param metric    a {@link CuVSDistance#value()}
     * @param neighbors output, {@code nRows x k} row ids, nearest first
     * @param distances output, {@code nRows x k} distances
     */
    public static LibraryTaskDescriptor allNeighbors(FloatArray dataset, int nRows, int dim, int k, int algo, int metric, LongArray neighbors, FloatArray distances) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cuvsAllNeighborsBuild") //
                .withParameters(new Object[] { dataset, nRows, dim, k, algo, metric, neighbors, distances }) //
                .withAccess(access(8, 6, 7));
    }

    /**
     * k-means clustering ({@code cuvsKMeansFit}, k-means++ initialisation).
     *
     * @param data      {@code nRows x dim} vectors
     * @param nRows     number of rows
     * @param dim       dimensionality
     * @param nClusters number of clusters
     * @param maxIter   maximum number of Lloyd iterations
     * @param centroids output, {@code nClusters x dim} centroids
     */
    public static LibraryTaskDescriptor kmeansFit(FloatArray data, int nRows, int dim, int nClusters, int maxIter, FloatArray centroids) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cuvsKMeansFit") //
                .withParameters(new Object[] { data, nRows, dim, nClusters, maxIter, centroids }) //
                .withAccess(access(6, 5));
    }

    /**
     * Nearest centroid of every row ({@code cuvsKMeansPredict}).
     *
     * @param data      {@code nRows x dim} vectors
     * @param nRows     number of rows
     * @param dim       dimensionality
     * @param centroids {@code nClusters x dim} centroids
     * @param nClusters number of clusters
     * @param labels    output, the index of the nearest centroid of every row
     */
    public static LibraryTaskDescriptor kmeansPredict(FloatArray data, int nRows, int dim, FloatArray centroids, int nClusters, IntArray labels) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cuvsKMeansPredict") //
                .withParameters(new Object[] { data, nRows, dim, centroids, nClusters, labels }) //
                .withAccess(access(6, 5));
    }

    /** All arguments read-only except the given outputs, which are write-only. */
    private static Access[] access(int numArgs, int... outputs) {
        Access[] accesses = new Access[numArgs];
        Arrays.fill(accesses, Access.READ_ONLY);
        for (int output : outputs) {
            accesses[output] = Access.WRITE_ONLY;
        }
        return accesses;
    }
}
