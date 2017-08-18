package tornado.examples.ooo;

import java.util.Random;
import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;

public class OOOTest {

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

    public static final void main(String[] args) {

        int numArrays = Integer.parseInt(args[0]);
        int n = Integer.parseInt(args[1]);
        float[][] As = new float[numArrays][n * n];
        float[][] Bs = new float[numArrays][n * n];
        float[][] Cs = new float[numArrays][n * n];

        System.out.printf("using %d arrays of %d x %d elements\n", numArrays, n, n);

        final Random random = new Random();
        TaskSchedule graph = new TaskSchedule("example");
        for (int ii = 0; ii < numArrays; ii++) {
            float[] a = new float[n * n];
            float[] b = new float[n * n];
            float[] c = new float[n * n];

            for (int i = 0; i < n; i++) {
                a[i * (n + 1)] = 1;
            }

            for (int i = 0; i < n * n; i++) {
                b[i] = random.nextFloat();
            }

            graph.task("t" + ii, OOOTest::sgemm, n, n, n, a, b, c);

            As[ii] = a;
            Bs[ii] = b;
            Cs[ii] = c;

        }

        graph.warmup();
        graph.execute();
        graph.clearProfiles();

        final long t0 = System.nanoTime();
        graph.execute();
        final long t1 = System.nanoTime();

        graph.dumpEvents();

        System.out.printf("time=%.9f s\n", (t1 - t0) * 1e-9);
    }

}
