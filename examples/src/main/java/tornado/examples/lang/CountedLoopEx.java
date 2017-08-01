package tornado.examples.lang;

import java.util.Arrays;
import tornado.runtime.api.TaskSchedule;

public class CountedLoopEx {

    public static void one(int[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = 1;
        }
    }

    public static void main(String[] args) {

        final int[] a = new int[8];

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", CountedLoopEx::one, a)
                .streamOut(a);

        s0.warmup();
        s0.execute();

        System.out.printf("a: %s\n", Arrays.toString(a));

    }

}
