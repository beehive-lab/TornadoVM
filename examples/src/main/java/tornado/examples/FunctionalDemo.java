package tornado.examples;

import java.util.function.IntUnaryOperator;
import tornado.api.Parallel;
import tornado.runtime.api.CompilableTask;
import tornado.runtime.api.TaskUtils;

public class FunctionalDemo {

    public static class Matrix {

        private final int[] array;
        private final int m;
        private final int n;

        public Matrix(final int m, final int n) {
            this.m = m;
            this.n = n;
            array = new int[m * n];
        }

        public void apply(final IntUnaryOperator action) {
            for (@Parallel int i = 0; i < m; i++) {
                for (@Parallel int j = 0; j < n; j++) {
                    set(i, j, action.applyAsInt(get(i, j)));
                }
            }
        }

        public int get(final int r, final int c) {
            return array[(r * m) + c];
        }

        public void set(final int r, final int c, final int value) {
            array[(r * m) + c] = value;
        }

        public void times2() {
            final IntUnaryOperator times2 = (value) -> value << 1;
            apply(times2);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();

            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    sb.append(String.format("%2d ", get(i, j)));
                }
                sb.append("\n");
            }

            return sb.toString();
        }
    }

    public static void main(final String[] args) {

        final Matrix m = new Matrix(4, 4);

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                m.set(i, j, (i * 4) + j);
            }
        }

        final CompilableTask times2 = TaskUtils.createTask("t0", Matrix::times2, m);
//        times2.mapTo(OpenCL.defaultDevice());
//		times2.execute();
        System.out.println(m);
    }
}
