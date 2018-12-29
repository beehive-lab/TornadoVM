package uk.ac.manchester.tornado.examples.compute;

import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class MatrixMultiplication {

    public static int N = 512;
    public static final int WARMING_UP_ITERATIONS = 15;

    public static void matrixMultiplication(final float[] A, final float[] B, final float[] C, final int size) {
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

    public static void main(String[] args) {
        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] matrixC = new float[N * N];
        float[] resultSeq = new float[N * N];

        Random r = new Random();
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
            matrixB[idx] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", MatrixMultiplication::matrixMultiplication, matrixA, matrixB, matrixC, N)
                .streamOut(matrixC);
        //@formatter:on

        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            t.execute();
        }

        // Run parallel on the GPU
        long start = System.currentTimeMillis();
        t.execute();
        long end = System.currentTimeMillis();

        // Run sequential
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            matrixMultiplication(matrixA, matrixB, resultSeq, N);
        }

        long startSequential = System.currentTimeMillis();
        matrixMultiplication(matrixA, matrixB, resultSeq, N);
        long endSequential = System.currentTimeMillis();
        long msecGPUElapsedTime = (end - start);
        long msecCPUElaptedTime = (endSequential - startSequential);

        // Compute Gigaflops
        double flops = 2 * Math.pow(N, 3);
        double gpuGigaFlops = (1.0E-9 * flops) / (msecGPUElapsedTime / 1000.0f);
        double cpuGigaFlops = (1.0E-9 * flops) / (msecCPUElaptedTime / 1000.0f);

        System.out.println("CPU Execution: " + cpuGigaFlops + " GFlops, " + (endSequential - startSequential) + " ms");
        System.out.println("GPU Execution: " + gpuGigaFlops + " GFlops, " + (end - start) + " ms");
        System.out.println("Speedup: " + ((endSequential - startSequential) / (end - start)) + "x");
    }

}
