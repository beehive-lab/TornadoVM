package tornado.examples.vectors;

import tornado.api.Read;
import tornado.api.Write;
import tornado.collections.types.Float3;
import tornado.collections.types.VectorFloat3;
import tornado.drivers.opencl.OpenCL;
import tornado.runtime.api.TaskGraph;

public class VectorPhiTest {

    private static void test(@Read VectorFloat3 a,
            @Write VectorFloat3 results) {

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
        TaskGraph graph = new TaskGraph()
                .add(VectorPhiTest::test, input, results)
                .streamOut(results)
                .mapAllTo(OpenCL.defaultDevice());
        //@formatter:on

        graph.schedule().waitOn();

        System.out.printf("result: %s\n", results.get(0).toString());

    }

}
