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
package uk.ac.manchester.tornado.unittests.kernelcontext.matrices;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * An accumulator fragment passed to a private helper that reads it as a {@code float[]}, as
 * jitLLM's {@code Qwen35MMAKernels.addTile(X, acc, r, c, ld)} does in the epilogue of
 * {@code gemmMMATiledBResidual} (TC-12: TC failed with "TC-E2107 operand 0 must be ..., found
 * vec<4 x f32>", the helper's array loads having the caller's fragment vector as their array).
 *
 * <p>
 * The first test adds a constant fragment through the helper. The second runs the m16n8k16 GEMM
 * of {@link TestMatrixMultiplicationMMA} and adds both accumulators into a zeroed array through
 * the helper, with the lane mapping of the m16n8k16 accumulator (element {@code i} of lane
 * {@code l} is row {@code l / 4 + 8 (i / 2)}, column {@code 2 (l % 4) + i % 2}); the sums must
 * equal {@code mmaStore}'s.
 * </p>
 *
 * <p>
 * How to run?
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMMAFragmentHelper
 * </code>
 * </p>
 */
public class TestMMAFragmentHelper extends TornadoTestBase {

    private static final int TILE = 16;
    private static final int WARP_SIZE = 32;

    /** {@code x[r][c] += acc[i]} for this lane's four elements of one 16 x 8 accumulator. */
    private static void addTile(FloatArray x, float[] acc, int r, int c, int ld) {
        int i0 = r * ld + c;
        x.set(i0, x.get(i0) + acc[0]);
        x.set(i0 + 1, x.get(i0 + 1) + acc[1]);
        int i1 = i0 + 8 * ld;
        x.set(i1, x.get(i1) + acc[2]);
        x.set(i1 + 1, x.get(i1 + 1) + acc[3]);
    }

    /** x[4 g + i] += acc[i] for a fragment of 1.5 in every element. */
    private static void addFlat(FloatArray x, float[] acc, int base) {
        x.set(base, x.get(base) + acc[0]);
        x.set(base + 1, x.get(base + 1) + acc[1]);
        x.set(base + 2, x.get(base + 2) + acc[2]);
        x.set(base + 3, x.get(base + 3) + acc[3]);
    }

    public static void constantFragment(KernelContext ctx, FloatArray x) {
        float[] frag = ctx.mmaFragment(1.5f);
        addFlat(x, frag, ctx.globalIdx * 4);
    }

    /** {@link TestMatrixMultiplicationMMA#gemmMMA}, storing with mmaStore into c and through addTile into x. */
    public static void gemmMMAWithHelper(KernelContext ctx, HalfFloatArray a, HalfFloatArray b, FloatArray c, FloatArray x, int dimM, int dimN, int dimK) {
        int warpId = ctx.groupIdx;
        int lane = ctx.localIdx;
        int numTilesN = dimN / TILE;
        int tileRow = (warpId / numTilesN) * TILE;
        int tileCol = (warpId % numTilesN) * TILE;
        int[] aTile = ctx.allocateIntLocalArray(TILE * TILE / 2);
        int[] bTile0 = ctx.allocateIntLocalArray(TILE * TILE / 2);
        int[] bTile1 = ctx.allocateIntLocalArray(TILE * TILE / 2);
        float[] fragC0 = ctx.mmaFragment(0.0f);
        float[] fragC1 = ctx.mmaFragment(0.0f);
        for (int kBase = 0; kBase < dimK; kBase += TILE) {
            for (int idx = lane; idx < (TILE * TILE) / 2; idx += WARP_SIZE) {
                int elemBase = idx * 2;
                int r = elemBase / TILE;
                int kk = elemBase % TILE;
                int globalBase = (tileRow + r) * dimK + kBase + kk;
                int lo = a.get(globalBase).getHalfFloatValue() & 0xFFFF;
                int hi = a.get(globalBase + 1).getHalfFloatValue() & 0xFFFF;
                aTile[r * (TILE / 2) + kk / 2] = lo | (hi << 16);
            }
            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int kRow = idx / 4;
                int jPair = idx % 4;
                int jBase = jPair * 2;
                int gL0 = (kBase + kRow) * dimN + tileCol + jBase;
                int loLeft = b.get(gL0).getHalfFloatValue() & 0xFFFF;
                int hiLeft = b.get(gL0 + 1).getHalfFloatValue() & 0xFFFF;
                bTile0[kRow * 4 + jPair] = loLeft | (hiLeft << 16);
                int gR0 = (kBase + kRow) * dimN + tileCol + 8 + jBase;
                int loRight = b.get(gR0).getHalfFloatValue() & 0xFFFF;
                int hiRight = b.get(gR0 + 1).getHalfFloatValue() & 0xFFFF;
                bTile1[kRow * 4 + jPair] = loRight | (hiRight << 16);
            }
            ctx.localBarrier();
            HalfFloat[] fragA = ctx.mmaLoadA(aTile, TILE);
            HalfFloat[] fragB0 = ctx.mmaLoadB(bTile0, TILE);
            fragC0 = ctx.mma(fragA, fragB0, fragC0, MMAShape.M16N8K16);
            HalfFloat[] fragB1 = ctx.mmaLoadB(bTile1, TILE);
            fragC1 = ctx.mma(fragA, fragB1, fragC1, MMAShape.M16N8K16);
            ctx.localBarrier();
        }
        ctx.mmaStore(fragC0, c, tileRow, tileCol, dimN);
        ctx.mmaStore(fragC1, c, tileRow, tileCol + 8, dimN);
        int r = tileRow + (lane >> 2);
        int col = tileCol + ((lane & 3) << 1);
        addTile(x, fragC0, r, col, dimN);
        addTile(x, fragC1, r, col + 8, dimN);
    }

    @Test
    public void testConstantFragmentThroughHelper() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
        FloatArray x = new FloatArray(4 * WARP_SIZE);
        x.init(1.0f);
        WorkerGrid1D worker = new WorkerGrid1D(WARP_SIZE);
        worker.setLocalWork(WARP_SIZE, 1, 1);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .task("t0", TestMMAFragmentHelper::constantFragment, new KernelContext(), x) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, x);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(new GridScheduler("s0.t0", worker)).execute();
        }
        for (int i = 0; i < x.getSize(); i++) {
            assertEquals(2.5f, x.get(i), 0.0f, "index " + i);
        }
    }

    @Test
    public void testGemmAccumulatorsThroughHelper() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
        int dimM = 32;
        int dimN = 32;
        int dimK = 64;
        Random random = new Random(12);
        HalfFloatArray a = new HalfFloatArray(dimM * dimK);
        HalfFloatArray b = new HalfFloatArray(dimK * dimN);
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }
        for (int i = 0; i < b.getSize(); i++) {
            b.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }
        FloatArray c = new FloatArray(dimM * dimN);
        FloatArray x = new FloatArray(dimM * dimN);
        x.init(0.0f);
        int warps = (dimM / TILE) * (dimN / TILE);
        WorkerGrid1D worker = new WorkerGrid1D(warps * WARP_SIZE);
        worker.setLocalWork(WARP_SIZE, 1, 1);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, x) //
                .task("t0", TestMMAFragmentHelper::gemmMMAWithHelper, new KernelContext(), a, b, c, x, dimM, dimN, dimK) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c, x);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(new GridScheduler("s0.t0", worker)).execute();
        }
        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(c.get(i), x.get(i), 0.0f, "element " + i + " (row " + i / dimN + ", column " + i % dimN + ")");
        }
    }
}
