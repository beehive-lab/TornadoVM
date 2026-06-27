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

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Unit tests for {@link KernelContext#matrixMultiply8x8}, which lowers to Apple's
 * {@code simdgroup_float8x8} hardware matrix-multiply instructions.
 *
 * <p>Metal-only: simdgroup_matrix has no equivalent in the OpenCL, CUDA or SPIR-V
 * backends, so the tests are skipped there.
 *
 * <p>How to run:
 * <code>
 * tornado-test --threadInfo --printKernel -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestSimdgroupMatrix
 * </code>
 */
public class TestSimdgroupMatrix extends TornadoTestBase {

    private static final int TILE = 8;
    private static final int SIMD_GROUP = 32; // Apple Silicon SIMD width

    /** One SIMD group per 8x8 output tile; C = A x B over the full K dimension. */
    private static void gemm(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c, int m, int n, int k) {
        int tilesPerRow = n / TILE;
        int tileIdx = ctx.groupIdx;
        int tileRow = tileIdx / tilesPerRow;
        int tileCol = tileIdx % tilesPerRow;

        int aBase = tileRow * TILE * k;
        int bBase = tileCol * TILE;
        int cBase = tileRow * TILE * n + tileCol * TILE;

        ctx.matrixMultiply8x8(a, aBase, k, b, bBase, n, c, cBase, n, k);
    }

    private static void cpuReference(FloatArray a, FloatArray b, FloatArray c, int m, int n, int k) {
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float acc = 0.0f;
                for (int p = 0; p < k; p++) {
                    acc += a.get(i * k + p) * b.get(p * n + j);
                }
                c.set(i * n + j, acc);
            }
        }
    }

    private void runAndCheck(int m, int n, int k) throws TornadoExecutionPlanException {
        // simdgroup_matrix is Metal-only.
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

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
        cpuReference(a, b, ref, m, n, k);

        KernelContext ctx = new KernelContext();
        int numTiles = (m / TILE) * (n / TILE);
        WorkerGrid1D grid = new WorkerGrid1D(numTiles * SIMD_GROUP);
        grid.setLocalWork(SIMD_GROUP, 1, 1);
        GridScheduler scheduler = new GridScheduler("s0.t0", grid);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestSimdgroupMatrix::gemm, ctx, a, b, c, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        float tol = 1e-3f * k;
        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(ref.get(i), c.get(i), tol);
        }
    }

    @Test
    public void testSingleTile() throws TornadoExecutionPlanException {
        runAndCheck(8, 8, 8);
    }

    @Test
    public void testSingleTileDeepK() throws TornadoExecutionPlanException {
        runAndCheck(8, 8, 256);
    }

    @Test
    public void testMultiTile() throws TornadoExecutionPlanException {
        runAndCheck(64, 64, 64);
    }

    @Test
    public void testRectangular() throws TornadoExecutionPlanException {
        runAndCheck(32, 16, 128);
    }
}
