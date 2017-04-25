package tornado.examples.arrays;

import java.util.Arrays;
import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;

public class ArrayAccInt {

    public static void acc(int[] a, int value) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] += value;
        }
    }

    public static void main(String[] args) {

        final int numElements = 8;
        final int numKernels = 8;
        int[] a = new int[numElements];

        Arrays.fill(a, 0);

        //@formatter:off
        TaskSchedule s0 = new TaskSchedule("s0");
        for (int i = 0; i < numKernels; i++) {
            s0.task("t" + i, ArrayAccInt::acc, a, 1);
        }
        s0.streamOut(a)
                .execute();
        s0.dumpEvents();
        //@formatter:on

        System.out.println("a: " + Arrays.toString(a));
    }

}
