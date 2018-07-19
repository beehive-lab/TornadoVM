package uk.ac.manchester.tornado.examples.fpga;

import java.util.Random;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class SGEMMFPGA {

    public static void sgemm(final int M, final int N, final int K, final int A[], final int B[], final int C[], final int[] dims) {

        for (@Parallel int i = 0; i < dims[0]; i++) {
            for (@Parallel int j = 0; j < dims[0]; j++) {
                int sum = 0;
                for (int k = 0; k < dims[0]; k++) {
                    sum += A[(i * N) + k] * B[(k * N) + j];
                }
                C[(i * N) + j] = sum;
            }
        }

    }

    public static void main(String[] args) {
        int size = Integer.parseInt(args[0]);

        int m = size;
        int n = size;

        int[] a,b,c,result,dims;

        a = new int[m * n];
        b = new int[m * n];
        c = new int[m * n];
        result = new int[m * n];
        dims = new int[1];

        dims[0] = size;

        final Random random = new Random();

        for (int i = 0; i < m; i++) {
            a[i * (m + 1)] = 1;
        }

        for (int i = 0; i < m * n; i++) {
            b[i] = random.nextInt();
        }

        TaskSchedule graph = new TaskSchedule("s0");
        graph.task("t0", SGEMMFPGA::sgemm, m, n, n, a, b, c, dims).streamOut(c);

        graph.warmup();

        graph.execute();

        System.out.println("Checking result");
        boolean wrongResult = false;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int sum = 0;
                for (int k = 0; k < size; k++) {
                    sum += a[(i * size) + k] * b[(k * size) + j];
                }
                result[(i * size) + j] = sum;

                if (result[(i * size) + j] != c[(i * size) + j]) {
                    wrongResult = true;
                    break;
                }
            }
        }

        if (!wrongResult) {
            System.out.println("Test success");
        } else {
            System.out.println("Result is wrong");
        }
    }
}
