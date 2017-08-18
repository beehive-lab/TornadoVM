package tornado.examples.ooo;

import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;

public class OOOTest {

    public static void sscal(float alpha, float[] x) {
        for (@Parallel int i = 0; i < x.length; i++) {
            x[i] *= alpha;
        }
    }

    public static final void main(String[] args) {

        int numArrays = Integer.parseInt(args[0]);
        int numElements = Integer.parseInt(args[1]);
        float[][] arrays = new float[numArrays][numElements];

        final float alpha = 2.0f;

        System.out.printf("using %d arrays of %d elements\n", numArrays, numElements);

        TaskSchedule graph = new TaskSchedule("example");
        for (int i = 0; i < numArrays; i++) {
            graph.task("t" + i, OOOTest::sscal, alpha, arrays[i]);
        }

        graph.warmup();

        final long t0 = System.nanoTime();
        graph.execute();
        final long t1 = System.nanoTime();

        graph.dumpEvents();

        System.out.printf("time=%.9f s\n", (t1 - t0) * 1e-9);
    }

}
