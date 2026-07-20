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
package uk.ac.manchester.tornado.examples.tile;

import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

/**
 * jTile tensor-core GEMM example (CUDA backend). Computes {@code C = A * B} in fp16 inputs /
 * fp32 accumulate with a single tile-level {@link Tile#matmul} call, which lowers to NVIDIA
 * mma.sync tensor-core instructions.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileMatrixMultiply
 * </code>
 */
public class TileMatrixMultiply {

    private static final int WMMA_M = 16;
    private static final int WMMA_N = 16;
    private static final int WARP_SIZE = 32;

    public static void main(String[] args) throws TornadoExecutionPlanException {
        final int m = 512;
        final int n = 512;
        final int k = 512;

        Random random = new Random(42);
        HalfFloatArray a = new HalfFloatArray(m * k);
        HalfFloatArray b = new HalfFloatArray(k * n);
        FloatArray c = new FloatArray(m * n);
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, new HalfFloat(random.nextFloat()));
        }
        for (int i = 0; i < b.getSize(); i++) {
            b.set(i, new HalfFloat(random.nextFloat()));
        }

        int numWarps = (m / WMMA_M) * (n / WMMA_N);
        WorkerGrid1D worker = new WorkerGrid1D(numWarps * WARP_SIZE);
        worker.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.gemm", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("gemm", Tile::matmul, context, a, b, c, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            long start = System.nanoTime();
            executionPlan.withGridScheduler(gridScheduler).execute();
            long elapsed = System.nanoTime() - start;

            // Verify a single element against the CPU reference.
            int row = 3;
            int col = 7;
            float expected = 0.0f;
            for (int kk = 0; kk < k; kk++) {
                expected += a.get(row * k + kk).getFloat32() * b.get(kk * n + col).getFloat32();
            }
            float got = c.get(row * n + col);
            boolean ok = Math.abs(expected - got) < 0.05f * k;

            System.out.printf("jTile GEMM %dx%dx%d tensor-core: C[%d][%d]=%.3f expected=%.3f -> %s (%.3f ms)%n", m, n, k, row, col, got, expected, ok ? "PASS" : "FAIL", elapsed / 1.0e6);
        }
    }
}
