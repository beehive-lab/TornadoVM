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

import java.lang.foreign.MemorySegment;
import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.cuvs.provider.CuVSLibraryProvider;

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
     * Whether the cuVS C library ({@code libcuvs_c}) can be loaded on this host. Code that only uses this class
     * can check it without depending on the TornadoVM runtime.
     */
    public static boolean isAvailable() {
        return CuVSLibraryProvider.isAvailable();
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
     * {@link #allNeighbors(FloatArray, int, int, int, int, int, LongArray, FloatArray)} with NN-Descent tuning.
     *
     * @param options NN-Descent parameters (ignored by {@link CuVSAllNeighborsAlgo#BRUTE_FORCE})
     */
    public static LibraryTaskDescriptor allNeighbors(FloatArray dataset, int nRows, int dim, int k, int algo, int metric, LongArray neighbors, FloatArray distances, CuVSAllNeighborsOptions options) {
        return allNeighbors(dataset, nRows, dim, k, algo, metric, neighbors, distances).withTuning(options);
    }

    /**
     * The k-NN graph of a host-resident dataset, which may be larger than device memory ({@code cuvsAllNeighborsBuild}
     * with a host dataset). With {@link CuVSAllNeighborsOptions#withClusters} cuVS builds it in batches: it partitions
     * the rows into clusters, assigns each row to its nearest {@code overlapFactor} clusters, builds each cluster's
     * graph on the device and merges the results. Without, the whole dataset is one batch.
     * <p>
     * Unlike the other operations this is a direct, synchronous call, not a task: the dataset is a
     * {@link MemorySegment} (e.g. a memory-mapped file), as it may hold more than {@code Integer.MAX_VALUE} floats.
     * It runs on the current CUDA device with its own cuVS resources.
     *
     * @param dataset   {@code nRows x dim} row-major float32 vectors in host memory
     * @param nRows     number of rows
     * @param dim       dimensionality
     * @param k         neighbours per row (each row's own id is included, as cuVS returns it)
     * @param algo      {@link CuVSAllNeighborsAlgo#NN_DESCENT} or {@link CuVSAllNeighborsAlgo#BRUTE_FORCE}
     * @param metric    the distance
     * @param neighbors output, {@code nRows x k} int64 row ids in host memory, nearest first
     * @param distances output, {@code nRows x k} float32 distances in host memory
     * @param options   NN-Descent parameters and batching, or null
     */
    public static void allNeighborsOnHost(MemorySegment dataset, long nRows, int dim, int k, CuVSAllNeighborsAlgo algo, CuVSDistance metric, MemorySegment neighbors,
            MemorySegment distances, CuVSAllNeighborsOptions options) {
        if (dataset.byteSize() < nRows * dim * Float.BYTES || neighbors.byteSize() < nRows * k * Long.BYTES || distances.byteSize() < nRows * k * Float.BYTES) {
            throw new IllegalArgumentException("segment smaller than nRows x dim (dataset) or nRows x k (outputs)");
        }
        CuVSLibraryProvider.allNeighborsOnHost(dataset, nRows, dim, k, algo.value(), metric.value(), neighbors, distances, options);
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
