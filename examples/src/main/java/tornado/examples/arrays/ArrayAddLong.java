package tornado.examples.arrays;

import java.util.Arrays;
import tornado.api.Parallel;
import tornado.drivers.opencl.OpenCL;
import tornado.runtime.api.TaskGraph;

public class ArrayAddLong {

    public static void add(long[] a, long[] b, long[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(String[] args) {

        final int numElements = 8;
        long[] a = new long[numElements];
        long[] b = new long[numElements];
        long[] c = new long[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);

        //@formatter:off
        final TaskGraph graph = new TaskGraph();
        graph
                .add(ArrayAddLong::add, a, b, c)
                .streamOut(c)
                .mapAllTo(OpenCL.defaultDevice())
                .schedule()
                .waitOn();
        //@formatter:on

        System.out.println("a: " + Arrays.toString(a));
        System.out.println("b: " + Arrays.toString(b));
        System.out.println("c: " + Arrays.toString(c));
    }

}
