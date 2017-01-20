package tornado.examples.vectors;

import tornado.collections.types.Float3;

import tornado.collections.types.VectorFloat3;
import tornado.runtime.api.TaskSchedule;

import static tornado.collections.types.Float3.add;

public class VectorTest {

    private static void test(Float3 a, Float3 b,
            VectorFloat3 results) {
        results.set(0, add(a, b));
    }

    public static void main(String[] args) {

        final Float3 value = new Float3(1f, 1f, 1f);
        System.out.printf("float3: %s\n", value.toString());

        System.out.printf("float3: %s\n", value.toString());

        final VectorFloat3 results = new VectorFloat3(1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", VectorTest::test, value, value, results)
                .streamOut(results)
                .execute();
        //@formatter:on

        System.out.printf("result: %s\n", results.get(0).toString());

    }

}
