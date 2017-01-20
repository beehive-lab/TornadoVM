package tornado.examples.vectors;

import tornado.api.Parallel;
import tornado.collections.types.Float3;

import tornado.collections.types.VectorFloat3;
import tornado.runtime.api.TaskSchedule;

import static tornado.collections.types.Float3.add;

public class VectorAddTest {

    private static void test(VectorFloat3 a, VectorFloat3 b,
            VectorFloat3 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, add(a.get(i), b.get(i)));
        }
    }

    public static void main(String[] args) {

        final VectorFloat3 a = new VectorFloat3(4);
        final VectorFloat3 b = new VectorFloat3(4);
        final VectorFloat3 results = new VectorFloat3(4);

        for (int i = 0; i < 4; i++) {
            a.set(i, new Float3(i, i, i));
            b.set(i, new Float3(2 * i, 2 * i, 2 * i));
        }

        System.out.printf("vector<float3>: %s\n", a.toString());

        System.out.printf("vector<float3>: %s\n", b.toString());

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", VectorAddTest::test, a, b, results)
                .streamOut(results)
                .execute();
        //@formatter:on

        System.out.printf("result: %s\n", results.toString());

    }

}
