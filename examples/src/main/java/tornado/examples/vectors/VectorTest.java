package tornado.examples.vectors;

import tornado.api.Read;
import tornado.api.Write;
import tornado.collections.types.Float3;
import static tornado.collections.types.Float3.add;
import tornado.collections.types.VectorFloat3;
import tornado.drivers.opencl.OpenCL;
import tornado.runtime.api.TaskGraph;

public class VectorTest {

    private static void test(@Read Float3 a, @Read Float3 b,
            @Write VectorFloat3 results) {
        results.set(0, add(a, b));
    }

    public static void main(String[] args) {

        final Float3 value = new Float3(1f, 1f, 1f);
        System.out.printf("float3: %s\n", value.toString());

        System.out.printf("float3: %s\n", value.toString());

        final VectorFloat3 results = new VectorFloat3(1);

        //@formatter:off
        TaskGraph graph = new TaskGraph()
                .add(VectorTest::test, value, value, results)
                .streamOut(results)
                .mapAllTo(OpenCL.defaultDevice())
                .schedule();
        //@formatter:on

        graph.waitOn();

        System.out.printf("result: %s\n", results.get(0).toString());

    }

}
