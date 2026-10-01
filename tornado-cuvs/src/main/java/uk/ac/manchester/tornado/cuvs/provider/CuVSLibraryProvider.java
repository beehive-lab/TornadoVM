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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Map;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cuvs.CuVS;
import uk.ac.manchester.tornado.cuvs.CuVSAllNeighborsOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * Library-task provider for NVIDIA cuVS.
 *
 * <p>One {@code cuvsResources_t} per execution plan, bound to the plan's CUDA stream with {@code cuvsStreamSet}, so
 * cuVS calls are ordered with the Java kernels and transfers of the same task graph. TornadoVM device buffers are
 * passed to cuVS zero-copy, as non-owning DLPack tensors on the CUDA device. cuVS allocates its own scratch memory
 * (through RMM).
 */
public final class CuVSLibraryProvider implements TornadoLibraryProvider {

    private static final class CuVSContext implements LibraryContext {
        private final long resources;
        private final int deviceId;

        private CuVSContext(long resources, int deviceId) {
            this.resources = resources;
            this.deviceId = deviceId;
        }
    }

    @FunctionalInterface
    private interface CuVSCall {
        void invoke(CuVSContext context, LibraryInvocation invocation, Arena arena);
    }

    /** Dispatch registry: function name -> marshalling call. */
    private static final Map<String, CuVSCall> FUNCTIONS = Map.of(//
            "cuvsBruteForceKnn", CuVSLibraryProvider::bruteForceKnn, //
            "cuvsAllNeighborsBuild", CuVSLibraryProvider::allNeighbors, //
            "cuvsKMeansFit", CuVSLibraryProvider::kmeansFit, //
            "cuvsKMeansPredict", CuVSLibraryProvider::kmeansPredict);

    /** Whether the cuVS C library can be loaded on this host. */
    public static boolean isAvailable() {
        try {
            CuVSNativeLib.load();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Direct, synchronous host-dataset all-neighbors build; see {@link CuVS#allNeighborsOnHost}. */
    public static void allNeighborsOnHost(MemorySegment dataset, long rows, int dim, int k, int algo, int metric, MemorySegment neighbors, MemorySegment distances,
            CuVSAllNeighborsOptions options) {
        CuVSNativeLib.load();
        CuVSNativeLib.allNeighborsOnHost(dataset, rows, dim, algo, metric, k, neighbors, distances, options);
    }

    @Override
    public String libraryName() {
        return CuVS.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        // Only the CUDA backend exposes its native stream for library interop
        return device instanceof TornadoNativeStreamSupport;
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        CuVSNativeLib.load();
        long stream = ((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId);
        long resources = CuVSNativeLib.resourcesCreate();
        CuVSNativeLib.streamSet(resources, stream);
        return new CuVSContext(resources, CuVSNativeLib.deviceId(resources));
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        CuVSCall call = FUNCTIONS.get(functionName);
        if (call == null) {
            throw new TornadoRuntimeException("[ERROR] cuVS function not supported: " + functionName);
        }
        if (invocation.isCapturing()) {
            throw new TornadoRuntimeException("[ERROR] cuVS library tasks cannot be captured in a CUDA graph (cuVS allocates scratch memory during the call)");
        }
        try (Arena arena = Arena.ofConfined()) {
            call.invoke((CuVSContext) invocation.getContext(), invocation, arena);
        }
    }

    @Override
    public void destroyContext(LibraryContext context) {
        CuVSNativeLib.resourcesDestroy(((CuVSContext) context).resources);
    }

    private static MemorySegment floats(CuVSContext ctx, LibraryInvocation inv, Arena arena, int arg, long rows, long cols) {
        return CuVSNativeLib.deviceTensor(arena, inv.getDevicePointer(arg), ctx.deviceId, CuVSNativeLib.DL_FLOAT, 32, rows, cols);
    }

    /** (dataset, nRows, dim, queries, nQueries, k, metric, neighbors, distances). */
    private static void bruteForceKnn(CuVSContext ctx, LibraryInvocation inv, Arena arena) {
        int nRows = (int) inv.getArg(1);
        int dim = (int) inv.getArg(2);
        int nQueries = (int) inv.getArg(4);
        int k = (int) inv.getArg(5);
        int metric = (int) inv.getArg(6);
        MemorySegment dataset = floats(ctx, inv, arena, 0, nRows, dim);
        MemorySegment queries = floats(ctx, inv, arena, 3, nQueries, dim);
        MemorySegment neighbors = CuVSNativeLib.deviceTensor(arena, inv.getDevicePointer(7), ctx.deviceId, CuVSNativeLib.DL_INT, 64, nQueries, k);
        MemorySegment distances = floats(ctx, inv, arena, 8, nQueries, k);
        CuVSNativeLib.bruteForceKnn(ctx.resources, dataset, queries, metric, neighbors, distances);
    }

    /** (dataset, nRows, dim, k, algo, metric, neighbors, distances). */
    private static void allNeighbors(CuVSContext ctx, LibraryInvocation inv, Arena arena) {
        int nRows = (int) inv.getArg(1);
        int dim = (int) inv.getArg(2);
        int k = (int) inv.getArg(3);
        int algo = (int) inv.getArg(4);
        int metric = (int) inv.getArg(5);
        MemorySegment dataset = floats(ctx, inv, arena, 0, nRows, dim);
        MemorySegment neighbors = CuVSNativeLib.deviceTensor(arena, inv.getDevicePointer(6), ctx.deviceId, CuVSNativeLib.DL_INT, 64, nRows, k);
        MemorySegment distances = floats(ctx, inv, arena, 7, nRows, k);
        CuVSNativeLib.allNeighbors(ctx.resources, dataset, algo, metric, k, neighbors, distances, //
                inv.getTuning() instanceof CuVSAllNeighborsOptions options ? options : null);
    }

    /** (data, nRows, dim, nClusters, maxIter, centroids). */
    private static void kmeansFit(CuVSContext ctx, LibraryInvocation inv, Arena arena) {
        int nRows = (int) inv.getArg(1);
        int dim = (int) inv.getArg(2);
        int nClusters = (int) inv.getArg(3);
        int maxIter = (int) inv.getArg(4);
        MemorySegment data = floats(ctx, inv, arena, 0, nRows, dim);
        MemorySegment centroids = floats(ctx, inv, arena, 5, nClusters, dim);
        CuVSNativeLib.kmeansFit(ctx.resources, data, nClusters, maxIter, centroids);
    }

    /** (data, nRows, dim, centroids, nClusters, labels). */
    private static void kmeansPredict(CuVSContext ctx, LibraryInvocation inv, Arena arena) {
        int nRows = (int) inv.getArg(1);
        int dim = (int) inv.getArg(2);
        int nClusters = (int) inv.getArg(4);
        MemorySegment data = floats(ctx, inv, arena, 0, nRows, dim);
        MemorySegment centroids = floats(ctx, inv, arena, 3, nClusters, dim);
        MemorySegment labels = CuVSNativeLib.deviceTensor(arena, inv.getDevicePointer(5), ctx.deviceId, CuVSNativeLib.DL_INT, 32, nRows, 0);
        CuVSNativeLib.kmeansPredict(ctx.resources, data, nClusters, centroids, labels);
    }
}
