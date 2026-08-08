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
package uk.ac.manchester.tornado.cublas.tests;

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
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;

/**
 * Row-major matrix-vector multiplication (y = W * x) benchmark: the TornadoVM
 * kernels from {@code tornado-examples} MatrixVectorRowMajor (naive
 * {@code @Parallel} and optimized KernelContext workgroup-per-row reduction)
 * against the equivalent cuBLAS SGEMV library task, all validated against a
 * sequential Java reference.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.MatrixVectorRowMajorWithCuBlas [inputDim] [outputDim] [localWorkGroupSize]
 * </code>
 */
public class MatrixVectorRowMajorWithCuBlas {

    private static final float DELTA = 1e-3f;
    private static final int WARM_UP_ITERATIONS = 140;
    private static final int BENCHMARK_ITERATIONS = 120;
    private static final Random random = new Random(42);
    private static int LOCAL_WORK_GROUP_SIZE = 32;

    private static void fillRandomData(FloatArray array, float min, float max) {
        float range = max - min;
        for (int i = 0; i < array.getSize(); i++) {
            array.set(i, min + random.nextFloat() * range);
        }
    }

    /**
     * Sequential implementation of matrix-vector multiplication.
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
     * Optimized implementation using the KernelContext API with a row-major
     * approach: one workgroup per output row, local-memory reduction.
     */
    public static void matrixVectorGeneric(KernelContext context, FloatArray x, FloatArray hb, FloatArray w, int n, int d, int localWorkGroupSize) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;
        int localSize = localWorkGroupSize;

        if (rowId >= d) {
            return;
        }
        float sum = matrixVectorRowMajorOptimized(context, localSize, x, w, n);

        if (localId == 0) {
            hb.set(rowId, sum);
        }
    }

    public static float matrixVectorRowMajorOptimized(KernelContext context, int localSize, FloatArray x, FloatArray w, int n) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        float[] localSum = context.allocateFloatLocalArray(localSize);

        int rowOffset = rowId * n;

        float partialSum = 0.0f;
        for (int j = localId; j < n; j += localSize) {
            partialSum += w.get(rowOffset + j) * x.get(j);
        }

        localSum[localId] = partialSum;
        context.localBarrier();

        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSum[localId] += localSum[localId + stride];
            }
            context.localBarrier();
        }

        return localSum[0];
    }

    private static void reportResults(String name, ArrayList<Long> timers, int n, int d, double referenceAvg) {
        LongSummaryStatistics stats = timers.stream().mapToLong(Long::longValue).summaryStatistics();
        double avgNanos = stats.getAverage();
        double gflops = (2.0 * n * d) / avgNanos; // FLOP / ns == GFLOP/s
        double gbPerSec = ((long) n * d + n + d) * 4.0 / avgNanos; // bytes / ns == GB/s
        System.out.printf("%-28s : %10.3f ms | %8.2f GFLOP/s | %8.2f GB/s | speedup vs seq: %8.2fx%n", //
                name, avgNanos * 1e-6, gflops, gbPerSec, referenceAvg / avgNanos);
    }

    private static float validate(String name, FloatArray expected, FloatArray actual, int d) {
        float maxError = 0.0f;
        for (int i = 0; i < d; i++) {
            maxError = Math.max(maxError, Math.abs(expected.get(i) - actual.get(i)));
        }
        System.out.printf("%-28s : max error = %.6f -> %s%n", name, maxError, (maxError <= DELTA) ? "OK" : "FAILED");
        return maxError;
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        System.out.println("Matrix-Vector Multiplication: TornadoVM kernels vs cuBLAS SGEMV library task");
        System.out.println("=============================================================================");

        int inputDim = 8192;   // columns (n)
        int outputDim = 2048;  // rows (d)

        if (args.length >= 3) {
            try {
                inputDim = Integer.parseInt(args[0]);
                outputDim = Integer.parseInt(args[1]);
                LOCAL_WORK_GROUP_SIZE = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Error parsing dimensions. Using defaults.");
            }
        } else if (args.length >= 2) {
            inputDim = Integer.parseInt(args[0]);
            outputDim = Integer.parseInt(args[1]);
        }

        System.out.println("Configuration:");
        System.out.println("- Input dimension (columns): " + inputDim);
        System.out.println("- Output dimension (rows): " + outputDim);
        System.out.println("- Local work group size: " + LOCAL_WORK_GROUP_SIZE);
        System.out.println("- Warmup iterations: " + WARM_UP_ITERATIONS);
        System.out.println("- Benchmark iterations: " + BENCHMARK_ITERATIONS);
        System.out.println();

        FloatArray input = new FloatArray(inputDim);
        FloatArray weights = new FloatArray(inputDim * outputDim);
        FloatArray outputSeq = new FloatArray(outputDim);
        FloatArray outputParallel = new FloatArray(outputDim);
        FloatArray outputKernelContext = new FloatArray(outputDim);
        FloatArray outputCuBlas = new FloatArray(outputDim);

        System.out.println("Initializing data...");
        fillRandomData(input, -1.0f, 1.0f);
        fillRandomData(weights, -0.1f, 0.1f);

        ArrayList<Long> sequentialTimers = new ArrayList<>();
        ArrayList<Long> parallelTimers = new ArrayList<>();
        ArrayList<Long> kernelContextTimers = new ArrayList<>();
        ArrayList<Long> cublasTimers = new ArrayList<>();

        // Naive @Parallel kernel
        TaskGraph taskGraphParallel = new TaskGraph("parallel") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weights) //
                .task("t0", MatrixVectorRowMajorWithCuBlas::matrixVectorParallel, input, outputParallel, weights, inputDim, outputDim) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputParallel);
        ImmutableTaskGraph immutableParallel = taskGraphParallel.snapshot();

        // Optimized KernelContext kernel: one workgroup per row
        WorkerGrid1D worker = new WorkerGrid1D(outputDim * LOCAL_WORK_GROUP_SIZE);
        worker.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);
        GridScheduler scheduler = new GridScheduler("kernelcontext.t0", worker);

        TaskGraph taskGraphKernelContext = new TaskGraph("kernelcontext") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weights) //
                .task("t0", MatrixVectorRowMajorWithCuBlas::matrixVectorGeneric, new KernelContext(), input, outputKernelContext, weights, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputKernelContext);
        ImmutableTaskGraph immutableKernelContext = taskGraphKernelContext.snapshot();

        // Equivalent cuBLAS SGEMV library task. Row-major W (d x n) is the
        // column-major matrix (n x d) with lda = n, so y = W * x is
        // sgemv(OP_T, n, d, alpha, W, n, x, 1, beta, y, 1).
        TaskGraph taskGraphCuBlas = new TaskGraph("cublas") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weights) //
                .libraryTask("sgemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), //
                        inputDim, outputDim, //
                        1.0f, weights, inputDim, input, //
                        1, 0.0f, outputCuBlas, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputCuBlas);
        ImmutableTaskGraph immutableCuBlas = taskGraphCuBlas.snapshot();

        // Sequential reference
        System.out.println("Warming up sequential implementation...");
        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            matrixVectorSequential(input, outputSeq, weights, inputDim, outputDim);
        }
        System.out.println("Benchmarking sequential implementation...");
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            matrixVectorSequential(input, outputSeq, weights, inputDim, outputDim);
            long end = System.nanoTime();
            sequentialTimers.add(end - start);
        }

        try (TornadoExecutionPlan planParallel = new TornadoExecutionPlan(immutableParallel); //
                TornadoExecutionPlan planKernelContext = new TornadoExecutionPlan(immutableKernelContext); //
                TornadoExecutionPlan planCuBlas = new TornadoExecutionPlan(immutableCuBlas)) {

            System.out.println("Warming up TornadoVM implementations...");
            for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
                planParallel.execute();
            }
            planKernelContext.withGridScheduler(scheduler);
            for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
                planKernelContext.withGridScheduler(scheduler).execute();
            }
            System.out.println("Warming up cuBLAS library task...");
            for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
                planCuBlas.execute();
            }

            System.out.println("Benchmarking @Parallel kernel...");
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                planParallel.execute();
                long end = System.nanoTime();
                parallelTimers.add(end - start);
            }

            System.out.println("Benchmarking KernelContext kernel...");
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                planKernelContext.withGridScheduler(scheduler).execute();
                long end = System.nanoTime();
                kernelContextTimers.add(end - start);
            }

            System.out.println("Benchmarking cuBLAS SGEMV library task...");
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                planCuBlas.execute();
                long end = System.nanoTime();
                cublasTimers.add(end - start);
            }
        }

        System.out.println();
        System.out.println("Validation (vs sequential Java):");
        float e1 = validate("@Parallel kernel", outputSeq, outputParallel, outputDim);
        float e2 = validate("KernelContext kernel", outputSeq, outputKernelContext, outputDim);
        float e3 = validate("cuBLAS SGEMV library task", outputSeq, outputCuBlas, outputDim);

        double seqAvg = sequentialTimers.stream().mapToLong(Long::longValue).summaryStatistics().getAverage();

        System.out.println();
        System.out.println("Results (average over " + BENCHMARK_ITERATIONS + " iterations, end-to-end execute):");
        reportResults("Sequential Java", sequentialTimers, inputDim, outputDim, seqAvg);
        reportResults("@Parallel kernel", parallelTimers, inputDim, outputDim, seqAvg);
        reportResults("KernelContext kernel", kernelContextTimers, inputDim, outputDim, seqAvg);
        reportResults("cuBLAS SGEMV library task", cublasTimers, inputDim, outputDim, seqAvg);

        double kernelContextAvg = kernelContextTimers.stream().mapToLong(Long::longValue).summaryStatistics().getAverage();
        double cublasAvg = cublasTimers.stream().mapToLong(Long::longValue).summaryStatistics().getAverage();
        System.out.printf("%ncuBLAS vs best TornadoVM kernel (KernelContext): %.2fx%n", kernelContextAvg / cublasAvg);

        boolean allValid = (e1 <= DELTA) && (e2 <= DELTA) && (e3 <= DELTA);
        System.out.println(allValid ? "Result is correct" : "Result is wrong");
    }
}
