package tornado.examples.lang;

import java.util.Arrays;
import tornado.runtime.api.TaskSchedule;

public class CountedLoopEx2 {

    public static void add(int[] a, int[] b, int[] c) {
        for (int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(String[] args) {

        final int[] a = new int[8];
        final int[] b = new int[8];
        final int[] c = new int[8];
        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", CountedLoopEx2::add, a, b, c)
                .streamOut(c);

        s0.warmup();
        s0.execute();

        System.out.printf("c: %s\n", Arrays.toString(c));

    }

}
