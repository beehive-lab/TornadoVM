package tornado.examples.lang;

import java.util.Arrays;
import java.util.function.BiFunction;
import tornado.runtime.api.TaskSchedule;

public class DynamicDispatch {

    static class AddOp implements BiFunction<Integer, Integer, Integer> {

        @Override
        public Integer apply(Integer x, Integer y) {
            return x + y;
        }

    }

    static class SubOp implements BiFunction<Integer, Integer, Integer> {

        @Override
        public Integer apply(Integer x, Integer y) {
            return x - y;
        }

    }

    public static final void applyOp(BiFunction<Integer, Integer, Integer> op, int[] a, int[] b, int[] c) {
        for (int i = 0; i < c.length; i++) {
            c[i] = op.apply(a[i], b[i]);
        }
    }

    public static final void main(String[] args) {

        int[] a = new int[8];
        int[] b = new int[8];
        int[] c = new int[8];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", DynamicDispatch::applyOp, new AddOp(), a, b, c)
                .streamOut(c);

        s0.warmup();
        s0.execute();

        System.out.printf("c = %s\n", Arrays.toString(c));

    }

}
