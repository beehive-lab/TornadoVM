package uk.ac.manchester.tornado.examples.matrices;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class MatrixMul1D {

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
        float[] matrixC = new float[N * N];

        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = 2.5f;
            matrixB[idx] = 3.5f;
        });

        TaskSchedule s = new TaskSchedule("s0")
                .task("t0", MatrixMul1D::matrixMultiplication, matrixA, matrixB, matrixC, N)
                .streamOut(matrixC);

        // Warm up
        for (int i = 0; i < 15; i++) {
            s.execute();
        }

        long start, stop;
        long[] execTimes = new long[100];
        for (int i = 0; i < execTimes.length; i++) {
            start = System.nanoTime();
            s.execute();
            stop = System.nanoTime();
            execTimes[i] = stop - start;
        }

        OptionalDouble avg = Arrays.stream(execTimes).average();
        double average;
        if (avg.isPresent()) average = avg.getAsDouble();
        else throw new Exception("Could not get average execution time");

        System.out.printf("Average time: %.2f\n", average);
    }
}
