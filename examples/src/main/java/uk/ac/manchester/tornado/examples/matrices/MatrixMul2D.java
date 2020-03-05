package uk.ac.manchester.tornado.examples.matrices;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Matrix2DFloat;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

import java.util.Random;

public class MatrixMul2D {

    private static final int WARMING_UP_ITERATIONS = 15;

    private static void matrixMultiplication(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A.get(i, k) * B.get(k, j);
                }
                C.set(i, j, sum);
            }
        }
    }

    public static void main(String[] args) {

        int size = 512;
        if (args.length >= 1) {
            try {
                size = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                size = 512;
            }
        }

        // Enable profiler
        //System.setProperty("tornado.profiler", "True");

        System.out.println("Computing MxM of " + size + "x" + size);
        System.out.println();

        Matrix2DFloat matrixA = new Matrix2DFloat(size, size);
        Matrix2DFloat matrixB = new Matrix2DFloat(size, size);
        Matrix2DFloat matrixCCUDA = new Matrix2DFloat(size, size);
        Matrix2DFloat matrixCOCL = new Matrix2DFloat(size, size);

        Random r = new Random();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrixA.set(i, j, r.nextFloat());
                matrixB.set(i, j, r.nextFloat());
            }
        }

        //@formatter:off
        TaskSchedule cudaTask = new TaskSchedule("cuda_s0")
                .task("t0", MatrixMul2D::matrixMultiplication, matrixA, matrixB, matrixCCUDA, size)
                .streamOut(matrixCCUDA);
        //@formatter:on
        TornadoDriver cudaDriver = TornadoRuntime.getTornadoRuntime().getDriver(0);
        cudaTask.mapAllTo(cudaDriver.getDevice(0));

        // Warm up CUDA
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            cudaTask.execute();
        }

        // Time CUDA
        long cudaStart = System.nanoTime();
        cudaTask.execute();
        long cudaEnd = System.nanoTime();

        //@formatter:off
        TaskSchedule oclTask = new TaskSchedule("ocl_s0")
                .task("t0", MatrixMul2D::matrixMultiplication, matrixA, matrixB, matrixCOCL, size)
                .streamOut(matrixCOCL);
        //@formatter:on
        TornadoDriver oclDriver = TornadoRuntime.getTornadoRuntime().getDriver(1);
        oclTask.mapAllTo(oclDriver.getDevice(1));

        // Warmup OPENCL
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            oclTask.execute();
        }

        // Time OPENCL
        long oclStart = System.nanoTime();
        oclTask.execute();
        long oclEnd = System.nanoTime();

        // Compute execution times
        long nsecCUDAElapsedTime = (cudaEnd - cudaStart);
        long nsecOCLElapsedTime = (oclEnd - oclStart);

        // Print result matrices if they are small enough
        if (size < 10) {
            System.out.println("CUDA:");
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    System.out.print(" | " + matrixCCUDA.get(i, j));
                }
                System.out.println(" |");
            }

            System.out.println("OPENCL:");
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    System.out.print(" | " + matrixCOCL.get(i, j));
                }
                System.out.println(" |");
            }
        }


        System.out.println("CUDA-PTX Execution: " + nsecCUDAElapsedTime + " ns");
        System.out.println("OPENCL   Execution: " + nsecOCLElapsedTime + " ns");
        System.out.println("           Speedup: " + (double) nsecOCLElapsedTime / nsecCUDAElapsedTime);
        System.out.println();

//        System.out.println("=================================================");
//        System.out.println("CUDA:");
//        System.out.println("\t       Kernel time: " + cudaTask.getDeviceKernelTime());
//        System.out.println("\tData transfer time: " + cudaTask.getDataTransfersTime());
//        System.out.println();
//        System.out.println("OPENCL:");
//        System.out.println("\t       Kernel time: " + oclTask.getDeviceKernelTime());
//        System.out.println("\tData transfer time: " + oclTask.getDataTransfersTime());

    }
}
