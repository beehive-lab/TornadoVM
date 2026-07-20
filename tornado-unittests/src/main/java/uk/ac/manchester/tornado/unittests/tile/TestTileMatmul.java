/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * jTile milestone M2 - tile-level tensor-core GEMM via {@link Tile#matmul}. CUDA backend only
 * (uses NVIDIA mma.sync tensor cores). Cross-checked against a CPU fp32 reference.
 *
 * <p>
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileMatmul
 * </code>
 * </p>
 */
public class TestTileMatmul extends TornadoTestBase {

    private static final int WMMA_M = 16;
    private static final int WMMA_N = 16;
    private static final int WARP_SIZE = 32;

    private static HalfFloatArray randomFP16(int size, long seed) {
        Random random = new Random(seed);
        HalfFloatArray array = new HalfFloatArray(size);
        for (int i = 0; i < size; i++) {
            array.set(i, new HalfFloat(random.nextFloat()));
        }
        return array;
    }

    private static void gemmReference(HalfFloatArray a, HalfFloatArray b, FloatArray ref, int dimM, int dimN, int dimK) {
        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                float sum = 0.0f;
                for (int k = 0; k < dimK; k++) {
                    sum += a.get(i * dimK + k).getFloat32() * b.get(k * dimN + j).getFloat32();
                }
                ref.set(i * dimN + j, sum);
            }
        }
    }

    private void runTileMatmul(int dimM, int dimN, int dimK) throws TornadoExecutionPlanException {
        // Tensor-core MMA is a CUDA-backend feature only.
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);

        HalfFloatArray a = randomFP16(dimM * dimK, 1);
        HalfFloatArray b = randomFP16(dimK * dimN, 2);
        FloatArray c = new FloatArray(dimM * dimN);

        FloatArray ref = new FloatArray(dimM * dimN);
        gemmReference(a, b, ref, dimM, dimN, dimK);

        int numWarps = (dimM / WMMA_M) * (dimN / WMMA_N);
        int globalSize = numWarps * WARP_SIZE;

        WorkerGrid1D worker = new WorkerGrid1D(globalSize);
        worker.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.gemm", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("gemm", Tile::matmul, context, a, b, c, dimM, dimN, dimK) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        float tol = 0.02f;
        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                int idx = i * dimN + j;
                assertEquals(String.format("C[%d][%d]", i, j), ref.get(idx), c.get(idx), tol);
            }
        }
    }

    @Test
    public void testTileMatmul16() throws TornadoExecutionPlanException {
        runTileMatmul(16, 16, 16);
    }

    @Test
    public void testTileMatmul64() throws TornadoExecutionPlanException {
        runTileMatmul(64, 64, 64);
    }

    @Test
    public void testTileMatmul128x64() throws TornadoExecutionPlanException {
        runTileMatmul(128, 64, 32);
    }
}
