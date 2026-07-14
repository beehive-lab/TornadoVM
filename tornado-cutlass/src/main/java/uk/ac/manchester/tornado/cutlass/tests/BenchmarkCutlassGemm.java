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
package uk.ac.manchester.tornado.cutlass.tests;

import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.cutlass.Cutlass;

/**
 * Benchmark: square row-major GEMM {@code C = A * B} comparing the TornadoVM
 * JIT-generated kernels (naive {@code @Parallel} and a tiled local-memory
 * KernelContext kernel) against CUTLASS FP32 (SIMT) and FP16 (tensor-core)
 * library tasks on the same device buffers, plus a fused MLP block
 * ({@code gelu(A*B + bias)}) run fused in one CUTLASS kernel vs unfused
 * (CUTLASS GEMM followed by a JIT bias+GELU task).
 *
 * <p>Inputs are transferred once (FIRST_EXECUTION) and outputs fetched
 * UNDER_DEMAND after the timed loop, so the measured time is the GEMM itself.</p>
 *
 * <p>How to run?</p>
 * <code>
 * tornado -m tornado.cutlass/uk.ac.manchester.tornado.cutlass.tests.BenchmarkCutlassGemm [size] [iterations]
 * </code>
 */
public class BenchmarkCutlassGemm {

    private static final int WARMUP_ITERATIONS = 20;
    private static final int TS = 32;

    public static void matrixMultiplication(FloatArray a, FloatArray b, FloatArray c, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += a.get(i * size + k) * b.get(k * size + j);
                }
                c.set(i * size + j, sum);
            }
        }
    }

    public static void matrixMultiplicationTiled(KernelContext context, FloatArray a, FloatArray b, FloatArray c, int size) {
        int localCol = context.localIdx;
        int localRow = context.localIdy;
        int globalCol = TS * context.groupIdx + localCol;
        int globalRow = TS * context.groupIdy + localRow;

        float[] aSub = context.allocateFloatLocalArray(TS * TS);
        float[] bSub = context.allocateFloatLocalArray(TS * TS);

        float sum = 0.0f;
        int numTiles = size / TS;
        for (int t = 0; t < numTiles; t++) {
            int tiledCol = TS * t + localCol;
            int tiledRow = TS * t + localRow;
            aSub[localRow * TS + localCol] = a.get(globalRow * size + tiledCol);
            bSub[localRow * TS + localCol] = b.get(tiledRow * size + globalCol);
            context.localBarrier();
            for (int k = 0; k < TS; k++) {
                sum += aSub[localRow * TS + k] * bSub[k * TS + localCol];
            }
            context.localBarrier();
        }
        c.set(globalRow * size + globalCol, sum);
    }

    /** JIT reference for the unfused fused-MLP path: D = gelu(gemm + bias). */
    public static void biasGelu(HalfFloatArray gemm, HalfFloatArray bias, HalfFloatArray out, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float x = gemm.get(i * size + j).getFloat32() + bias.get(j).getFloat32();
                float g = 0.5f * x * (1.0f + TornadoMath.tanh(0.7978845608f * (x + 0.044715f * x * x * x)));
                out.set(i * size + j, new HalfFloat(g));
            }
        }
    }

    private static double benchmark(TornadoExecutionPlan plan, GridScheduler grid, int iterations) {
        if (grid != null) {
            plan.withGridScheduler(grid);
        }
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            plan.execute();
        }
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            plan.execute();
        }
        long end = System.nanoTime();
        return (end - start) / (double) iterations;
    }

    private static void report(String name, double nanos, double gflop) {
        double ms = nanos * 1e-6;
        System.out.printf("  %-26s %8.3f ms   %8.2f GFLOP/s%n", name, ms, gflop / (nanos * 1e-9));
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        final int size = (args.length > 0) ? Integer.parseInt(args[0]) : 1024;
        final int iterations = (args.length > 1) ? Integer.parseInt(args[1]) : 100;
        final double gflop = 2.0 * size * size * size * 1e-9;

        if (size % TS != 0) {
            throw new IllegalArgumentException("Size must be a multiple of the tile size (" + TS + ")");
        }

        System.out.println("CUTLASS GEMM benchmark: " + size + "x" + size + ", " + iterations + " iterations (+" + WARMUP_ITERATIONS + " warm-up)");

        FloatArray a = new FloatArray(size * size);
        FloatArray b = new FloatArray(size * size);
        FloatArray cJit = new FloatArray(size * size);
        FloatArray cTiled = new FloatArray(size * size);
        FloatArray cCutlass = new FloatArray(size * size);
        HalfFloatArray aH = new HalfFloatArray(size * size);
        HalfFloatArray bH = new HalfFloatArray(size * size);
        HalfFloatArray dH = new HalfFloatArray(size * size);
        HalfFloatArray bias = new HalfFloatArray(size);
        HalfFloatArray fused = new HalfFloatArray(size * size);
        HalfFloatArray unfusedGemm = new HalfFloatArray(size * size);
        HalfFloatArray unfusedOut = new HalfFloatArray(size * size);

        Random random = new Random(42);
        for (int i = 0; i < size * size; i++) {
            a.set(i, random.nextFloat());
            b.set(i, random.nextFloat());
            aH.set(i, new HalfFloat(a.get(i)));
            bH.set(i, new HalfFloat(b.get(i)));
        }
        for (int i = 0; i < size; i++) {
            bias.set(i, new HalfFloat(random.nextFloat()));
        }

        TaskGraph jitGraph = new TaskGraph("jit") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("mxm", BenchmarkCutlassGemm::matrixMultiplication, a, b, cJit, size) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, cJit);

        WorkerGrid2D workerGrid = new WorkerGrid2D(size, size);
        workerGrid.setLocalWork(TS, TS, 1);
        GridScheduler gridScheduler = new GridScheduler("tiled.mxm", workerGrid);
        TaskGraph tiledGraph = new TaskGraph("tiled") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("mxm", BenchmarkCutlassGemm::matrixMultiplicationTiled, new KernelContext(), a, b, cTiled, size) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, cTiled);

        TaskGraph cutlassGraph = new TaskGraph("cutlass") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .libraryTask("sgemm", Cutlass::cutlassSgemm, size, size, size, 1.0f, a, b, 0.0f, cCutlass) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, cCutlass);

        TaskGraph cutlassHalfGraph = new TaskGraph("cutlassFP16") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aH, bH) //
                .libraryTask("hgemm", Cutlass::cutlassHgemm, size, size, size, 1.0f, aH, bH, 0.0f, dH) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, dH);

        TaskGraph fusedGraph = new TaskGraph("fused") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aH, bH, bias) //
                .libraryTask("gemmBiasGelu", Cutlass::cutlassGemmBiasGelu, size, size, size, aH, bH, bias, fused) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, fused);

        TaskGraph unfusedGraph = new TaskGraph("unfused") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aH, bH, bias) //
                .libraryTask("hgemm", Cutlass::cutlassHgemm, size, size, size, 1.0f, aH, bH, 0.0f, unfusedGemm) //
                .task("biasGelu", BenchmarkCutlassGemm::biasGelu, unfusedGemm, bias, unfusedOut, size) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, unfusedOut);

        System.out.println("GEMM (C = A * B):");
        try (TornadoExecutionPlan p = new TornadoExecutionPlan(jitGraph.snapshot())) {
            report("JIT @Parallel", benchmark(p, null, iterations), gflop);
        }
        try (TornadoExecutionPlan p = new TornadoExecutionPlan(tiledGraph.snapshot())) {
            report("JIT tiled KernelContext", benchmark(p, gridScheduler, iterations), gflop);
        }
        try (TornadoExecutionPlan p = new TornadoExecutionPlan(cutlassGraph.snapshot())) {
            report("CUTLASS FP32 SIMT", benchmark(p, null, iterations), gflop);
        }
        try (TornadoExecutionPlan p = new TornadoExecutionPlan(cutlassHalfGraph.snapshot())) {
            report("CUTLASS FP16 tensor-core", benchmark(p, null, iterations), gflop);
        }

        System.out.println("Fused MLP block (gelu(A * B + bias)):");
        try (TornadoExecutionPlan p = new TornadoExecutionPlan(fusedGraph.snapshot())) {
            report("CUTLASS fused GEMM+bias+GELU", benchmark(p, null, iterations), gflop);
        }
        try (TornadoExecutionPlan p = new TornadoExecutionPlan(unfusedGraph.snapshot())) {
            report("CUTLASS GEMM + JIT bias+GELU", benchmark(p, null, iterations), gflop);
        }
    }
}
