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
package uk.ac.manchester.tornado.unittests.tile;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceTileNotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Tensor-core GEMM through CUDA Tile.
 *
 * <p>
 * The point of this test is not only the numbers. One {@code tc.mma} replaces the whole
 * hand-written mma.sync sequence, and the tile compiler picks the instruction and the operand
 * layouts, so the generated kernel must contain {@code ct::mma} and no inline PTX. Check that
 * with {@code --printKernel}, and check the SASS of the cached cubin for {@code HMMA}.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileMatmul
 * </pre>
 */
public class TestTileMatmul extends TornadoTestBase {

    private static final int TILE_M = 32;

    private static final int TILE_N = 32;

    private static final int TILE_K = 32;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    private static void executeOrReportUnsupported(TornadoExecutionPlan plan, GridScheduler grid) throws TornadoExecutionPlanException {
        try {
            plan.withGridScheduler(grid).execute();
        } catch (TornadoDeviceTileNotSupported e) {
            throw new TornadoVMCUDANotSupported(e.getMessage());
        } catch (TornadoBailoutRuntimeException e) {
            String message = String.valueOf(e.getMessage());
            if (message.contains("device kernel image is invalid")) {
                throw new TornadoVMCUDANotSupported("Loading a CUDA Tile cubin requires driver R580 or newer: " + message);
            }
            throw e;
        }
    }

    /**
     * C = A * B, with fp16 operands and an fp32 accumulator. The accumulator is carried across
     * the k loop, which is what makes it a phi in the graph, and the plugins have to see
     * through that to type the mma.
     */
    public static void matmul(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c, int m, int n, int k) {
        PartitionView av = tc.partition(tc.view(a, m, k), TILE_M, TILE_K);
        PartitionView bv = tc.partition(tc.view(b, k, n), TILE_K, TILE_N);
        PartitionView cv = tc.partition(tc.view(c, m, n), TILE_M, TILE_N);

        Tile acc = tc.zeros(DType.F32, TILE_M, TILE_N);
        for (int step = 0; step < k / TILE_K; step++) {
            acc = tc.mma(av.load(tc.bidX(), step), bv.load(step, tc.bidY()), acc);
        }
        cv.store(acc, tc.bidX(), tc.bidY());
    }

    private static void runGemm(int m, int n, int k) throws TornadoExecutionPlanException {
        HalfFloatArray a = new HalfFloatArray(m * k);
        HalfFloatArray b = new HalfFloatArray(k * n);
        FloatArray c = new FloatArray(m * n);
        java.util.Random random = new java.util.Random(17);
        for (int i = 0; i < m * k; i++) {
            a.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }
        for (int i = 0; i < k * n; i++) {
            b.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }

        TaskGraph graph = new TaskGraph("tileGemm") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("gemm", TestTileMatmul::matmul, new TileContext(), a, b, c, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        WorkerGrid2D worker = new WorkerGrid2D(m / TILE_M, n / TILE_N);
        worker.setLocalWork(1, 1, 1);
        GridScheduler grid = new GridScheduler("tileGemm.gemm", worker);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            executeOrReportUnsupported(plan, grid);
        }

        for (int row = 0; row < m; row++) {
            for (int column = 0; column < n; column++) {
                float expected = 0.0f;
                for (int inner = 0; inner < k; inner++) {
                    expected += a.get(row * k + inner).getFloat32() * b.get(inner * n + column).getFloat32();
                }
                assertEquals(expected, c.get(row * n + column), 0.05f);
            }
        }
    }

    /**
     * GEMM with a K that is not a multiple of the tile depth, using masked loads.
     *
     * <p>
     * TileGym parameterises this case (k = 1023) and then skips it, noting a "result mismatch
     * when cannot divide BLOCK". It works here for a specific reason: a masked load zero-pads
     * the tail of the last k-tile, and a zero contributes nothing to a dot product, so the
     * padded lanes are harmless. That only holds for accumulate-style reductions, which is
     * worth stating rather than assuming it generalises.
     * </p>
     */
    public static void matmulRagged(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c, int m, int n, int k, int kTiles) {
        PartitionView av = tc.partition(tc.view(a, m, k), TILE_M, TILE_K);
        PartitionView bv = tc.partition(tc.view(b, k, n), TILE_K, TILE_N);
        PartitionView cv = tc.partition(tc.view(c, m, n), TILE_M, TILE_N);

        Tile acc = tc.zeros(DType.F32, TILE_M, TILE_N);
        for (int step = 0; step < kTiles; step++) {
            acc = tc.mma(av.loadMasked(tc.bidX(), step), bv.loadMasked(step, tc.bidY()), acc);
        }
        cv.storeMasked(acc, tc.bidX(), tc.bidY());
    }

    private static void runRaggedGemm(int m, int n, int k) throws TornadoExecutionPlanException {
        HalfFloatArray a = new HalfFloatArray(m * k);
        HalfFloatArray b = new HalfFloatArray(k * n);
        FloatArray c = new FloatArray(m * n);
        java.util.Random random = new java.util.Random(53 + k);
        for (int i = 0; i < m * k; i++) {
            a.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }
        for (int i = 0; i < k * n; i++) {
            b.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }

        int kTiles = (k + TILE_K - 1) / TILE_K;
        TaskGraph graph = new TaskGraph("ragged") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("gemm", TestTileMatmul::matmulRagged, new TileContext(), a, b, c, m, n, k, kTiles) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        WorkerGrid2D worker = new WorkerGrid2D((m + TILE_M - 1) / TILE_M, (n + TILE_N - 1) / TILE_N);
        GridScheduler grid = new GridScheduler("ragged.gemm", worker);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            executeOrReportUnsupported(plan, grid);
        }

        for (int row = 0; row < m; row++) {
            for (int column = 0; column < n; column++) {
                float expected = 0.0f;
                for (int inner = 0; inner < k; inner++) {
                    expected += a.get(row * k + inner).getFloat32() * b.get(inner * n + column).getFloat32();
                }
                assertEquals("(" + row + "," + column + ")", expected, c.get(row * n + column), 0.05f);
            }
        }
    }

    @Test
    public void testGemmNonDivisibleK() throws TornadoExecutionPlanException {
        runRaggedGemm(64, 64, 96 + 5);
    }

    @Test
    public void testGemmNonDivisibleAllDims() throws TornadoExecutionPlanException {
        // Partial tiles in M, N and K at once, so both masked loads and a masked store are used.
        runRaggedGemm(70, 100, 37);
    }

    @Test
    public void testGemmTiny() throws TornadoExecutionPlanException {
        // TileGym's (8, 8, 8): smaller than a single tile in every dimension.
        runRaggedGemm(8, 8, 8);
    }

    @Test
    public void testGemm64() throws TornadoExecutionPlanException {
        runGemm(64, 64, 64);
    }

    @Test
    public void testGemm128() throws TornadoExecutionPlanException {
        runGemm(128, 128, 128);
    }

    /**
     * The same GEMM with a CUDA Tile launch hint set.
     *
     * <p>
     * {@code -Dtornado.cuda.tile.hints} puts a {@code [[using cutile : hint(0, ...)]]}
     * attribute on the generated kernel. The hint changes what the tile compiler does with
     * registers and shared memory - measured on sm_89, {@code occupancy=8} takes the kernel
     * from 110 registers and 36 KB of shared memory down to 64 and 8 KB - so what this test
     * checks is the thing a hint must never change: the result.
     * </p>
     *
     * <p>
     * The property is read at code generation time, in this process, so setting it here
     * affects the compilation that follows. It is restored afterwards so the rest of the suite
     * compiles unhinted.
     * </p>
     */
    @Test
    public void testGemmWithLaunchHint() throws TornadoExecutionPlanException {
        final String property = "tornado.cuda.tile.hints";
        String previous = System.getProperty(property);
        System.setProperty(property, "occupancy=2");
        try {
            runGemm(64, 64, 64);
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    @Test
    public void testGemmRectangular() throws TornadoExecutionPlanException {
        runGemm(128, 64, 96);
    }
}
