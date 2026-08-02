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
package uk.ac.manchester.tornado.examples.kernelcontext.matrices;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FP8Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * The FP8 (E4M3) stage of {@link LowPrecisionGemmBenchmark}, kept in its own class so that the
 * hardware-gated failure on pre-Ada GPUs stays isolated from the rest of the benchmark.
 *
 * <p>
 * FP8 tensor-core MMA needs compute capability &gt;= 8.9 (Ada/Hopper) AND PTX ISA &gt;= 8.4
 * (CUDA 12.4+). TornadoVM enforces both in {@code CUDATensorCoreSupportPhase} and raises
 * {@code TornadoDeviceFP8NotSupported} otherwise -- so on, say, an Ampere RTX 3070 (sm_86) this
 * whole stage is reported as skipped rather than failing the run.
 *
 * <p>
 * Note the K-step is 32 here, not 16: FP8 MMA uses {@link MMAShape#M16N8K32}, twice the depth per
 * instruction, since twice as many 8-bit operands fit in the same fragment registers.
 */
public final class FP8GemmStage {

    private static final int WMMA_M = 16;
    private static final int WMMA_N = 16;
    private static final int WMMA_K = 32;
    private static final int WARP_SIZE = 32;

    private FP8GemmStage() {
    }

    /**
     * GEMM over E4M3 FP8 operands with fp32 accumulation. Four FP8 bytes are packed per shared
     * int, so the tile arithmetic divides by 4 where the fp16 path divides by 2.
     */
    public static void gemmFP8(KernelContext ctx, FP8Array a, FP8Array b, FloatArray c, int dimN, int dimK) {
        int warpId = ctx.groupIdx;
        int lane = ctx.localIdx;

        int numTilesN = dimN / WMMA_N;
        int tileRow = (warpId / numTilesN) * WMMA_M;
        int tileCol = (warpId % numTilesN) * WMMA_N;

        int[] aTile = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 4);
        int[] bTile0 = ctx.allocateIntLocalArray(WMMA_K * 8 / 4);
        int[] bTile1 = ctx.allocateIntLocalArray(WMMA_K * 8 / 4);

        float[] fragC0 = ctx.mmaFragment(0.0f);
        float[] fragC1 = ctx.mmaFragment(0.0f);

        for (int kBase = 0; kBase < dimK; kBase += WMMA_K) {
            for (int idx = lane; idx < (WMMA_M * WMMA_K) / 4; idx += WARP_SIZE) {
                int elemBase = idx * 4;
                int r = elemBase / WMMA_K;
                int kk = elemBase % WMMA_K;
                int globalBase = (tileRow + r) * dimK + kBase + kk;
                int b0 = a.get(globalBase) & 0xFF;
                int b1 = a.get(globalBase + 1) & 0xFF;
                int b2 = a.get(globalBase + 2) & 0xFF;
                int b3 = a.get(globalBase + 3) & 0xFF;
                aTile[r * (WMMA_K / 4) + kk / 4] = b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
            }

            for (int idx = lane; idx < (WMMA_K * 8) / 4; idx += WARP_SIZE) {
                int kRow = idx / 2;
                int jQuad = idx % 2;
                int jBase = jQuad * 4;

                int gL = (kBase + kRow) * dimN + tileCol + jBase;
                int l0 = b.get(gL) & 0xFF;
                int l1 = b.get(gL + 1) & 0xFF;
                int l2 = b.get(gL + 2) & 0xFF;
                int l3 = b.get(gL + 3) & 0xFF;
                bTile0[kRow * 2 + jQuad] = l0 | (l1 << 8) | (l2 << 16) | (l3 << 24);

                int gR = (kBase + kRow) * dimN + tileCol + 8 + jBase;
                int r0 = b.get(gR) & 0xFF;
                int r1 = b.get(gR + 1) & 0xFF;
                int r2 = b.get(gR + 2) & 0xFF;
                int r3 = b.get(gR + 3) & 0xFF;
                bTile1[kRow * 2 + jQuad] = r0 | (r1 << 8) | (r2 << 16) | (r3 << 24);
            }
            ctx.localBarrier();

            byte[] fragA = ctx.mmaLoadAFP8(aTile, WMMA_K);
            byte[] fragB0 = ctx.mmaLoadBFP8(bTile0, WMMA_K);
            fragC0 = ctx.mmaFP8E4M3(fragA, fragB0, fragC0, MMAShape.M16N8K32);
            byte[] fragB1 = ctx.mmaLoadBFP8(bTile1, WMMA_K);
            fragC1 = ctx.mmaFP8E4M3(fragA, fragB1, fragC1, MMAShape.M16N8K32);

            ctx.localBarrier();
        }

        ctx.mmaStore(fragC0, c, tileRow, tileCol, dimN);
        ctx.mmaStore(fragC1, c, tileRow, tileCol + 8, dimN);
    }

    /**
     * Builds, runs and times the FP8 stage.
     *
     * <p>
     * Two different failure modes have to be handled, because they depend on a JVM flag the
     * caller may not control. With {@code -Dtornado.recover.bailout=False} (what {@code
     * tornado-test} sets) an unsupported-hardware FP8 kernel raises
     * {@code TornadoDeviceFP8NotSupported}, which propagates out of here. With the default
     * bailout behaviour (what a plain {@code tornado} example run uses) TornadoVM instead prints
     * a {@code [Bailout]} warning and silently re-runs the method as sequential host Java -- where
     * {@link KernelContext} indices are all zero, so nothing meaningful is computed and the
     * "kernel" appears absurdly fast. Timing that would report a fictional five-digit GFLOP/s
     * figure, so the result is validated before any number is printed: an untouched (all-zero)
     * output means the device kernel never ran.
     *
     * @return {@code true} if the FP8 kernel genuinely executed on the device, {@code false} if it
     *     silently bailed out to the sequential host path.
     */
    public static boolean run(KernelContext context, float[] refA, float[] refB, int m, int n, int k, int iterations) throws TornadoExecutionPlanException {
        FP8Array a = new FP8Array(m * k);
        FP8Array b = new FP8Array(k * n);
        FloatArray c = new FloatArray(m * n);

        for (int i = 0; i < m * k; i++) {
            a.setE4M3(i, refA[i]);
        }
        for (int i = 0; i < k * n; i++) {
            b.setE4M3(i, refB[i]);
        }

        int warpTiles = (m / WMMA_M) * (n / WMMA_N);
        WorkerGrid1D worker = new WorkerGrid1D(warpTiles * WARP_SIZE);
        worker.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler grid = new GridScheduler("fp8.gemm", worker);

        TaskGraph graph = new TaskGraph("fp8") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("gemm", FP8GemmStage::gemmFP8, context, a, b, c, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        long firstRunNs;
        long steadyNs;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            long start = System.nanoTime();
            plan.withGridScheduler(grid).execute();
            firstRunNs = System.nanoTime() - start;

            long steadyStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                plan.withGridScheduler(grid).execute();
            }
            steadyNs = (System.nanoTime() - steadyStart) / iterations;
        }

        // A silent bailout to sequential host Java leaves C untouched: KernelContext indices are
        // all zero there, so only element (0,0) is ever a candidate to be written and the rest
        // stay at their initial 0.0f. Sample a few genuinely-should-be-nonzero outputs.
        boolean ranOnDevice = false;
        for (int i = 1; i < 4 && !ranOnDevice; i++) {
            for (int j = 1; j < 4; j++) {
                if (c.get(i * n + j) != 0.0f) {
                    ranOnDevice = true;
                    break;
                }
            }
        }
        if (!ranOnDevice) {
            return false;
        }

        double ops = 2.0 * m * n * k;
        double gflops = (ops / (steadyNs / 1e9)) / 1e9;
        System.out.printf("  %-26s  steady=%8.3f ms   %8.1f GFLOP/s   first-run=%8.1f ms%n", "FP8 E4M3 MMA", steadyNs / 1e6, gflops, firstRunNs / 1e6);
        return true;
    }

}
