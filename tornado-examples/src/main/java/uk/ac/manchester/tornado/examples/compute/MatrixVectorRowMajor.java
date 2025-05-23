/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.compute;

import java.util.ArrayList;
import java.util.LongSummaryStatistics;
import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * </p>
 * <code>
 * $ tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixVectorRowMajor
 * </code>
 *
 */
public class MatrixVectorRowMajor {

    private static final float DELTA = 1e-4f;
    private static final int WARM_UP_ITERATIONS = 140;
    private static final int BENCHMARK_ITERATIONS = 120;
    private static final int LOCAL_WORK_GROUP_SIZE = 128; // Number of threads per workgroup
    private static final Random random = new Random(42); // Fixed seed for reproducibility

    /**
     * Fills an array with random data in the specified range
     */
    private static void fillRandomData(FloatArray array, float min, float max) {
        float range = max - min;
        for (int i = 0; i < array.getSize(); i++) {
            array.set(i, min + random.nextFloat() * range);
        }
    }

    /**
     * Sequential implementation of matrix-vector multiplication
     */
    public static void matrixVectorSequential(FloatArray x, FloatArray hb, FloatArray w, int n, int d) {
        for (int i = 0; i < d; i++) {
            float sum = 0.0f;
            int rowOffset = i * n;
            for (int j = 0; j < n; j++) {
                sum += w.get(rowOffset + j) * x.get(j);
            }
            hb.set(i, sum);
        }
    }

    public static void matrixVectorParallel(FloatArray x, FloatArray hb, FloatArray w, int n, int d) {
        for (@Parallel int i = 0; i < d; i++) {
            float sum = 0.0f;
            int rowOffset = i * n;
            for (int j = 0; j < n; j++) {
                sum += w.get(rowOffset + j) * x.get(j);
            }
            hb.set(i, sum);
        }
    }

    /**
     * Optimized implementation using KernelContext API with a row major approach
     */
    public static void matrixVectorGeneric(KernelContext context, FloatArray x, FloatArray hb, FloatArray w, int n, int d, int localWorkGroupSize) {
        // One row per workgroup (not per thread)
        int rowId = context.groupIdx;
        int localId = context.localIdx;
        int localSize = localWorkGroupSize;

        // Early exit if this workgroup is beyond our output dimension
        if (rowId >= d) {
            return;
        }
        float sum = matrixVectorRowMajorOptimized(context, localSize, x, w, n);

        // Thread 0 in each workgroup writes the final result
        if (localId == 0) {
            hb.set(rowId, sum);
        }
    }

    /**
     * Helper method to compute the dot product for a single row in an optimized way
     */
    public static float matrixVectorRowMajorOptimized(KernelContext context, int localSize, FloatArray x, FloatArray w, int n) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        // Allocate local memory for reduction
        float[] localSum = context.allocateFloatLocalArray(localSize);

        int rowOffset = rowId * n;

        // Each thread calculates partial dot product
        float partialSum = 0.0f;
        for (int j = localId; j < n; j += localSize) {
            int matrixIdx = rowOffset + j;
            partialSum += w.get(matrixIdx) * x.get(j);
        }

        // Store partial sum in local memory
        localSum[localId] = partialSum;
        context.localBarrier();

        // Parallel reduction within workgroup
        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSum[localId] += localSum[localId + stride];
            }
            context.localBarrier();
        }

        return localSum[0];
    }

    /**
     * Runs the benchmark for different matrix sizes and reports results
     */
    public static void main(String[] args) {
        System.out.println("Matrix-Vector Multiplication Benchmark");
        System.out.println("======================================");

        // Default parameters
        int inputDim = 8192;   // Default input dimension (columns)
        int outputDim = 2048; // Default output dimension (rows)

        // Parse command line arguments if provided
        if (args.length >= 3) {
            try {
                inputDim = Integer.parseInt(args[0]);
                outputDim = Integer.parseInt(args[1]);
                int localWorkGroupSize = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Error parsing dimensions. Using defaults.");
            }
        }

        System.out.println("Configuration:");
        System.out.println("- Input dimension (columns): " + inputDim);
        System.out.println("- Output dimension (rows): " + outputDim);
        System.out.println("- Local work group size: " + LOCAL_WORK_GROUP_SIZE);
        System.out.println("- Warmup iterations: " + WARM_UP_ITERATIONS);
        System.out.println("- Benchmark iterations: " + BENCHMARK_ITERATIONS);
        System.out.println();

        // Create data arrays
        FloatArray input = new FloatArray(inputDim);
        FloatArray weights = new FloatArray(inputDim * outputDim);
        FloatArray outputParallel = new FloatArray(outputDim);
        FloatArray outputPureTornado = new FloatArray(outputDim);
        FloatArray outputSeq = new FloatArray(outputDim);

        // Initialize data
        System.out.println("Initializing data...");
        fillRandomData(input, -1.0f, 1.0f);
        fillRandomData(weights, -0.1f, 0.1f);

        // Arrays for timing measurements
        ArrayList<Long> sequentialTimers = new ArrayList<>();
        ArrayList<Long> kernelContextTimers = new ArrayList<>();
        ArrayList<Long> parallelTimers = new ArrayList<>();

        // Set up TornadoVM execution
        System.out.println("Setting up TornadoVM execution...");
        WorkerGrid1D worker = new WorkerGrid1D(outputDim * LOCAL_WORK_GROUP_SIZE);
        GridScheduler scheduler = new GridScheduler("s0.t0", worker);
        worker.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weights).task("t0", MatrixVectorRowMajor::matrixVectorGeneric, new KernelContext(), input,
                outputParallel, weights, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE).transferToHost(DataTransferMode.EVERY_EXECUTION, outputParallel);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        TaskGraph taskGraphPure = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weights) //
                .task("t0", MatrixVectorRowMajor::matrixVectorParallel, input, outputPureTornado, weights, inputDim, outputDim) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputPureTornado); //

        ImmutableTaskGraph immutableTaskGraphParallel = taskGraphPure.snapshot();

        // Warm-up sequential version
        System.out.println("Warming up sequential implementation...");
        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            matrixVectorSequential(input, outputSeq, weights, inputDim, outputDim);
        }

        // Benchmark sequential version
        System.out.println("Benchmarking sequential implementation...");
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            matrixVectorSequential(input, outputSeq, weights, inputDim, outputDim);
            long end = System.nanoTime();
            sequentialTimers.add(end - start);
        }

        // TornadoVM execution with benchmark
        System.out.println("Benchmarking TornadoVM implementation...");
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        TornadoExecutionPlan executionPlan2 = new TornadoExecutionPlan(immutableTaskGraphParallel);

        executionPlan.withGridScheduler(scheduler);

        // Warm-up parallel version
        System.out.println("Warming up parallel implementation...");
        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            executionPlan2.execute();
        }

        // Benchmark parallel version
        System.out.println("Benchmarking parallel implementation...");
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            executionPlan.withGridScheduler(scheduler).execute();
            long end = System.nanoTime();
            kernelContextTimers.add(end - start);
        }

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            executionPlan2.execute();
            long end = System.nanoTime();
            parallelTimers.add(end - start);
        }

        // Validate results
        System.out.println("Validating results...");
        boolean isValid = true;
        float maxError = 0.0f;
        float maxError2 = 0.0f;

        for (int i = 0; i < outputDim; i++) {
            float error = Math.abs(outputSeq.get(i) - outputParallel.get(i));
            maxError = Math.max(maxError, error);

            float error2 = Math.abs(outputSeq.get(i) - outputPureTornado.get(i));
            maxError2 = Math.max(maxError2, error2);

            if (error > DELTA) {
                System.out.printf("[KernelContext] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n", i, outputSeq.get(i), outputParallel.get(i), error);
                isValid = false;
            }

            if (error2 > DELTA) {
                System.out.printf("[@Parallel] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n", i, outputSeq.get(i), outputPureTornado.get(i), error);
                isValid = false;
            }
        }

        if (isValid) {
            System.out.println("Validation PASSED ✓");
        } else {
            System.out.println("[KernelContext] Maximum error: " + maxError);

            System.out.println("[@Parallel] Maximum error: " + maxError2);
        }

        // Compute and report performance statistics
        LongSummaryStatistics statsSeq = sequentialTimers.stream().mapToLong(Long::longValue).summaryStatistics();
        LongSummaryStatistics statsKernelContext = kernelContextTimers.stream().mapToLong(Long::longValue).summaryStatistics();
        LongSummaryStatistics statsParallel = parallelTimers.stream().mapToLong(Long::longValue).summaryStatistics();

        // Calculate GFLOP/s (2*inputDim operations per output element)
        long flopsPerRow = 2L * inputDim; // multiply + add for each element
        long totalFlops = flopsPerRow * outputDim;
        double seqGFlops = (totalFlops * 1e-9) / (statsSeq.getAverage() * 1e-9);
        double kernelContextGFlops = (totalFlops * 1e-9) / (statsKernelContext.getAverage() * 1e-9);
        double parallelGFlops = (totalFlops * 1e-9) / (statsParallel.getAverage() * 1e-9);

        System.out.println("\nPerformance Results:");
        System.out.println("====================");
        System.out.printf("Matrix size: %d x %d\n", outputDim, inputDim);

        System.out.println("Sequential Implementation:");
        System.out.printf("  Average time: %.3f ms\n", statsSeq.getAverage() / 1_000_000);
        System.out.printf("  Min time: %.3f ms\n", (double) statsSeq.getMin() / 1_000_000);
        System.out.printf("  Max time: %.3f ms\n", (double) statsSeq.getMax() / 1_000_000);
        System.out.printf("  Performance: %.2f GFLOP/s\n", seqGFlops);

        System.out.println("Parallel Implementation (TornadoVM):");
        System.out.printf("  Average time: %.3f ms\n", statsKernelContext.getAverage() / 1_000_000);
        System.out.printf("  Min time: %.3f ms\n", (double) statsKernelContext.getMin() / 1_000_000);
        System.out.printf("  Max time: %.3f ms\n", (double) statsKernelContext.getMax() / 1_000_000);
        System.out.printf("  Performance: %.2f GFLOP/s\n", kernelContextGFlops);

        System.out.println("Pure TornadoVM @Parallel Implementation (TornadoVM):");
        System.out.printf("  Average time: %.3f ms\n", statsParallel.getAverage() / 1_000_000);
        System.out.printf("  Min time: %.3f ms\n", (double) statsParallel.getMin() / 1_000_000);
        System.out.printf("  Max time: %.3f ms\n", (double) statsParallel.getMax() / 1_000_000);
        System.out.printf("  Performance: %.2f GFLOP/s\n", parallelGFlops);

        double speedup = statsSeq.getAverage() / statsKernelContext.getAverage();
        System.out.printf("\nSpeedup: KernelContext vs Java %.2fx\n", speedup);

        double speedup2 = statsSeq.getAverage() / statsParallel.getAverage();
        System.out.printf("\nSpeedup: @Parallel vs Java %.2fx\n", speedup2);

        double speedup3 = statsParallel.getAverage() / statsKernelContext.getAverage();
        System.out.printf("\nSpeedup: KernelContext vs @Parallel %.2fx\n", speedup3);
    }
}
