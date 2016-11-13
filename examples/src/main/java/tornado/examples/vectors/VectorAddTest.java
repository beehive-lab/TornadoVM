package tornado.examples.vectors;

import tornado.api.Parallel;
import tornado.api.Read;
import tornado.api.Write;
import tornado.collections.types.Float3;
import static tornado.collections.types.Float3.add;
import tornado.collections.types.VectorFloat3;
import tornado.drivers.opencl.OpenCL;
import tornado.runtime.api.TaskGraph;

public class VectorAddTest {

    private static void test(@Read VectorFloat3 a, @Read VectorFloat3 b,
            @Write VectorFloat3 results) {
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
        TaskGraph graph = new TaskGraph()
                .add(VectorAddTest::test, a, b, results)
                .streamOut(results)
                .mapAllTo(OpenCL.defaultDevice())
                .schedule();
        //@formatter:on

        graph.waitOn();

        System.out.printf("result: %s\n", results.toString());

    }

}
