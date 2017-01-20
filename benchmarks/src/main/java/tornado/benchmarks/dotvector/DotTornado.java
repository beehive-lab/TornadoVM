package tornado.benchmarks.dotvector;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.Float3;
import tornado.collections.types.VectorFloat3;
import tornado.runtime.api.TaskSchedule;

import static tornado.collections.math.TornadoMath.findULPDistance;
import static tornado.common.Tornado.getProperty;

public class DotTornado extends BenchmarkDriver {

    private final int numElements;

    private VectorFloat3 a, b;
    private float[] c;

    private TaskSchedule graph;

    public DotTornado(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        a = new VectorFloat3(numElements);
        b = new VectorFloat3(numElements);
        c = new float[numElements];

        final Float3 valueA = new Float3(new float[]{1f, 1f, 1f});
        final Float3 valueB = new Float3(new float[]{2f, 2f, 2f});
        for (int i = 0; i < numElements; i++) {
            a.set(i, valueA);
            b.set(i, valueB);
        }

        graph = new TaskSchedule("s0")
                .task("t0", GraphicsKernels::dotVector, a, b, c)
                .streamOut(c);

        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpTimes();
        graph.dumpProfiles();

        a = null;
        b = null;
        c = null;

        graph.getDefaultDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final float[] result = new float[numElements];

        code();
        graph.clearProfiles();

        GraphicsKernels.dotVector(a, b, result);

        final float ulp = findULPDistance(result, c);
        return ulp < MAX_ULP;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf(
                    "id=%s, elapsed=%f, per iteration=%f\n",
                    getProperty("s0.device"), getElapsed(),
                    getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n",
                    getProperty("s0.device"));
        }
    }
}
