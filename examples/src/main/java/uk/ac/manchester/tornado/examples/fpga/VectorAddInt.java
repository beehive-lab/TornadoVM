package uk.ac.manchester.tornado.examples.fpga;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class VectorAddInt {

    private static void vectorAdd(int[] a, int[] b, int[] c) {

        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }

    }

    public static void main(String[] args) {
        int size = Integer.parseInt(args[0]);

        // final int size = 8192;

        int[] a = new int[size];
        int[] b = new int[size];
        int[] c = new int[size];
        int[] result = new int[size];

        Arrays.fill(a, 10);
        Arrays.fill(b, 20);

        //@formatter:off
        TaskSchedule graph = new TaskSchedule("s0")
                .task("t0", VectorAddInt::vectorAdd, a, b, c)
        .streamOut(c);
        //@formatter:on

        for (int idx = 0; idx < 10; idx++) {
            graph.execute();
            long t1 = System.nanoTime();
            vectorAdd(a, b, result);
            long t2 = System.nanoTime();

            long seqTimeKernel = t2 - t1;

            // System.out.println("Sequential kernel time: " + seqTimeKernel +
            // "ns" + "\n");
            // System.out.printf("result: %d\n", c.toString());
            // System.out.println(Arrays.toString(c));

            System.out.println("Checking result");
            boolean wrongResult = false;

            for (int i = 0; i < c.length; i++) {
                if (c[i] != 30) {
                    wrongResult = true;
                    break;
                }
            }
            if (!wrongResult) {
                System.out.println("Test success");
            } else {
                System.out.println("Result is wrong");
            }
        }
    }
}
