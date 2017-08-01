package tornado.examples.lang;

import tornado.runtime.api.TaskSchedule;

public class DotProductBasic {

    public static float[] mult3(int n, float[] a, float[] b) {
        final float[] c = new float[n];
        for (int i = 0; i < n; i++) {
            c[i] = a[i] * b[i];
        }
        return c;
    }

    public static float dot3(int n, float[] a, float[] b) {
        float[] c = mult3(n, a, b);
        float sum = 0;
        for (int i = 0; i < n; i++) {
            sum += c[i];
        }
        return sum;
    }

    public static final void main(String[] args) {
        float[] a = new float[]{1, 1, 1};
        float[] b = new float[]{2, 2, 2};

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", DotProductBasic::dot3, 3, a, b);

        s0.warmup();
        s0.schedule();

        System.out.printf("result = 0x%x\n", s0.getReturnValue("t0"));

    }

}
