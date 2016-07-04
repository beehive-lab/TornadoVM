package tornado.benchmarks.sgemm;

import java.util.Random;
import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;
import tornado.collections.math.TornadoMath;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskGraph;

public class SgemmTornado extends BenchmarkDriver {

    private final DeviceMapping device;
    private final int m, n;

    private float[] a, b, c;

    private TaskGraph graph;

    public SgemmTornado(int iterations, int m, int n, DeviceMapping device) {
        super(iterations);
        this.m = m;
        this.n = n;
        this.device = device;
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

        graph = new TaskGraph()
                .add(LinearAlgebraArrays::sgemm, m, n, n, a, b,
                        c)
                .streamOut(c)
                .mapAllTo(device);

        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpTimes();
        graph.dumpProfiles();

        a = null;
        b = null;
        c = null;

        ((OCLDeviceMapping) device).reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.schedule().waitOn();
    }

    @Override
    public boolean validate() {

        final float[] result = new float[m * n];

        code();

        LinearAlgebraArrays.sgemm(m, n, m, a, b, result);

        final float ulp = TornadoMath.findULPDistance(c, result);
        return ulp < MAX_ULP;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf(
                    "id=opencl-device-%d, elapsed=%f, per iteration=%f\n",
                    ((OCLDeviceMapping) device).getDeviceIndex(), getElapsed(),
                    getElapsedPerIteration());
        } else {
            System.out.printf("id=opencl-device-%d produced invalid result\n",
                    ((OCLDeviceMapping) device).getDeviceIndex());
        }
    }

}
