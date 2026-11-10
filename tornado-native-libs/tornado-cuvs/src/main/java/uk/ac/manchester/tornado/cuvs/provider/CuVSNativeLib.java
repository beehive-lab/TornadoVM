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
package uk.ac.manchester.tornado.cuvs.provider;

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_DOUBLE;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_FLOAT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cuvs.CuVSAllNeighborsOptions;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.ffm.ROCmLibraries;

/**
 * Panama FFM bindings to the cuVS C API ({@code libcuvs_c}), and the DLPack tensors it takes. On AMD GPUs the same
 * API comes from hipVS, AMD's HIP port of cuVS, whose C library has the same name.
 *
 * <p>Written against the cuVS 26.08 C headers. The resources, brute-force, all-neighbors and k-means entry points
 * used here are opaque-handle functions; the only struct fields written directly are {@code n_clusters},
 * {@code max_iter} and {@code metric} of {@code cuvsKMeansParams}, and {@code algo}, {@code n_clusters} and
 * {@code metric} of {@code cuvsAllNeighborsIndexParams}, at their C layout offsets.
 */
final class CuVSNativeLib {

    /** {@code CUVS_SUCCESS} ({@code cuvsError_t} is {@code { CUVS_ERROR = 0, CUVS_SUCCESS = 1 }}). */
    private static final int CUVS_SUCCESS = 1;

    private static final SymbolLookup LIBCUVS = FFMSupport.loadLibrary("libcuvs_c.so", "libcuvs_c.so.26", "cuvs_c.dll");

    /**
     * Whether the library is hipVS, whose tasks run on HIP devices: {@code -Dtornado.cuvs.library=hipvs|cuvs}, else
     * whether the libcuvs_c that loaded runs on the HIP runtime. cuVS and hipVS have the same library name, so a
     * process holds one of them, and the provider handles devices of its backend only.
     */
    private static final boolean HIPVS = useHipvs();

    // DLPack (dlpack.h): device types, data type codes and the DLManagedTensor layout
    static final int DL_CPU = 1;
    /** hipVS takes device tensors as {@code kDLCUDA} too: it refuses {@code kDLROCM} ones. */
    static final int DL_CUDA = 2;
    static final byte DL_INT = 0;
    static final byte DL_UINT = 1;
    static final byte DL_FLOAT = 2;

    /** {@code DLManagedTensor}: {@code DLTensor dl_tensor; void* manager_ctx; void (*deleter)(DLManagedTensor*)}. */
    private static final StructLayout DL_MANAGED_TENSOR = MemoryLayout.structLayout(//
            C_POINTER.withName("data"), //
            C_INT.withName("device_type"), //
            C_INT.withName("device_id"), //
            C_INT.withName("ndim"), //
            ValueLayout.JAVA_BYTE.withName("dtype_code"), //
            ValueLayout.JAVA_BYTE.withName("dtype_bits"), //
            ValueLayout.JAVA_SHORT.withName("dtype_lanes"), //
            C_POINTER.withName("shape"), //
            C_POINTER.withName("strides"), //
            C_LONG.withName("byte_offset"), //
            C_POINTER.withName("manager_ctx"), //
            C_POINTER.withName("deleter"));

    /** {@code cuvsFilter}, passed by value: {@code { uintptr_t addr; enum cuvsFilterType type; }}. */
    private static final StructLayout CUVS_FILTER = MemoryLayout.structLayout(C_LONG.withName("addr"), C_INT.withName("type"), MemoryLayout.paddingLayout(4));

    // cuvsKMeansParams field offsets (metric, n_clusters, init, max_iter, ...)
    private static final long KMEANS_METRIC = 0;
    private static final long KMEANS_N_CLUSTERS = 4;
    private static final long KMEANS_MAX_ITER = 12;

    // cuvsAllNeighborsIndexParams field offsets (algo, overlap_factor, n_clusters, metric, ...)
    private static final long ALL_NEIGHBORS_ALGO = 0;
    private static final long ALL_NEIGHBORS_OVERLAP = 8;
    private static final long ALL_NEIGHBORS_N_CLUSTERS = 16;
    private static final long ALL_NEIGHBORS_METRIC = 24;
    private static final long ALL_NEIGHBORS_NN_DESCENT_PARAMS = 40;

    // cuvsNNDescentIndexParams field offsets (metric, metric_arg, graph_degree, intermediate_graph_degree, ...)
    private static final long NND_METRIC = 0;
    private static final long NND_GRAPH_DEGREE = 8;
    private static final long NND_INTERMEDIATE_GRAPH_DEGREE = 16;
    private static final long NND_MAX_ITERATIONS = 24;
    private static final long NND_TERMINATION_THRESHOLD = 32;

    private static final MethodHandle RESOURCES_CREATE;
    private static final MethodHandle RESOURCES_DESTROY;
    private static final MethodHandle STREAM_SET;
    private static final MethodHandle DEVICE_ID_GET;
    private static final MethodHandle LAST_ERROR_TEXT;
    private static final MethodHandle BRUTE_FORCE_INDEX_CREATE;
    private static final MethodHandle BRUTE_FORCE_INDEX_DESTROY;
    private static final MethodHandle BRUTE_FORCE_BUILD;
    private static final MethodHandle BRUTE_FORCE_SEARCH;
    private static final MethodHandle ALL_NEIGHBORS_PARAMS_CREATE;
    private static final MethodHandle ALL_NEIGHBORS_PARAMS_DESTROY;
    private static final MethodHandle ALL_NEIGHBORS_BUILD;
    private static final MethodHandle NN_DESCENT_PARAMS_CREATE;
    private static final MethodHandle KMEANS_PARAMS_CREATE;
    private static final MethodHandle KMEANS_PARAMS_DESTROY;
    private static final MethodHandle KMEANS_FIT;
    private static final MethodHandle KMEANS_PREDICT;

    /**
     * hipVS builds an all-neighbors graph of a host dataset into device outputs only, so the host path stages them
     * through device memory (cuVS also takes host outputs): RMM memory from the resources, a stream sync, and a copy
     * back with the HIP runtime.
     */
    private static final MethodHandle RMM_ALLOC;
    private static final MethodHandle RMM_FREE;
    private static final MethodHandle STREAM_SYNC;
    private static final MethodHandle HIP_MEMCPY;

    static {
        if (LIBCUVS == null || !HIPVS) {
            RMM_ALLOC = null;
            RMM_FREE = null;
            STREAM_SYNC = null;
            HIP_MEMCPY = null;
        } else {
            RMM_ALLOC = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER, C_LONG), "cuvsRMMAlloc");
            RMM_FREE = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG), "cuvsRMMFree");
            STREAM_SYNC = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_LONG), "cuvsStreamSync");
            SymbolLookup hip = ROCmLibraries.load(List.of("libamdhip64.so.7", "libamdhip64.so.6", "libamdhip64.so"), List.of("amdhip64_7.dll", "amdhip64_6.dll"));
            HIP_MEMCPY = hip == null ? null : FFMSupport.downcall(hip, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_LONG, C_INT), "hipMemcpy");
        }
    }

    static {
        if (LIBCUVS == null) {
            RESOURCES_CREATE = null;
            RESOURCES_DESTROY = null;
            STREAM_SET = null;
            DEVICE_ID_GET = null;
            LAST_ERROR_TEXT = null;
            BRUTE_FORCE_INDEX_CREATE = null;
            BRUTE_FORCE_INDEX_DESTROY = null;
            BRUTE_FORCE_BUILD = null;
            BRUTE_FORCE_SEARCH = null;
            ALL_NEIGHBORS_PARAMS_CREATE = null;
            ALL_NEIGHBORS_PARAMS_DESTROY = null;
            ALL_NEIGHBORS_BUILD = null;
            NN_DESCENT_PARAMS_CREATE = null;
            KMEANS_PARAMS_CREATE = null;
            KMEANS_PARAMS_DESTROY = null;
            KMEANS_FIT = null;
            KMEANS_PREDICT = null;
        } else {
            RESOURCES_CREATE = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_POINTER), "cuvsResourcesCreate");
            RESOURCES_DESTROY = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_LONG), "cuvsResourcesDestroy");
            STREAM_SET = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "cuvsStreamSet");
            DEVICE_ID_GET = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "cuvsDeviceIdGet");
            LAST_ERROR_TEXT = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_POINTER), "cuvsGetLastErrorText");
            BRUTE_FORCE_INDEX_CREATE = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_POINTER), "cuvsBruteForceIndexCreate");
            BRUTE_FORCE_INDEX_DESTROY = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_LONG), "cuvsBruteForceIndexDestroy");
            // (res, dataset, metric, metric_arg, index)
            BRUTE_FORCE_BUILD = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER, C_INT, C_FLOAT, C_LONG), "cuvsBruteForceBuild");
            // (res, index, queries, neighbors, distances, cuvsFilter by value)
            BRUTE_FORCE_SEARCH = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_POINTER, C_POINTER, C_POINTER, CUVS_FILTER), "cuvsBruteForceSearch");
            ALL_NEIGHBORS_PARAMS_CREATE = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_POINTER), "cuvsAllNeighborsIndexParamsCreate");
            ALL_NEIGHBORS_PARAMS_DESTROY = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_LONG), "cuvsAllNeighborsIndexParamsDestroy");
            // (res, params, dataset, indices, distances, core_distances, alpha)
            ALL_NEIGHBORS_BUILD = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_FLOAT), "cuvsAllNeighborsBuild");
            NN_DESCENT_PARAMS_CREATE = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_POINTER), "cuvsNNDescentIndexParamsCreate");
            KMEANS_PARAMS_CREATE = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_POINTER), "cuvsKMeansParamsCreate");
            KMEANS_PARAMS_DESTROY = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_LONG), "cuvsKMeansParamsDestroy");
            // (res, params, X, sample_weight, centroids, double* inertia, int* n_iter)
            KMEANS_FIT = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER), "cuvsKMeansFit");
            // (res, params, X, sample_weight, centroids, labels, bool normalize_weight, double* inertia)
            KMEANS_PREDICT = FFMSupport.downcall(LIBCUVS, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_POINTER, C_POINTER, C_POINTER, C_POINTER, ValueLayout.JAVA_BOOLEAN, C_POINTER),
                    "cuvsKMeansPredict");
        }
    }

    private CuVSNativeLib() {
    }

    private static boolean useHipvs() {
        String library = System.getProperty("tornado.cuvs.library", "");
        if (!library.isBlank()) {
            return library.trim().equalsIgnoreCase("hipvs");
        }
        // the lookup also searches the libraries libcuvs_c depends on
        return LIBCUVS != null && FFMSupport.findSymbol(LIBCUVS, "hipGetDevice") != null;
    }

    /** Whether the library is hipVS, whose tasks run on HIP devices. */
    static boolean isHipvs() {
        return HIPVS;
    }

    /** Throws if the cuVS C library could not be loaded. */
    static synchronized void load() {
        if ((LIBCUVS == null || RESOURCES_CREATE == null) && HIPVS) {
            throw new TornadoRuntimeException("[ERROR] Unable to load hipVS. Install the hipVS C library (the amd-libhipvs wheel) and put libcuvs_c.so on the library path.");
        }
        if (LIBCUVS == null || RESOURCES_CREATE == null) {
            throw new TornadoRuntimeException("[ERROR] Unable to load cuVS. Install the cuVS C library (e.g. pip install libcuvs-cu12) and put libcuvs_c.so on the library path.");
        }
    }

    // ---- DLPack ----

    /**
     * A 2D (or 1D if {@code cols == 0}) row-major DLPack tensor over a device pointer, allocated in {@code arena}.
     * The tensor does not own the memory: {@code manager_ctx} and {@code deleter} are null.
     */
    static MemorySegment deviceTensor(Arena arena, long devicePointer, int deviceId, byte code, int bits, long rows, long cols) {
        return tensor(arena, devicePointer, DL_CUDA, deviceId, code, bits, rows, cols);
    }

    /** {@link #deviceTensor} over host memory. */
    static MemorySegment hostTensor(Arena arena, MemorySegment memory, byte code, int bits, long rows, long cols) {
        return tensor(arena, memory.address(), DL_CPU, 0, code, bits, rows, cols);
    }

    private static MemorySegment tensor(Arena arena, long pointer, int deviceType, int deviceId, byte code, int bits, long rows, long cols) {
        MemorySegment t = arena.allocate(DL_MANAGED_TENSOR);
        int ndim = cols == 0 ? 1 : 2;
        // FFMSupport keeps this portable: on JDK 21 Arena.allocate(C_LONG, n) allocates ONE long of value n
        MemorySegment shape = FFMSupport.allocateLongArray(arena, ndim == 2 ? new long[] { rows, cols } : new long[] { rows });
        t.set(C_POINTER, offset("data"), MemorySegment.ofAddress(pointer));
        t.set(C_INT, offset("device_type"), deviceType);
        t.set(C_INT, offset("device_id"), deviceId);
        t.set(C_INT, offset("ndim"), ndim);
        t.set(ValueLayout.JAVA_BYTE, offset("dtype_code"), code);
        t.set(ValueLayout.JAVA_BYTE, offset("dtype_bits"), (byte) bits);
        t.set(ValueLayout.JAVA_SHORT, offset("dtype_lanes"), (short) 1);
        t.set(C_POINTER, offset("shape"), shape);
        t.set(C_POINTER, offset("strides"), MemorySegment.NULL);
        t.set(C_LONG, offset("byte_offset"), 0L);
        t.set(C_POINTER, offset("manager_ctx"), MemorySegment.NULL);
        t.set(C_POINTER, offset("deleter"), MemorySegment.NULL);
        return t;
    }

    private static long offset(String field) {
        return DL_MANAGED_TENSOR.byteOffset(MemoryLayout.PathElement.groupElement(field));
    }

    // ---- resources ----

    static long resourcesCreate() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment res = arena.allocate(C_LONG);
            check((int) RESOURCES_CREATE.invokeExact(res), "cuvsResourcesCreate");
            return res.get(C_LONG, 0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void resourcesDestroy(long res) {
        if (res == 0) {
            return;
        }
        try {
            int ignored = (int) RESOURCES_DESTROY.invokeExact(res);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void streamSet(long res, long stream) {
        try {
            check((int) STREAM_SET.invokeExact(res, stream), "cuvsStreamSet");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int deviceId(long res) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment id = arena.allocate(C_INT);
            check((int) DEVICE_ID_GET.invokeExact(res, id), "cuvsDeviceIdGet");
            return id.get(C_INT, 0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    // ---- brute force ----

    static void bruteForceKnn(long res, MemorySegment dataset, MemorySegment queries, int metric, MemorySegment neighbors, MemorySegment distances) {
        long index = 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment indexOut = arena.allocate(C_LONG);
            check((int) BRUTE_FORCE_INDEX_CREATE.invokeExact(indexOut), "cuvsBruteForceIndexCreate");
            index = indexOut.get(C_LONG, 0);
            check((int) BRUTE_FORCE_BUILD.invokeExact(res, dataset, metric, 2.0f, index), "cuvsBruteForceBuild");
            MemorySegment noFilter = arena.allocate(CUVS_FILTER); // { addr = 0, type = NO_FILTER }
            check((int) BRUTE_FORCE_SEARCH.invokeExact(res, index, queries, neighbors, distances, noFilter), "cuvsBruteForceSearch");
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            if (index != 0) {
                try {
                    int ignored = (int) BRUTE_FORCE_INDEX_DESTROY.invokeExact(index);
                } catch (Throwable t) {
                    // the search result is already written; a failed destroy only leaks the index
                }
            }
        }
    }

    // ---- all-neighbors ----

    static void allNeighbors(long res, MemorySegment dataset, int algo, int metric, long k, MemorySegment neighbors, MemorySegment distances, CuVSAllNeighborsOptions options) {
        // a device-resident dataset is processed as one batch: n_clusters must be 1
        allNeighbors(res, dataset, algo, metric, k, neighbors, distances, options, 1L, 1L);
    }

    /**
     * Host-resident {@code dataset} and outputs: cuVS partitions the rows into {@code nClusters} clusters, assigns
     * each row to its {@code overlap} nearest ones and builds the graph cluster by cluster on the device.
     */
    static void allNeighborsOnHost(MemorySegment dataset, long rows, long dim, int algo, int metric, long k, MemorySegment neighbors, MemorySegment distances,
            CuVSAllNeighborsOptions options) {
        long res = resourcesCreate();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = hostTensor(arena, dataset, DL_FLOAT, 32, rows, dim);
            long clusters = options != null && options.getClusters() > 0 ? options.getClusters() : 1L;
            long overlap = options != null && options.getOverlapFactor() > 0 ? options.getOverlapFactor() : 1L;
            if (HIPVS) {
                allNeighborsOnHostStaged(arena, res, data, rows, algo, metric, k, neighbors, distances, options, clusters, overlap);
            } else {
                MemorySegment ids = hostTensor(arena, neighbors, DL_INT, 64, rows, k);
                MemorySegment dist = hostTensor(arena, distances, DL_FLOAT, 32, rows, k);
                allNeighbors(res, data, algo, metric, k, ids, dist, options, clusters, overlap);
            }
        } finally {
            resourcesDestroy(res);
        }
    }

    private static void allNeighborsOnHostStaged(Arena arena, long res, MemorySegment data, long rows, int algo, int metric, long k, MemorySegment neighbors,
            MemorySegment distances, CuVSAllNeighborsOptions options, long clusters, long overlap) {
        if (HIP_MEMCPY == null) {
            throw new TornadoRuntimeException("[ERROR] cuvsAllNeighborsBuild on hipVS: the HIP runtime could not be loaded");
        }
        long idsBytes = rows * k * Long.BYTES;
        long distBytes = rows * k * Float.BYTES;
        long ids = 0;
        long dist = 0;
        try {
            MemorySegment out = arena.allocate(C_LONG);
            check((int) RMM_ALLOC.invokeExact(res, out, idsBytes), "cuvsRMMAlloc");
            ids = out.get(C_LONG, 0);
            check((int) RMM_ALLOC.invokeExact(res, out, distBytes), "cuvsRMMAlloc");
            dist = out.get(C_LONG, 0);
            int device = deviceId(res);
            allNeighbors(res, data, algo, metric, k, deviceTensor(arena, ids, device, DL_INT, 64, rows, k), deviceTensor(arena, dist, device, DL_FLOAT, 32, rows, k), options,
                    clusters, overlap);
            check((int) STREAM_SYNC.invokeExact(res), "cuvsStreamSync");
            if ((int) HIP_MEMCPY.invokeExact(neighbors, ids, idsBytes, HIP_MEMCPY_DEVICE_TO_HOST) != 0
                    || (int) HIP_MEMCPY.invokeExact(distances, dist, distBytes, HIP_MEMCPY_DEVICE_TO_HOST) != 0) {
                throw new TornadoRuntimeException("[ERROR] cuvsAllNeighborsBuild on hipVS: copying the graph to the host failed");
            }
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            try {
                if (dist != 0) {
                    int ignored = (int) RMM_FREE.invokeExact(res, dist, distBytes);
                }
                if (ids != 0) {
                    int ignored = (int) RMM_FREE.invokeExact(res, ids, idsBytes);
                }
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }
    }

    /** {@code hipMemcpyDeviceToHost}. */
    private static final int HIP_MEMCPY_DEVICE_TO_HOST = 2;

    private static void allNeighbors(long res, MemorySegment dataset, int algo, int metric, long k, MemorySegment neighbors, MemorySegment distances, CuVSAllNeighborsOptions options,
            long clusters, long overlap) {
        long params = 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(C_LONG);
            check((int) ALL_NEIGHBORS_PARAMS_CREATE.invokeExact(out), "cuvsAllNeighborsIndexParamsCreate");
            params = out.get(C_LONG, 0);
            MemorySegment p = FFMSupport.asSegment(params, 48);
            p.set(C_INT, ALL_NEIGHBORS_ALGO, algo);
            p.set(C_LONG, ALL_NEIGHBORS_OVERLAP, overlap);
            p.set(C_LONG, ALL_NEIGHBORS_N_CLUSTERS, clusters);
            p.set(C_INT, ALL_NEIGHBORS_METRIC, metric);
            if (options != null) {
                // the all-neighbors params own an optional NN-Descent params struct (null = cuVS defaults);
                // cuvsAllNeighborsIndexParamsDestroy frees it
                check((int) NN_DESCENT_PARAMS_CREATE.invokeExact(out), "cuvsNNDescentIndexParamsCreate");
                long nnd = out.get(C_LONG, 0);
                p.set(C_LONG, ALL_NEIGHBORS_NN_DESCENT_PARAMS, nnd);
                MemorySegment q = FFMSupport.asSegment(nnd, 48);
                // the conversion copies every field, so the metric and degree must match the call
                q.set(C_INT, NND_METRIC, metric);
                q.set(C_LONG, NND_GRAPH_DEGREE, k);
                long intermediate = options.getIntermediateGraphDegree() > 0 ? options.getIntermediateGraphDegree() : Math.max(q.get(C_LONG, NND_INTERMEDIATE_GRAPH_DEGREE), k);
                q.set(C_LONG, NND_INTERMEDIATE_GRAPH_DEGREE, Math.max(intermediate, k));
                if (options.getMaxIterations() > 0) {
                    q.set(C_LONG, NND_MAX_ITERATIONS, options.getMaxIterations());
                }
                if (options.getTerminationThreshold() > 0) {
                    q.set(C_FLOAT, NND_TERMINATION_THRESHOLD, options.getTerminationThreshold());
                }
            }
            check((int) ALL_NEIGHBORS_BUILD.invokeExact(res, params, dataset, neighbors, distances, MemorySegment.NULL, 1.0f), "cuvsAllNeighborsBuild");
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            if (params != 0) {
                try {
                    int ignored = (int) ALL_NEIGHBORS_PARAMS_DESTROY.invokeExact(params);
                } catch (Throwable t) {
                    // ignore
                }
            }
        }
    }

    // ---- k-means ----

    private static long kmeansParams(Arena arena, int nClusters, int maxIter) throws Throwable {
        MemorySegment out = arena.allocate(C_LONG);
        check((int) KMEANS_PARAMS_CREATE.invokeExact(out), "cuvsKMeansParamsCreate");
        long params = out.get(C_LONG, 0);
        MemorySegment p = FFMSupport.asSegment(params, 16);
        p.set(C_INT, KMEANS_METRIC, 0); // L2Expanded
        p.set(C_INT, KMEANS_N_CLUSTERS, nClusters);
        p.set(C_INT, KMEANS_MAX_ITER, maxIter);
        return params;
    }

    static void kmeansFit(long res, MemorySegment data, int nClusters, int maxIter, MemorySegment centroids) {
        long params = 0;
        try (Arena arena = Arena.ofConfined()) {
            params = kmeansParams(arena, nClusters, maxIter);
            MemorySegment inertia = arena.allocate(C_DOUBLE);
            MemorySegment nIter = arena.allocate(C_INT);
            check((int) KMEANS_FIT.invokeExact(res, params, data, MemorySegment.NULL, centroids, inertia, nIter), "cuvsKMeansFit");
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            destroyKMeansParams(params);
        }
    }

    static void kmeansPredict(long res, MemorySegment data, int nClusters, MemorySegment centroids, MemorySegment labels) {
        long params = 0;
        try (Arena arena = Arena.ofConfined()) {
            params = kmeansParams(arena, nClusters, 1);
            MemorySegment inertia = arena.allocate(C_DOUBLE);
            check((int) KMEANS_PREDICT.invokeExact(res, params, data, MemorySegment.NULL, centroids, labels, false, inertia), "cuvsKMeansPredict");
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            destroyKMeansParams(params);
        }
    }

    private static void destroyKMeansParams(long params) {
        if (params != 0) {
            try {
                int ignored = (int) KMEANS_PARAMS_DESTROY.invokeExact(params);
            } catch (Throwable t) {
                // ignore
            }
        }
    }

    // ---- errors ----

    private static void check(int status, String function) {
        if (status != CUVS_SUCCESS) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed: " + lastError());
        }
    }

    private static String lastError() {
        try {
            MemorySegment text = (MemorySegment) LAST_ERROR_TEXT.invokeExact();
            return text.equals(MemorySegment.NULL) ? "unknown error" : FFMSupport.readCString(text);
        } catch (Throwable t) {
            return "unknown error";
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException e) {
            throw e;
        }
        if (t instanceof Error e) {
            throw e;
        }
        throw new IllegalStateException(t);
    }
}
