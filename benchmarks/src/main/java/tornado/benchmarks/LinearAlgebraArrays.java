package tornado.benchmarks;

import tornado.api.Atomic;
import tornado.api.Parallel;

public class LinearAlgebraArrays {

    public static void reduceInt(@Atomic int[] result, int[] input) {
        int sum = 0;
        for (@Parallel int i = 0; i < input.length; i++) {
            sum += input[i];
        }
        result[0] = sum;
    }

    public static void reduce1(float[] output, float[] input) {
        final int numThreads = output.length;
        for (@Parallel int thread = 0; thread < numThreads; thread++) {

            float sum = 0f;
            for (int i = thread; i < input.length; i += numThreads) {
                sum += input[i];
            }

            output[thread] = sum;

        }
    }

    public static void reduce2(float[] output, float[] input) {
        final int numThreads = output.length;
        for (@Parallel int thread = 0; thread < numThreads; thread++) {

            float sum = 0f;
            for (int i = 0; i < input.length; i += numThreads) {
                sum += input[i];
            }

            output[thread] = sum;
        }
    }

    public static void ladd(long[] a, long[] b, long[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void sadd(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void striad(float alpha, float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = alpha * a[i] + b[i];
        }
    }

    public static void sscal(float alpha, float[] x) {
        for (@Parallel int i = 0; i < x.length; i++) {
            x[i] *= alpha;
        }
    }

    public static void saxpy(float alpha, float[] x, float[] y) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] += alpha * x[i];
        }
    }

    public static void sgemm(final int M, final int N, final int K, final float A[], final float B[],
            final float C[]) {

        for (@Parallel int i = 0; i < N; i++) {
            for (@Parallel int j = 0; j < N; j++) {
                float sum = 0.0f;
                for (int k = 0; k < K; k++) {
                    sum += A[(i * N) + k] * B[(k * N) + j];
                }
                C[(i * N) + j] = sum;
            }
        }

    }

    public static void spmv(final float[] val, final int[] cols,
            final int[] rowDelimiters, final float[] vec,
            final int dim, final float[] out) {

        for (@Parallel int i = 0; i < dim; i++) {
            float t = 0.0f;
            for (int j = rowDelimiters[i]; j < rowDelimiters[i + 1]; j++) {
                final int col = cols[j];
                t += val[j] * vec[col];
            }
            out[i] = t;
        }
    }

}
