package tornado.benchmarks.bandwidth;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;
import tornado.runtime.api.TaskSchedule;

import static tornado.benchmarks.LinearAlgebraArrays.ladd;
import static tornado.common.Tornado.getProperty;

public class BandwidthTornado extends BenchmarkDriver {

    private final int numElements;

    private long[] a, b, c;

    private TaskSchedule graph;

    public BandwidthTornado(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        a = new long[numElements];
        b = new long[numElements];
        c = new long[numElements];

        for (int i = 0; i < numElements; i++) {
            a[i] = 1;
            b[i] = 2;
            c[i] = 0;
        }

        graph = new TaskSchedule("s0")
                .task("t0", LinearAlgebraArrays::ladd, a, b, c)
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

        final long[] result = new long[numElements];

        code();
        graph.clearProfiles();

        ladd(a, b, result);

        int errors = 0;
        for (int i = 0; i < numElements; i++) {
            if (result[i] != c[i]) {
                errors++;
            }
        }

        return errors == 0;
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
