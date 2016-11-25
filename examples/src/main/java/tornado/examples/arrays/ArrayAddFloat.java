package tornado.examples.arrays;

import java.util.Arrays;
import tornado.collections.math.SimpleMath;
import tornado.common.RuntimeUtilities;
import tornado.drivers.opencl.OpenCL;
import tornado.runtime.api.TaskGraph;

public class ArrayAddFloat {

    public static void main(final String[] args) {
        final int numElements = (args.length == 1) ? Integer.parseInt(args[0])
                : 8192;
        final int iterations = 1;
        final float[] a = new float[numElements];
        final float[] b = new float[numElements];
        final float[] c = new float[numElements];

        Arrays.fill(a, 3);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);

        //@formatter:off
        final TaskGraph graph = new TaskGraph()
                .add(SimpleMath::vectorAdd, a, b, c)
                .streamOut(c)
                .mapAllTo(OpenCL.defaultDevice());
        //@formatter:on

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            graph.schedule().waitOn();
        }
        long stop = System.nanoTime();

        double elapsed = (stop - start) * 1e-9;
        double megaBytes = (((double) numElements * 4)) * 3 * iterations;
        double bw = megaBytes / elapsed;

        System.out.printf("Overall  : time = %f seconds, bw = %s\n", elapsed,
                RuntimeUtilities.formatBytesPerSecond(bw));

        /*
         * Check to make sure result is correct
         */
        int errors = 0;
        for (final float value : c) {
            if (value != 5f) {
                errors++;
            }
        }

        if (errors > 0) {
            System.out.printf("Invalid result: %d errors\n", errors);
        }
    }
}
