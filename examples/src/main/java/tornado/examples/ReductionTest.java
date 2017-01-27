package tornado.examples;

import java.util.Arrays;
import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;

public class ReductionTest {

    public final int[] data;
    public int result;

    public void sum() {
        int sum = 0;
        for (@Parallel int i = 0; i < data.length; i++) {
            sum += data[i];
        }
        result = sum;
    }

    public ReductionTest(int[] data) {
        this.data = data;
    }

    public static void main(String[] args) {
        final int[] data = new int[614400];

        Arrays.fill(data, 1);

        ReductionTest rt = new ReductionTest(data);

        new TaskSchedule("s0")
                .task("t0", ReductionTest::sum, rt)
                .streamOut(rt)
                .execute();

        int sum = 0;
        for (int value : data) {
            sum += value;
        }

        System.out.printf("result: %d == %d\n", rt.result, sum);

    }

}
