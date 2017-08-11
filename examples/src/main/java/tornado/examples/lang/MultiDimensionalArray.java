package tornado.examples.lang;

import java.util.Arrays;
import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;

public class MultiDimensionalArray {

    public static void fill(int[][] values) {
        for (@Parallel int i = 0; i < values.length; i++) {
            Arrays.fill(values[i], i);
        }
    }

    public static final void main(String[] args) {

        int n = 8;
        int m = 8;
        int[][] values = new int[n][m];

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", MultiDimensionalArray::fill, values)
                .streamOut(new Object[]{values});

        s0.warmup();

        s0.execute();

        for (int i = 0; i < values.length; i++) {
            System.out.printf("%d| %s\n", i, Arrays.toString(values[i]));
        }

    }

}
