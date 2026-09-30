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

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * Panama FFM bindings to the cuVS C API ({@code libcuvs_c}), and the DLPack tensors it takes.
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

    // DLPack (dlpack.h): device types, data type codes and the DLManagedTensor layout
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
    private static final MethodHandle KMEANS_PARAMS_CREATE;
    private static final MethodHandle KMEANS_PARAMS_DESTROY;
    private static final MethodHandle KMEANS_FIT;
    private static final MethodHandle KMEANS_PREDICT;

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

    /** Throws if the cuVS C library could not be loaded. */
    static synchronized void load() {
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
        MemorySegment t = arena.allocate(DL_MANAGED_TENSOR);
        int ndim = cols == 0 ? 1 : 2;
        // FFMSupport keeps this portable: on JDK 21 Arena.allocate(C_LONG, n) allocates ONE long of value n
        MemorySegment shape = FFMSupport.allocateLongArray(arena, ndim == 2 ? new long[] { rows, cols } : new long[] { rows });
        t.set(C_POINTER, offset("data"), MemorySegment.ofAddress(devicePointer));
        t.set(C_INT, offset("device_type"), DL_CUDA);
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

    static void allNeighbors(long res, MemorySegment dataset, int algo, int metric, MemorySegment neighbors, MemorySegment distances) {
        long params = 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(C_LONG);
            check((int) ALL_NEIGHBORS_PARAMS_CREATE.invokeExact(out), "cuvsAllNeighborsIndexParamsCreate");
            params = out.get(C_LONG, 0);
            MemorySegment p = FFMSupport.asSegment(params, 32);
            p.set(C_INT, ALL_NEIGHBORS_ALGO, algo);
            // a device-resident dataset is processed as one batch: n_clusters must be 1
            p.set(C_LONG, ALL_NEIGHBORS_OVERLAP, 1L);
            p.set(C_LONG, ALL_NEIGHBORS_N_CLUSTERS, 1L);
            p.set(C_INT, ALL_NEIGHBORS_METRIC, metric);
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
