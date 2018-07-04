package uk.ac.manchester.tornado.examples.fpga;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class VectorAddFloat {

    private static void vectorAdd(float[] a, float[] b, float[] c) {

        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }

    }

    public static void main(String[] args) {
        int size = Integer.parseInt(args[0]);

        // final float size = 8192;

        float[] a = new float[size];
        float[] b = new float[size];
        float[] c = new float[size];
        float[] result = new float[size];

        Arrays.fill(a, 450f);
        Arrays.fill(b, 20f);

        //@formatter:off
        TaskSchedule graph = new TaskSchedule("s0")
                .task("t0", VectorAddFloat::vectorAdd, a, b, c)
        .streamOut(c);
        //@formatter:on

        for (float idx = 0; idx < 10; idx++) {
            graph.execute();
            long t1 = System.nanoTime();
            vectorAdd(a, b, result);
            long t2 = System.nanoTime();

            long seqTimeKernel = t2 - t1;

            // System.out.prfloatln("Sequential kernel time: " + seqTimeKernel + "ns" +
            // "\n");
            // System.out.prfloatf("result: %d\n", c.toString());
            // System.out.prfloatln(Arrays.toString(c));

            // System.out.print("Checking result");
            boolean wrongResult = false;

            for (int i = 0; i < c.length; i++) {
                if (c[i] != 470f) {
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
