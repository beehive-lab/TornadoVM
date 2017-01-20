package tornado.examples.arrays;

import java.util.Arrays;
import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;

public class ArrayAddDouble {

    public static void add(double[] a, double[] b, double[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(String[] args) {

        final int numElements = 8;
        double[] a = new double[numElements];
        double[] b = new double[numElements];
        double[] c = new double[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", ArrayAddDouble::add, a, b, c)
                .streamOut(c)
                .execute();
        //@formatter:on

        System.out.println("a: " + Arrays.toString(a));
        System.out.println("b: " + Arrays.toString(b));
        System.out.println("c: " + Arrays.toString(c));
    }

}
