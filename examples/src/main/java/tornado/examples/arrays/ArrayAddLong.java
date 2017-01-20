package tornado.examples.arrays;

import java.util.Arrays;
import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;

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
        new TaskSchedule("s0")
                .task("t0", ArrayAddLong::add, a, b, c)
                .streamOut(c)
                .execute();
        //@formatter:on

        System.out.println("a: " + Arrays.toString(a));
        System.out.println("b: " + Arrays.toString(b));
        System.out.println("c: " + Arrays.toString(c));
    }

}
