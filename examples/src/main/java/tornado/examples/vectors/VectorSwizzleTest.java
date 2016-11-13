package tornado.examples.vectors;

import tornado.api.Read;
import tornado.api.Write;
import tornado.collections.types.Float3;
import tornado.collections.types.VectorFloat3;
import tornado.drivers.opencl.OpenCL;
import tornado.runtime.api.TaskGraph;

public class VectorSwizzleTest {

    private static void test(@Read Float3 a,
            @Write VectorFloat3 results) {
        Float3 b = new Float3(a.getZ(),a.getY(),a.getX());
        results.set(0, b);
    }

    public static void main(String[] args) {

        final Float3 value = new Float3(1f, 2f, 3f);
        System.out.printf("float3: %s\n", value.toString());

        System.out.printf("float3: %s\n", value.toString());

        final VectorFloat3 results = new VectorFloat3(1);

        //@formatter:off
        TaskGraph graph = new TaskGraph()
                .add(VectorSwizzleTest::test, value, results)
                .streamOut(results)
                .mapAllTo(OpenCL.defaultDevice())
                .schedule();
        //@formatter:on

        graph.waitOn();

        System.out.printf("result: %s\n", results.get(0).toString());

    }

}
