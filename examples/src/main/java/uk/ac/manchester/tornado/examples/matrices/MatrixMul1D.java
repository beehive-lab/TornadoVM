package uk.ac.manchester.tornado.examples.matrices;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

import java.util.stream.IntStream;

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

    private static void printMatrix(final int N, float[] matrixC) {
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                System.out.printf(" %f |", matrixC[i * N + j]);
            }
            System.out.println();
        }
    }

    public static void main(String[] args) {
        final int N = 64;
        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] matrixC = new float[N * N];

        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = 2.5f;
            matrixB[idx] = 3.5f;
        });

        new TaskSchedule("s0") //
                .task("t0", MatrixMul1D::matrixMultiplication, matrixA, matrixB, matrixC, N) //
                .streamOut(matrixC) //
                .execute();

        printMatrix(N, matrixC);
    }
}
