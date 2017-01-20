package tornado.benchmarks.addvector;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.Float4;
import tornado.collections.types.FloatOps;
import tornado.collections.types.VectorFloat4;
import tornado.runtime.api.TaskSchedule;

import static tornado.common.Tornado.getProperty;

public class AddTornado extends BenchmarkDriver {

    private final int numElements;

    private VectorFloat4 a, b, c;

    private TaskSchedule graph;

    public AddTornado(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        a = new VectorFloat4(numElements);
        b = new VectorFloat4(numElements);
        c = new VectorFloat4(numElements);

        final Float4 valueA = new Float4(new float[]{1f, 1f, 1f, 1f});
        final Float4 valueB = new Float4(new float[]{2f, 2f, 2f, 2f});
        for (int i = 0; i < numElements; i++) {
            a.set(i, valueA);
            b.set(i, valueB);
        }

        graph = new TaskSchedule("s0")
                .task("t0", GraphicsKernels::addVector, a, b, c)
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

        final VectorFloat4 result = new VectorFloat4(numElements);

        code();
        graph.clearProfiles();

        GraphicsKernels.addVector(a, b, result);

        float maxULP = 0f;
        for (int i = 0; i < numElements; i++) {
            final float ulp = FloatOps.findMaxULP(result.get(i), c.get(i));

            if (ulp > maxULP) {
                maxULP = ulp;
            }
        }
        return maxULP < MAX_ULP;
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
