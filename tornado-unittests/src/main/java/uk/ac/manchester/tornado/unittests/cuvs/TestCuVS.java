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
package uk.ac.manchester.tornado.unittests.cuvs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.cuvs.CuVS;
import uk.ac.manchester.tornado.cuvs.CuVSAllNeighborsAlgo;
import uk.ac.manchester.tornado.cuvs.CuVSAllNeighborsOptions;
import uk.ac.manchester.tornado.cuvs.CuVSDistance;
import uk.ac.manchester.tornado.cuvs.provider.CuVSLibraryProvider;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.cuvs.TestCuVS
 * </code>
 */
public class TestCuVS extends TornadoTestBase {

    /** Batched builds lose some neighbours across cluster borders (random data has no cluster structure). */
    private static final double OVERLAP_BATCHED = 0.8;

    /**
     * cuVS library tasks require the CUDA backend and the cuVS C library (libcuvs_c). Unavailable configurations
     * throw the typed *NotSupported exceptions that the TornadoTestRunner counts as [UNSUPPORTED].
     */
    @Before
    public void cuVSMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "cuVS library tasks require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        if (!CuVSLibraryProvider.isAvailable()) {
            throw new TornadoVMCUDANotSupported("cuVS (libcuvs_c) is not available on this host");
        }
    }

    private static FloatArray randomMatrix(int rows, int cols, long seed) {
        Random random = new Random(seed);
        FloatArray m = new FloatArray(rows * cols);
        for (int i = 0; i < rows * cols; i++) {
            m.set(i, random.nextFloat() * 2 - 1);
        }
        return m;
    }

    private static float squaredL2(FloatArray a, int i, FloatArray b, int j, int dim) {
        float s = 0;
        for (int d = 0; d < dim; d++) {
            float x = a.get(i * dim + d) - b.get(j * dim + d);
            s += x * x;
        }
        return s;
    }

    /** Exact k-NN on the CPU: row ids of the k smallest squared L2 distances, nearest first. */
    private static int[][] exactKnn(FloatArray dataset, int nRows, FloatArray queries, int nQueries, int dim, int k) {
        int[][] result = new int[nQueries][];
        for (int q = 0; q < nQueries; q++) {
            final int query = q;
            Integer[] ids = new Integer[nRows];
            for (int i = 0; i < nRows; i++) {
                ids[i] = i;
            }
            float[] dist = new float[nRows];
            for (int i = 0; i < nRows; i++) {
                dist[i] = squaredL2(queries, query, dataset, i, dim);
            }
            Arrays.sort(ids, (x, y) -> Float.compare(dist[x], dist[y]));
            result[q] = new int[k];
            for (int c = 0; c < k; c++) {
                result[q][c] = ids[c];
            }
        }
        return result;
    }

    /** Fraction of the GPU neighbours that are in the exact CPU top-k (ties at the boundary can swap ids). */
    private static double overlap(int[][] exact, LongArray neighbors, int k) {
        long same = 0;
        for (int q = 0; q < exact.length; q++) {
            for (int c = 0; c < k; c++) {
                long id = neighbors.get(q * k + c);
                for (int e : exact[q]) {
                    if (e == id) {
                        same++;
                        break;
                    }
                }
            }
        }
        return same / (double) (exact.length * k);
    }

    @Test
    public void testBruteForceKnn() throws TornadoExecutionPlanException {
        final int nRows = 2048;
        final int nQueries = 64;
        final int dim = 64;
        final int k = 10;
        FloatArray dataset = randomMatrix(nRows, dim, 1);
        FloatArray queries = randomMatrix(nQueries, dim, 2);
        LongArray neighbors = new LongArray(nQueries * k);
        FloatArray distances = new FloatArray(nQueries * k);

        TaskGraph taskGraph = new TaskGraph("cuvs") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, dataset, queries) //
                .libraryTask("knn", CuVS::bruteForceKnn, dataset, nRows, dim, queries, nQueries, k, CuVSDistance.L2_EXPANDED.value(), neighbors, distances) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, neighbors, distances);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        int[][] exact = exactKnn(dataset, nRows, queries, nQueries, dim, k);
        assertTrue("neighbour overlap with the exact CPU result", overlap(exact, neighbors, k) >= 0.99);
        for (int q = 0; q < nQueries; q++) {
            // the nearest neighbour's distance matches the CPU one
            float expected = squaredL2(queries, q, dataset, exact[q][0], dim);
            assertEquals(expected, distances.get(q * k), 1e-3f * Math.max(1.0f, expected));
        }
    }

    /** Normalises every row in place (a Java kernel that runs before cuVS on the same stream). */
    public static void normaliseRows(FloatArray data, int nRows, int dim) {
        for (@Parallel int i = 0; i < nRows; i++) {
            float s = 0;
            for (int d = 0; d < dim; d++) {
                s += data.get(i * dim + d) * data.get(i * dim + d);
            }
            float inv = 1.0f / (float) Math.sqrt(s);
            for (int d = 0; d < dim; d++) {
                data.set(i * dim + d, data.get(i * dim + d) * inv);
            }
        }
    }

    @Test
    public void testJavaKernelChainedWithBruteForceKnn() throws TornadoExecutionPlanException {
        final int nRows = 1024;
        final int dim = 32;
        final int k = 5;
        FloatArray dataset = randomMatrix(nRows, dim, 3);
        FloatArray expectedNormalised = new FloatArray(nRows * dim);
        expectedNormalised.init(0);
        for (int i = 0; i < nRows * dim; i++) {
            expectedNormalised.set(i, dataset.get(i));
        }
        normaliseRows(expectedNormalised, nRows, dim);
        LongArray neighbors = new LongArray(nRows * k);
        FloatArray distances = new FloatArray(nRows * k);

        // Java kernel -> cuVS on the device buffer it just wrote, with no host round trip in between
        TaskGraph taskGraph = new TaskGraph("cuvsChain") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, dataset) //
                .task("normalise", TestCuVS::normaliseRows, dataset, nRows, dim) //
                .libraryTask("knn", CuVS::bruteForceKnn, dataset, nRows, dim, dataset, nRows, k, CuVSDistance.INNER_PRODUCT.value(), neighbors, distances) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, neighbors, distances);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int q = 0; q < nRows; q++) {
            // on unit vectors the best inner product is the row itself, 1.0
            assertEquals(q, neighbors.get(q * k));
            assertEquals(1.0f, distances.get(q * k), 1e-3f);
        }
    }

    @Test
    public void testAllNeighborsBruteForce() throws TornadoExecutionPlanException {
        final int nRows = 1024;
        final int dim = 32;
        final int k = 8;
        FloatArray dataset = randomMatrix(nRows, dim, 4);
        LongArray neighbors = new LongArray(nRows * k);
        FloatArray distances = new FloatArray(nRows * k);

        TaskGraph taskGraph = new TaskGraph("cuvsAll") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, dataset) //
                .libraryTask("graph", CuVS::allNeighbors, dataset, nRows, dim, k, CuVSAllNeighborsAlgo.BRUTE_FORCE.value(), CuVSDistance.L2_EXPANDED.value(), neighbors, distances) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, neighbors, distances);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        int[][] exact = exactKnn(dataset, nRows, dataset, nRows, dim, k);
        assertTrue("k-NN graph overlap with the exact CPU result", overlap(exact, neighbors, k) >= 0.99);
    }

    @Test
    public void testAllNeighborsNNDescentWithOptions() throws TornadoExecutionPlanException {
        final int nRows = 2048;
        final int dim = 32;
        final int k = 8;
        FloatArray dataset = randomMatrix(nRows, dim, 6);
        LongArray neighbors = new LongArray(nRows * k);
        FloatArray distances = new FloatArray(nRows * k);
        CuVSAllNeighborsOptions options = new CuVSAllNeighborsOptions() //
                .withIntermediateGraphDegree(32) //
                .withMaxIterations(20);

        TaskGraph taskGraph = new TaskGraph("cuvsNND") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, dataset) //
                .libraryTask("graph", CuVS::allNeighbors, dataset, nRows, dim, k, CuVSAllNeighborsAlgo.NN_DESCENT.value(), CuVSDistance.L2_EXPANDED.value(), neighbors, distances, options) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, neighbors, distances);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        // approximate, but NN-Descent on a small dataset finds almost all exact neighbours
        int[][] exact = exactKnn(dataset, nRows, dataset, nRows, dim, k);
        assertTrue("NN-Descent k-NN graph overlap with the exact CPU result", overlap(exact, neighbors, k) >= 0.9);
    }

    @Test
    public void testAllNeighborsOnHostBatched() {
        final int nRows = 4096;
        final int dim = 16;
        final int k = 8;
        FloatArray dataset = randomMatrix(nRows, dim, 7);
        try (Arena arena = Arena.ofConfined()) {
            // host-resident input and outputs, built in 4 overlapping batches
            MemorySegment data = arena.allocate((long) nRows * dim * Float.BYTES, 64);
            MemorySegment.copy(dataset.getSegment(), 0, data, 0, data.byteSize());
            MemorySegment ids = arena.allocate((long) nRows * k * Long.BYTES, 64);
            MemorySegment dists = arena.allocate((long) nRows * k * Float.BYTES, 64);
            CuVSAllNeighborsOptions options = new CuVSAllNeighborsOptions().withClusters(4, 2);
            CuVS.allNeighborsOnHost(data, nRows, dim, k, CuVSAllNeighborsAlgo.NN_DESCENT, CuVSDistance.L2_EXPANDED, ids, dists, options);

            LongArray neighbors = new LongArray(nRows * k);
            MemorySegment.copy(ids, 0, neighbors.getSegment(), 0, ids.byteSize());
            int[][] exact = exactKnn(dataset, nRows, dataset, nRows, dim, k);
            double found = overlap(exact, neighbors, k);
            assertTrue("batched host k-NN graph overlap with the exact CPU result: " + found, found >= OVERLAP_BATCHED);
        }
    }

    @Test
    public void testKMeansFitAndPredict() throws TornadoExecutionPlanException {
        final int clusters = 4;
        final int perCluster = 500;
        final int nRows = clusters * perCluster;
        final int dim = 16;
        // well-separated blobs around (10 * c, 10 * c, ...)
        Random random = new Random(5);
        FloatArray data = new FloatArray(nRows * dim);
        for (int i = 0; i < nRows; i++) {
            int c = i / perCluster;
            for (int d = 0; d < dim; d++) {
                data.set(i * dim + d, 10.0f * c + (float) random.nextGaussian());
            }
        }
        FloatArray centroids = new FloatArray(clusters * dim);
        IntArray labels = new IntArray(nRows);

        TaskGraph taskGraph = new TaskGraph("cuvsKMeans") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .libraryTask("fit", CuVS::kmeansFit, data, nRows, dim, clusters, 20, centroids) //
                .libraryTask("predict", CuVS::kmeansPredict, data, nRows, dim, centroids, clusters, labels) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, centroids, labels);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        // every blob maps to a single label, and different blobs to different labels
        int[] labelOfBlob = new int[clusters];
        for (int c = 0; c < clusters; c++) {
            labelOfBlob[c] = labels.get(c * perCluster);
            for (int i = c * perCluster; i < (c + 1) * perCluster; i++) {
                assertEquals("points of blob " + c, labelOfBlob[c], labels.get(i));
            }
        }
        assertEquals(clusters, Arrays.stream(labelOfBlob).distinct().count());
        // and its centroid is near the blob centre
        for (int c = 0; c < clusters; c++) {
            assertEquals(10.0f * c, centroids.get(labelOfBlob[c] * dim), 0.5f);
        }
    }
}
