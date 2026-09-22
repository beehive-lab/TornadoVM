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

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;

/**
 * Times the mixed JIT + tile + cuBLAS pipeline with and without CUDA Graph capture, and is the
 * intended target for an nsys profile.
 *
 * <p>
 * It lives in tornado-unittests rather than tornado-examples because it needs the cuBLAS module,
 * and tornado-examples is shared by every backend; coupling it to a CUDA-only module for one
 * benchmark would be the wrong trade.
 * </p>
 *
 * <p>
 * Profile the JVM directly rather than the launcher: {@code nsys profile $(which tornado) ...}
 * records a report with no GPU rows, because the Python launcher execs a child JVM that the
 * injection does not follow. Generating an argfile with {@code tornado --printJavaFlags} and
 * profiling {@code java @argfile} avoids that entirely.
 * </p>
 *
 * <pre>
 * tornado --printJavaFlags | sed 's| -m .[*]||' | tr ' ' '\n' | grep . &gt; tornadovm.args
 * nsys profile --trace=cuda,nvtx --cuda-graph-trace=node -o tilechain \
 *     java @tornadovm.args -m tornado.unittests/uk.ac.manchester.tornado.unittests.tile.TileChainBenchmark 2000
 * nsys stats --report cuda_gpu_kern_sum --report cuda_gpu_mem_time_sum tilechain.nsys-rep
 * </pre>
 */
public class TileChainBenchmark {

    private static final int SIZE = 1024;

    private static final int TILE = 256;

    public static void scale(FloatArray in, FloatArray out, float factor) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) * factor);
        }
    }

    public static void tileDouble(TileContext tc, FloatArray in, FloatArray out, int n) {
        PartitionView iv = tc.partition(tc.view(in, n), TILE);
        PartitionView ov = tc.partition(tc.view(out, n), TILE);
        int block = tc.bidX();
        ov.store(tc.add(iv.load(block), iv.load(block)), block);
    }

    public static void bias(FloatArray in, FloatArray out, float amount) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) + amount);
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        final int iterations = args.length > 0 ? Integer.parseInt(args[0]) : 1000;

        FloatArray input = new FloatArray(SIZE);
        FloatArray matrix = new FloatArray(SIZE * SIZE);
        FloatArray scaled = new FloatArray(SIZE);
        FloatArray doubled = new FloatArray(SIZE);
        FloatArray projected = new FloatArray(SIZE);
        FloatArray result = new FloatArray(SIZE);
        java.util.Random random = new java.util.Random(13);
        for (int i = 0; i < SIZE; i++) {
            input.set(i, random.nextFloat());
        }
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrix.set(i, random.nextFloat() - 0.5f);
        }

        WorkerGrid1D tileWorker = new WorkerGrid1D(SIZE / TILE);
        GridScheduler grid = new GridScheduler("chain.tile", tileWorker);

        TaskGraph graph = new TaskGraph("chain") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrix) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("scale", TileChainBenchmark::scale, input, scaled, 3.0f) //
                .task("tile", TileChainBenchmark::tileDouble, new TileContext(), scaled, doubled, SIZE) //
                .libraryTask("gemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, doubled, 1, 0.0f, projected, 1) //
                .task("bias", TileChainBenchmark::bias, projected, result, 0.25f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        System.out.printf("pipeline: JIT scale -> tile double -> cublasSgemv -> JIT bias, n=%d, iterations=%d%n", SIZE, iterations);
        double withoutGraph = time(graph, grid, iterations, false);
        double withGraph = time(graph, grid, iterations, true);

        System.out.printf("  without CUDA Graph : %8.3f ms total, %7.4f ms/iteration%n", withoutGraph, withoutGraph / iterations);
        System.out.printf("  with    CUDA Graph : %8.3f ms total, %7.4f ms/iteration%n", withGraph, withGraph / iterations);
        System.out.printf("  ratio              : %6.2fx%n", withoutGraph / withGraph);

        // Correctness is independent of how it was launched.
        for (int i = 0; i < SIZE; i++) {
            float expected = 0.0f;
            for (int j = 0; j < SIZE; j++) {
                expected += matrix.get(i * SIZE + j) * (2.0f * 3.0f * input.get(j));
            }
            if (Math.abs(expected + 0.25f - result.get(i)) > 0.2f) {
                System.out.printf("MISMATCH at %d: %f vs %f%n", i, expected + 0.25f, result.get(i));
                return;
            }
        }
        System.out.println("  result verified");
    }

    private static double time(TaskGraph graph, GridScheduler grid, int iterations, boolean cudaGraph) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            TornadoExecutionPlan configured = cudaGraph
                    ? plan.withGridScheduler(grid).withCUDAGraph()
                    : plan.withGridScheduler(grid);
            // Warm up: JIT, nvcc for the tile kernel, and the capture itself.
            for (int i = 0; i < 20; i++) {
                configured.execute();
            }
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                configured.execute();
            }
            return (System.nanoTime() - start) / 1.0e6;
        }
    }
}
