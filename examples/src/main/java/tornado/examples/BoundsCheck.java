package tornado.examples;

import java.util.Arrays;
import tornado.common.exceptions.TornadoRuntimeException;
import tornado.runtime.api.TaskSchedule;

public class BoundsCheck {

    /*
     * The following code generates an index out-of-bounds exception
     */
    public static void add(final int[] a, final int[] b, final int[] c) {
        for (int i = 0; i < a.length + 1; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(final String[] args) throws TornadoRuntimeException {

        final int numElements = 16;

        final int[] a = new int[numElements];
        final int[] b = new int[numElements];
        final int[] c = new int[numElements];

        Arrays.fill(a, 3);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);

        /*
         * First step is to create a reference to the method invocation This
         * involves finding the methods called and the arguments used in each
         * call.
         */
        TaskSchedule graph = new TaskSchedule("s0")
                .task("t0", BoundsCheck::add, a, b, c)
                .streamOut(c);

        /*
         * Calculate a (3) + b (2) = c (5)
         */
        graph.execute();

        /*
         * Check to make sure result is correct
         */
        for (final int value : c) {
            if (value != 5) {
                System.out.println("Invalid result");
            }
        }
    }
}
