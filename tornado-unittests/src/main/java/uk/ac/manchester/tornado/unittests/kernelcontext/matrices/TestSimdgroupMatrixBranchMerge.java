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
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix8x8Float;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Checks that {@code simdgroup_float8x8} accumulators produced by both arms of an
 * if/else, each ending in a loop, are merged correctly before a single store after
 * the merge. Each arm must keep its own result: the value from one branch must not
 * overwrite the value of the other.
 *
 * <p>{@code k} is read from a device array rather than passed as a scalar argument:
 * scalar arguments are constant-folded, which would remove the branch (and unroll the
 * loops). Each branch direction is exercised by its own test with a different {@code k}.
 *
 * <p>Metal-only: the primitives have no OpenCL/CUDA equivalent.
 *
 * <p>How to run:
 * <code>
 * tornado-test --printKernel -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestSimdgroupMatrixBranchMerge
 * </code>
 */
public class TestSimdgroupMatrixBranchMerge extends TornadoTestBase {

    private static final int TILE = 8;
    private static final int SIMD_GROUP = 32;
    private static final int THRESHOLD = 100;

    /**
     * One SIMD group per 8x8 tile of C. For {@code k > THRESHOLD} it computes C = A x B,
     * otherwise C = 2 * A x B (two multiply-accumulates per step), then stores once after the merge.
     */
    private static void gemmBranches(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c, IntArray dims) {
        int n = dims.get(0);
        int k = dims.get(1);
        int tilesPerRow = n / TILE;
        int tileIdx = ctx.groupIdx;
        int tileRow = tileIdx / tilesPerRow;
        int tileCol = tileIdx % tilesPerRow;

        int aBase = tileRow * TILE * k;
        int bBase = tileCol * TILE;
        int cBase = tileRow * TILE * n + tileCol * TILE;

        Matrix8x8Float acc;
        if (k > THRESHOLD) {
            acc = ctx.simdgroupMatrixZero();
            for (int p = 0; p < k; p += TILE) {
                Matrix8x8Float af = ctx.simdgroupMatrixLoad(a, aBase + p, k);
                Matrix8x8Float bf = ctx.simdgroupMatrixLoad(b, bBase + p * n, n);
                acc = ctx.simdgroupMatrixMultiplyAccumulate(af, bf, acc);
            }
        } else {
            acc = ctx.simdgroupMatrixZero();
            for (int p = 0; p < k; p += TILE) {
                Matrix8x8Float af = ctx.simdgroupMatrixLoad(a, aBase + p, k);
                Matrix8x8Float bf = ctx.simdgroupMatrixLoad(b, bBase + p * n, n);
                acc = ctx.simdgroupMatrixMultiplyAccumulate(af, bf, acc);
                acc = ctx.simdgroupMatrixMultiplyAccumulate(af, bf, acc);
            }
        }
        ctx.simdgroupMatrixStore(acc, c, cBase, n);
    }

    private static void cpuReference(FloatArray a, FloatArray b, FloatArray c, int m, int n, int k, float scale) {
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float acc = 0.0f;
                for (int p = 0; p < k; p++) {
                    acc += a.get(i * k + p) * b.get(p * n + j);
                }
                c.set(i * n + j, scale * acc);
            }
        }
    }

    private void runAndCheck(int m, int n, int k) throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.CUDA);

        FloatArray a = new FloatArray(m * k);
        FloatArray b = new FloatArray(k * n);
        FloatArray c = new FloatArray(m * n);
        FloatArray ref = new FloatArray(m * n);

        Random rnd = new Random(7);
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, rnd.nextFloat() - 0.5f);
        }
        for (int i = 0; i < b.getSize(); i++) {
            b.set(i, rnd.nextFloat() - 0.5f);
        }
        cpuReference(a, b, ref, m, n, k, k > THRESHOLD ? 1.0f : 2.0f);

        IntArray dims = IntArray.fromElements(n, k);

        KernelContext ctx = new KernelContext();
        int numTiles = (m / TILE) * (n / TILE);
        WorkerGrid1D grid = new WorkerGrid1D(numTiles * SIMD_GROUP);
        grid.setLocalWork(SIMD_GROUP, 1, 1);
        GridScheduler scheduler = new GridScheduler("s0.t0", grid);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, dims) //
                .task("t0", TestSimdgroupMatrixBranchMerge::gemmBranches, ctx, a, b, c, dims) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        float tol = 2e-3f * k;
        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(ref.get(i), c.get(i), tol, "mismatch at index " + i);
        }
    }

    @Test
    public void testIfBranch() throws TornadoExecutionPlanException {
        runAndCheck(16, 16, 128);
    }

    @Test
    public void testElseBranch() throws TornadoExecutionPlanException {
        runAndCheck(16, 16, 64);
    }
}
