package tornado.benchmarks.sgemm;

import java.util.Random;
import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;
import tornado.runtime.api.TaskSchedule;

import static tornado.benchmarks.LinearAlgebraArrays.sgemm;
import static tornado.collections.math.TornadoMath.findULPDistance;
import static tornado.common.Tornado.getProperty;

public class SgemmTornado extends BenchmarkDriver {

    private final int m, n;

    private float[] a, b, c;

    private TaskSchedule graph;

    public SgemmTornado(int iterations, int m, int n) {
        super(iterations);
        this.m = m;
        this.n = n;
    }

    @Override
    public void setUp() {
        a = new float[m * n];
        b = new float[m * n];
        c = new float[m * n];

        final Random random = new Random();

        for (int i = 0; i < m; i++) {
            a[i * (m + 1)] = 1;
        }

        for (int i = 0; i < m * n; i++) {
            b[i] = random.nextFloat();
        }

        graph = new TaskSchedule("s0")
                .task("t0", LinearAlgebraArrays::sgemm, m, n, n, a, b,
                        c)
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

        final float[] result = new float[m * n];

        code();
        graph.clearProfiles();

        sgemm(m, n, m, a, b, result);

        final float ulp = findULPDistance(c, result);
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
