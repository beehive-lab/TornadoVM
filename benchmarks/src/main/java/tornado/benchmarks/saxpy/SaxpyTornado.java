package tornado.benchmarks.saxpy;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;
import tornado.runtime.api.TaskSchedule;

import static tornado.benchmarks.LinearAlgebraArrays.saxpy;
import static tornado.collections.math.TornadoMath.findULPDistance;
import static tornado.common.Tornado.getProperty;

public class SaxpyTornado extends BenchmarkDriver {

    private final int numElements;

    private float[] x, y;
    private final float alpha = 2f;

    private TaskSchedule graph;

    public SaxpyTornado(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        x = new float[numElements];
        y = new float[numElements];

        for (int i = 0; i < numElements; i++) {
            x[i] = i;
        }

        graph = new TaskSchedule("s0")
                .task("t0", LinearAlgebraArrays::saxpy, alpha, x, y)
                .streamOut(y);

        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpTimes();
        graph.dumpProfiles();

        x = null;
        y = null;

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

        saxpy(alpha, x, result);

        final float ulp = findULPDistance(y, result);
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
