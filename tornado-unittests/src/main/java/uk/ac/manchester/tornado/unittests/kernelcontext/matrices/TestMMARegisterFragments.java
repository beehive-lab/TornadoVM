/*
 * Copyright (c) 2026 APT Group, Department of Computer Science,
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
 * Tests the m16n8k16 fragments a kernel builds from its own registers,
 * {@link KernelContext#mmaFragmentA(int, int, int, int)} and
 * {@link KernelContext#mmaFragmentB(int, int)}: each lane packs the half pairs the documented
 * layout gives it, and the products are checked against a CPU GEMM and against the fragments
 * {@link KernelContext#mmaLoadA(HalfFloatArray, int, int, int)} and
 * {@link KernelContext#mmaLoadB(HalfFloatArray, int, int, int)} load.
 *
 * <p>
 * How to run?
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMMARegisterFragments
 * </code>
 * </p>
 */
public class TestMMARegisterFragments extends TornadoTestBase {

    private static final int TILE = 16;
    private static final int WARP_SIZE = 32;

    /** Halves {@code first} and {@code second} as one register: {@code first} in bits 0-15. */
    private static int pack(HalfFloat first, HalfFloat second) {
        return (first.getHalfFloatValue() & 0xFFFF) | ((second.getHalfFloatValue() & 0xFFFF) << 16);
    }

    /**
     * C = A * B, row-major, each warp one 16x16 tile of C from fragments its lanes build in
     * registers. Lane {@code L} packs, for A, row {@code L / 4 + 8 * (i % 2)}, columns
     * {@code (L % 4) * 2 + 8 * (i / 2)} and the next one into register {@code i}; for B, column
     * {@code L / 4}, k {@code (L % 4) * 2 + 8 * i} and the next one.
     */
    public static void gemmRegisters(KernelContext ctx, HalfFloatArray a, HalfFloatArray b, FloatArray c, int dimM, int dimN, int dimK) {
        int lane = ctx.localIdx & 31;
        int warpId = ctx.globalIdx / WARP_SIZE;
        int tilesN = dimN / TILE;
        int tileRow = (warpId / tilesN) * TILE;
        int tileCol = (warpId % tilesN) * TILE;
        int r = tileRow + lane / 4;
        int nLeft = tileCol + lane / 4;
        float[] accLeft = ctx.mmaFragment(0.0f);
        float[] accRight = ctx.mmaFragment(0.0f);
        for (int k = 0; k < dimK; k += TILE) {
            int ca = k + (lane % 4) * 2;
            HalfFloat[] fragA = ctx.mmaFragmentA( //
                    pack(a.get(r * dimK + ca), a.get(r * dimK + ca + 1)), //
                    pack(a.get((r + 8) * dimK + ca), a.get((r + 8) * dimK + ca + 1)), //
                    pack(a.get(r * dimK + ca + 8), a.get(r * dimK + ca + 9)), //
                    pack(a.get((r + 8) * dimK + ca + 8), a.get((r + 8) * dimK + ca + 9)));
            int kb = k + (lane % 4) * 2;
            HalfFloat[] fragBLeft = ctx.mmaFragmentB( //
                    pack(b.get(kb * dimN + nLeft), b.get((kb + 1) * dimN + nLeft)), //
                    pack(b.get((kb + 8) * dimN + nLeft), b.get((kb + 9) * dimN + nLeft)));
            HalfFloat[] fragBRight = ctx.mmaFragmentB( //
                    pack(b.get(kb * dimN + nLeft + 8), b.get((kb + 1) * dimN + nLeft + 8)), //
                    pack(b.get((kb + 8) * dimN + nLeft + 8), b.get((kb + 9) * dimN + nLeft + 8)));
            accLeft = ctx.mma(fragA, fragBLeft, accLeft, MMAShape.M16N8K16);
            accRight = ctx.mma(fragA, fragBRight, accRight, MMAShape.M16N8K16);
        }
        ctx.mmaStore(accLeft, c, tileRow, tileCol, dimN);
        ctx.mmaStore(accRight, c, tileRow, tileCol + 8, dimN);
    }

    /** {@link #gemmRegisters} with A built in registers and B loaded: both layouts must agree. */
    public static void gemmRegisterALoadedB(KernelContext ctx, HalfFloatArray a, HalfFloatArray b, FloatArray c, int dimM, int dimN, int dimK) {
        int lane = ctx.localIdx & 31;
        int warpId = ctx.globalIdx / WARP_SIZE;
        int tilesN = dimN / TILE;
        int tileRow = (warpId / tilesN) * TILE;
        int tileCol = (warpId % tilesN) * TILE;
        int r = tileRow + lane / 4;
        float[] accLeft = ctx.mmaFragment(0.0f);
        float[] accRight = ctx.mmaFragment(0.0f);
        for (int k = 0; k < dimK; k += TILE) {
            int ca = k + (lane % 4) * 2;
            HalfFloat[] fragA = ctx.mmaFragmentA( //
                    pack(a.get(r * dimK + ca), a.get(r * dimK + ca + 1)), //
                    pack(a.get((r + 8) * dimK + ca), a.get((r + 8) * dimK + ca + 1)), //
                    pack(a.get(r * dimK + ca + 8), a.get(r * dimK + ca + 9)), //
                    pack(a.get((r + 8) * dimK + ca + 8), a.get((r + 8) * dimK + ca + 9)));
            HalfFloat[] fragBLeft = ctx.mmaLoadB(b, k, tileCol, dimN);
            HalfFloat[] fragBRight = ctx.mmaLoadB(b, k, tileCol + 8, dimN);
            accLeft = ctx.mma(fragA, fragBLeft, accLeft, MMAShape.M16N8K16);
            accRight = ctx.mma(fragA, fragBRight, accRight, MMAShape.M16N8K16);
        }
        ctx.mmaStore(accLeft, c, tileRow, tileCol, dimN);
        ctx.mmaStore(accRight, c, tileRow, tileCol + 8, dimN);
    }

    private static HalfFloatArray randomFP16(int size, long seed) {
        Random random = new Random(seed);
        HalfFloatArray array = new HalfFloatArray(size);
        for (int i = 0; i < size; i++) {
            array.set(i, new HalfFloat(random.nextFloat() * 2.0f - 1.0f));
        }
        return array;
    }

    private void runGemm(int dimM, int dimN, int dimK, int localSize, int mixed) throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        HalfFloatArray a = randomFP16(dimM * dimK, 13);
        HalfFloatArray b = randomFP16(dimK * dimN, 17);
        FloatArray c = new FloatArray(dimM * dimN);

        WorkerGrid1D worker = new WorkerGrid1D((dimM / TILE) * (dimN / TILE) * WARP_SIZE);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler scheduler = new GridScheduler("s0.t0", worker);
        KernelContext ctx = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b);
        if (mixed == 1) {
            taskGraph.task("t0", TestMMARegisterFragments::gemmRegisterALoadedB, ctx, a, b, c, dimM, dimN, dimK);
        } else {
            taskGraph.task("t0", TestMMARegisterFragments::gemmRegisters, ctx, a, b, c, dimM, dimN, dimK);
        }
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(scheduler).execute();
        }

        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                float sum = 0.0f;
                for (int k = 0; k < dimK; k++) {
                    sum += a.get(i * dimK + k).getFloat32() * b.get(k * dimN + j).getFloat32();
                }
                assertEquals(sum, c.get(i * dimN + j), 1e-2f, String.format("C[%d][%d]", i, j));
            }
        }
    }

    @Test
    public void testSingleTile() throws TornadoExecutionPlanException {
        runGemm(16, 16, 16, WARP_SIZE, 0);
    }

    @Test
    public void testDeepK() throws TornadoExecutionPlanException {
        runGemm(16, 16, 128, WARP_SIZE, 0);
    }

    @Test
    public void testMultiTileFourWarpsPerGroup() throws TornadoExecutionPlanException {
        runGemm(64, 64, 64, 4 * WARP_SIZE, 0);
    }

    /** A built in registers, B loaded from global memory: the two layouts must match. */
    @Test
    public void testRegisterAWithLoadedB() throws TornadoExecutionPlanException {
        runGemm(32, 32, 48, WARP_SIZE, 1);
    }
}
