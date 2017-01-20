package tornado.examples.vectors;

import tornado.collections.types.Float3;
import tornado.collections.types.VectorFloat3;
import tornado.runtime.api.TaskSchedule;

public class VectorPhiTest {

    private static void test(VectorFloat3 a,
            VectorFloat3 results) {

        Float3 sum = new Float3();
        for (int i = 0; i < a.getLength(); i++) {
            sum = Float3.add(sum, a.get(i));
        }
        results.set(0, sum);
    }

    public static void main(String[] args) {

        final VectorFloat3 input = new VectorFloat3(8);
        input.fill(1f);
        final VectorFloat3 results = new VectorFloat3(1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", VectorPhiTest::test, input, results)
                .streamOut(results)
                .execute();
        //@formatter:on

        System.out.printf("result: %s\n", results.get(0).toString());

    }

}
