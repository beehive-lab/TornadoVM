package uk.ac.manchester.tornado.examples.matrices;

import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Matrix2DFloat;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class MatrixMul1D {

    public static final int WARMUP_ITERATIONS = 15;
    public static final int EXECUTE_ITERATIONS = 100;
    private static final boolean CHECK_RESULT = true;
    private static final float DELTA = 0.01f;

    private static void matrixMultiplication(final float[] A, final float[] B, final float[] C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A[(i * size) + k] * B[(k * size) + j];
                }
                C[(i * size) + j] = sum;
            }
        }
    }

    public static void matrixMultiplicationNewApi(TornadoVMContext context, final float[] A, final float[] B, final float[] C, final int size) {
        // Thread identifiers
        int globalRow = context.threadIdx; // Row ID of C (0..M)
        int globalCol = context.threadIdy; // Col ID of C (0..N)

        // Compute a single element (loop over K)
        float sum = 0.0f;
        for (int k = 0; k < size; k++) {
            sum += A[globalRow] * B[globalCol];
        }

        // Store the result
        C[globalRow + globalCol] = sum;
    }

    public static void main(String[] args) throws Exception {
        int N = 512;
        if (args.length == 1) {
            N = Integer.parseInt(args[0]);
        }

        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] matrixCSeq = new float[N * N];
        float[] matrixCCUDA = new float[N * N];
        float[] matrixCOCL = new float[N * N];
        float[] matrixCOCLNewApi = new float[N * N];

        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = 2.5f;
            matrixB[idx] = 3.5f;
        });

        TaskSchedule scheduleCUDA = new TaskSchedule("s0").task("t0", MatrixMul1D::matrixMultiplication, matrixA, matrixB, matrixCCUDA, N).streamOut(matrixCCUDA);

        TornadoDriver cudaDriver = TornadoRuntime.getTornadoRuntime().getDriver(0);
        TornadoDevice cudaDevice = cudaDriver.getDevice(0);
        scheduleCUDA.mapAllTo(cudaDevice);

        // Warm up CUDA
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            scheduleCUDA.execute();
        }

        // Time CUDA
        long start,stop;
        long[] execTimesCUDA = new long[EXECUTE_ITERATIONS];
        for (int i = 0; i < execTimesCUDA.length; i++) {
            start = System.currentTimeMillis();
            scheduleCUDA.execute();
            stop = System.currentTimeMillis();
            execTimesCUDA[i] = stop - start;
        }

        OptionalDouble avgCudaOptional = Arrays.stream(execTimesCUDA).average();
        double averageCUDA;
        if (avgCudaOptional.isPresent())
            averageCUDA = avgCudaOptional.getAsDouble();
        else
            throw new Exception("Could not get average execution time");

        TaskSchedule scheduleOCL = new TaskSchedule("s1").task("t0", MatrixMul1D::matrixMultiplication, matrixA, matrixB, matrixCOCL, N).streamOut(matrixCOCL);

        // Get the same device but running the OCL backend
        TornadoDriver oclDriver = TornadoRuntime.getTornadoRuntime().getDriver(1);
        TornadoDevice oclDevice = null;
        for (int i = 0; i < oclDriver.getDeviceCount(); i++) {
            TornadoDevice device = oclDriver.getDevice(i);
            if (device.getDevice().getDeviceName().equalsIgnoreCase(cudaDevice.getDevice().getDeviceName())) {
                oclDevice = device;
            }
        }
        if (oclDevice == null) {
            System.err.println("There is no device with both OpenCL and CUDA-PTX support");
            System.exit(1);
        }
        scheduleOCL.mapAllTo(oclDevice);

        // Warm up OpenCL
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            scheduleOCL.execute();
        }

        // Time OpenCL
        long[] execTimesOCL = new long[EXECUTE_ITERATIONS];
        for (int i = 0; i < execTimesOCL.length; i++) {
            start = System.currentTimeMillis();
            scheduleOCL.execute();
            stop = System.currentTimeMillis();
            execTimesOCL[i] = stop - start;
        }

        OptionalDouble avgOpenCLOptional = Arrays.stream(execTimesOCL).average();
        double averageOpenCL;
        if (avgOpenCLOptional.isPresent())
            averageOpenCL = avgOpenCLOptional.getAsDouble();
        else
            throw new Exception("Could not get average execution time");

        // Time New API OpenCL
        WorkerGrid worker = new WorkerGrid1D(N);
        GridTask gridTask = new GridTask();
        gridTask.set("ocl_new_api.t0", worker);
        TornadoVMContext context = new TornadoVMContext(worker);

        TaskSchedule oclNewApiTask = new TaskSchedule("ocl_new_api") //
                .task("t0", MatrixMul1D::matrixMultiplicationNewApi, context, matrixA, matrixB, matrixCOCLNewApi, N) //
                .streamOut(matrixCOCLNewApi); //
        // Change the Grid
        worker.setGlobalWork(N, N, 1);
        worker.setLocalWork(32, 1, 1);
        oclNewApiTask.mapAllTo(oclDevice);

        // Warmup New Api OPENCL
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            oclNewApiTask.execute(gridTask);
        }

        // Time OPENCL
        long[] execTimesOCLNewApi = new long[EXECUTE_ITERATIONS];

        for (int i = 0; i < EXECUTE_ITERATIONS; i++) {
            start = System.currentTimeMillis();
            oclNewApiTask.execute(gridTask);
            stop = System.currentTimeMillis();
            execTimesOCLNewApi[i] = stop - start;
        }

        OptionalDouble avgOpenCLOptionalNewApi = Arrays.stream(execTimesOCLNewApi).average();
        double averageOpenCLNewApi;
        if (avgOpenCLOptionalNewApi.isPresent())
            averageOpenCLNewApi = avgOpenCLOptionalNewApi.getAsDouble();
        else
            throw new Exception("Could not get average execution time");

        // Warm up sequential
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            matrixMultiplication(matrixA, matrixB, matrixCSeq, N);
        }

        // Time sequential
        long[] execTimesSeq = new long[EXECUTE_ITERATIONS];
        for (int i = 0; i < execTimesSeq.length; i++) {
            start = System.currentTimeMillis();
            matrixMultiplication(matrixA, matrixB, matrixCSeq, N);
            stop = System.currentTimeMillis();
            execTimesSeq[i] = stop - start;
        }

        OptionalDouble avgSeqOptional = Arrays.stream(execTimesSeq).average();
        double averageSeq;
        if (avgSeqOptional.isPresent())
            averageSeq = avgSeqOptional.getAsDouble();
        else
            throw new Exception("Could not get average execution time");

        // Validate Results
        boolean correctResult = true;
        boolean validationCUDA = true;
        boolean validationOCL = true;
        boolean validationOCLNewApi = true;

        if (CHECK_RESULT) {
            for (int i = 0; i < N; i++) {
                if (Math.abs(matrixCCUDA[i] - matrixCSeq[i]) > DELTA) {
                    validationCUDA = false;
                    System.out.println("CUDA validation failed");
                }
                if (Math.abs(matrixCOCL[i] - matrixCSeq[i]) > DELTA) {
                    validationOCL = false;
                    System.out.println("OpenCL validation failed");
                }
                if (Math.abs(matrixCOCLNewApi[i] - matrixCSeq[i]) > DELTA) {
                    validationOCLNewApi = false;
                    System.out.println("OpenCL new api validation failed");
                    System.out.println("Result is (" + matrixCOCLNewApi[i] + ") - while should be (" + matrixCSeq[i] + ")");
                }
                correctResult = validationCUDA && validationOCL && validationOCLNewApi;

                if (!correctResult) {
                    break;
                }
            }
        }

        if (correctResult) {
            System.out.println("[RESULT] correct");
        } else {
            System.out.println("[RESULT] wrong");
        }

        // Compute Gigaflops and performance
        double flops = 2 * Math.pow(N, 3);
        double CUDAGigaFlops = (1.0E-9 * flops) / (averageCUDA / 1000.0f);
        double OpenCLGigaFlops = (1.0E-9 * flops) / (averageOpenCL / 1000.0f);
        double OpenCLNewApiGigaFlops = (1.0E-9 * flops) / (averageOpenCLNewApi / 1000.0f);
        double CUDAspeedup = averageSeq / averageCUDA;
        double OpenCLspeedup = averageSeq / averageOpenCL;
        double OpenCLNewApispeedup = averageSeq / averageOpenCLNewApi;

        String formatCUDAFGlops = String.format("%.2f", CUDAGigaFlops);
        String formatOpenCLFGlops = String.format("%.2f", OpenCLGigaFlops);
        String formatOpenCLNewApiFGlops = String.format("%.2f", OpenCLNewApiGigaFlops);

        System.out.println("\tOpenCL Execution: " + formatOpenCLFGlops + " GFlops, Total time = " + averageOpenCL + " ms");
        System.out.println("\tOpenCL Execution New Api: " + formatOpenCLNewApiFGlops + " GFlops, Total time = " + averageOpenCLNewApi + " ms");
        System.out.println("\tPTX Execution: " + formatCUDAFGlops + " GFlops, Total Time = " + averageCUDA + " ms");
        System.out.println("\tOpenCL Speedup: " + OpenCLspeedup + "x");
        System.out.println("\tOpenCL Speedup with New Api: " + OpenCLNewApispeedup + "x");
        System.out.println("\tPTX Speedup: " + CUDAspeedup + "x");
        System.out.println();

    }
}
