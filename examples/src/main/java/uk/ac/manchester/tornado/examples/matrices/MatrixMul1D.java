package uk.ac.manchester.tornado.examples.matrices;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.stream.IntStream;

public class MatrixMul1D {

    public static final int WARMUP_ITERATIONS = 15;
    public static final int EXECUTE_ITERATIONS = 100;

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

        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = 2.5f;
            matrixB[idx] = 3.5f;
        });

        TaskSchedule scheduleCUDA = new TaskSchedule("cuda_old_api").task("t0", MatrixMul1D::matrixMultiplication, matrixA, matrixB, matrixCCUDA, N).streamOut(matrixCCUDA);

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

        TaskSchedule scheduleOCL = new TaskSchedule("ocl_old_api").task("t0", MatrixMul1D::matrixMultiplication, matrixA, matrixB, matrixCOCL, N).streamOut(matrixCOCL);

        // Get the same device but running the OCL backend
        TornadoDriver oclDriver = TornadoRuntime.getTornadoRuntime().getDriver(1);
        TornadoDevice oclDevice = null;
        for (int i = 0; i < oclDriver.getDeviceCount(); i++) {
            TornadoDevice device = oclDriver.getDevice(i);
            if (device.getPhysicalDevice().getDeviceName().equalsIgnoreCase(cudaDevice.getPhysicalDevice().getDeviceName())) {
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

        // Compute Gigaflops and performance
        double flops = 2 * Math.pow(N, 3);
        double CUDAGigaFlops = (1.0E-9 * flops) / (averageCUDA / 1000.0f);
        double OpenCLGigaFlops = (1.0E-9 * flops) / (averageOpenCL / 1000.0f);
        double CUDAspeedup = averageSeq / averageCUDA;
        double OpenCLspeedup = averageSeq / averageOpenCL;

        String formatCUDAFGlops = String.format("%.2f", CUDAGigaFlops);
        String formatOpenCLFGlops = String.format("%.2f", OpenCLGigaFlops);

        System.out.println("\tOpenCL Execution: " + formatOpenCLFGlops + " GFlops, Total time = " + averageOpenCL + " ms");
        System.out.println("\tPTX Execution: " + formatCUDAFGlops + " GFlops, Total Time = " + averageCUDA + " ms");
        System.out.println("\tOpenCL Speedup: " + OpenCLspeedup + "x");
        System.out.println("\tPTX Speedup: " + CUDAspeedup + "x");
        System.out.println();

    }
}
